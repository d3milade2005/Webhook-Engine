package com.webhookengine.app.delivery;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.webhookengine.app.entity.*;
import com.webhookengine.app.repository.DeadLetterEventRepository;
import com.webhookengine.app.repository.DeliveryAttemptRepository;
import com.webhookengine.app.repository.EventRepository;
import com.webhookengine.app.repository.WebhookEndpointRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class WebhookDeliveryWorker {

    private final EventRepository eventRepository;
    private final WebhookEndpointRepository endpointRepository;
    private final DeliveryAttemptRepository deliveryAttemptRepository;
    private final DeadLetterEventRepository deadLetterEventRepository;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    private static final int MAX_ATTEMPTS = 5;
    private static final long[] BACKOFF_SECONDS = {10, 30, 120, 600, 3600};
    private static final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();


    @KafkaListener(topics = KafkaConfig.WEBHOOK_TOPIC, groupId = "webhook-engine-group")
    public void consume(String rawMessage) {
        try {
            EventMessage message = objectMapper.readValue(rawMessage, EventMessage.class);

            Event event = eventRepository.findById(message.getEventId()).orElse(null);
            if (event == null) {
                log.warn("Event {} not found, skipping", message.getEventId());
                return;
            }

            List<WebhookEndpoint> endpoints = endpointRepository.findByTenantAndIsActiveTrue(event.getTenant());

            if (endpoints.isEmpty()) {
                log.warn("No active endpoints for tenant {}", event.getTenant().getId());
                event.setStatus(EventStatus.DELIVERED);
                eventRepository.save(event);
                return;
            }

            boolean allSucceeded = true;

            for (WebhookEndpoint endpoint : endpoints) {
                boolean success = deliverToEndpoint(event, endpoint);
                if (!success) allSucceeded = false;
            }

            // Only mark DELIVERED if every endpoint succeeded
            if (allSucceeded) {
                event.setStatus(EventStatus.DELIVERED);
                eventRepository.save(event);
                log.info("Event {} fully delivered to all endpoints", event.getId());
            }

        } catch (Exception e) {
            log.error("Failed to process Kafka message: {}", e.getMessage());
        }
    }

    private boolean deliverToEndpoint(Event event, WebhookEndpoint endpoint) {
        String idempotencyKey = "delivery:" + event.getDeliveryId() + ":" + endpoint.getId();

        // Check Redis: already delivered?
        Boolean alreadyDelivered = redisTemplate.hasKey(idempotencyKey);
        if (Boolean.TRUE.equals(alreadyDelivered)) {
            log.info("Event {} already delivered to endpoint {}, skipping", event.getId(), endpoint.getId());
            return true;
        }

        try {
            String payloadJson = objectMapper.writeValueAsString(event.getPayload());
            String signature = computeSignature(endpoint.getSecret(), payloadJson);

            long startTime = System.currentTimeMillis();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint.getUrl()))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json")
                    .header("X-Webhook-Signature", "sha256=" + signature)
                    .header("X-Webhook-Event", event.getEventType())
                    .header("X-Delivery-Id", event.getDeliveryId().toString())
                    .POST(HttpRequest.BodyPublishers.ofString(payloadJson))
                    .build();

            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());

            long latency = System.currentTimeMillis() - startTime;

            // Record the attempt
            recordAttempt(event, endpoint, response.statusCode(), response.body(), (int) latency);

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                // Success
                log.info("Delivered event {} to {} — HTTP {}", event.getId(), endpoint.getUrl(), response.statusCode());

                event.setStatus(EventStatus.DELIVERED);
                event.setAttemptCount(event.getAttemptCount() + 1);
                eventRepository.save(event);

                // Stamp Redis idempotency key -> 7 day TTL
                redisTemplate.opsForValue().set(idempotencyKey, "1", 7, TimeUnit.DAYS);

                return true;

            } else {
                // Failed
                log.warn("Delivery failed for event {} to {} — HTTP {}", event.getId(), endpoint.getUrl(), response.statusCode());
                handleFailure(event, endpoint, "HTTP " + response.statusCode());
                return false;
            }

        } catch (Exception e) {
            log.error("Exception delivering event {} to {}: {}", event.getId(), endpoint.getUrl(), e.getMessage());
            handleFailure(event, endpoint, e.getMessage());
            return false;
        }
    }

    private void handleFailure(Event event, WebhookEndpoint endpoint, String reason) {
        int attempts = event.getAttemptCount() + 1;
        event.setAttemptCount(attempts);

        if (attempts >= MAX_ATTEMPTS) {
            // Dead letter
            log.error("Event {} exhausted all retries, moving to dead letter", event.getId());
            event.setStatus(EventStatus.DEAD);
            eventRepository.save(event);

            DeadLetterEvent deadLetter = new DeadLetterEvent();
            deadLetter.setEvent(event);
            deadLetter.setEndpoint(endpoint);
            deadLetter.setReason(reason);
            deadLetterEventRepository.save(deadLetter);

        } else {
            // Schedule retry with exponential backoff + jitter
            int index = Math.min(attempts - 1, BACKOFF_SECONDS.length - 1);
            long backoffSeconds = BACKOFF_SECONDS[index];
            long jitter = (long) (Math.random() * 10); // up to 10s jitter
            event.setStatus(EventStatus.FAILED);
            event.setNextRetryAt(OffsetDateTime.now().plusSeconds(backoffSeconds + jitter));
            eventRepository.save(event);

            log.info("Event {} scheduled for retry {} in {}s", event.getId(), attempts, backoffSeconds + jitter);
        }
    }

    private void recordAttempt(Event event, WebhookEndpoint endpoint, int httpStatus, String responseBody, int latencyMs) {
        DeliveryAttempt attempt = new DeliveryAttempt();
        attempt.setEvent(event);
        attempt.setEndpoint(endpoint);
        attempt.setHttpStatus(httpStatus);
        attempt.setResponseBody(responseBody);
        attempt.setLatencyMs(latencyMs);
        deliveryAttemptRepository.save(attempt);
    }

    private String computeSignature(String secret, String payload) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        SecretKeySpec keySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        mac.init(keySpec);
        byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(hash);
    }
}

package com.webhookengine.app.delivery;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.webhookengine.app.entity.Event;
import com.webhookengine.app.entity.EventStatus;
import com.webhookengine.app.repository.EventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class EventScheduler {

    private final EventRepository eventRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Scheduled(fixedDelay = 5000) // runs every 5 seconds
    @Transactional
    public void pollAndPublish() {
        List<Event> events = eventRepository.findEventsReadyForDelivery(
                OffsetDateTime.now(),
                OffsetDateTime.now().minusMinutes(5) // anything IN_FLIGHT for 5+ mins is stuck
        );

        if (events.isEmpty()) return;

        log.info("Scheduler found {} event(s) ready for delivery", events.size());

        for (Event event : events) {
            try {
                // Mark as IN_FLIGHT so scheduler doesn't pick it up again
                event.setStatus(EventStatus.IN_FLIGHT);
                eventRepository.save(event);

                EventMessage message = new EventMessage(
                        event.getId(),
                        event.getDeliveryId(),
                        event.getTenant().getId()
                );

                String payload = objectMapper.writeValueAsString(message);

                // Partition key is tenantId ensures ordering per tenant
                kafkaTemplate.send(
                        KafkaConfig.WEBHOOK_TOPIC,
                        event.getTenant().getId().toString(),
                        payload
                );

                log.info("Published event {} to Kafka for tenant {}",
                        event.getId(), event.getTenant().getId());

            } catch (Exception e) {
                log.error("Failed to publish event {} to Kafka: {}", event.getId(), e.getMessage());
                // Reset back to PENDING so scheduler picks it up next cycle
                event.setStatus(EventStatus.PENDING);
                eventRepository.save(event);
            }
        }
    }
}

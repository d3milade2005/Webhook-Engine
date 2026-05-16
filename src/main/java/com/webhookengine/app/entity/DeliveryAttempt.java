package com.webhookengine.app.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;


@Entity
@Table(name="delivery_attempts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DeliveryAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "endpoint_id", nullable = false)
    private WebhookEndpoint endpoint;

    @Column(name = "http_status")
    private Integer httpStatus;

    @Column(name = "response_body", columnDefinition = "TEXT")
    private String responseBody;

    @Column(name = "latency_ms")
    private Integer latencyMs;

    @Column(name = "attempted_at", nullable = false, updatable = false)
    private OffsetDateTime attemptedAt;

    @PrePersist
    public void prePersist() {
        this.attemptedAt = OffsetDateTime.now();
    }
}

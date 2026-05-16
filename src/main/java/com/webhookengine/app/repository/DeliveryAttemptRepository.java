package com.webhookengine.app.repository;

import com.webhookengine.app.entity.DeliveryAttempt;
import com.webhookengine.app.entity.WebhookEndpoint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface DeliveryAttemptRepository extends JpaRepository<DeliveryAttempt, UUID> {
    List<DeliveryAttempt> findByEventId(UUID eventId);
}

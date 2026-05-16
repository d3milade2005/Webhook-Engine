package com.webhookengine.app.repository;

import com.webhookengine.app.entity.DeadLetterEvent;
import com.webhookengine.app.entity.DeliveryAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface DeadLetterEventRepository extends JpaRepository<DeadLetterEvent, UUID> {
    List<DeadLetterEvent> findByEndpointId(UUID endpointId);
}
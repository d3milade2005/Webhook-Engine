package com.webhookengine.app.repository;

import com.webhookengine.app.entity.Event;
import com.webhookengine.app.entity.EventStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface EventRepository extends JpaRepository<Event, UUID> {
    // Used by the scheduler to find events ready for delivery
    @Query("""
    SELECT e FROM Event e
    WHERE e.status = EventStatus.PENDING
    OR (e.status = EventStatus.FAILED AND e.nextRetryAt <= :now)
    OR (e.status = EventStatus.IN_FLIGHT AND e.createdAt <= :stuckThreshold)
    """)
    List<Event> findEventsReadyForDelivery(OffsetDateTime now, OffsetDateTime stuckThreshold);
    List<Event> findByTenantIdAndStatus(UUID tenantId, EventStatus status);
}

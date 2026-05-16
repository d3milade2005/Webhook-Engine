package com.webhookengine.app.service;

import com.webhookengine.app.config.TenantContext;
import com.webhookengine.app.dto.EventRequest;
import com.webhookengine.app.dto.EventResponse;
import com.webhookengine.app.entity.Event;
import com.webhookengine.app.entity.EventStatus;
import com.webhookengine.app.entity.Tenant;
import com.webhookengine.app.repository.EventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class EventService {
    private final EventRepository eventRepository;

    @Transactional
    public EventResponse ingest(EventRequest request) {
        Tenant tenant = TenantContext.getCurrentTenant();

        Event event = new Event();
        event.setTenant(tenant);
        event.setEventType(request.getEventType());
        event.setPayload(request.getPayload());
        event.setStatus(EventStatus.PENDING);

        Event saved = eventRepository.save(event);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<EventResponse> listEvents() {
        Tenant tenant = TenantContext.getCurrentTenant();
        return eventRepository
                .findByTenantIdAndStatus(tenant.getId(), EventStatus.PENDING)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private EventResponse toResponse(Event event) {
        return new EventResponse(
                event.getId(),
                event.getDeliveryId(),
                event.getEventType(),
                event.getStatus(),
                event.getAttemptCount(),
                event.getCreatedAt()
        );
    }
}

package com.webhookengine.app.dto;

import com.webhookengine.app.entity.EventStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@AllArgsConstructor
public class EventResponse {
    private UUID id;
    private UUID deliveryId;
    private String eventType;
    private EventStatus status;
    private int attemptCount;
    private OffsetDateTime createdAt;
}

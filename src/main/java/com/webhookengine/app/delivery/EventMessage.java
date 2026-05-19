package com.webhookengine.app.delivery;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class EventMessage {
    private UUID eventId;
    private UUID deliveryId;
    private UUID tenantId;
}

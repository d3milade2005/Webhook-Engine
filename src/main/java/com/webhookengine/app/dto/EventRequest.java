package com.webhookengine.app.dto;


import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Map;

@Getter
@AllArgsConstructor
public class EventRequest {
    @NotBlank(message = "Event type is required")
    private String eventType;

    @NotNull(message = "Payload is required")
    private Map<String, Object> payload;
}

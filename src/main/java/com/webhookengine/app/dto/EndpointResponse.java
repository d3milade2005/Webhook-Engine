package com.webhookengine.app.dto;


import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@AllArgsConstructor
public class EndpointResponse {
    private UUID id;
    private String url;
    private boolean isActive;
    private OffsetDateTime createdAt;
}

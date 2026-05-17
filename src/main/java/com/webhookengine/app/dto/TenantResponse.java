package com.webhookengine.app.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@AllArgsConstructor
public class TenantResponse {
    private UUID id;
    private String name;
    private String apiKey;
    private OffsetDateTime createdAt;
}

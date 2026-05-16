package com.webhookengine.app.dto;


import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@AllArgsConstructor
public class EndpointResponse {
    private UUID id;
    private String url;
    @JsonProperty("isActive")
    private Boolean isActive;
    private OffsetDateTime createdAt;
}

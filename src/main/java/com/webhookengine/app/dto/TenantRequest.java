package com.webhookengine.app.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class TenantRequest {
    @NotBlank(message="Name is required")
    private String name;
}

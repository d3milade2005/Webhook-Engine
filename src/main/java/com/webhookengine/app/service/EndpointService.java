package com.webhookengine.app.service;

import com.webhookengine.app.config.TenantContext;
import com.webhookengine.app.dto.EndpointRequest;
import com.webhookengine.app.dto.EndpointResponse;
import com.webhookengine.app.entity.Tenant;
import com.webhookengine.app.entity.WebhookEndpoint;
import com.webhookengine.app.repository.WebhookEndpointRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EndpointService {
    private final WebhookEndpointRepository webhookEndpointRepository;

    @Transactional
    public EndpointResponse register(EndpointRequest request) {
        Tenant tenant = TenantContext.getCurrentTenant();

        WebhookEndpoint endpoint = new WebhookEndpoint();
        endpoint.setTenant(tenant);
        endpoint.setUrl(request.getUrl());
        endpoint.setSecret(generateSecret());
        endpoint.setActive(true);

        WebhookEndpoint saved = webhookEndpointRepository.save(endpoint);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<EndpointResponse> listEndpoints() {
        Tenant tenant = TenantContext.getCurrentTenant();
        return webhookEndpointRepository.findByTenant(tenant)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public void deleteEndpoint(UUID endpointId) {
        Tenant tenant = TenantContext.getCurrentTenant();
        WebhookEndpoint endpoint = webhookEndpointRepository
                .findByIdAndTenant(endpointId, tenant)
                .orElseThrow(() -> new RuntimeException("Endpoint not found"));

        webhookEndpointRepository.delete(endpoint);
    }

    private String generateSecret() {
        return "whsec_" + UUID.randomUUID().toString().replace("-", "");
    }

    private EndpointResponse toResponse(WebhookEndpoint endpoint) {
        return new EndpointResponse(
                endpoint.getId(),
                endpoint.getUrl(),
                endpoint.isActive(),
                endpoint.getCreatedAt()
        );
    }
}

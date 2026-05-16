package com.webhookengine.app.repository;

import com.webhookengine.app.entity.Tenant;
import com.webhookengine.app.entity.WebhookEndpoint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WebhookEndpointRepository extends JpaRepository<WebhookEndpoint, UUID> {
    List<WebhookEndpoint> findByTenant(Tenant tenant);
    List<WebhookEndpoint> findByTenantAndIsActiveTrue(Tenant tenant);
    Optional<WebhookEndpoint> findByIdAndTenant(UUID id, Tenant tenant);
}

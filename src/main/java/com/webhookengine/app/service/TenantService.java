package com.webhookengine.app.service;

import com.webhookengine.app.dto.TenantRequest;
import com.webhookengine.app.dto.TenantResponse;
import com.webhookengine.app.entity.Tenant;
import com.webhookengine.app.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TenantService {

    private final TenantRepository tenantRepository;

    @Transactional
    public TenantResponse register(TenantRequest request) {
        Tenant tenant = new Tenant();
        tenant.setName(request.getName());
        tenant.setApiKey(generateApiKey());
        tenant.setActive(true);

        Tenant saved = tenantRepository.save(tenant);

        return new TenantResponse(
                saved.getId(),
                saved.getName(),
                saved.getApiKey(),  // returned once, never again
                saved.getCreatedAt()
        );
    }

    private String generateApiKey() {
        return "whk_" + UUID.randomUUID().toString().replace("-", "");
    }
}

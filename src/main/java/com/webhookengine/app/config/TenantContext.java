package com.webhookengine.app.config;

import com.webhookengine.app.entity.Tenant;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

// a utility class to get the logged-in tenant
public class TenantContext {
    public static Tenant getCurrentTenant() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof Tenant tenant) {
            return tenant;
        }
        throw new IllegalStateException("No authenticated tenant in context");
    }
}
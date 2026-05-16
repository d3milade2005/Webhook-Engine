-- Tenants
CREATE TABLE tenants (
     id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
     name VARCHAR(255) NOT NULL,
     api_key VARCHAR(255) NOT NULL UNIQUE,
     is_active BOOLEAN DEFAULT TRUE,
     created_at TIMESTAMPTZ DEFAULT NOW()
);

-- Webhook endpoints
CREATE TABLE webhook_endpoints (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    url TEXT NOT NULL,
    secret VARCHAR(255) NOT NULL,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_webhook_endpoints_tenant_id ON webhook_endpoints(tenant_id);

-- Event status enum
CREATE TYPE event_status AS ENUM ('PENDING', 'DELIVERED', 'FAILED', 'DEAD');

-- Events
CREATE TABLE events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    delivery_id UUID UNIQUE NOT NULL DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    event_type VARCHAR(255) NOT NULL,
    payload JSONB NOT NULL,
    status event_status DEFAULT 'PENDING',
    attempt_count INT DEFAULT 0,
    next_retry_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_events_tenant_id ON events(tenant_id);
CREATE INDEX idx_events_status ON events(status);
CREATE INDEX idx_events_next_retry_at ON events(next_retry_at);

-- Delivery attempts
CREATE TABLE delivery_attempts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id UUID NOT NULL REFERENCES events(id),
    endpoint_id UUID NOT NULL REFERENCES webhook_endpoints(id),
    http_status INT,
    response_body TEXT,
    latency_ms INT,
    attempted_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_delivery_attempts_event_id ON delivery_attempts(event_id);
CREATE INDEX idx_delivery_attempts_endpoint_id ON delivery_attempts(endpoint_id);

-- Dead letter events
CREATE TABLE dead_letter_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id UUID NOT NULL REFERENCES events(id),
    endpoint_id UUID NOT NULL REFERENCES webhook_endpoints(id),
    reason TEXT,
    dead_lettered_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_dead_letter_events_endpoint_id ON dead_letter_events(endpoint_id);
CREATE INDEX idx_dead_letter_events_dead_lettered_at ON dead_letter_events(dead_lettered_at);

-- Seed test tenant
INSERT INTO tenants (id, name, api_key, is_active)
VALUES (
   'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11',
   'Test Tenant',
   'key-abc123',
   TRUE
);
# Webhook Engine

A production-grade webhook delivery system modeled after how Stripe, GitHub, and Shopify handle outbound event notifications. When something happens inside your platform, the Webhook Engine reliably delivers that event to external endpoints registered by your customers with retries, dead-lettering, ordering guarantees, and delivery receipts.

---

## Architecture

```
Producer → POST /events
              ↓
         PostgreSQL (PENDING)
              ↓
       Scheduler polls every 5s
              ↓
         Kafka (partitioned by tenantId)
              ↓
        Worker consumes
          ↙        ↘
    Redis check    HTTP POST to endpoint
          ↓              ↓
    skip if dup    PostgreSQL (DELIVERED / FAILED)
                         ↓
                  FAILED → exponential backoff retry
                         ↓
                  max retries → dead-letter table
                         ↓
                  Python sidecar analyzes + alerts
```

---

## Tech Stack

| Technology | Role |
|---|---|
| Spring Boot 4.x | Ingestion API, delivery workers, retry scheduler |
| Apache Kafka | Delivery queue, partitioned by tenantId |
| PostgreSQL | Event persistence, delivery history, dead-letter table |
| Redis | Idempotency key store with TTL-based expiry |
| Python FastAPI | Dead-letter analyzer, failure pattern detection, alerting |
| Java 21 Virtual Threads | High-concurrency HTTP delivery without thread pool exhaustion |
| Flyway | Database schema versioning and migrations |
| Docker | Containerized infrastructure |

---

## Project Structure

```
webhook-engine/
├── src/
│   └── main/
│       ├── java/com/webhookengine/app/
│       │   ├── config/          # Security, Kafka, Redis, exception handling
│       │   ├── delivery/        # Kafka producer, consumer, retry scheduler
│       │   ├── domain/          # JPA entities
│       │   ├── repository/      # Spring Data JPA repositories
│       │   ├── service/         # Business logic
│       │   └── web/             # REST controllers + DTOs
│       └── resources/
│           ├── db/migration/    # Flyway SQL migrations
│           └── application.yaml
├── dead-letter-analyzer/        # Python FastAPI sidecar
│   ├── main.py
│   ├── requirements.txt
│   └── Dockerfile
├── docker-compose.yml
└── Dockerfile
```

---

## Getting Started

### Prerequisites

- Java 21
- Maven
- Docker + Docker Compose
- Python 3.12+ (for running the sidecar locally)

### 1. Clone the repository

```bash
git clone https://github.com/your-username/webhook-engine.git
cd webhook-engine
```

### 2. Set up environment variables

Create a `.env` file in the project root:

```properties
APP_DB_NAME=webhook_engine
APP_DB_USER=webhook_engine_user
APP_DB_PASSWORD=your_password
APP_DB_PORT=5432
APP_REDIS_PORT=6379
SLACK_WEBHOOK_URL=
```

Create a `local.properties` file for local Spring development:

```properties
APP_DB_HOST=localhost
APP_DB_PORT=5432
APP_DB_NAME=webhook_engine
APP_DB_USER=webhook_engine_user
APP_DB_PASSWORD=your_password
APP_REDIS_HOST=localhost
APP_REDIS_PORT=6379
APP_KAFKA_BOOTSTRAP_SERVERS=localhost:9092
```

### 3. Start infrastructure

```bash
docker compose up -d
```

This starts PostgreSQL, Redis, and Kafka. Flyway migrations run automatically on app startup.

### 4. Run the Spring app

```bash
./mvnw spring-boot:run
```

Or on Windows:

```powershell
./mvnw spring-boot:run
```

### 5. Run the Python sidecar

```bash
cd dead-letter-analyzer
pip install -r requirements.txt
uvicorn main:app --reload --port 8090
```

### 6. Run everything in Docker

```bash
docker compose up --build -d
```

---

## API Reference

### Tenant Management

#### Register a tenant
```http
POST /tenants/register
Content-Type: application/json

{
  "name": "Acme Corp"
}
```

Response:
```json
{
  "id": "uuid",
  "name": "Acme Corp",
  "apiKey": "whk_abc123...",
  "createdAt": "2026-01-01T00:00:00Z"
}
```

> **Important:** Store the `apiKey` securely — it is only returned once.

---

### Webhook Endpoints

All requests require `X-Api-Key` header.

#### Register an endpoint
```http
POST /webhooks/endpoints
X-Api-Key: whk_abc123...
Content-Type: application/json

{
  "url": "https://yourapp.com/webhooks"
}
```

#### List endpoints
```http
GET /webhooks/endpoints
X-Api-Key: whk_abc123...
```

#### Delete an endpoint
```http
DELETE /webhooks/endpoints/{id}
X-Api-Key: whk_abc123...
```

---

### Events

#### Ingest an event
```http
POST /events
X-Api-Key: whk_abc123...
Content-Type: application/json

{
  "eventType": "payment.completed",
  "payload": {
    "amount": 5000,
    "currency": "USD",
    "customerId": "cust_123"
  }
}
```

Response:
```json
{
  "id": "uuid",
  "deliveryId": "uuid",
  "eventType": "payment.completed",
  "status": "PENDING",
  "attemptCount": 0,
  "createdAt": "2026-01-01T00:00:00Z"
}
```

#### List events
```http
GET /events
X-Api-Key: whk_abc123...
```

---

## How It Works

### Authentication

Every request carries an `X-Api-Key` header. Spring Security resolves it to a tenant via a database lookup. Invalid or missing keys return `401`.

### Event Lifecycle

```
PENDING → IN_FLIGHT → DELIVERED
                   ↘ FAILED → (retry) → DELIVERED
                                      ↘ DEAD
```

### Delivery

The scheduler polls for `PENDING` and `FAILED` (past retry time) events every 5 seconds. It marks them `IN_FLIGHT` and publishes them to Kafka partitioned by `tenantId` — ensuring ordering per tenant and isolation between tenants.

The worker consumes from Kafka and makes HTTP POST requests to all active endpoints for that tenant. Each request is signed with HMAC-SHA256:

```
X-Webhook-Signature: sha256=<hmac>
X-Webhook-Event: payment.completed
X-Delivery-Id: <uuid>
```

### Retry Logic

Failed deliveries are retried with exponential backoff and jitter:

| Attempt | Wait |
|---|---|
| 1st retry | 10 seconds |
| 2nd retry | 30 seconds |
| 3rd retry | 2 minutes |
| 4th retry | 10 minutes |
| 5th retry | 1 hour |

After 5 failed attempts, the event is moved to the dead-letter table.

### Idempotency

Every delivery is stamped in Redis with a `deliveryId + endpointId` key (7-day TTL). If a worker crashes after a successful HTTP call but before committing to the database, the redelivered message is detected and skipped — preventing duplicate deliveries.

### HMAC Verification

Receiving endpoints can verify webhook authenticity:

```python
import hmac, hashlib

def verify(secret, payload, signature):
    expected = hmac.new(secret.encode(), payload.encode(), hashlib.sha256).hexdigest()
    return hmac.compare_digest(f"sha256={expected}", signature)
```

---

## Dead Letter Analyzer

The Python sidecar runs every 15 minutes and:

- Detects failure spikes (3+ dead letters in 1 hour per endpoint)
- Identifies error patterns:
    - `401` → auth token needs rotation
    - `404` → endpoint URL changed
    - `5xx` → downstream server issues
- Auto-disables endpoints failing consistently for 24+ hours
- Sends Slack alerts (configure `SLACK_WEBHOOK_URL`)

### Sidecar API

| Endpoint | Description |
|---|---|
| `GET /health` | Health check |
| `GET /analysis/run` | Manually trigger analysis |
| `GET /dead-letters` | List all dead letter events |
| `GET /dead-letters/stats` | Failure stats per endpoint |

---

## Database Schema

| Table | Purpose |
|---|---|
| `tenants` | Tenant registry with API keys |
| `webhook_endpoints` | Customer-registered delivery URLs |
| `events` | All inbound events and their status |
| `delivery_attempts` | Full history of every HTTP attempt |
| `dead_letter_events` | Events that exhausted all retries |

---

## Key Design Decisions

**Write-first ingestion** — events are persisted to PostgreSQL before any delivery attempt. No event is ever lost even if the worker crashes immediately after ingestion.

**Kafka partitioning by tenantId** — ensures events for a single tenant are processed in order, while preventing a slow or failing tenant from blocking others.

**Per-endpoint idempotency keys** — Redis tracks delivery per `deliveryId + endpointId`. On retry, already-delivered endpoints are skipped automatically.

**Exponential backoff with jitter** — prevents thundering herd problems when a downstream endpoint recovers and all retries fire simultaneously.

**Auto-disable broken endpoints** — the Python sidecar removes permanently broken endpoints from the delivery pool, stopping wasted retries.

---

## Environment Variables

### Spring App

| Variable | Description | Default |
|---|---|---|
| `APP_DB_HOST` | PostgreSQL host | `localhost` |
| `APP_DB_PORT` | PostgreSQL port | `5432` |
| `APP_DB_NAME` | Database name | `webhook_engine` |
| `APP_DB_USER` | Database user | — |
| `APP_DB_PASSWORD` | Database password | — |
| `APP_REDIS_HOST` | Redis host | `localhost` |
| `APP_REDIS_PORT` | Redis port | `6379` |
| `APP_KAFKA_BOOTSTRAP_SERVERS` | Kafka brokers | `localhost:9092` |
| `API_KEYS` | Comma-separated valid API keys | — |

### Dead Letter Analyzer

| Variable | Description |
|---|---|
| `DB_HOST` | PostgreSQL host |
| `DB_PORT` | PostgreSQL port |
| `DB_NAME` | Database name |
| `DB_USER` | Database user |
| `DB_PASSWORD` | Database password |
| `SLACK_WEBHOOK_URL` | Slack webhook for alerts (optional) |

---
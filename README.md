# Orvexa

> AI-native customer operations platform combining reliable backend services, grounded knowledge retrieval, controlled tool execution, and real-time interaction.

## Project status

**Phase 1 — Control Plane foundation complete**

The Java control plane now provides the first real product capability: tenant-scoped sessions, durable lifecycle events, PostgreSQL persistence, Flyway migrations, validation, error contracts, correlation IDs, API-key protection, pagination, and integration/unit tests.

## Architecture

```text
React + TypeScript
        |
     REST / WS
        |
  +-----+------------------+
  |                        |
  v                        v
Spring Boot             FastAPI
Control Plane          AI Gateway
  |                        |
  v                    +---+---+
PostgreSQL              |       |
                     Qdrant   Ollama
```

### Ownership rules

- **Spring Boot** owns business workflows, session lifecycle, durable business records, and audit events.
- **PostgreSQL** is the source of truth for durable application state.
- **FastAPI** owns AI orchestration, retrieval, model/provider adapters, and AI tool execution policy.
- **Qdrant** stores vector representations used for semantic retrieval; it is not the business system of record.
- **React** is the operator/customer interaction console.

## Phase 1 capabilities

### Session domain

A session contains:

- tenant scope
- creator/actor
- optional title
- lifecycle status: `ACTIVE`, `ESCALATED`, `CLOSED`
- optimistic version field
- last event sequence
- creation/update timestamps

Valid lifecycle transitions:

```text
ACTIVE -> ESCALATED -> CLOSED
ACTIVE -> CLOSED
```

A closed session cannot be reopened in Phase 1.

### Durable event log

Every session starts with a `SESSION_CREATED` event. Lifecycle changes also generate durable events.

Internal services can append events using a client-supplied idempotency key (`clientEventId`). Replaying the exact same event returns the existing event instead of creating a duplicate. Reusing the same key with different event data returns `409 IDEMPOTENCY_CONFLICT`.

### API protection

Phase 1 uses two development-friendly API-key headers:

- External API: `X-API-Key`
- Internal service API: `X-Internal-API-Key`

Tenant and actor context are supplied through `X-Tenant-Id` and `X-Actor-Id`. These are intentionally simple Phase 1 boundaries; a JWT/OIDC identity layer can replace them in a later phase.

### API

```text
POST   /api/v1/sessions
GET    /api/v1/sessions
GET    /api/v1/sessions/{sessionId}
PATCH  /api/v1/sessions/{sessionId}/status
GET    /api/v1/sessions/{sessionId}/events
POST   /internal/v1/sessions/{sessionId}/events
GET    /actuator/health
```

OpenAPI contract: `contracts/openapi/control-plane.yaml`

## Database migrations

Flyway owns schema changes. Phase 0's `V1__baseline.sql` remains intact and Phase 1 adds `V2__create_session_domain.sql`.

Do not edit an already-applied migration. Add a new versioned migration for future schema changes.

## Local run

Prerequisites:

- JDK 21
- Maven 3.9+
- Docker Desktop
- PostgreSQL is supplied by Docker Compose

Copy the environment template if desired:

```bash
cp .env.example .env
```

Start PostgreSQL:

```bash
docker compose up -d postgres
```

Run the control plane:

```bash
cd services/control-plane
mvn spring-boot:run
```

Health:

```text
http://localhost:8080/actuator/health
```

### Create a session

PowerShell:

```powershell
$tenant = [guid]::NewGuid()
$actor = [guid]::NewGuid()

curl.exe -X POST "http://localhost:8080/api/v1/sessions" `
  -H "X-API-Key: local-api-key" `
  -H "X-Tenant-Id: $tenant" `
  -H "X-Actor-Id: $actor" `
  -H "Content-Type: application/json" `
  -d '{"title":"Support conversation"}'
```

## Tests

The integration suite uses Testcontainers with PostgreSQL. Docker must be available when running it.

```bash
cd services/control-plane
mvn test
```

Tests cover:

- application smoke wiring
- REST request validation
- session creation
- Flyway + real PostgreSQL integration
- lifecycle transition rules
- durable creation events
- event idempotency/replay

## Engineering decisions

### PostgreSQL is the source of truth

The session lifecycle and durable event history are business state. They stay in PostgreSQL. Vector search remains a separate concern owned by Qdrant.

### Session row locking protects event sequencing

When appending an event, the control plane locks the session row, increments `last_event_sequence`, and persists the event in the same transaction. This prevents two concurrent writers from allocating the same session-local sequence number.

### No premature infrastructure

Phase 1 intentionally does not add Redis, Kafka/RabbitMQ, Kubernetes, SIP, or a multi-agent framework. Each can be introduced when a real requirement exists.

## Next phase

**Phase 2 — AI Gateway + RAG**

- FastAPI AI boundary
- document ingestion
- chunking and metadata
- FastEmbed embeddings
- Qdrant collection/indexing
- Ollama provider abstraction
- retrieval service
- source/citation model
- grounded answer generation
- abstention on insufficient evidence

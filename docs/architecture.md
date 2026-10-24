# Orvexa Architecture

Status: describes v0.2.0. Anything not yet built is marked **Planned**.

## 1. Goals and non-goals

**Goals**

- Keep business state (sessions, audit events) durable, ordered and tenant-scoped.
- Answer questions from a tenant's own documents, with citations, and refuse when evidence is weak.
- Keep the AI boundary replaceable: model, embedder and vector store sit behind small interfaces.
- Run the whole stack locally without paid services.

**Non-goals (for now)**

- Production-grade authentication, multi-region deployment, horizontal autoscaling.
- Redis, message brokers, Kubernetes or multi-agent orchestration. Each is added only when a
  measured requirement exists.

## 2. System context

```mermaid
flowchart LR
  C["Client / operator"] -->|"X-API-Key + X-Tenant-Id"| CP["Control Plane<br/>Spring Boot :8080"]
  CP -->|"JPA + Flyway"| PG[("PostgreSQL 17")]
  C -->|"X-Internal-API-Key + X-Tenant-Id"| GW["AI Gateway<br/>FastAPI :8000"]
  GW -->|"vectors + chunk text"| QD[("Qdrant :6333")]
  GW -->|"/api/chat"| OL["Ollama :11434"]
  CP -.->|"Planned: query"| GW
  GW -.->|"Planned: append events"| CP
```

| Component | Technology | Owns |
|---|---|---|
| Control plane | Java 21, Spring Boot 4.1, Spring Data JPA, Flyway | Sessions, lifecycle, event log |
| PostgreSQL | 17 | Durable business state (system of record) |
| AI gateway | Python 3.12, FastAPI, Pydantic | Ingestion, retrieval, prompting, abstention |
| Qdrant | latest image | Chunk vectors and chunk text (derived data) |
| Ollama | `llama3.2:3b` by default | Local text generation |
| FastEmbed | `BAAI/bge-small-en-v1.5` by default | Local embeddings |

**Why two services.** Session and audit logic benefits from a transactional JVM stack; retrieval
and model orchestration benefit from the Python AI ecosystem. The boundary is an HTTP contract
([ADR 0001](decisions/0001-two-service-backend.md)). Qdrant holds derived data and can be rebuilt
from source documents, so only PostgreSQL needs durability guarantees.

## 3. Control plane

### 3.1 Layering

```mermaid
flowchart TB
  F["Filters: CorrelationIdFilter, ApiKeyAuthenticationFilter"] --> CT["Controllers (api/controller)"]
  CT --> SV["Services (domain/service)"]
  SV --> RP["Repositories (domain/repository)"]
  RP --> DB[("PostgreSQL")]
  CT --- DTO["DTO records (api/dto)"]
  SV --- DM["Entities and enums (domain/model)"]
  EH["GlobalExceptionHandler"] -.-> CT
```

Controllers translate HTTP to service calls and never touch repositories. Services own
transactions. Entities do not leak into responses; DTO records do.

### 3.2 Data model

```mermaid
erDiagram
  SESSIONS ||--o{ SESSION_EVENTS : "has"
  SESSIONS {
    uuid id PK
    uuid tenant_id
    uuid created_by
    varchar title
    varchar status
    bigint last_event_sequence
    bigint version
    timestamptz created_at
    timestamptz updated_at
  }
  SESSION_EVENTS {
    uuid id PK
    uuid session_id FK
    uuid client_event_id
    bigint sequence_number
    varchar event_type
    varchar actor_type
    jsonb payload
    timestamptz occurred_at
  }
```

Constraints that carry correctness (see `V2__create_session_domain.sql`):

- `sessions.status` is checked against `ACTIVE | ESCALATED | CLOSED`.
- `session_events.actor_type` is checked against `USER | SYSTEM | AI | TOOL`.
- `UNIQUE (session_id, client_event_id)` enforces idempotency.
- `UNIQUE (session_id, sequence_number)` guarantees a gap-free, collision-free order per session.
- `event_type` is **not** constrained in SQL; the Java enum is the source of truth. Existing types:
  `SESSION_CREATED`, `SESSION_STATUS_CHANGED`, `MESSAGE_CREATED`, `TOOL_EXECUTION`, `AI_RESPONSE`,
  `ESCALATION_REQUESTED`, `ERROR`. Adding a type needs no migration.

### 3.3 Session lifecycle

```mermaid
stateDiagram-v2
  [*] --> ACTIVE
  ACTIVE --> ESCALATED
  ACTIVE --> CLOSED
  ESCALATED --> CLOSED
  CLOSED --> [*]
```

A same-state transition is an idempotent no-op. Leaving `CLOSED` is rejected with
`409 INVALID_STATE_TRANSITION`. Every real transition appends a `SESSION_STATUS_CHANGED` event in
the same transaction.

### 3.4 Event append: ordering and idempotency

```mermaid
sequenceDiagram
  participant K as Internal caller
  participant C as Controller
  participant S as SessionService
  participant DB as PostgreSQL
  K->>C: POST /internal/v1/sessions/{id}/events (clientEventId)
  C->>S: appendInternalEvent
  S->>DB: BEGIN, then SELECT session FOR UPDATE
  alt clientEventId already used
    S->>DB: find existing event
    alt same type, actor and payload
      S-->>K: 202 existing event (replay)
    else different content
      S-->>K: 409 IDEMPOTENCY_CONFLICT
    end
  else new event
    S->>S: sequence = last_event_sequence + 1
    S->>DB: INSERT event, UPDATE session
    S-->>K: 202 new event
  end
  S->>DB: COMMIT
```

The row lock serializes writers for one session, so the idempotency lookup and sequence allocation
are atomic. The two unique constraints are a backstop if the lock is ever bypassed. Writers on
*different* sessions do not block each other.

Consequence: **never hold this lock during a slow call.** The planned integration (section 5.4)
is designed around that rule.

### 3.5 Request context and errors

- `X-Correlation-Id` is accepted or generated, placed in the logging MDC, and returned in error bodies.
- External routes: `X-API-Key`, `X-Tenant-Id` (and `X-Actor-Id` on create). Internal routes:
  `X-Internal-API-Key`, `X-Internal-Caller`.
- Error body: `{timestamp, status, code, message, path, correlationId}`. Codes:
  `VALIDATION_ERROR`, `BAD_REQUEST`, `RESOURCE_NOT_FOUND`, `INVALID_STATE_TRANSITION`,
  `IDEMPOTENCY_CONFLICT`, `UNAUTHORIZED`.
- External reads resolve sessions by `(id, tenant_id)`, never by id alone.

## 4. AI gateway

### 4.1 Module map

| Module | Responsibility |
|---|---|
| `main.py` | App factory, auth and tenant dependencies, routes, error handlers |
| `rag.py` | `RagService`: ingest and query orchestration, prompt, citation parsing |
| `chunking.py` | Sliding word window (220 words, 40 overlap by default) |
| `embeddings.py` | `Embedder` interface, FastEmbed adapter, deterministic hash embedder for tests |
| `vectorstore.py` | Qdrant wrapper; every read and delete requires a tenant |
| `llm.py` | `LLMProvider` interface, Ollama adapter, `LLMUnavailableError` |
| `extract.py` | `.txt`, `.md`, `.pdf` text extraction |
| `config.py` | Settings from `GATEWAY_*` environment variables |

Dependencies point inward: `RagService` depends on three interfaces and knows nothing about
FastAPI, Qdrant or Ollama types. That is why the test suite runs with an in-memory Qdrant, a fake
embedder and a fake LLM, with no network.

### 4.2 Ingestion

```mermaid
sequenceDiagram
  participant K as Caller
  participant G as Gateway
  participant E as Embedder
  participant Q as Qdrant
  K->>G: POST /v1/ingest (title, text, documentId?)
  G->>G: validate, chunk into word windows
  G->>E: embed passages
  G->>Q: delete existing chunks for (tenant, documentId)
  G->>Q: upsert chunks (payload: tenant_id, document_id, chunk_index, title, text)
  G-->>K: 201 {documentId, chunkCount}
```

Chunk point IDs are deterministic (`uuid5(tenant:document:index)`), and prior chunks are deleted
first, so re-ingesting a document replaces it and leaves no orphans.

### 4.3 Query with abstention

```mermaid
sequenceDiagram
  participant K as Caller
  participant G as Gateway
  participant Q as Qdrant
  participant L as Ollama
  K->>G: POST /v1/query (question)
  G->>Q: search top_k, filtered by tenant_id
  Q-->>G: candidates with cosine scores
  G->>G: topScore = best candidate score
  alt no candidate >= min_score
    G-->>K: abstained = true, topScore (LLM not called)
  else evidence found
    G->>L: system prompt + numbered passages + question
    L-->>G: answer
    alt model replies INSUFFICIENT_EVIDENCE
      G-->>K: abstained = true
    else answer
      G->>G: parse [n] markers into citations
      G-->>K: answer, citations, grounded, topScore
    end
  end
```

Response fields: `answer`, `abstained`, `grounded`, `citations[{ref, documentId, chunkIndex,
title, snippet, score}]`, `topScore`. `grounded = false` means the model produced an answer
without citing any valid passage; callers should treat that as lower confidence.

`topScore` is returned even on abstention because it is the number used to tune `min_score`.

## 5. Cross-cutting concerns

### 5.1 Tenant isolation

| Layer | Mechanism |
|---|---|
| Control plane reads | Repositories resolve by `(id, tenant_id)` |
| Control plane internal append | Looks up by session id only. Trusted caller; see [security.md](security.md) |
| Gateway | Qdrant filter on `tenant_id` for every search and delete; no unscoped method exists |
| Tests | Gateway tests prove tenant B cannot read or delete tenant A's documents |

### 5.2 Failure modes

| Failure | Behavior | Recovery |
|---|---|---|
| Ollama down or model missing | `502 LLM_UNAVAILABLE`; abstention path still works | Start Ollama, `ollama pull` the model |
| Qdrant down | `/ready` returns 503; ingest and query fail | Restore Qdrant; data lives in its volume |
| Qdrant data lost | Retrieval returns nothing, so the gateway abstains | Re-ingest source documents (derived data) |
| PostgreSQL down | Control plane health fails; requests error | Restore the database; this is the system of record |
| Duplicate client retry | Replayed `clientEventId` returns the original event | None needed |
| Concurrent writers, same session | Serialized by row lock | None needed |
| Embedding model changed | Dimension mismatch against the existing collection | Recreate the collection and re-ingest |

### 5.3 Scaling and where it breaks first

1. **Gateway LLM calls are synchronous.** Each `/v1/query` holds a worker thread while the model
   generates; concurrency is bounded by the server thread pool and by Ollama. First fix: async
   endpoints with a bounded concurrency limit and a timeout budget.
2. **Hot sessions.** The row lock serializes appends per session. This is fine for chat-rate
   traffic; a single session receiving thousands of events per second would need a different design.
3. **Event reads** are paged (max 100) and indexed on `(session_id, sequence_number)`.
4. **Qdrant** scales independently of the services; payload indexes exist on `tenant_id` and
   `document_id`.
5. Both services are stateless, so they can run as multiple instances behind a load balancer
   once authentication moves off shared keys.

### 5.4 Planned: control plane to gateway integration (ADR 0005)

**Status: Planned, not implemented.**

```mermaid
sequenceDiagram
  participant U as Client
  participant CP as Control Plane
  participant DB as PostgreSQL
  participant GW as AI Gateway
  U->>CP: POST message (clientEventId)
  CP->>DB: txn 1: lock session, append MESSAGE_CREATED, commit
  CP->>GW: /v1/query (timeout, bounded retry, circuit breaker)
  alt answer
    CP->>DB: txn 2: append AI_RESPONSE with citations
  else abstained
    CP->>DB: txn 2: append AI_RESPONSE (abstained)
    CP->>CP: after N consecutive abstentions: ESCALATED + ESCALATION_REQUESTED
  else gateway failure
    CP->>DB: txn 2: append ERROR event
  end
  CP-->>U: events so far
```

Key rule: the gateway call happens **between** two short transactions, never inside one, so the
session row lock is not held during model latency.

## 6. Deployment topology (local)

```mermaid
flowchart LR
  subgraph compose["docker compose"]
    PG[("postgres")]
    QD[("qdrant")]
    OL["ollama"]
    GW["ai-gateway"]
  end
  CP["control plane<br/>mvn spring-boot:run"] --> PG
  GW --> QD
  GW --> OL
```

The control plane has a Dockerfile but is not in `docker-compose.yml`; it runs from Maven during
development. Volumes: `orvexa-postgres-data`, `qdrant-data`, `ollama-data`, `fastembed-models`.
Configuration and runbook: [operations.md](operations.md).

## 7. Technical debt register

| Item | Impact | Suggested fix |
|---|---|---|
| Shared API keys, header-trusted tenant | Not production authz | JWT/OIDC with tenant claim |
| Two different error shapes | Client complexity | Shared error contract in Phase 3 |
| No gateway correlation IDs | Hard cross-service tracing | Propagate `X-Correlation-Id` |
| Unbounded internal event payload | Memory/abuse risk | Size limit and schema per event type |
| Default keys accepted at startup | Easy misconfiguration | Fail fast outside a `dev` profile |
| Empty scaffold packages in the gateway (`api/`, `core/`, ...) | Misleading structure | Use them or delete them |

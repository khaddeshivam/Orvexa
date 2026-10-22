# Operations Runbook

Applies to the local Docker Compose stack in v0.2.0.

## 1. Services and ports

| Service | Port | Started by | Health |
|---|---|---|---|
| PostgreSQL | 5432 | compose | `pg_isready` (compose health check) |
| Qdrant | 6333 | compose | `http://localhost:6333/dashboard` |
| Ollama | 11434 | compose | `curl http://localhost:11434/api/tags` |
| AI gateway | 8000 | compose | `/health` (liveness), `/ready` (Qdrant reachable) |
| Control plane | 8080 | `mvn spring-boot:run` | `/actuator/health` |

Start order: Postgres, then the control plane; Qdrant and Ollama, then the gateway. Compose
declares the gateway's dependencies; the control plane is started manually.

## 2. Configuration

### Compose and shared (`.env`)

| Variable | Default | Purpose |
|---|---|---|
| `POSTGRES_DB` / `POSTGRES_USER` / `POSTGRES_PASSWORD` | `orvexa` / `orvexa` / `change-me` | Database credentials |
| `POSTGRES_PORT`, `QDRANT_PORT`, `OLLAMA_PORT`, `AI_GATEWAY_PORT` | 5432, 6333, 11434, 8000 | Published ports |
| `ORVEXA_INTERNAL_API_KEY` | `local-internal-key` | Gateway key (compose maps it to `GATEWAY_INTERNAL_API_KEY`) |
| `GATEWAY_OLLAMA_MODEL` | `llama3.2:3b` | Model name the gateway requests |

### Control plane

| Variable | Default | Purpose |
|---|---|---|
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5432/orvexa` | Database URL |
| `SPRING_DATASOURCE_USERNAME` / `_PASSWORD` | `orvexa` / `change-me` | Credentials |
| `CONTROL_PLANE_PORT` | `8080` | HTTP port |
| `ORVEXA_API_KEY` | `local-api-key` | External API key |
| `ORVEXA_INTERNAL_API_KEY` | `local-internal-key` | Internal API key |

### AI gateway (`GATEWAY_` prefix)

| Variable | Default | Purpose |
|---|---|---|
| `INTERNAL_API_KEY` | `local-internal-key` | Required header value |
| `QDRANT_URL` | `http://localhost:6333` | Vector store (`:memory:` for in-process dev) |
| `QDRANT_COLLECTION` | `orvexa_chunks` | Collection name |
| `EMBEDDER` | `fastembed` | `fastembed`, or `hash` (offline dev; no semantics) |
| `EMBEDDING_MODEL` | `BAAI/bge-small-en-v1.5` | Embedding model |
| `OLLAMA_BASE_URL` | `http://localhost:11434` | LLM endpoint |
| `OLLAMA_MODEL` | `llama3.2:3b` | LLM name |
| `LLM_TIMEOUT_SECONDS` | `120` | Per-call timeout |
| `CHUNK_WORDS` / `CHUNK_OVERLAP_WORDS` | `220` / `40` | Chunking |
| `TOP_K` | `5` | Candidates retrieved |
| `MIN_SCORE` | `0.5` | Abstention threshold (cosine similarity) |

## 3. Routine tasks

**Bring the stack up**

```bash
docker compose up -d
docker compose exec ollama ollama pull llama3.2:3b     # once
cd services/control-plane && mvn spring-boot:run
```

**Smoke test the gateway** (prints a score table; exits non-zero on wrong behavior)

```bash
cd services/ai-gateway && python scripts/smoke.py
```

**Tune the abstention threshold.** Run the smoke script. In-scope questions should score above
`MIN_SCORE`, out-of-scope below it. Set `GATEWAY_MIN_SCORE` between the two groups. If the groups
overlap, retrieval quality (chunking or model) is the problem, not the threshold.

**Reset all data** (destructive)

```bash
docker compose down -v
```

**Back up PostgreSQL** (the only system of record)

```bash
docker compose exec postgres pg_dump -U orvexa orvexa > orvexa-backup.sql
```

Qdrant does not need backing up for correctness: it holds derived data and is rebuilt by
re-ingesting documents.

## 4. Observability

- Control plane: Plain-text logs with `correlationId` in the MDC; send `X-Correlation-Id` to
  trace a request; error responses echo it. Actuator exposes `health`, `info`, `metrics`.
- Gateway: standard Uvicorn access logs. No correlation IDs or metrics yet (tracked in the debt register).

## 5. Troubleshooting

| Symptom | Likely cause | Fix |
|---|---|---|
| Gateway `/ready` returns 503 | Qdrant not reachable | `docker compose ps`; check `GATEWAY_QDRANT_URL` |
| `/v1/query` returns `502 LLM_UNAVAILABLE` | Ollama down, or model not pulled | `docker compose exec ollama ollama pull llama3.2:3b` |
| First query takes minutes | Embedding model downloading | Wait; it is cached in the `fastembed-models` volume |
| Everything abstains | `MIN_SCORE` too high, or no documents for this tenant | Check `topScore`; re-ingest under the same `X-Tenant-Id` |
| Junk questions get answers | `MIN_SCORE` too low | Raise it using the smoke script's scores |
| Vector size mismatch error after changing `EMBEDDING_MODEL` | Collection created with the old dimension | Delete the Qdrant collection (or `down -v`) and re-ingest |
| `401 UNAUTHORIZED` | Wrong or missing key header | Use `X-API-Key` (control plane) or `X-Internal-API-Key` (gateway and `/internal/**`) |
| `400 INVALID_TENANT` / validation error | `X-Tenant-Id` is not a UUID | Send a UUID |
| Control plane fails to start with a Flyway or validation error | Schema drift, or edited applied migration | Never edit applied migrations; add a new versioned one |
| Control plane cannot reach Postgres | Compose not up, wrong port | `docker compose up -d postgres`; check `SPRING_DATASOURCE_URL` |
| `docker compose up` port already in use | Another local service | Change the port in `.env` |

## 6. Release procedure

1. All CI jobs green on `main` (control plane, gateway, web, structure).
2. Run the verification checklist in [testing.md](testing.md#7-release-verification-checklist).
3. Update [CHANGELOG.md](../CHANGELOG.md).
4. Tag: `git tag -a vX.Y.Z -m "..." && git push origin vX.Y.Z`.

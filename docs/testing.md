# Testing Strategy

## 1. Approach

| Level | Goal | Tooling |
|---|---|---|
| Unit | Pure logic: chunking, citation parsing, state transitions | pytest, JUnit 5 |
| Component | One service against real or in-memory dependencies | pytest + in-memory Qdrant; Spring + Testcontainers PostgreSQL |
| Contract | Request and response shapes stay stable | OpenAPI files in `contracts/openapi/` (reviewed manually; not yet enforced in CI) |
| End to end | Real Qdrant + Ollama behave as the code assumes | `services/ai-gateway/scripts/smoke.py` (manual) |

Design choice: the gateway's collaborators (`Embedder`, `LLMProvider`, `VectorStore`) are
interfaces, so unit and component tests use a deterministic hash embedder, a fake LLM and an
in-memory Qdrant. The suite is fast (about a second) and needs no Docker, GPU or network.

## 2. Running

```bash
cd services/ai-gateway   && pip install -r requirements-dev.txt && pytest
cd services/control-plane && mvn test            # Docker required for Testcontainers
cd apps/web              && npm ci && npm run build
```

## 3. AI gateway coverage (34 tests)

| File | Tests | Covers |
|---|---|---|
| `test_rag.py` | 11 | Grounded answers and citations; abstention without an LLM call; model-initiated abstention; uncited and hallucinated citation handling; **tenant isolation on search and delete**; re-ingest replaces old chunks; delete; best score reported on abstention |
| `test_api.py` | 11 | Auth (401), tenant header validation, validation errors, camelCase wire format, file upload, unsupported type (415), empty file (422), LLM outage (502), delete, `/ready` up and down |
| `test_chunking.py` | 7 | Window and overlap math, edge cases, invalid parameters |
| `test_llm.py` | 4 | Ollama request shape, response parsing, failure mapping to `LLMUnavailableError` |
| `test_health.py` | 1 | Liveness |

## 4. Control plane coverage (7 test methods)

| Class | Tests | Covers |
|---|---|---|
| `SessionServiceIntegrationTest` | 4 | Real PostgreSQL via Testcontainers and Flyway: session plus creation event, lifecycle rules, idempotent replay |
| `SessionControllerUnitTest` | 2 | Required headers, title length validation |
| `OrvexaControlPlaneApplicationTests` | 1 | Context wiring |

## 5. What is not tested (read this before trusting the system)

- **Real model behavior.** No automated test runs a real Ollama or FastEmbed model. Whether the
  threshold separates good from bad questions is unknown until the smoke script is run and `evals/` is filled.
- **Retrieval quality.** `evals/questions.jsonl` and `expected_sources.jsonl` are empty. There is no
  hit-rate, citation-accuracy or abstention-precision measurement.
- **Concurrency under load.** The row-locking design is covered by constraints and a replay test, not
  by a multi-threaded stress test.
- **Authorization at the HTTP layer of the control plane.** Auth filter behavior (including the
  constant-time comparison) has no dedicated test yet.
- **Cross-service flows.** The services are not integrated; there is nothing to test end to end.
- **Web UI.** Build only; no component or e2e tests.
- **Contract drift.** OpenAPI files are not validated against the running services.

## 6. Recommended next tests

1. A control-plane test class for `ApiKeyAuthenticationFilter` (valid, invalid, missing, wrong-length key; internal vs external route).
2. A concurrent-append test: N threads appending to one session must produce sequence numbers 1..N with no gaps.
3. An eval runner over a 30 to 50 question set, publishing retrieval hit rate and abstention precision.
4. Contract tests that load the OpenAPI files and validate live responses.

## 7. Release verification checklist

Tick each box when it is true, before tagging a release.

- [ ] CI is green on `main` for all four jobs: control-plane, python-gateway, web, structure
- [ ] `mvn test` passes locally with Docker running
- [ ] `docker compose up -d` brings postgres, qdrant, ollama and ai-gateway to healthy
- [ ] `docker compose exec ollama ollama pull llama3.2:3b` completes
- [ ] `python services/ai-gateway/scripts/smoke.py` prints PASS against the real stack
- [ ] `GATEWAY_MIN_SCORE` set from the printed scores and recorded in `.env.example` or the docs
- [ ] A session can be created and its events listed through the control plane
- [ ] README, CHANGELOG and docs describe only what is actually built
- [ ] No secrets in the repository (`git log -p | grep -i -E "password|secret|api[_-]?key"` reviewed)

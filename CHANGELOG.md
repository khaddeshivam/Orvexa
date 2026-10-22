# Changelog

All notable changes to this project are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/), and the project uses [SemVer](https://semver.org/).

## [0.2.0]

### Added
- **AI Gateway** (`services/ai-gateway`, FastAPI): tenant-scoped document ingestion (JSON, `.txt`,
  `.md`, `.pdf`), word-window chunking, FastEmbed embeddings, Qdrant retrieval, grounded answers
  with citations through an Ollama provider, and abstention on weak evidence.
- Abstention returns the best similarity score (`topScore`) so the threshold can be tuned.
- `/ready` readiness probe that checks Qdrant; Docker Compose health check uses it.
- `scripts/smoke.py` end-to-end check and sample knowledge documents.
- Qdrant, Ollama and the AI Gateway in `docker-compose.yml`.
- OpenAPI contract for the gateway (`contracts/openapi/ai-gateway.yaml`).
- Documentation set: architecture, security model, operations runbook, testing strategy, ADRs 0003-0005.
- CI jobs for the gateway tests and the web build.

### Changed
- Control plane API-key check now uses a constant-time comparison.
- CI installs `requirements-dev.txt` so `pytest` is available; Python pinned to 3.12 to match the Dockerfile.

### Fixed
- Removed the empty `app/rag/` package that shadowed `app/rag.py` and made the gateway unimportable.

### Known limitations
See [README](README.md#known-limitations). The control plane and gateway are not integrated yet (Phase 3).

## [0.1.0]

### Added
- Spring Boot control plane: tenant-scoped sessions, lifecycle state machine
  (`ACTIVE -> ESCALATED -> CLOSED`), durable per-session event log with idempotent appends,
  Flyway migrations, API-key protection, correlation IDs, Testcontainers integration tests.

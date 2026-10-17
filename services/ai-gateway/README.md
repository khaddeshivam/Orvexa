# AI Gateway (Phase 2)

FastAPI service that owns AI orchestration for Orvexa: ingestion, retrieval, grounded answers
with citations, and abstention when evidence is weak. Spring Boot stays the system of record;
this service stores only vectors.

## Endpoints

All `/v1/*` routes require `X-Internal-API-Key` and `X-Tenant-Id` (UUID).

| Method | Path | Purpose |
|---|---|---|
| POST | `/v1/ingest` | `{title, text, documentId?}` -> chunk, embed, index (re-ingest replaces) |
| POST | `/v1/ingest/file` | multipart `.txt` / `.md` / `.pdf` |
| POST | `/v1/query` | `{question}` -> answer + citations, or abstention |
| DELETE | `/v1/documents/{id}` | remove a document's chunks |
| GET | `/health` | liveness |

## Query pipeline

1. Embed question, search Qdrant **filtered by tenant**.
2. Drop hits below `GATEWAY_MIN_SCORE`. If none remain: abstain, **LLM is never called**.
3. Prompt the model with numbered passages; it must cite `[n]` or reply `INSUFFICIENT_EVIDENCE`.
4. Parse `[n]` markers into citations. `grounded=false` means the model cited nothing valid.

## Run locally

From the repository root, start the containerized stack:

    docker compose up -d
    docker compose exec ollama ollama pull llama3.2:3b
    curl http://localhost:8000/health

The first query downloads the embedding model (~130 MB) into the `fastembed-models` volume.

For gateway-only development, start Qdrant and Ollama from the repository root, then run FastAPI
from this directory:

    docker compose up -d qdrant ollama
    docker compose exec ollama ollama pull llama3.2:3b
    python -m venv .venv && .venv\Scripts\activate      # PowerShell
    pip install -r requirements-dev.txt
    uvicorn app.main:app --reload --port 8000

## Tests

    pytest

Uses an in-memory Qdrant, a deterministic hash embedder and a fake LLM, so no Docker, Ollama
or model download is needed.

## Known limits

- `min_score` default (0.5) is a guess for bge-small; tune it on the Phase 6 eval set.
- Chunking is a plain word window; it ignores headings and tables.
- Sync endpoints (run in FastAPI's threadpool). Fine for now; revisit if the LLM call dominates.

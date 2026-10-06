# ADR 0002: Local-first AI Gateway

## Decision

Phase 2 keeps model inference local through Ollama and semantic retrieval local through Qdrant. FastEmbed provides local embeddings.

## Why

- avoids mandatory paid model APIs during development
- keeps provider integration behind a replaceable boundary
- makes RAG behavior reproducible enough for local evaluation
- keeps business state separate from vector state

## Consequences

Ollama must be installed/running locally for grounded answer generation. Embedding model weights are downloaded on first use. Hosted model providers can be added later behind the same provider abstraction.

# Orvexa Architecture

## Phase 0 decision

Orvexa uses a deliberately small polyglot backend:

- Java/Spring Boot for business and control-plane responsibilities.
- Python/FastAPI for AI orchestration and retrieval responsibilities.
- PostgreSQL as durable system of record.
- Qdrant as semantic retrieval store (introduced in the RAG phase).
- Ollama as local-first LLM runtime (introduced in the RAG phase).

The project will not introduce Kubernetes, Kafka, RabbitMQ, Redis, SIP, or multi-agent orchestration into the MVP without a concrete engineering requirement.

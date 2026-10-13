from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_prefix="GATEWAY_", env_file=".env", extra="ignore")

    internal_api_key: str = "local-internal-key"

    qdrant_url: str = "http://localhost:6333"  # ":memory:" for an in-process store
    qdrant_collection: str = "orvexa_chunks"

    embedder: str = "fastembed"  # "fastembed" | "hash" (offline dev only)
    embedding_model: str = "BAAI/bge-small-en-v1.5"

    ollama_base_url: str = "http://localhost:11434"
    ollama_model: str = "llama3.2:3b"
    llm_timeout_seconds: float = 120.0

    # Chunk sizes are in words, a rough proxy for tokens (~0.75 words/token).
    chunk_words: int = 220
    chunk_overlap_words: int = 40

    top_k: int = 5
    # Cosine similarity floor. Below it we abstain without calling the LLM.
    # Tune against the Phase 6 eval set; do not trust this default.
    min_score: float = 0.5

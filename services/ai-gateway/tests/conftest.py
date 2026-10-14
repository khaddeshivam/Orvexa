import uuid

import pytest
from qdrant_client import QdrantClient

from app.embeddings import HashEmbedder
from app.rag import RagService
from app.vectorstore import VectorStore


class FakeLLM:
    def __init__(self, reply: str = "Refunds are processed within five business days [1]."):
        self.reply = reply
        self.calls: list[str] = []

    def generate(self, system: str, prompt: str) -> str:
        self.calls.append(prompt)
        return self.reply


@pytest.fixture
def llm() -> FakeLLM:
    return FakeLLM()


@pytest.fixture
def rag(llm) -> RagService:
    embedder = HashEmbedder()
    store = VectorStore(QdrantClient(":memory:"), "test_chunks", embedder.dim)
    return RagService(
        embedder, store, llm, chunk_words=50, chunk_overlap_words=10, top_k=3, min_score=0.2
    )


@pytest.fixture
def tenant_a() -> str:
    return str(uuid.uuid4())


@pytest.fixture
def tenant_b() -> str:
    return str(uuid.uuid4())


REFUND_DOC = "Refunds are processed within five business days after approval."

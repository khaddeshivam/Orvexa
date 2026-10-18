import re
import uuid

from .chunking import chunk_text
from .embeddings import Embedder
from .llm import LLMProvider
from .models import Citation, IngestResponse, QueryResponse
from .vectorstore import Hit, VectorStore

SYSTEM_PROMPT = (
    "You are a customer-operations assistant. Answer the question using ONLY the numbered "
    "context passages. Cite the passages you used like [1] or [2]. If the context does not "
    "contain the answer, reply with exactly: INSUFFICIENT_EVIDENCE"
)
ABSTAIN_MESSAGE = "I don't have enough information in the knowledge base to answer that."
ABSTAIN_TOKEN = "INSUFFICIENT_EVIDENCE"


class EmptyDocumentError(ValueError):
    pass


class RagService:
    def __init__(
        self,
        embedder: Embedder,
        store: VectorStore,
        llm: LLMProvider,
        *,
        chunk_words: int,
        chunk_overlap_words: int,
        top_k: int,
        min_score: float,
    ):
        self.embedder = embedder
        self.store = store
        self.llm = llm
        self.chunk_words = chunk_words
        self.chunk_overlap_words = chunk_overlap_words
        self.top_k = top_k
        self.min_score = min_score

    def ingest(
        self, tenant_id: str, title: str, text: str, document_id: str | None = None
    ) -> IngestResponse:
        chunks = chunk_text(text, self.chunk_words, self.chunk_overlap_words)
        if not chunks:
            raise EmptyDocumentError("document has no text")
        doc_id = document_id or str(uuid.uuid4())

        vectors = self.embedder.embed_passages(chunks)
        # Replace semantics: drop stale chunks first so a shorter re-ingest leaves no orphans.
        self.store.delete_document(tenant_id, doc_id)
        self.store.upsert_chunks(tenant_id, doc_id, title, chunks, vectors)
        return IngestResponse(document_id=doc_id, chunk_count=len(chunks))

    def delete_document(self, tenant_id: str, document_id: str) -> None:
        self.store.delete_document(tenant_id, document_id)

    def query(self, tenant_id: str, question: str) -> QueryResponse:
        candidates = self.store.search(tenant_id, self.embedder.embed_query(question), self.top_k)
        # Report the best score even when we abstain: it is the number you tune min_score with.
        top_score = max((h.score for h in candidates), default=None)
        hits = [h for h in candidates if h.score >= self.min_score]

        if not hits:
            return self._abstain(top_score)

        raw = self.llm.generate(SYSTEM_PROMPT, self._build_prompt(question, hits))
        if ABSTAIN_TOKEN in raw:
            return self._abstain(top_score)

        cited = self._cited_refs(raw, len(hits))
        refs = cited or list(range(1, len(hits) + 1))
        return QueryResponse(
            answer=raw,
            abstained=False,
            grounded=bool(cited),
            citations=[self._citation(ref, hits[ref - 1]) for ref in refs],
            top_score=top_score,
        )

    @staticmethod
    def _abstain(top_score: float | None) -> QueryResponse:
        return QueryResponse(
            answer=ABSTAIN_MESSAGE, abstained=True, grounded=False, citations=[], top_score=top_score
        )

    @staticmethod
    def _build_prompt(question: str, hits: list[Hit]) -> str:
        context = "\n\n".join(f"[{i}] ({h.title}) {h.text}" for i, h in enumerate(hits, start=1))
        return f"Context:\n{context}\n\nQuestion: {question}"

    @staticmethod
    def _cited_refs(answer: str, n_hits: int) -> list[int]:
        seen: list[int] = []
        for m in re.findall(r"\[(\d+)\]", answer):
            ref = int(m)
            if 1 <= ref <= n_hits and ref not in seen:
                seen.append(ref)
        return seen

    @staticmethod
    def _citation(ref: int, hit: Hit) -> Citation:
        return Citation(
            ref=ref,
            document_id=hit.document_id,
            chunk_index=hit.chunk_index,
            title=hit.title,
            snippet=hit.text[:240],
            score=round(hit.score, 4),
        )

import uuid
from dataclasses import dataclass

from qdrant_client import QdrantClient
from qdrant_client import models as qm

_NS = uuid.UUID("6f1c2a9e-0b7d-4c1e-9a53-3f2d8e7b4a10")


@dataclass(frozen=True)
class Hit:
    document_id: str
    chunk_index: int
    title: str
    text: str
    score: float


def _tenant_filter(tenant_id: str, document_id: str | None = None) -> qm.Filter:
    must = [qm.FieldCondition(key="tenant_id", match=qm.MatchValue(value=tenant_id))]
    if document_id is not None:
        must.append(qm.FieldCondition(key="document_id", match=qm.MatchValue(value=document_id)))
    return qm.Filter(must=must)


class VectorStore:
    """Qdrant wrapper. Every read and delete is scoped by tenant_id: there is no unscoped path."""

    def __init__(self, client: QdrantClient, collection: str, dim: int):
        self.client = client
        self.collection = collection
        if not client.collection_exists(collection):
            client.create_collection(
                collection_name=collection,
                vectors_config=qm.VectorParams(size=dim, distance=qm.Distance.COSINE),
            )
            for field in ("tenant_id", "document_id"):
                client.create_payload_index(collection, field, qm.PayloadSchemaType.KEYWORD)

    def upsert_chunks(
        self,
        tenant_id: str,
        document_id: str,
        title: str,
        chunks: list[str],
        vectors: list[list[float]],
    ) -> None:
        points = [
            qm.PointStruct(
                # Deterministic id: re-ingesting the same chunk overwrites instead of duplicating.
                id=str(uuid.uuid5(_NS, f"{tenant_id}:{document_id}:{i}")),
                vector=vec,
                payload={
                    "tenant_id": tenant_id,
                    "document_id": document_id,
                    "chunk_index": i,
                    "title": title,
                    "text": text,
                },
            )
            for i, (text, vec) in enumerate(zip(chunks, vectors, strict=True))
        ]
        self.client.upsert(collection_name=self.collection, points=points)

    def delete_document(self, tenant_id: str, document_id: str) -> None:
        self.client.delete(
            collection_name=self.collection,
            points_selector=qm.FilterSelector(filter=_tenant_filter(tenant_id, document_id)),
        )

    def search(self, tenant_id: str, vector: list[float], limit: int) -> list[Hit]:
        res = self.client.query_points(
            collection_name=self.collection,
            query=vector,
            query_filter=_tenant_filter(tenant_id),
            limit=limit,
            with_payload=True,
        )
        return [
            Hit(
                document_id=p.payload["document_id"],
                chunk_index=p.payload["chunk_index"],
                title=p.payload["title"],
                text=p.payload["text"],
                score=p.score,
            )
            for p in res.points
        ]

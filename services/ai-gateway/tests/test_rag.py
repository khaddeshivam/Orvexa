from qdrant_client import models as qm

from tests.conftest import REFUND_DOC


def _stored_chunks(rag, tenant_id: str, document_id: str) -> int:
    flt = qm.Filter(
        must=[
            qm.FieldCondition(key="tenant_id", match=qm.MatchValue(value=tenant_id)),
            qm.FieldCondition(key="document_id", match=qm.MatchValue(value=document_id)),
        ]
    )
    return rag.store.client.count(rag.store.collection, count_filter=flt, exact=True).count


def test_in_scope_question_is_answered_with_citation(rag, llm, tenant_a):
    rag.ingest(tenant_a, "Refund policy", REFUND_DOC, document_id="refunds")
    out = rag.query(tenant_a, "How many business days do refunds take?")

    assert not out.abstained
    assert out.grounded
    assert out.citations[0].document_id == "refunds"
    assert out.citations[0].title == "Refund policy"
    assert len(llm.calls) == 1
    assert REFUND_DOC in llm.calls[0]


def test_out_of_scope_question_abstains_without_calling_llm(rag, llm, tenant_a):
    rag.ingest(tenant_a, "Refund policy", REFUND_DOC)
    out = rag.query(tenant_a, "What is the capital of France?")

    assert out.abstained
    assert out.citations == []
    assert llm.calls == []


def test_model_can_abstain_itself(rag, llm, tenant_a):
    llm.reply = "INSUFFICIENT_EVIDENCE"
    rag.ingest(tenant_a, "Refund policy", REFUND_DOC)
    out = rag.query(tenant_a, "How many business days do refunds take?")

    assert out.abstained
    assert out.citations == []


def test_uncited_answer_is_flagged_not_grounded(rag, llm, tenant_a):
    llm.reply = "Five business days."
    rag.ingest(tenant_a, "Refund policy", REFUND_DOC)
    out = rag.query(tenant_a, "How many business days do refunds take?")

    assert not out.abstained
    assert not out.grounded
    assert out.citations  # falls back to retrieved passages


def test_hallucinated_citation_index_is_ignored(rag, llm, tenant_a):
    llm.reply = "Five days [7]."
    rag.ingest(tenant_a, "Refund policy", REFUND_DOC)
    out = rag.query(tenant_a, "How many business days do refunds take?")

    assert not out.grounded


def test_tenants_cannot_see_each_others_documents(rag, llm, tenant_a, tenant_b):
    rag.ingest(tenant_a, "Refund policy", REFUND_DOC)

    out = rag.query(tenant_b, "How many business days do refunds take?")
    assert out.abstained
    assert llm.calls == []


def test_tenant_cannot_delete_another_tenants_document(rag, tenant_a, tenant_b):
    rag.ingest(tenant_a, "Refund policy", REFUND_DOC, document_id="refunds")
    rag.delete_document(tenant_b, "refunds")

    assert not rag.query(tenant_a, "How many business days do refunds take?").abstained


def test_reingest_replaces_old_chunks(rag, tenant_a):
    long_text = " ".join(f"alpha{i}" for i in range(200))
    first = rag.ingest(tenant_a, "Doc", long_text, document_id="d1")
    assert first.chunk_count > 1

    second = rag.ingest(tenant_a, "Doc", REFUND_DOC, document_id="d1")
    assert second.chunk_count == 1
    assert _stored_chunks(rag, tenant_a, "d1") == 1  # no orphans from the longer version


def test_delete_document_removes_it(rag, tenant_a):
    rag.ingest(tenant_a, "Refund policy", REFUND_DOC, document_id="refunds")
    rag.delete_document(tenant_a, "refunds")

    assert rag.query(tenant_a, "How many business days do refunds take?").abstained

import uuid

import pytest
from fastapi.testclient import TestClient

from app.config import Settings
from app.llm import LLMUnavailableError
from app.main import create_app
from tests.conftest import REFUND_DOC

KEY = "test-key"


@pytest.fixture
def client(rag):
    app = create_app(Settings(internal_api_key=KEY), rag=rag)
    with TestClient(app) as c:
        yield c


def headers(tenant: str, key: str = KEY) -> dict:
    return {"X-Internal-API-Key": key, "X-Tenant-Id": tenant}


def test_health_is_open(client):
    assert client.get("/health").json() == {"status": "ok"}


def test_missing_or_wrong_key_is_401(client, tenant_a):
    body = {"question": "hi"}
    assert client.post("/v1/query", json=body, headers={"X-Tenant-Id": tenant_a}).status_code == 401
    assert client.post("/v1/query", json=body, headers=headers(tenant_a, "nope")).status_code == 401


def test_bad_tenant_header_is_400(client):
    r = client.post("/v1/query", json={"question": "hi"}, headers={"X-Internal-API-Key": KEY})
    assert r.status_code == 400
    assert r.json()["code"] == "INVALID_TENANT"


def test_blank_question_is_422(client, tenant_a):
    r = client.post("/v1/query", json={"question": ""}, headers=headers(tenant_a))
    assert r.status_code == 422
    assert r.json()["code"] == "VALIDATION_ERROR"


def test_ingest_then_query_uses_camel_case(client, tenant_a):
    r = client.post(
        "/v1/ingest",
        json={"title": "Refund policy", "text": REFUND_DOC, "documentId": "refunds"},
        headers=headers(tenant_a),
    )
    assert r.status_code == 201
    assert r.json() == {"documentId": "refunds", "chunkCount": 1}

    r = client.post(
        "/v1/query",
        json={"question": "How many business days do refunds take?"},
        headers=headers(tenant_a),
    )
    body = r.json()
    assert r.status_code == 200
    assert body["abstained"] is False and body["grounded"] is True
    assert body["citations"][0]["documentId"] == "refunds"
    assert "topScore" in body


def test_file_upload_txt(client, tenant_a):
    r = client.post(
        "/v1/ingest/file",
        files={"file": ("refunds.txt", REFUND_DOC.encode(), "text/plain")},
        data={"documentId": "refunds"},
        headers=headers(tenant_a),
    )
    assert r.status_code == 201
    assert r.json()["documentId"] == "refunds"


def test_unsupported_file_type_is_415(client, tenant_a):
    r = client.post(
        "/v1/ingest/file",
        files={"file": ("x.exe", b"MZ", "application/octet-stream")},
        headers=headers(tenant_a),
    )
    assert r.status_code == 415


def test_empty_file_is_422(client, tenant_a):
    r = client.post(
        "/v1/ingest/file",
        files={"file": ("empty.txt", b"   ", "text/plain")},
        headers=headers(tenant_a),
    )
    assert r.status_code == 422
    assert r.json()["code"] == "EMPTY_DOCUMENT"


def test_llm_down_is_502(client, rag, tenant_a):
    client.post(
        "/v1/ingest", json={"title": "Refund policy", "text": REFUND_DOC}, headers=headers(tenant_a)
    )

    def boom(system, prompt):
        raise LLMUnavailableError("ollama down")

    rag.llm.generate = boom
    r = client.post(
        "/v1/query",
        json={"question": "How many business days do refunds take?"},
        headers=headers(tenant_a),
    )
    assert r.status_code == 502
    assert r.json()["code"] == "LLM_UNAVAILABLE"


def test_delete_document(client, tenant_a):
    client.post(
        "/v1/ingest",
        json={"title": "Refund policy", "text": REFUND_DOC, "documentId": "refunds"},
        headers=headers(tenant_a),
    )
    assert client.delete("/v1/documents/refunds", headers=headers(tenant_a)).status_code == 204
    r = client.post(
        "/v1/query",
        json={"question": "How many business days do refunds take?"},
        headers=headers(tenant_a),
    )
    assert r.json()["abstained"] is True

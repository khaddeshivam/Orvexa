import secrets
import uuid
from contextlib import asynccontextmanager

from fastapi import Depends, FastAPI, File, Form, Header, Request, UploadFile
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse, Response
from qdrant_client import QdrantClient

from .config import Settings
from .embeddings import Embedder, FastEmbedEmbedder, HashEmbedder
from .extract import UnsupportedFileError, extract_text
from .llm import LLMUnavailableError, OllamaProvider
from .models import IngestRequest, IngestResponse, QueryRequest, QueryResponse
from .rag import EmptyDocumentError, RagService
from .vectorstore import VectorStore

MAX_UPLOAD_BYTES = 10 * 1024 * 1024


class ApiError(Exception):
    def __init__(self, status: int, code: str, message: str):
        self.status, self.code, self.message = status, code, message


def build_rag(settings: Settings) -> RagService:
    embedder: Embedder = (
        HashEmbedder() if settings.embedder == "hash" else FastEmbedEmbedder(settings.embedding_model)
    )
    client = (
        QdrantClient(":memory:")
        if settings.qdrant_url == ":memory:"
        else QdrantClient(url=settings.qdrant_url)
    )
    store = VectorStore(client, settings.qdrant_collection, embedder.dim)
    llm = OllamaProvider(settings.ollama_base_url, settings.ollama_model, settings.llm_timeout_seconds)
    return RagService(
        embedder,
        store,
        llm,
        chunk_words=settings.chunk_words,
        chunk_overlap_words=settings.chunk_overlap_words,
        top_k=settings.top_k,
        min_score=settings.min_score,
    )


def create_app(settings: Settings | None = None, rag: RagService | None = None) -> FastAPI:
    settings = settings or Settings()

    @asynccontextmanager
    async def lifespan(app: FastAPI):
        app.state.rag = rag or build_rag(settings)
        yield

    app = FastAPI(title="Orvexa AI Gateway", version="0.2.0", lifespan=lifespan)
    app.state.settings = settings
    if rag is not None:
        app.state.rag = rag

    @app.exception_handler(ApiError)
    async def api_error_handler(_: Request, exc: ApiError):
        return JSONResponse(status_code=exc.status, content={"code": exc.code, "message": exc.message})

    @app.exception_handler(RequestValidationError)
    async def validation_handler(_: Request, exc: RequestValidationError):
        return JSONResponse(
            status_code=422,
            content={"code": "VALIDATION_ERROR", "message": "invalid request", "errors": exc.errors()},
        )

    def require_internal_key(
        request: Request, x_internal_api_key: str | None = Header(default=None)
    ) -> None:
        expected = request.app.state.settings.internal_api_key
        if not x_internal_api_key or not secrets.compare_digest(x_internal_api_key, expected):
            raise ApiError(401, "UNAUTHORIZED", "missing or invalid X-Internal-API-Key")

    def tenant_id(x_tenant_id: str | None = Header(default=None)) -> str:
        try:
            return str(uuid.UUID(x_tenant_id or ""))
        except ValueError:
            raise ApiError(400, "INVALID_TENANT", "X-Tenant-Id must be a UUID") from None

    def get_rag(request: Request) -> RagService:
        return request.app.state.rag

    @app.get("/health")
    def health():
        return {"status": "ok"}

    protected = [Depends(require_internal_key)]

    @app.post("/v1/ingest", response_model=IngestResponse, status_code=201, dependencies=protected)
    def ingest(body: IngestRequest, tenant: str = Depends(tenant_id), svc: RagService = Depends(get_rag)):
        try:
            return svc.ingest(tenant, body.title, body.text, body.document_id)
        except EmptyDocumentError as exc:
            raise ApiError(422, "EMPTY_DOCUMENT", str(exc)) from exc

    @app.post("/v1/ingest/file", response_model=IngestResponse, status_code=201, dependencies=protected)
    def ingest_file(
        file: UploadFile = File(...),
        title: str | None = Form(default=None),
        document_id: str | None = Form(default=None, alias="documentId"),
        tenant: str = Depends(tenant_id),
        svc: RagService = Depends(get_rag),
    ):
        filename = file.filename or "upload"
        data = file.file.read(MAX_UPLOAD_BYTES + 1)
        if len(data) > MAX_UPLOAD_BYTES:
            raise ApiError(413, "FILE_TOO_LARGE", "max upload size is 10 MB")
        try:
            text = extract_text(filename, data)
            return svc.ingest(tenant, title or filename, text, document_id)
        except UnsupportedFileError as exc:
            raise ApiError(415, "UNSUPPORTED_FILE_TYPE", str(exc)) from exc
        except EmptyDocumentError as exc:
            raise ApiError(422, "EMPTY_DOCUMENT", str(exc)) from exc

    @app.post("/v1/query", response_model=QueryResponse, dependencies=protected)
    def query(body: QueryRequest, tenant: str = Depends(tenant_id), svc: RagService = Depends(get_rag)):
        try:
            return svc.query(tenant, body.question)
        except LLMUnavailableError as exc:
            raise ApiError(502, "LLM_UNAVAILABLE", str(exc)) from exc

    @app.delete("/v1/documents/{document_id}", status_code=204, dependencies=protected)
    def delete_document(
        document_id: str, tenant: str = Depends(tenant_id), svc: RagService = Depends(get_rag)
    ):
        svc.delete_document(tenant, document_id)
        return Response(status_code=204)

    return app


app = create_app()

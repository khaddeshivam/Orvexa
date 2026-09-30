from fastapi import FastAPI

app = FastAPI(
    title="Orvexa AI Gateway",
    version="0.1.0",
    description="AI orchestration and retrieval service for Orvexa.",
)


@app.get("/api/v1/health", tags=["health"])
def health() -> dict[str, str]:
    return {"status": "ok", "service": "ai-gateway"}

from pydantic import BaseModel, ConfigDict, Field
from pydantic.alias_generators import to_camel


class ApiModel(BaseModel):
    """camelCase on the wire, snake_case in Python (matches the Java control plane)."""

    model_config = ConfigDict(alias_generator=to_camel, populate_by_name=True)


class IngestRequest(ApiModel):
    title: str = Field(min_length=1, max_length=200)
    text: str = Field(min_length=1, max_length=500_000)
    document_id: str | None = Field(default=None, max_length=100)


class IngestResponse(ApiModel):
    document_id: str
    chunk_count: int


class QueryRequest(ApiModel):
    question: str = Field(min_length=1, max_length=2000)


class Citation(ApiModel):
    ref: int
    document_id: str
    chunk_index: int
    title: str
    snippet: str
    score: float


class QueryResponse(ApiModel):
    answer: str
    abstained: bool
    # True only if the model's answer cites at least one retrieved passage.
    grounded: bool
    citations: list[Citation]
    top_score: float | None = None

# Orvexa Control Plane API Design — Phase 1

## Request context

External endpoints require:

- `X-API-Key`
- `X-Tenant-Id`
- `X-Actor-Id` for session creation

Internal event ingestion requires:

- `X-Internal-API-Key`
- `X-Internal-Caller`

## Session lifecycle

```text
ACTIVE -> ESCALATED -> CLOSED
ACTIVE -> CLOSED
```

Same-state transitions are treated as idempotent no-ops. Reopening `CLOSED` is rejected.

## Error contract

```json
{
  "timestamp": "2026-10-01T00:00:00Z",
  "status": 409,
  "code": "INVALID_STATE_TRANSITION",
  "message": "Cannot transition session ...",
  "path": "/api/v1/sessions/.../status",
  "correlationId": "..."
}
```

## Event idempotency

`clientEventId` is unique per session. The first request creates an event. A byte-for-byte semantic replay returns the existing event. A conflicting payload using the same idempotency key fails with `409`.

## Example request flow

1. Generate or reuse a tenant UUID and actor UUID.
2. Create a session with `POST /api/v1/sessions`.
3. Capture the returned session ID.
4. Read events with `GET /api/v1/sessions/{sessionId}/events`.
5. Transition the session with `PATCH /api/v1/sessions/{sessionId}/status`.
6. AI Gateway will later use the internal event endpoint to append AI/tool/retrieval events using an idempotency key.

The tenant header is required for external reads so the control plane never resolves a session by ID alone on the external API.

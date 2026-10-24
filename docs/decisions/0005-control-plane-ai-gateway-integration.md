# ADR 0005: Control plane to AI gateway integration

Status: **Proposed** (not implemented)

## Context

The control plane owns sessions and the event log; the gateway answers questions. Today they do not
call each other. The next milestone makes a customer message produce a durable, cited AI response.

Constraints from the existing code:

- Appending an event locks the session row (`SELECT ... FOR UPDATE`). Holding that lock across a slow
  model call would block every other writer on the session.
- `event_type` has no SQL constraint, and the enum already contains `MESSAGE_CREATED`,
  `AI_RESPONSE`, `ESCALATION_REQUESTED` and `ERROR`, so **no migration is needed**.
- `ACTIVE -> ESCALATED` is already a legal transition.

## Decision

The control plane orchestrates, and the gateway stays stateless about sessions:

1. Transaction 1: lock the session, append `MESSAGE_CREATED` (idempotent on `clientEventId`), commit.
2. Call the gateway `/v1/query` **outside any transaction**, with a connect/read timeout, bounded
   retry (queries are idempotent), and a circuit breaker.
3. Transaction 2: append `AI_RESPONSE` (answer, citations, `abstained`, `grounded`, `topScore`), or `ERROR` on
   failure.
4. After a configurable number of consecutive abstentions, transition to `ESCALATED` and append
   `ESCALATION_REQUESTED`.

The control plane passes `X-Tenant-Id` from the session, never from the message body.

## Consequences

- No long-held locks; the log stays ordered and idempotent.
- A crash between steps 1 and 3 leaves a `MESSAGE_CREATED` without a response. A retry with the same
  `clientEventId` must resume rather than duplicate; this needs an explicit test.
- Error shapes differ between the two services today and should be unified at this boundary.
- Alternative considered: the gateway writes events itself through `/internal/v1/.../events`. Rejected for
  now because it spreads session-state logic across two services and complicates escalation.

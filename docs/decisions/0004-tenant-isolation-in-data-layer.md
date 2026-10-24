# ADR 0004: Tenant isolation lives in the data layer

Status: Accepted

## Context

Both services are multi-tenant. If every route handler must remember to filter by tenant, a single
omission leaks data across tenants.

## Decision

- **Gateway:** `VectorStore` is the only code that talks to Qdrant, and every `search` and
  `delete_document` takes a `tenant_id` and always applies it as a payload filter. There is no
  unscoped read or delete method to call by mistake.
- **Control plane:** external reads and transitions resolve sessions with `(id, tenant_id)`
  repository methods rather than id alone.
- Tests assert cross-tenant reads and deletes fail.

## Consequences

- Isolation is structural, not a convention each caller must follow.
- The tenant identity is still caller-supplied. This ADR protects against *accidental* leakage, not a
  malicious key holder; see [security.md](../security.md).
- The control plane's internal append path resolves by session id only. It is restricted to trusted
  internal callers and is listed as a known gap.

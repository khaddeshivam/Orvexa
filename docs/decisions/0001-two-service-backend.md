# ADR 0001: Two-Service Backend Boundary

## Status
Accepted

## Decision
Keep the backend as two services for the initial product:

1. Control Plane — Java/Spring Boot
2. AI Gateway — Python/FastAPI

## Rationale

The boundary matches two distinct engineering responsibilities while avoiding premature microservice decomposition.

The Control Plane owns business state and workflow integrity. The AI Gateway owns model interaction, retrieval, and AI orchestration.

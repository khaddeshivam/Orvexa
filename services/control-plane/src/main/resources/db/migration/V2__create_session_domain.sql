CREATE TABLE sessions (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    created_by UUID NOT NULL,
    title VARCHAR(120),
    status VARCHAR(24) NOT NULL,
    last_event_sequence BIGINT NOT NULL DEFAULT 0,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT sessions_status_check
        CHECK (status IN ('ACTIVE', 'ESCALATED', 'CLOSED'))
);

CREATE INDEX idx_sessions_tenant_created_at
    ON sessions (tenant_id, created_at DESC);

CREATE INDEX idx_sessions_tenant_status_created_at
    ON sessions (tenant_id, status, created_at DESC);

CREATE TABLE session_events (
    id UUID PRIMARY KEY,
    session_id UUID NOT NULL,
    client_event_id UUID NOT NULL,
    sequence_number BIGINT NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    actor_type VARCHAR(24) NOT NULL,
    payload JSONB,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_session_events_session
        FOREIGN KEY (session_id) REFERENCES sessions (id) ON DELETE CASCADE,
    CONSTRAINT session_events_actor_type_check
        CHECK (actor_type IN ('USER', 'SYSTEM', 'AI', 'TOOL')),
    CONSTRAINT uq_session_event_idempotency
        UNIQUE (session_id, client_event_id),
    CONSTRAINT uq_session_event_sequence
        UNIQUE (session_id, sequence_number)
);

CREATE INDEX idx_session_events_session_sequence
    ON session_events (session_id, sequence_number ASC);

CREATE INDEX idx_session_events_session_occurred_at
    ON session_events (session_id, occurred_at ASC);

-- Phase 0 baseline. Domain tables are introduced in Phase 1.
CREATE TABLE IF NOT EXISTS schema_version_marker (
    id INTEGER PRIMARY KEY,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO schema_version_marker (id)
VALUES (1)
ON CONFLICT (id) DO NOTHING;

CREATE TABLE import_runs (
    id BIGSERIAL PRIMARY KEY,
    started_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ,
    status VARCHAR(32) NOT NULL,
    records_processed INTEGER NOT NULL DEFAULT 0,
    records_failed INTEGER NOT NULL DEFAULT 0,
    error_message TEXT,
    CONSTRAINT import_runs_status_check
        CHECK (status IN ('RUNNING', 'COMPLETED', 'FAILED'))
);

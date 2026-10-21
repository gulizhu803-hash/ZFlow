-- Office lease migration v1: safe reclaim of runs owned by a lost instance.
-- Apply once after 03-office-artifacts.sql.
ALTER TABLE ai_office_run
    ADD COLUMN owner_instance VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL AFTER cancel_requested_at,
    ADD COLUMN heartbeat_at DATETIME(3) NULL AFTER owner_instance;

CREATE INDEX idx_office_run_lease ON ai_office_run (status, heartbeat_at);

-- Office M1 migration v1. Apply once to MySQL 8 after 01-schema.sql and 02-seed.sql.
CREATE TABLE ai_office_run (
    id BIGINT NOT NULL AUTO_INCREMENT,
    run_id VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    owner_id BIGINT NOT NULL,
    session_id VARCHAR(128) COLLATE utf8mb4_bin NOT NULL,
    idempotency_key VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    request_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    request_snapshot JSON NOT NULL,
    retry_of_run_id VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    status VARCHAR(24) NOT NULL,
    stage VARCHAR(32) NOT NULL DEFAULT 'QUEUED',
    last_event_seq BIGINT NOT NULL DEFAULT 0,
    trace_id VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    model_snapshot JSON NOT NULL,
    model_attempt_count INT NOT NULL DEFAULT 0,
    generation_receipt JSON NULL,
    error_code VARCHAR(64) NULL,
    error_message VARCHAR(512) NULL,
    started_at DATETIME(3) NULL,
    deadline_at DATETIME(3) NULL,
    cancel_requested_at DATETIME(3) NULL,
    finished_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_office_run_id (run_id),
    UNIQUE KEY uk_office_run_request (owner_id, idempotency_key),
    KEY idx_office_run_queue (status, created_at, id),
    KEY idx_office_run_session (owner_id, session_id, created_at, id),
    KEY idx_office_run_owner (owner_id, created_at, id),
    KEY idx_office_run_capacity (owner_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE ai_artifact (
    id BIGINT NOT NULL AUTO_INCREMENT,
    artifact_id VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    owner_id BIGINT NOT NULL,
    session_id VARCHAR(128) COLLATE utf8mb4_bin NOT NULL,
    artifact_type VARCHAR(24) NOT NULL,
    title VARCHAR(200) NOT NULL,
    current_version INT NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_artifact_id (artifact_id),
    KEY idx_artifact_session (owner_id, session_id, created_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE ai_artifact_revision (
    id BIGINT NOT NULL AUTO_INCREMENT,
    revision_id VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    artifact_id VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    version INT NOT NULL,
    base_version INT NOT NULL,
    save_request_key VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    save_request_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    run_id VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    output_key VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    created_by BIGINT NOT NULL,
    object_key VARCHAR(512) COLLATE utf8mb4_bin NOT NULL,
    content_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    content_type VARCHAR(128) NOT NULL,
    size_bytes BIGINT NOT NULL,
    structured_content JSON NULL,
    source_snapshot JSON NULL,
    validation_report JSON NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_revision_id (revision_id),
    UNIQUE KEY uk_artifact_version (artifact_id, version),
    UNIQUE KEY uk_artifact_save_request (artifact_id, save_request_key),
    UNIQUE KEY uk_run_output (run_id, output_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE ai_office_run_event (
    id BIGINT NOT NULL AUTO_INCREMENT,
    run_id VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    seq BIGINT NOT NULL,
    event_type VARCHAR(48) NOT NULL,
    schema_version INT NOT NULL DEFAULT 1,
    payload JSON NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_office_run_event (run_id, seq)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

ALTER TABLE ai_session MODIFY session_type VARCHAR(32) NOT NULL COMMENT 'chat/work/office';

CREATE TABLE IF NOT EXISTS agent_event_archives (
    id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
    archive_id BIGINT UNSIGNED NOT NULL,
    run_id BIGINT UNSIGNED NOT NULL,
    first_sequence BIGINT UNSIGNED NOT NULL,
    last_sequence BIGINT UNSIGNED NOT NULL,
    event_count INT UNSIGNED NOT NULL,
    object_key VARCHAR(500) NOT NULL,
    size_bytes BIGINT UNSIGNED NOT NULL,
    sha256 CHAR(64) NOT NULL,
    archive_status VARCHAR(24) NOT NULL,
    verified_at DATETIME(3) NULL,
    expires_at DATETIME(3) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_agent_event_archives_id (archive_id),
    UNIQUE KEY uk_agent_event_archives_run (run_id),
    KEY idx_agent_event_archives_expiry (archive_status, expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

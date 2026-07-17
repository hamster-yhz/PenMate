CREATE TABLE IF NOT EXISTS agent_tool_call_executions (
    id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
    execution_id BIGINT UNSIGNED NOT NULL,
    run_id BIGINT UNSIGNED NOT NULL,
    tool_call_id VARCHAR(128) NOT NULL,
    tool_code VARCHAR(100) NOT NULL,
    request_sha256 CHAR(64) NOT NULL,
    execution_token BIGINT UNSIGNED NOT NULL,
    execution_status VARCHAR(24) NOT NULL,
    result_json LONGTEXT NULL,
    error_code VARCHAR(96) NULL,
    error_message VARCHAR(500) NULL,
    started_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    finished_at DATETIME(3) NULL,
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_agent_tool_call_executions_id (execution_id),
    UNIQUE KEY uk_agent_tool_call_executions_call (run_id, tool_call_id),
    KEY idx_agent_tool_call_executions_status (execution_status, updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

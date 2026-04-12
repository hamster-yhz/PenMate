CREATE TABLE IF NOT EXISTS novel_projects (
    id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
    owner_user_id BIGINT UNSIGNED NOT NULL,
    title VARCHAR(200) NOT NULL,
    summary TEXT NULL,
    status TINYINT UNSIGNED NOT NULL DEFAULT 1,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted_at DATETIME(3) NULL,
    KEY idx_novel_owner_status (owner_user_id, status, deleted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS agent_approval_requests (
    id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
    project_id BIGINT UNSIGNED NOT NULL,
    task_id BIGINT UNSIGNED NULL,
    approval_type VARCHAR(40) NOT NULL,
    payload_json JSON NOT NULL,
    risk_level TINYINT UNSIGNED NOT NULL DEFAULT 2,
    status VARCHAR(20) NOT NULL DEFAULT 'pending',
    requested_by BIGINT UNSIGNED NOT NULL,
    reviewed_by BIGINT UNSIGNED NULL,
    reviewed_at DATETIME(3) NULL,
    review_comment VARCHAR(500) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    KEY idx_approval_project_status (project_id, status, created_at),
    KEY idx_approval_task (task_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS agent_approval_actions (
    id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
    request_id BIGINT UNSIGNED NOT NULL,
    action VARCHAR(20) NOT NULL,
    operator_id BIGINT UNSIGNED NOT NULL,
    comment VARCHAR(500) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    KEY idx_approval_actions_request (request_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS ops_audit_logs (
    id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
    trace_id VARCHAR(64) NOT NULL,
    user_id BIGINT UNSIGNED NULL,
    module VARCHAR(64) NOT NULL,
    action VARCHAR(64) NOT NULL,
    resource_type VARCHAR(64) NOT NULL,
    resource_id VARCHAR(64) NULL,
    request_json JSON NULL,
    response_code INT NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    KEY idx_audit_user_created (user_id, created_at),
    KEY idx_audit_module_created (module, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;


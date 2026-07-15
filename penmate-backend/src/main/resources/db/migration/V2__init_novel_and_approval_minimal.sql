CREATE TABLE IF NOT EXISTS novel_projects (
    id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
    project_id BIGINT UNSIGNED NOT NULL,
    owner_user_id BIGINT UNSIGNED NOT NULL,
    title VARCHAR(200) NOT NULL,
    summary TEXT NULL,
    status TINYINT UNSIGNED NOT NULL DEFAULT 1,
    structure_revision BIGINT UNSIGNED NOT NULL DEFAULT 1,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted_at DATETIME(3) NULL,
    UNIQUE KEY uk_novel_projects_project_id (project_id),
    KEY idx_novel_owner_status (owner_user_id, status, deleted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS agent_approval_requests (
    id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
    approval_request_id BIGINT UNSIGNED NOT NULL,
    project_id BIGINT UNSIGNED NOT NULL,
    run_id BIGINT UNSIGNED NULL,
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
    UNIQUE KEY uk_agent_approval_requests_request_id (approval_request_id),
    KEY idx_approval_project_status (project_id, status, created_at),
    KEY idx_approval_run (run_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS agent_approval_actions (
    id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
    approval_action_id BIGINT UNSIGNED NOT NULL,
    request_id BIGINT UNSIGNED NOT NULL,
    action VARCHAR(20) NOT NULL,
    operator_id BIGINT UNSIGNED NOT NULL,
    comment VARCHAR(500) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_agent_approval_actions_action_id (approval_action_id),
    KEY idx_approval_actions_request (request_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;


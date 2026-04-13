CREATE TABLE IF NOT EXISTS agent_conversations (
    id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
    project_id BIGINT UNSIGNED NOT NULL,
    user_id BIGINT UNSIGNED NOT NULL,
    title VARCHAR(200) NULL,
    context_scope_json JSON NULL,
    last_message_at DATETIME(3) NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'active',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted_at DATETIME(3) NULL,
    KEY idx_agent_conversation_project_deleted (project_id, deleted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS agent_messages (
    id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
    conversation_id BIGINT UNSIGNED NOT NULL,
    role VARCHAR(20) NOT NULL,
    user_message_type VARCHAR(40) NULL,
    content_md LONGTEXT NOT NULL,
    attachments_json JSON NULL,
    tool_calls_json JSON NULL,
    seq_no INT NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    KEY idx_agent_messages_conversation_seq (conversation_id, seq_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS agent_generation_tasks (
    id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
    project_id BIGINT UNSIGNED NOT NULL,
    conversation_id BIGINT UNSIGNED NOT NULL,
    chapter_id BIGINT UNSIGNED NULL,
    task_type VARCHAR(40) NOT NULL,
    prompt_snapshot JSON NULL,
    style_profile_snapshot JSON NULL,
    plugin_snapshot JSON NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'running',
    started_at DATETIME(3) NULL,
    finished_at DATETIME(3) NULL,
    error_msg VARCHAR(500) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    KEY idx_agent_generation_project_status (project_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS ops_async_jobs (
    id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
    job_type VARCHAR(60) NOT NULL,
    biz_key VARCHAR(120) NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'pending',
    error_msg VARCHAR(500) NULL,
    started_at DATETIME(3) NULL,
    finished_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    KEY idx_ops_job_type_status (job_type, status),
    KEY idx_ops_job_biz_key (biz_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS ops_migrations (
    id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
    migration_type VARCHAR(80) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'running',
    progress_pct INT NOT NULL DEFAULT 0,
    summary_json JSON NULL,
    error_msg VARCHAR(500) NULL,
    started_at DATETIME(3) NULL,
    finished_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;


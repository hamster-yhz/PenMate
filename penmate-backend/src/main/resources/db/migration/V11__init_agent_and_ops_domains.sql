CREATE TABLE IF NOT EXISTS agent_sessions (
    id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
    session_id BIGINT UNSIGNED NOT NULL,
    project_id BIGINT UNSIGNED NOT NULL,
    owner_user_id BIGINT UNSIGNED NOT NULL,
    title VARCHAR(200) NOT NULL,
    session_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    bound_style_id BIGINT UNSIGNED NULL,
    active_context_version INT NOT NULL DEFAULT 1,
    last_turn_id BIGINT UNSIGNED NULL,
    last_run_id BIGINT UNSIGNED NULL,
    last_message_at DATETIME(3) NULL,
    resumed_at DATETIME(3) NULL,
    total_prompt_tokens INT UNSIGNED NOT NULL DEFAULT 0,
    total_completion_tokens INT UNSIGNED NOT NULL DEFAULT 0,
    total_tokens INT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted_at DATETIME(3) NULL,
    UNIQUE KEY uk_agent_sessions_session_id (session_id),
    KEY idx_agent_sessions_project_updated (project_id, updated_at),
    KEY idx_agent_sessions_project_status_deleted (project_id, session_status, deleted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent sessions';

CREATE TABLE IF NOT EXISTS agent_turns (
    id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
    turn_id BIGINT UNSIGNED NOT NULL,
    session_id BIGINT UNSIGNED NOT NULL,
    turn_seq INT NOT NULL,
    user_message_id BIGINT UNSIGNED NULL,
    assistant_message_id BIGINT UNSIGNED NULL,
    run_id BIGINT UNSIGNED NULL,
    turn_status VARCHAR(24) NOT NULL DEFAULT 'PENDING',
    resume_token VARCHAR(128) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_agent_turns_turn_id (turn_id),
    UNIQUE KEY uk_agent_turns_session_seq (session_id, turn_seq),
    KEY idx_agent_turns_session_status (session_id, turn_status),
    KEY idx_agent_turns_run (run_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent conversation turns';

CREATE TABLE IF NOT EXISTS agent_messages (
    id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
    message_id BIGINT UNSIGNED NOT NULL,
    session_id BIGINT UNSIGNED NOT NULL,
    turn_id BIGINT UNSIGNED NULL,
    role VARCHAR(20) NOT NULL,
    message_kind VARCHAR(30) NOT NULL DEFAULT 'CHAT',
    content_markdown LONGTEXT NOT NULL,
    render_blocks_json LONGTEXT NULL,
    tool_call_id VARCHAR(128) NULL,
    approval_id BIGINT UNSIGNED NULL,
    delivery_status VARCHAR(20) NOT NULL DEFAULT 'FINAL',
    seq_no INT NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_agent_messages_message_id (message_id),
    UNIQUE KEY uk_agent_messages_session_seq (session_id, seq_no),
    KEY idx_agent_messages_turn (turn_id),
    KEY idx_agent_messages_session_created (session_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent session messages';

CREATE TABLE IF NOT EXISTS agent_runs (
    id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
    run_id BIGINT UNSIGNED NOT NULL,
    project_id BIGINT UNSIGNED NOT NULL,
    session_id BIGINT UNSIGNED NOT NULL,
    turn_id BIGINT UNSIGNED NOT NULL,
    owner_user_id BIGINT UNSIGNED NOT NULL,
    run_status VARCHAR(32) NOT NULL,
    run_phase VARCHAR(64) NOT NULL,
    active_approval_id BIGINT UNSIGNED NULL,
    latest_event_seq BIGINT UNSIGNED NOT NULL DEFAULT 0,
    latest_checkpoint_id BIGINT UNSIGNED NULL,
    trace_id VARCHAR(64) NULL,
    started_at DATETIME(3) NULL,
    finished_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_agent_runs_run_id (run_id),
    UNIQUE KEY uk_agent_runs_turn_id (turn_id),
    KEY idx_agent_runs_session_updated (session_id, updated_at),
    KEY idx_agent_runs_project_status (project_id, run_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent run execution aggregate';

CREATE TABLE IF NOT EXISTS agent_run_inputs (
    id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
    run_id BIGINT UNSIGNED NOT NULL,
    prompt_snapshot LONGTEXT NOT NULL,
    task_type VARCHAR(32) NOT NULL,
    chapter_id BIGINT UNSIGNED NULL,
    selected_text LONGTEXT NULL,
    style_snapshot_json LONGTEXT NULL,
    model_snapshot_json LONGTEXT NULL,
    plugin_bindings_json LONGTEXT NULL,
    input_hash VARCHAR(128) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_agent_run_inputs_run_id (run_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent run input snapshot';

CREATE TABLE IF NOT EXISTS agent_events (
    id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
    event_id BIGINT UNSIGNED NOT NULL,
    run_id BIGINT UNSIGNED NOT NULL,
    project_id BIGINT UNSIGNED NOT NULL,
    session_id BIGINT UNSIGNED NOT NULL,
    turn_id BIGINT UNSIGNED NOT NULL,
    sequence BIGINT UNSIGNED NOT NULL,
    schema_version INT UNSIGNED NOT NULL DEFAULT 1,
    event_type VARCHAR(96) NOT NULL,
    payload_json LONGTEXT NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_agent_events_event_id (event_id),
    UNIQUE KEY uk_agent_events_run_seq (run_id, sequence),
    KEY idx_agent_events_run_type (run_id, event_type),
    KEY idx_agent_events_session_created (session_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent run durable events';

CREATE TABLE IF NOT EXISTS agent_checkpoints (
    id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
    checkpoint_id BIGINT UNSIGNED NOT NULL,
    run_id BIGINT UNSIGNED NOT NULL,
    checkpoint_no BIGINT UNSIGNED NOT NULL,
    last_event_seq BIGINT UNSIGNED NOT NULL,
    state_json LONGTEXT NOT NULL,
    state_size_bytes INT UNSIGNED NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_agent_checkpoints_checkpoint_id (checkpoint_id),
    UNIQUE KEY uk_agent_checkpoints_run_no (run_id, checkpoint_no),
    KEY idx_agent_checkpoints_run_latest (run_id, checkpoint_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent run checkpoints';

CREATE TABLE IF NOT EXISTS agent_run_projections (
    id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
    run_id BIGINT UNSIGNED NOT NULL,
    project_id BIGINT UNSIGNED NOT NULL,
    session_id BIGINT UNSIGNED NOT NULL,
    turn_id BIGINT UNSIGNED NOT NULL,
    run_status VARCHAR(32) NOT NULL,
    run_phase VARCHAR(64) NOT NULL,
    status_message VARCHAR(500) NULL,
    active_approval_id BIGINT UNSIGNED NULL,
    latest_sequence BIGINT UNSIGNED NOT NULL DEFAULT 0,
    last_error_code VARCHAR(96) NULL,
    last_error_message VARCHAR(500) NULL,
    current_assistant_message_id BIGINT UNSIGNED NULL,
    result_artifact_id BIGINT UNSIGNED NULL,
    token_usage_json LONGTEXT NULL,
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_agent_run_projections_run_id (run_id),
    KEY idx_agent_run_projections_session (session_id, updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent run query projection';

CREATE TABLE IF NOT EXISTS agent_tool_call_projections (
    id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
    run_id BIGINT UNSIGNED NOT NULL,
    tool_call_id VARCHAR(128) NOT NULL,
    tool_code VARCHAR(100) NOT NULL,
    tool_name VARCHAR(200) NULL,
    status VARCHAR(32) NOT NULL,
    iteration INT NULL,
    arguments_preview_json LONGTEXT NULL,
    output_preview LONGTEXT NULL,
    output_artifact_id BIGINT UNSIGNED NULL,
    approval_id BIGINT UNSIGNED NULL,
    error_code VARCHAR(96) NULL,
    error_message VARCHAR(500) NULL,
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_agent_tool_call_projection (run_id, tool_call_id),
    KEY idx_agent_tool_call_run_status (run_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent tool call projection';

CREATE TABLE IF NOT EXISTS agent_todo_projections (
    id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
    run_id BIGINT UNSIGNED NOT NULL,
    todo_id VARCHAR(128) NOT NULL,
    title VARCHAR(500) NOT NULL,
    status VARCHAR(32) NOT NULL,
    sort_order INT NOT NULL,
    blocked_reason VARCHAR(500) NULL,
    error_summary VARCHAR(500) NULL,
    completed_summary VARCHAR(500) NULL,
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_agent_todo_projection (run_id, todo_id),
    KEY idx_agent_todo_run_status (run_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent todo projection';

CREATE TABLE IF NOT EXISTS agent_artifacts (
    id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
    artifact_id BIGINT UNSIGNED NOT NULL,
    run_id BIGINT UNSIGNED NOT NULL,
    artifact_type VARCHAR(64) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    content_text LONGTEXT NULL,
    metadata_json LONGTEXT NULL,
    size_bytes INT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_agent_artifacts_artifact_id (artifact_id),
    KEY idx_agent_artifacts_run_type (run_id, artifact_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent run artifacts';

CREATE TABLE IF NOT EXISTS agent_session_style_bindings (
    id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
    binding_id BIGINT UNSIGNED NOT NULL,
    session_id BIGINT UNSIGNED NOT NULL,
    style_id BIGINT UNSIGNED NOT NULL,
    source VARCHAR(24) NOT NULL,
    activated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    deactivated_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_agent_session_style_bindings_binding_id (binding_id),
    KEY idx_agent_session_style_bindings_session_active (session_id, deactivated_at),
    KEY idx_agent_session_style_bindings_style (style_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent session style binding history';

CREATE TABLE IF NOT EXISTS ops_async_jobs (
    id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
    job_id BIGINT UNSIGNED NOT NULL,
    job_type VARCHAR(60) NOT NULL,
    biz_key VARCHAR(120) NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'pending',
    error_msg VARCHAR(500) NULL,
    started_at DATETIME(3) NULL,
    finished_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_ops_async_jobs_job_id (job_id),
    KEY idx_ops_job_type_status (job_type, status),
    KEY idx_ops_job_biz_key (biz_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Ops async jobs';

CREATE TABLE IF NOT EXISTS ops_migrations (
    id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
    migration_id BIGINT UNSIGNED NOT NULL,
    migration_type VARCHAR(80) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'running',
    progress_pct INT NOT NULL DEFAULT 0,
    summary_json JSON NULL,
    error_msg VARCHAR(500) NULL,
    started_at DATETIME(3) NULL,
    finished_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_ops_migrations_migration_id (migration_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Ops migrations';

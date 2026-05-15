/*
 * 该表用于恢复 Agent 会话主状态、最近 turn/task 指针以及工作台重新进入时的排序与一致性校验。
 */
CREATE TABLE IF NOT EXISTS agent_sessions (
    id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT COMMENT '自增主键，仅供内部关联',
    session_id BIGINT UNSIGNED NOT NULL COMMENT '会话业务 ID',
    project_id BIGINT UNSIGNED NOT NULL COMMENT '小说项目业务 ID',
    owner_user_id BIGINT UNSIGNED NOT NULL COMMENT '会话拥有者用户业务 ID',
    title VARCHAR(200) NOT NULL COMMENT '会话标题，供历史列表展示',
    session_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '会话状态：ACTIVE/ARCHIVED/CLOSED',
    bound_style_id BIGINT UNSIGNED NULL COMMENT '当前绑定的风格业务 ID',
    active_context_version INT NOT NULL DEFAULT 1 COMMENT '当前上下文版本号，用于恢复一致性校验',
    last_turn_id BIGINT UNSIGNED NULL COMMENT '最后一个 turn 业务 ID',
    last_task_id BIGINT UNSIGNED NULL COMMENT '最后一个 task 业务 ID',
    last_message_at DATETIME(3) NULL COMMENT '最后消息时间，用于历史列表排序',
    resumed_at DATETIME(3) NULL COMMENT '最近一次被恢复到工作台的时间',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted_at DATETIME(3) NULL COMMENT '软删除时间',
    UNIQUE KEY uk_agent_sessions_session_id (session_id),
    KEY idx_agent_sessions_project_updated (project_id, updated_at),
    KEY idx_agent_sessions_project_status_deleted (project_id, session_status, deleted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent 会话主表';

/*
 * 该表用于恢复会话内 turn 边界、轮次顺序与断点续跑 token。
 */
CREATE TABLE IF NOT EXISTS agent_turns (
    id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT COMMENT '自增主键，仅供内部关联',
    turn_id BIGINT UNSIGNED NOT NULL COMMENT '轮次业务 ID',
    session_id BIGINT UNSIGNED NOT NULL COMMENT '所属会话业务 ID',
    turn_seq INT NOT NULL COMMENT '会话内轮次序号，从 1 递增',
    user_message_id BIGINT UNSIGNED NULL COMMENT '用户主消息业务 ID',
    assistant_message_id BIGINT UNSIGNED NULL COMMENT '助手主消息业务 ID',
    task_id BIGINT UNSIGNED NULL COMMENT '关联任务业务 ID',
    turn_status VARCHAR(24) NOT NULL DEFAULT 'PENDING' COMMENT '轮次状态：PENDING/RUNNING/WAITING_APPROVAL/COMPLETED/FAILED/CANCELLED',
    resume_token VARCHAR(128) NULL COMMENT '恢复令牌；显式 resume 时用于校验当前 turn 是否仍对应同一断点',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    UNIQUE KEY uk_agent_turns_turn_id (turn_id),
    UNIQUE KEY uk_agent_turns_session_seq (session_id, turn_seq),
    KEY idx_agent_turns_session_status (session_id, turn_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent 会话轮次表';

/*
 * 该表用于恢复会话消息流、工具卡片与前端渲染块，不再依赖旧 conversation message 结构。
 */
CREATE TABLE IF NOT EXISTS agent_messages (
    id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT COMMENT '自增主键，仅供内部关联',
    message_id BIGINT UNSIGNED NOT NULL COMMENT '消息业务 ID',
    session_id BIGINT UNSIGNED NOT NULL COMMENT '所属会话业务 ID',
    turn_id BIGINT UNSIGNED NULL COMMENT '所属轮次业务 ID',
    role VARCHAR(20) NOT NULL COMMENT '消息角色：SYSTEM/USER/ASSISTANT/TOOL',
    message_kind VARCHAR(30) NOT NULL DEFAULT 'CHAT' COMMENT '消息类型：CHAT/TOOL_PLAN/TOOL_RESULT/APPROVAL_CARD/ERROR',
    content_markdown LONGTEXT NOT NULL COMMENT '消息 markdown 正文',
    render_blocks_json LONGTEXT NULL COMMENT '结构化渲染块 JSON，前端恢复时直接消费',
    tool_call_id VARCHAR(128) NULL COMMENT '工具调用链路 ID',
    approval_id BIGINT UNSIGNED NULL COMMENT '审批单业务 ID，审批卡片消息时必填',
    delivery_status VARCHAR(20) NOT NULL DEFAULT 'FINAL' COMMENT '投递状态：PERSISTED/STREAMING/FINAL',
    seq_no INT NOT NULL COMMENT '会话内消息序号',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    UNIQUE KEY uk_agent_messages_message_id (message_id),
    UNIQUE KEY uk_agent_messages_session_seq (session_id, seq_no),
    KEY idx_agent_messages_turn (turn_id),
    KEY idx_agent_messages_session_created (session_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent 会话消息表';

/*
 * 该表用于恢复 task 运行状态、SSE 通道、挂起审批指针以及 turn/session 与项目之间的关联。
 */
CREATE TABLE IF NOT EXISTS agent_tasks (
    id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT COMMENT '自增主键，仅供内部关联',
    task_id BIGINT UNSIGNED NOT NULL COMMENT '任务业务 ID',
    session_id BIGINT UNSIGNED NOT NULL COMMENT '所属会话业务 ID',
    turn_id BIGINT UNSIGNED NOT NULL COMMENT '所属轮次业务 ID',
    project_id BIGINT UNSIGNED NOT NULL COMMENT '所属项目业务 ID',
    task_type VARCHAR(32) NOT NULL COMMENT '任务类型：CHAT/WRITE/REWRITE/SUMMARIZE/TOOL_APPROVAL_RESUME',
    task_status VARCHAR(24) NOT NULL DEFAULT 'QUEUED' COMMENT '任务状态：QUEUED/RUNNING/WAITING_APPROVAL/SUCCEEDED/FAILED/CANCELLED/APPLIED',
    prompt_snapshot LONGTEXT NULL COMMENT '提交执行前冻结的提示词快照；异步恢复与 preflight 重试必须依赖该字段',
    request_context_id BIGINT UNSIGNED NULL COMMENT '请求上下文业务 ID',
    result_id BIGINT UNSIGNED NULL COMMENT '任务结果业务 ID',
    active_approval_id BIGINT UNSIGNED NULL COMMENT '当前挂起审批单业务 ID；WAITING_APPROVAL 恢复时作为唯一断点指针',
    stream_channel_key VARCHAR(128) NULL COMMENT 'SSE 流通道键，页面恢复时可直接重连',
    trace_id VARCHAR(64) NULL COMMENT '链路追踪 ID',
    started_at DATETIME(3) NULL COMMENT '开始执行时间',
    finished_at DATETIME(3) NULL COMMENT '结束执行时间',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    UNIQUE KEY uk_agent_tasks_task_id (task_id),
    KEY idx_agent_tasks_session_created (session_id, created_at),
    KEY idx_agent_tasks_project_status (project_id, task_status),
    KEY idx_agent_tasks_turn (turn_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent 任务主表';

/*
 * 该表用于恢复任务输入上下文快照，确保 style/model/cards/RAG 等依赖可在重连或审批后续跑时复原。
 */
CREATE TABLE IF NOT EXISTS agent_task_contexts (
    id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT COMMENT '自增主键，仅供内部关联',
    context_id BIGINT UNSIGNED NOT NULL COMMENT '任务上下文业务 ID',
    task_id BIGINT UNSIGNED NOT NULL COMMENT '所属任务业务 ID',
    chapter_id BIGINT UNSIGNED NULL COMMENT '当前章节业务 ID',
    selected_text LONGTEXT NULL COMMENT '用户在编辑器中的选中文本快照',
    outline_snapshot_json LONGTEXT NULL COMMENT '大纲快照 JSON',
    cards_snapshot_json LONGTEXT NULL COMMENT '资料卡快照 JSON',
    rag_snapshot_json LONGTEXT NULL COMMENT 'RAG 检索结果快照 JSON',
    plugin_bindings_json LONGTEXT NULL COMMENT '启用插件绑定快照 JSON',
    style_snapshot_json LONGTEXT NULL COMMENT '风格快照 JSON',
    model_snapshot_json LONGTEXT NULL COMMENT '模型选择快照 JSON',
    task_profile_json LONGTEXT NULL COMMENT 'TaskProfile 快照 JSON',
    prompt_plan_json LONGTEXT NULL COMMENT 'PromptPlan 快照 JSON',
    context_package_json LONGTEXT NULL COMMENT 'ContextPackage 快照 JSON',
    active_tool_calls_snapshot LONGTEXT NULL COMMENT '当前运行中的工具调用快照 JSON',
    last_runtime_status VARCHAR(64) NULL COMMENT '最近一次运行态状态',
    recovery_cursor VARCHAR(128) NULL COMMENT '恢复游标，用于标记从哪个运行态断点恢复',
    context_hash VARCHAR(128) NOT NULL COMMENT '上下文哈希，恢复时用于一致性校验',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    UNIQUE KEY uk_agent_task_contexts_context_id (context_id),
    UNIQUE KEY uk_agent_task_contexts_task_id (task_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent 任务输入上下文快照表';

/*
 * 该表用于恢复任务执行结果、错误信息、结构化输出以及工具轨迹，以支持回填和历史回放。
 */
CREATE TABLE IF NOT EXISTS agent_task_results (
    id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT COMMENT '自增主键，仅供内部关联',
    result_id BIGINT UNSIGNED NOT NULL COMMENT '任务结果业务 ID',
    task_id BIGINT UNSIGNED NOT NULL COMMENT '所属任务业务 ID',
    result_status VARCHAR(24) NOT NULL COMMENT '结果状态：SUCCEEDED/FAILED/CANCELLED/APPLIED',
    assistant_message_id BIGINT UNSIGNED NULL COMMENT '结果落地的助手消息业务 ID',
    output_markdown LONGTEXT NULL COMMENT '最终 markdown 文本',
    output_structured_json LONGTEXT NULL COMMENT '结构化结果 JSON，可用于编辑器回填或差量预览',
    tool_trace_json LONGTEXT NULL COMMENT '工具调用轨迹 JSON',
    draft_summary LONGTEXT NULL COMMENT '草稿结果摘要 JSON',
    quality_report_summary LONGTEXT NULL COMMENT '质量审查摘要 JSON',
    todo_summary LONGTEXT NULL COMMENT 'Todo 规划摘要 JSON',
    story_bible_proposal_summary LONGTEXT NULL COMMENT 'Story Bible 提案摘要 JSON',
    token_usage_json LONGTEXT NULL COMMENT 'token 使用量 JSON',
    cost_usage_json LONGTEXT NULL COMMENT '费用使用量 JSON',
    error_code VARCHAR(64) NULL COMMENT '失败错误码',
    error_message VARCHAR(500) NULL COMMENT '失败错误信息',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    UNIQUE KEY uk_agent_task_results_result_id (result_id),
    UNIQUE KEY uk_agent_task_results_task_id (task_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent 任务结果表';

/*
 * 该表用于恢复会话当前生效风格及其切换历史，保证风格绑定在新 task 里可追溯和可重放。
 */
CREATE TABLE IF NOT EXISTS agent_session_style_bindings (
    id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT COMMENT '自增主键，仅供内部关联',
    binding_id BIGINT UNSIGNED NOT NULL COMMENT '绑定业务 ID',
    session_id BIGINT UNSIGNED NOT NULL COMMENT '所属会话业务 ID',
    style_id BIGINT UNSIGNED NOT NULL COMMENT '风格业务 ID',
    source VARCHAR(24) NOT NULL COMMENT '绑定来源：PROJECT_DEFAULT/MANUAL_SWITCH/TASK_OVERRIDE',
    activated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '启用时间',
    deactivated_at DATETIME(3) NULL COMMENT '失效时间；NULL 表示当前生效',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    UNIQUE KEY uk_agent_session_style_bindings_binding_id (binding_id),
    KEY idx_agent_session_style_bindings_session_active (session_id, deactivated_at),
    KEY idx_agent_session_style_bindings_style (style_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会话与风格绑定历史表';

/*
 * 该表用于恢复异步运维任务执行状态，不属于 session recovery 主链路，但保留在本 migration 中维持现有 ops 领域基线。
 */
CREATE TABLE IF NOT EXISTS ops_async_jobs (
    id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT COMMENT '自增主键，仅供内部关联',
    job_id BIGINT UNSIGNED NOT NULL COMMENT '异步任务业务 ID',
    job_type VARCHAR(60) NOT NULL COMMENT '任务类型编码',
    biz_key VARCHAR(120) NULL COMMENT '业务关联键',
    status VARCHAR(20) NOT NULL DEFAULT 'pending' COMMENT '任务状态：pending/running/succeeded/failed/cancelled',
    error_msg VARCHAR(500) NULL COMMENT '失败错误信息',
    started_at DATETIME(3) NULL COMMENT '开始执行时间',
    finished_at DATETIME(3) NULL COMMENT '结束执行时间',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    UNIQUE KEY uk_ops_async_jobs_job_id (job_id),
    KEY idx_ops_job_type_status (job_type, status),
    KEY idx_ops_job_biz_key (biz_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='运维异步任务表';

/*
 * 该表用于恢复运维迁移任务进度，不属于 session recovery 主链路，但保留在本 migration 中维持现有 ops 领域基线。
 */
CREATE TABLE IF NOT EXISTS ops_migrations (
    id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT COMMENT '自增主键，仅供内部关联',
    migration_id BIGINT UNSIGNED NOT NULL COMMENT '迁移任务业务 ID',
    migration_type VARCHAR(80) NOT NULL COMMENT '迁移类型编码',
    status VARCHAR(20) NOT NULL DEFAULT 'running' COMMENT '迁移状态：pending/running/succeeded/failed/cancelled',
    progress_pct INT NOT NULL DEFAULT 0 COMMENT '进度百分比，范围 0-100',
    summary_json JSON NULL COMMENT '迁移结果摘要 JSON',
    error_msg VARCHAR(500) NULL COMMENT '失败错误信息',
    started_at DATETIME(3) NULL COMMENT '开始执行时间',
    finished_at DATETIME(3) NULL COMMENT '结束执行时间',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    UNIQUE KEY uk_ops_migrations_migration_id (migration_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='运维迁移任务表';

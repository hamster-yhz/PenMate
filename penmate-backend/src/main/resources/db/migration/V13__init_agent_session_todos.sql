CREATE TABLE IF NOT EXISTS agent_session_todos (
    id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT COMMENT '自增主键，仅供内部关联',
    todo_id BIGINT UNSIGNED NOT NULL COMMENT '待办业务 ID',
    project_id BIGINT UNSIGNED NOT NULL COMMENT '所属项目业务 ID',
    session_id BIGINT UNSIGNED NOT NULL COMMENT '所属会话业务 ID',
    source_run_id BIGINT UNSIGNED NULL COMMENT 'Source agent run business ID; nullable for user-created todos',
    title VARCHAR(255) NOT NULL COMMENT '待办标题',
    description TEXT NULL COMMENT '待办说明',
    source_type VARCHAR(32) NOT NULL COMMENT '来源类型：USER_REQUEST/QUALITY_REVIEW/STORY_BIBLE_UPDATE/PLANNING',
    todo_status VARCHAR(32) NOT NULL COMMENT '待办状态：TODO/IN_PROGRESS/BLOCKED/DONE',
    completed_at DATETIME(3) NULL COMMENT '完成时间',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted_at DATETIME(3) NULL COMMENT '软删除时间',
    UNIQUE KEY uk_agent_session_todos_todo_id (todo_id),
    KEY idx_agent_session_todos_session_status_deleted (session_id, todo_status, deleted_at),
    KEY idx_agent_session_todos_session_created (session_id, created_at),
    KEY idx_agent_session_todos_source_run (source_run_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会话级 Todo 持久化表';

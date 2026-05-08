/*
 * 该表用于恢复等待审批的工具调用断点，审批通过后可依赖 resume payload、tool context 与幂等键继续执行。
 */
CREATE TABLE IF NOT EXISTS agent_pending_approvals (
    id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT COMMENT '自增主键，仅供内部关联',
    pending_approval_id BIGINT UNSIGNED NOT NULL COMMENT '待恢复审批业务 ID',
    approval_id BIGINT UNSIGNED NOT NULL COMMENT '审批单业务 ID',
    project_id BIGINT UNSIGNED NOT NULL COMMENT '项目业务 ID',
    session_id BIGINT UNSIGNED NOT NULL COMMENT '会话业务 ID',
    turn_id BIGINT UNSIGNED NOT NULL COMMENT '轮次业务 ID',
    task_id BIGINT UNSIGNED NOT NULL COMMENT '任务业务 ID',
    tool_call_id VARCHAR(128) NOT NULL COMMENT '工具调用业务 ID',
    tool_code VARCHAR(100) NOT NULL COMMENT '工具编码',
    tool_args_json LONGTEXT NULL COMMENT '工具入参快照 JSON',
    tool_context_json LONGTEXT NULL COMMENT '工具执行上下文快照 JSON',
    resume_payload_json LONGTEXT NULL COMMENT '审批通过后恢复执行的完整 payload',
    idempotency_key VARCHAR(128) NOT NULL COMMENT '恢复幂等键；审批恢复重放时用于去重与防止重复执行',
    pending_status VARCHAR(24) NOT NULL DEFAULT 'PENDING' COMMENT '挂起状态：PENDING/APPROVED/REJECTED/RESUMED/EXPIRED',
    operator_id BIGINT UNSIGNED NULL COMMENT '最后处理人业务 ID',
    trace_id VARCHAR(64) NULL COMMENT '链路追踪 ID',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    UNIQUE KEY uk_agent_pending_approvals_pending_approval_id (pending_approval_id),
    UNIQUE KEY uk_agent_pending_approvals_approval_id (approval_id),
    UNIQUE KEY uk_agent_pending_approvals_idempotency_key (idempotency_key),
    KEY idx_agent_pending_approvals_task_status (task_id, pending_status),
    KEY idx_agent_pending_approvals_session_status (session_id, pending_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent 等待审批与恢复执行断点表';

# PenMate 会话 / Agent / 风格系统全量重构 Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use [executing-plans] mode to implement this plan task-by-task.

**Goal:** 对 PenMate 的会话系统、历史恢复、Agent 任务/消息/上下文/结果模型、风格系统与工作台前端状态流做一次不保留兼容层的前后端全量重构，使用户从历史记录返回会话后可无感恢复并继续对话。

**Architecture:** 本次重构以“会话恢复快照”作为唯一事实源：后端围绕会话聚合重建 agent conversation / turn / task / approval / style binding / context snapshot / result snapshot 模型，前端围绕工作台恢复编排器与会话 store 重建状态流。数据库不新增迁移版本，直接重写 [`V11__init_agent_and_ops_domains.sql`](penmate-backend/src/main/resources/db/migration/V11__init_agent_and_ops_domains.sql) 与 [`V12__init_pending_tool_invocations.sql`](penmate-backend/src/main/resources/db/migration/V12__init_pending_tool_invocations.sql)，并同步重写接口契约与测试，彻底移除旧 shape、旧字段语义与兼容代码。

**Tech Stack:** Java 21, Spring Boot, MyBatis-style repository, Flyway SQL, Vue 3, TypeScript, Vitest, JUnit 5, MockMvc, SSE, MySQL JSON/LONGTEXT

---

## 一、重构目标与硬约束

### 1.1 范围边界

本计划覆盖以下模块：

- 后端 [`agent`](penmate-backend/src/main/java/com/penmate/backend/application/agent/) 全链路
- 后端 [`style`](penmate-backend/src/main/java/com/penmate/backend/interfaces/api/style/StyleController.java:30) 与 agent 绑定关系
- 后端 [`approval`](penmate-backend/src/main/java/com/penmate/backend/interfaces/api/approval/ApprovalController.java:27) 的 agent 恢复协作
- 前端 [`Workbench`](penmate-frontend/src/views/Workbench/index.vue:1) 页面状态装配
- 前端 [`useWorkbenchChat`](penmate-frontend/src/composables/workbench/useWorkbenchChat.ts:155) 聊天流程
- 前端 [`agentApi`](penmate-frontend/src/api/modules/agent.api.ts:18) 契约
- SQL [`V11__init_agent_and_ops_domains.sql`](penmate-backend/src/main/resources/db/migration/V11__init_agent_and_ops_domains.sql) 与 [`V12__init_pending_tool_invocations.sql`](penmate-backend/src/main/resources/db/migration/V12__init_pending_tool_invocations.sql)

### 1.2 强制约束

1. 不保留旧兼容 DTO、旧字段双写、旧接口 fallback。
2. 不新增 [`V13`](penmate-backend/src/main/resources/db/migration/) 及后续 migration；直接重写 [`V11`](penmate-backend/src/main/resources/db/migration/V11__init_agent_and_ops_domains.sql) / [`V12`](penmate-backend/src/main/resources/db/migration/V12__init_pending_tool_invocations.sql)。
3. 前后端接口一次性切换到新 contract。
4. Java 与 SQL 都要补全注释，注释说明业务语义、关键字段、状态机与恢复用途。
5. 执行时必须使用 [test-driven-development]；每个任务先写失败测试，再最小实现。

---

## 二、现状诊断摘要

### 2.1 后端现状问题

- [`AgentController`](penmate-backend/src/main/java/com/penmate/backend/interfaces/api/agent/AgentController.java:31) 当前只暴露 conversation / message / generation 三类资源，缺少“工作台恢复快照”接口。
- [`agent_conversations`](penmate-backend/src/main/resources/db/migration/V11__init_agent_and_ops_domains.sql:1) 只有 `context_scope_json` 与 `last_message_at`，不能表达恢复所需的 style 绑定、活跃任务、上下文版本、恢复锚点。
- [`agent_messages`](penmate-backend/src/main/resources/db/migration/V11__init_agent_and_ops_domains.sql:17) 只存 `content_md` / `tool_calls_json`，没有 turn 语义、消息状态、渲染块、审批挂载关系。
- [`agent_generation_tasks`](penmate-backend/src/main/resources/db/migration/V11__init_agent_and_ops_domains.sql:32) 当前是 prompt/style/plugin 快照堆叠表，不足以表达“任务输入上下文”“任务输出结果”“恢复点”“等待审批点”。
- [`pending_tool_invocations`](penmate-backend/src/main/resources/db/migration/V12__init_pending_tool_invocations.sql:1) 只与 approval 轻量关联，不是可恢复运行时实体。

### 2.2 前端现状问题

- [`Workbench/index.vue`](penmate-frontend/src/views/Workbench/index.vue:534) 直接装配聊天、审批、模型、插件，页面承担恢复编排职责。
- [`useWorkbenchChat`](penmate-frontend/src/composables/workbench/useWorkbenchChat.ts:267) 的 [`loadConversationHistory()`](penmate-frontend/src/composables/workbench/useWorkbenchChat.ts:267) 只做 `ensureConversationId + listMessages`，不恢复 task、pending approval、style、context。
- [`onMounted()`](penmate-frontend/src/views/Workbench/index.vue:706) 只加载大纲/卡片/插件/模型，不加载会话运行态，用户返回历史会话时无法无感续聊。
- [`agentApi`](penmate-frontend/src/api/modules/agent.api.ts:18) 缺少恢复快照接口、turn 创建接口、会话 resume 接口。

### 2.3 风格系统现状问题

- [`StyleController`](penmate-backend/src/main/java/com/penmate/backend/interfaces/api/style/StyleController.java:30) 仍是独立 CRUD；风格与会话/turn/task 之间没有显式绑定模型。
- 当前 [`createGeneration()`](penmate-frontend/src/composables/workbench/useWorkbenchChat.ts:416) 仅把 `styleProfileSnapshot` 作为字符串塞入 payload，风格无法作为稳定业务对象参与恢复。

---

## 三、目标领域模型

### 3.1 聚合划分

#### A. 会话聚合 [`domain/agent/session`](penmate-backend/src/main/java/com/penmate/backend/domain/agent/)

- `AgentSession`
  - sessionId
  - projectId
  - ownerUserId
  - title
  - status: `ACTIVE | ARCHIVED | CLOSED`
  - boundStyleId
  - activeContextVersion
  - lastTurnId
  - lastTaskId
  - resumedAt

- `AgentTurn`
  - turnId
  - sessionId
  - turnSeq
  - userMessageId
  - assistantMessageId
  - turnStatus: `PENDING | RUNNING | WAITING_APPROVAL | COMPLETED | FAILED | CANCELLED`
  - taskId
  - resumeToken

- `AgentMessage`
  - messageId
  - sessionId
  - turnId
  - role: `SYSTEM | USER | ASSISTANT | TOOL`
  - messageKind: `CHAT | TOOL_PLAN | TOOL_RESULT | APPROVAL_CARD | ERROR`
  - contentMarkdown
  - renderBlocksJson
  - deliveryStatus: `PERSISTED | STREAMING | FINAL`

#### B. 任务聚合 [`domain/agent/task`](penmate-backend/src/main/java/com/penmate/backend/domain/agent/)

- `AgentTask`
  - taskId
  - sessionId
  - turnId
  - taskType: `CHAT | WRITE | REWRITE | SUMMARIZE | TOOL_APPROVAL_RESUME`
  - taskStatus: `QUEUED | RUNNING | WAITING_APPROVAL | SUCCEEDED | FAILED | CANCELLED | APPLIED`
  - requestContextId
  - resultId
  - activeApprovalId
  - streamChannelKey
  - startedAt / finishedAt

- `AgentTaskContext`
  - contextId
  - taskId
  - chapterId
  - selectedText
  - outlineSnapshotJson
  - cardsSnapshotJson
  - ragSnapshotJson
  - pluginBindingsJson
  - styleSnapshotJson
  - modelSnapshotJson
  - contextHash

- `AgentTaskResult`
  - resultId
  - taskId
  - resultStatus
  - assistantMessageId
  - outputMarkdown
  - outputStructuredJson
  - toolTraceJson
  - tokenUsageJson
  - costUsageJson
  - errorCode / errorMessage

#### C. 风格绑定聚合 [`domain/style`](penmate-backend/src/main/java/com/penmate/backend/domain/style/)

- `StyleProfile` 保留，但增加“会话绑定”语义
- 新增 `SessionStyleBinding`
  - bindingId
  - sessionId
  - styleId
  - source: `PROJECT_DEFAULT | MANUAL_SWITCH | TASK_OVERRIDE`
  - activatedAt
  - deactivatedAt

#### D. 恢复聚合 [`domain/agent/recovery`](penmate-backend/src/main/java/com/penmate/backend/domain/agent/)

- `AgentSessionRecoverySnapshot`
  - session summary
  - active style
  - recent turns
  - pending approvals
  - active task summary
  - workbench context
  - resume capability flags

### 3.2 关键领域规则

1. 一个 session 同时最多一个 active task。
2. 一个 turn 只能绑定一个主 user message 与一个主 assistant message。
3. `WAITING_APPROVAL` task 必须绑定唯一 pending approval。
4. style 切换进入新 turn 生效，不回写历史 turn snapshot。
5. 恢复时以后端 `AgentSessionRecoverySnapshot` 为唯一事实源，前端不能拼凑本地恢复。

---

## 四、目标表结构设计

> 本节描述的是要直接写入 [`V11__init_agent_and_ops_domains.sql`](penmate-backend/src/main/resources/db/migration/V11__init_agent_and_ops_domains.sql) / [`V12__init_pending_tool_invocations.sql`](penmate-backend/src/main/resources/db/migration/V12__init_pending_tool_invocations.sql) 的新结构，而不是新增 migration。

### 4.1 重写 [`V11__init_agent_and_ops_domains.sql`](penmate-backend/src/main/resources/db/migration/V11__init_agent_and_ops_domains.sql)

#### 表 1：`agent_sessions`

```sql
CREATE TABLE IF NOT EXISTS agent_sessions (
    id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT COMMENT '自增主键，仅供内部关联',
    session_id BIGINT UNSIGNED NOT NULL COMMENT '会话业务 ID',
    project_id BIGINT UNSIGNED NOT NULL COMMENT '小说项目业务 ID',
    owner_user_id BIGINT UNSIGNED NOT NULL COMMENT '会话拥有者用户业务 ID',
    title VARCHAR(200) NOT NULL COMMENT '会话标题，供历史列表展示',
    session_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/ARCHIVED/CLOSED',
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
```

#### 表 2：`agent_turns`

```sql
CREATE TABLE IF NOT EXISTS agent_turns (
    id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT COMMENT '自增主键，仅供内部关联',
    turn_id BIGINT UNSIGNED NOT NULL COMMENT '轮次业务 ID',
    session_id BIGINT UNSIGNED NOT NULL COMMENT '所属会话业务 ID',
    turn_seq INT NOT NULL COMMENT '会话内轮次序号，从 1 递增',
    user_message_id BIGINT UNSIGNED NULL COMMENT '用户主消息业务 ID',
    assistant_message_id BIGINT UNSIGNED NULL COMMENT '助手主消息业务 ID',
    task_id BIGINT UNSIGNED NULL COMMENT '关联任务业务 ID',
    turn_status VARCHAR(24) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/RUNNING/WAITING_APPROVAL/COMPLETED/FAILED/CANCELLED',
    resume_token VARCHAR(128) NULL COMMENT '恢复令牌，用于断点续跑校验',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    UNIQUE KEY uk_agent_turns_turn_id (turn_id),
    UNIQUE KEY uk_agent_turns_session_seq (session_id, turn_seq),
    KEY idx_agent_turns_session_status (session_id, turn_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent 会话轮次表';
```

#### 表 3：`agent_messages`

```sql
CREATE TABLE IF NOT EXISTS agent_messages (
    id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT COMMENT '自增主键，仅供内部关联',
    message_id BIGINT UNSIGNED NOT NULL COMMENT '消息业务 ID',
    session_id BIGINT UNSIGNED NOT NULL COMMENT '所属会话业务 ID',
    turn_id BIGINT UNSIGNED NULL COMMENT '所属轮次业务 ID',
    role VARCHAR(20) NOT NULL COMMENT 'SYSTEM/USER/ASSISTANT/TOOL',
    message_kind VARCHAR(30) NOT NULL DEFAULT 'CHAT' COMMENT 'CHAT/TOOL_PLAN/TOOL_RESULT/APPROVAL_CARD/ERROR',
    content_markdown LONGTEXT NOT NULL COMMENT '消息 markdown 正文',
    render_blocks_json LONGTEXT NULL COMMENT '结构化渲染块 JSON，前端恢复时直接消费',
    tool_call_id VARCHAR(128) NULL COMMENT '工具调用链路 ID',
    approval_id BIGINT UNSIGNED NULL COMMENT '审批单业务 ID，审批卡片消息时必填',
    delivery_status VARCHAR(20) NOT NULL DEFAULT 'FINAL' COMMENT 'PERSISTED/STREAMING/FINAL',
    seq_no INT NOT NULL COMMENT '会话内消息序号',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    UNIQUE KEY uk_agent_messages_message_id (message_id),
    UNIQUE KEY uk_agent_messages_session_seq (session_id, seq_no),
    KEY idx_agent_messages_turn (turn_id),
    KEY idx_agent_messages_session_created (session_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent 会话消息表';
```

#### 表 4：`agent_tasks`

```sql
CREATE TABLE IF NOT EXISTS agent_tasks (
    id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT COMMENT '自增主键，仅供内部关联',
    task_id BIGINT UNSIGNED NOT NULL COMMENT '任务业务 ID',
    session_id BIGINT UNSIGNED NOT NULL COMMENT '所属会话业务 ID',
    turn_id BIGINT UNSIGNED NOT NULL COMMENT '所属轮次业务 ID',
    project_id BIGINT UNSIGNED NOT NULL COMMENT '所属项目业务 ID',
    task_type VARCHAR(32) NOT NULL COMMENT 'CHAT/WRITE/REWRITE/SUMMARIZE/TOOL_APPROVAL_RESUME',
    task_status VARCHAR(24) NOT NULL DEFAULT 'QUEUED' COMMENT 'QUEUED/RUNNING/WAITING_APPROVAL/SUCCEEDED/FAILED/CANCELLED/APPLIED',
    request_context_id BIGINT UNSIGNED NULL COMMENT '请求上下文业务 ID',
    result_id BIGINT UNSIGNED NULL COMMENT '任务结果业务 ID',
    active_approval_id BIGINT UNSIGNED NULL COMMENT '当前挂起审批单业务 ID',
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
```

#### 表 5：`agent_task_contexts`

```sql
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
    context_hash VARCHAR(128) NOT NULL COMMENT '上下文哈希，恢复时用于一致性断言',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    UNIQUE KEY uk_agent_task_contexts_context_id (context_id),
    UNIQUE KEY uk_agent_task_contexts_task_id (task_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent 任务输入上下文快照表';
```

#### 表 6：`agent_task_results`

```sql
CREATE TABLE IF NOT EXISTS agent_task_results (
    id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT COMMENT '自增主键，仅供内部关联',
    result_id BIGINT UNSIGNED NOT NULL COMMENT '任务结果业务 ID',
    task_id BIGINT UNSIGNED NOT NULL COMMENT '所属任务业务 ID',
    result_status VARCHAR(24) NOT NULL COMMENT 'SUCCEEDED/FAILED/CANCELLED/APPLIED',
    assistant_message_id BIGINT UNSIGNED NULL COMMENT '结果落地的助手消息业务 ID',
    output_markdown LONGTEXT NULL COMMENT '最终 markdown 文本',
    output_structured_json LONGTEXT NULL COMMENT '结构化结果 JSON，可用于编辑器回填/差量预览',
    tool_trace_json LONGTEXT NULL COMMENT '工具调用轨迹 JSON',
    token_usage_json LONGTEXT NULL COMMENT 'token 使用量 JSON',
    cost_usage_json LONGTEXT NULL COMMENT '费用使用量 JSON',
    error_code VARCHAR(64) NULL COMMENT '失败错误码',
    error_message VARCHAR(500) NULL COMMENT '失败错误信息',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    UNIQUE KEY uk_agent_task_results_result_id (result_id),
    UNIQUE KEY uk_agent_task_results_task_id (task_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent 任务结果表';
```

#### 表 7：`agent_session_style_bindings`

```sql
CREATE TABLE IF NOT EXISTS agent_session_style_bindings (
    id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT COMMENT '自增主键，仅供内部关联',
    binding_id BIGINT UNSIGNED NOT NULL COMMENT '绑定业务 ID',
    session_id BIGINT UNSIGNED NOT NULL COMMENT '所属会话业务 ID',
    style_id BIGINT UNSIGNED NOT NULL COMMENT '风格业务 ID',
    source VARCHAR(24) NOT NULL COMMENT 'PROJECT_DEFAULT/MANUAL_SWITCH/TASK_OVERRIDE',
    activated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '启用时间',
    deactivated_at DATETIME(3) NULL COMMENT '失效时间；NULL 表示当前生效',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    UNIQUE KEY uk_agent_session_style_bindings_binding_id (binding_id),
    KEY idx_agent_session_style_bindings_session_active (session_id, deactivated_at),
    KEY idx_agent_session_style_bindings_style (style_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会话与风格绑定历史表';
```

### 4.2 重写 [`V12__init_pending_tool_invocations.sql`](penmate-backend/src/main/resources/db/migration/V12__init_pending_tool_invocations.sql)

```sql
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
    idempotency_key VARCHAR(128) NOT NULL COMMENT '恢复幂等键',
    pending_status VARCHAR(24) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/APPROVED/REJECTED/RESUMED/EXPIRED',
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
```

### 4.3 SQL 注释要求

在 [`V11`](penmate-backend/src/main/resources/db/migration/V11__init_agent_and_ops_domains.sql) / [`V12`](penmate-backend/src/main/resources/db/migration/V12__init_pending_tool_invocations.sql) 中必须：

- 每张表添加表级 `COMMENT`
- 每个字段添加业务语义 `COMMENT`
- 在表定义前添加块注释说明“该表用于恢复什么”
- 对状态字段注释写明所有枚举值

---

## 五、目标接口设计

### 5.1 保留但重写语义的接口

- [`GET /api/v1/novels/{projectId}/agent/conversations`](penmate-backend/src/main/java/com/penmate/backend/interfaces/api/agent/AgentController.java:51)
  - 返回 `session summary`，不再直接回传旧 `AgentConversation` shape
- [`GET /api/v1/novels/{projectId}/agent/conversations/{conversationId}/messages`](penmate-backend/src/main/java/com/penmate/backend/interfaces/api/agent/AgentController.java:75)
  - 返回消息列表，但消息字段改为 `messageKind/renderBlocks/deliveryStatus`
- [`POST /api/v1/novels/{projectId}/agent/generations`](penmate-backend/src/main/java/com/penmate/backend/interfaces/api/agent/AgentController.java:103)
  - 重命名实现语义为“创建 task + turn”

### 5.2 新增接口

#### 1) 会话恢复快照接口

- `GET /api/v1/novels/{projectId}/agent/sessions/{sessionId}/recovery`

返回示例：

```json
{
  "session": {
    "sessionId": 90001,
    "title": "第三章润色",
    "status": "ACTIVE",
    "boundStyle": {
      "styleId": 81,
      "name": "冷峻悬疑"
    },
    "resumeCapabilities": {
      "canSendMessage": true,
      "canResumePendingTask": true,
      "canApplyResult": false
    }
  },
  "activeTask": {
    "taskId": 70001,
    "taskStatus": "WAITING_APPROVAL",
    "turnId": 88001,
    "streamChannelKey": "agent-task-70001"
  },
  "pendingApproval": {
    "approvalId": 60001,
    "toolCallId": "tool-call-xyz",
    "toolCode": "chapter.replace",
    "approvalMessage": "检测到待审批章节覆盖操作"
  },
  "messages": [
    {
      "messageId": 501,
      "role": "USER",
      "messageKind": "CHAT",
      "contentMarkdown": "把第三章节奏压快一点",
      "renderBlocks": []
    },
    {
      "messageId": 502,
      "role": "ASSISTANT",
      "messageKind": "APPROVAL_CARD",
      "contentMarkdown": "",
      "renderBlocks": [
        {
          "type": "approval-card",
          "approvalId": 60001,
          "message": "检测到待审批章节覆盖操作"
        }
      ]
    }
  ],
  "workbenchContext": {
    "chapterId": 301,
    "selectedText": "原段落片段",
    "activePlugins": ["outline.search"],
    "modelConfigId": "mcfg-001"
  }
}
```

#### 2) 显式恢复接口

- `POST /api/v1/novels/{projectId}/agent/sessions/{sessionId}/resume`
- 作用：记录 `resumed_at`、分配 `resumeToken`、返回最新 recovery snapshot

#### 3) turn 创建接口

- `POST /api/v1/novels/{projectId}/agent/sessions/{sessionId}/turns`
- 取代“先 createMessage 再 createGeneration”的双请求模式

请求示例：

```json
{
  "operatorId": 201,
  "userMessage": {
    "contentMarkdown": "继续写第三章尾声",
    "messageKind": "CHAT"
  },
  "taskRequest": {
    "taskType": "WRITE",
    "chapterId": 301,
    "selectedText": "",
    "styleId": 81,
    "activePlugins": ["outline.search"],
    "modelConfigId": "mcfg-001"
  }
}
```

#### 4) task 查询接口

- `GET /api/v1/novels/{projectId}/agent/tasks/{taskId}`
- `GET /api/v1/novels/{projectId}/agent/tasks/{taskId}/stream`

#### 5) 风格绑定接口

- `POST /api/v1/novels/{projectId}/agent/sessions/{sessionId}/style-bindings`
- 用于在工作台右侧会话里切换 style，并明确影响后续 turn

### 5.3 移除接口行为

1. 移除前端对 [`createMessage()`](penmate-frontend/src/api/modules/agent.api.ts:28) 与 [`createGeneration()`](penmate-frontend/src/api/modules/agent.api.ts:34) 顺序耦合。
2. 移除前端 [`ensureConversationId()`](penmate-frontend/src/views/Workbench/index.vue:464) 的“查第一个会话，没有则创建”的隐式逻辑。
3. 移除 `styleProfileSnapshot` 原始字符串直传。

---

## 六、前端目标状态流

### 6.1 新状态拆分

新增以下文件：

- [`penmate-frontend/src/stores/workbenchSession.ts`](penmate-frontend/src/stores/workbenchSession.ts)
- [`penmate-frontend/src/composables/workbench/useWorkbenchSessionRecovery.ts`](penmate-frontend/src/composables/workbench/useWorkbenchSessionRecovery.ts)
- [`penmate-frontend/src/composables/workbench/useWorkbenchTurnComposer.ts`](penmate-frontend/src/composables/workbench/useWorkbenchTurnComposer.ts)
- [`penmate-frontend/src/composables/workbench/useWorkbenchChatTimeline.ts`](penmate-frontend/src/composables/workbench/useWorkbenchChatTimeline.ts)
- [`penmate-frontend/src/composables/workbench/useWorkbenchTaskRuntime.ts`](penmate-frontend/src/composables/workbench/useWorkbenchTaskRuntime.ts)

### 6.2 职责重组

#### 1) [`workbenchSession.ts`](penmate-frontend/src/stores/workbenchSession.ts)

负责：

- `sessionSummary`
- `recoverySnapshot`
- `activeTask`
- `pendingApproval`
- `timelineMessages`
- `boundStyle`
- `resumeToken`

#### 2) [`useWorkbenchSessionRecovery.ts`](penmate-frontend/src/composables/workbench/useWorkbenchSessionRecovery.ts)

负责：

- 页面进入时调用 `resumeSession()`
- 根据 recovery snapshot 一次性回填聊天区、风格、活跃插件、任务状态、审批卡片
- 如果存在 `activeTask.taskStatus=RUNNING`，自动重连 SSE
- 如果存在 `WAITING_APPROVAL`，直接显示审批卡且 composer 保持可见/禁用状态正确

#### 3) [`useWorkbenchTurnComposer.ts`](penmate-frontend/src/composables/workbench/useWorkbenchTurnComposer.ts)

负责：

- 输入框内容
- 发送按钮状态
- turn 请求组装
- 与 style/model/plugin 当前选择联动

#### 4) [`useWorkbenchChatTimeline.ts`](penmate-frontend/src/composables/workbench/useWorkbenchChatTimeline.ts)

负责：

- timeline 渲染实体映射
- `renderBlocks` → UI 组件的数据转换
- 合并 streaming token
- approval card / tool result card / error card 渲染

#### 5) [`useWorkbenchTaskRuntime.ts`](penmate-frontend/src/composables/workbench/useWorkbenchTaskRuntime.ts)

负责：

- task SSE 生命周期
- polling fallback（如仍保留）
- `activeTask` 状态转移
- 流式 token 并入 timeline message

### 6.3 页面装配目标

[`Workbench/index.vue`](penmate-frontend/src/views/Workbench/index.vue) 只保留：

- 工作台区域布局
- 子模块 composable 装配
- `onMounted()` 统一触发 `loadWorkbenchData + resumeSession`

必须从 [`index.vue`](penmate-frontend/src/views/Workbench/index.vue:464) 移除：

- `ensureConversationId()`
- 聊天领域状态定义
- `refreshActiveModelInfo()` 与聊天任务拼接逻辑耦合

---

## 七、无感恢复机制设计

### 7.1 恢复时机

1. 用户进入 [`/workbench`](penmate-frontend/src/views/Workbench/index.vue:1)
2. 用户点击历史会话项
3. 用户审批完成后返回会话
4. 页面刷新或浏览器重开后重入

### 7.2 恢复步骤

1. 前端调用 `resumeSession(projectId, sessionId)`
2. 后端校验 session 属主、项目归属、最近上下文版本
3. 后端组装 `AgentSessionRecoverySnapshot`
4. 前端将 snapshot 一次性写入 [`workbenchSession.ts`](penmate-frontend/src/stores/workbenchSession.ts)
5. 若 `activeTask.status=RUNNING`，自动 `openGenerationStream(taskId)`
6. 若 `WAITING_APPROVAL`，timeline 显示审批卡，composer 保持上下文不丢失
7. 用户继续发消息时，新 turn 基于 session 的最新 style/context 生成

### 7.3 必须满足的体验目标

1. 用户从历史记录进入时，消息列表、审批状态、风格状态、模型状态一次到位。
2. 若旧任务仍在流式生成，页面应自动续流，不出现“重新发送才能看到结果”。
3. 若任务等待审批，用户批准后无需刷新页面即可继续得到后续结果。
4. 恢复后继续发送消息，不要求用户手工重新选择模型/风格/上下文。

### 7.4 恢复失败策略

- snapshot 拉取失败：展示系统卡片消息，不伪造空会话
- session 已关闭：只读展示历史 timeline，禁用 composer
- active task 已失效：显示失败卡片并允许继续发起新 turn

---

## 八、后端代码重组目标文件

### 8.1 新增/重写核心后端文件

- 新增 [`penmate-backend/src/main/java/com/penmate/backend/application/agent/usecase/AgentSessionRecoveryAppService.java`](penmate-backend/src/main/java/com/penmate/backend/application/agent/usecase/AgentSessionRecoveryAppService.java)
- 新增 [`penmate-backend/src/main/java/com/penmate/backend/application/agent/usecase/AgentTurnAppService.java`](penmate-backend/src/main/java/com/penmate/backend/application/agent/usecase/AgentTurnAppService.java)
- 新增 [`penmate-backend/src/main/java/com/penmate/backend/application/agent/query/AgentSessionRecoveryQueryService.java`](penmate-backend/src/main/java/com/penmate/backend/application/agent/query/AgentSessionRecoveryQueryService.java)
- 新增 [`penmate-backend/src/main/java/com/penmate/backend/domain/agent/model/AgentSession.java`](penmate-backend/src/main/java/com/penmate/backend/domain/agent/model/AgentSession.java)
- 新增 [`penmate-backend/src/main/java/com/penmate/backend/domain/agent/model/AgentTurn.java`](penmate-backend/src/main/java/com/penmate/backend/domain/agent/model/AgentTurn.java)
- 新增 [`penmate-backend/src/main/java/com/penmate/backend/domain/agent/model/AgentTaskContext.java`](penmate-backend/src/main/java/com/penmate/backend/domain/agent/model/AgentTaskContext.java)
- 新增 [`penmate-backend/src/main/java/com/penmate/backend/domain/agent/model/AgentTaskResult.java`](penmate-backend/src/main/java/com/penmate/backend/domain/agent/model/AgentTaskResult.java)
- 新增 [`penmate-backend/src/main/java/com/penmate/backend/domain/agent/model/AgentSessionRecoverySnapshot.java`](penmate-backend/src/main/java/com/penmate/backend/domain/agent/model/AgentSessionRecoverySnapshot.java)
- 新增 [`penmate-backend/src/main/java/com/penmate/backend/domain/agent/service/AgentSessionRecoveryPolicy.java`](penmate-backend/src/main/java/com/penmate/backend/domain/agent/service/AgentSessionRecoveryPolicy.java)
- 重写 [`penmate-backend/src/main/java/com/penmate/backend/interfaces/api/agent/AgentController.java`](penmate-backend/src/main/java/com/penmate/backend/interfaces/api/agent/AgentController.java)

### 8.2 接口 DTO 目标文件

- 新增 [`penmate-backend/src/main/java/com/penmate/backend/interfaces/api/agent/dto/ResumeAgentSessionDto.java`](penmate-backend/src/main/java/com/penmate/backend/interfaces/api/agent/dto/ResumeAgentSessionDto.java)
- 新增 [`penmate-backend/src/main/java/com/penmate/backend/interfaces/api/agent/dto/CreateAgentTurnDto.java`](penmate-backend/src/main/java/com/penmate/backend/interfaces/api/agent/dto/CreateAgentTurnDto.java)
- 新增 [`penmate-backend/src/main/java/com/penmate/backend/interfaces/api/agent/dto/BindSessionStyleDto.java`](penmate-backend/src/main/java/com/penmate/backend/interfaces/api/agent/dto/BindSessionStyleDto.java)
- 删除或停止引用旧 [`CreateAgentMessageDto`](penmate-backend/src/main/java/com/penmate/backend/interfaces/api/agent/dto/CreateAgentMessageDto.java) / [`CreateAgentGenerationDto`](penmate-backend/src/main/java/com/penmate/backend/interfaces/api/agent/dto/CreateAgentGenerationDto.java)

### 8.3 注释标准

所有新 Java 文件类头部与关键方法必须采用与 [`StyleController`](penmate-backend/src/main/java/com/penmate/backend/interfaces/api/style/StyleController.java:26) 同等级别注释：

- 业务目的
- 流程主线
- 关键调用
- ID 语义
- 异常与分支
- 副作用

---

## 九、前端代码重组目标文件

### 9.1 重写/新增文件

- 重写 [`penmate-frontend/src/api/modules/agent.api.ts`](penmate-frontend/src/api/modules/agent.api.ts)
- 重写 [`penmate-frontend/src/views/Workbench/index.vue`](penmate-frontend/src/views/Workbench/index.vue)
- 重写 [`penmate-frontend/src/composables/workbench/useWorkbenchChat.ts`](penmate-frontend/src/composables/workbench/useWorkbenchChat.ts)
- 新增 [`penmate-frontend/src/stores/workbenchSession.ts`](penmate-frontend/src/stores/workbenchSession.ts)
- 新增 [`penmate-frontend/src/composables/workbench/useWorkbenchSessionRecovery.ts`](penmate-frontend/src/composables/workbench/useWorkbenchSessionRecovery.ts)
- 新增 [`penmate-frontend/src/composables/workbench/useWorkbenchTurnComposer.ts`](penmate-frontend/src/composables/workbench/useWorkbenchTurnComposer.ts)
- 新增 [`penmate-frontend/src/composables/workbench/useWorkbenchChatTimeline.ts`](penmate-frontend/src/composables/workbench/useWorkbenchChatTimeline.ts)
- 新增 [`penmate-frontend/src/composables/workbench/useWorkbenchTaskRuntime.ts`](penmate-frontend/src/composables/workbench/useWorkbenchTaskRuntime.ts)

### 9.2 组件契约调整

- [`ConversationHistoryPanel.vue`](penmate-frontend/src/components/workbench/chat/ConversationHistoryPanel.vue) 改为展示 `session summary`
- [`ChatMessageList.vue`](penmate-frontend/src/components/workbench/chat/ChatMessageList.vue) 改为消费 `renderBlocks`
- [`AgentSessionHeader.vue`](penmate-frontend/src/components/workbench/chat/AgentSessionHeader.vue) 增加 style/model/runtime 状态展示
- [`StyleManager.vue`](penmate-frontend/src/components/workbench/StyleManager.vue) 增加“绑定到当前会话”事件

---

## 十、详细任务分解

> 每个任务都按 2–5 分钟粒度拆解；实现时使用 [test-driven-development]。

### Task 1: 冻结新目标 contract 与文档骨架

**Files:**
- Modify: [`docs/project-specifications/backend/业务流程梳理-后端全链路-v1.0.md`](docs/project-specifications/backend/业务流程梳理-后端全链路-v1.0.md)
- Create: [`docs/analysis/2026-05-07-agent-session-recovery-contract.md`](docs/analysis/2026-05-07-agent-session-recovery-contract.md)
- Test: 无

**Step 1: 写出 contract 文档初稿**

在 [`docs/analysis/2026-05-07-agent-session-recovery-contract.md`](docs/analysis/2026-05-07-agent-session-recovery-contract.md) 写入：

```md
# Agent Session Recovery Contract

## Session Summary
- sessionId
- title
- status
- boundStyle
- lastTaskStatus

## Recovery Snapshot
- session
- activeTask
- pendingApproval
- messages
- workbenchContext

## Turn Create Contract
- operatorId
- userMessage
- taskRequest
```

**Step 2: 人工校验文档与计划一致**

Run: `type docs\analysis\2026-05-07-agent-session-recovery-contract.md`

Expected: 输出中包含 `Recovery Snapshot`、`Turn Create Contract`

**Step 3: 提交文档骨架**

Run: `git add docs/analysis/2026-05-07-agent-session-recovery-contract.md docs/project-specifications/backend/业务流程梳理-后端全链路-v1.0.md && git commit -m "docs: define agent session recovery contract"`

### Task 2: 重写 SQL migration 设计与契约测试

**Files:**
- Modify: [`penmate-backend/src/main/resources/db/migration/V11__init_agent_and_ops_domains.sql`](penmate-backend/src/main/resources/db/migration/V11__init_agent_and_ops_domains.sql)
- Modify: [`penmate-backend/src/main/resources/db/migration/V12__init_pending_tool_invocations.sql`](penmate-backend/src/main/resources/db/migration/V12__init_pending_tool_invocations.sql)
- Modify: [`penmate-backend/src/test/java/com/penmate/backend/infrastructure/persistence/agent/AgentMapperGenerationTaskMysqlContractTest.java`](penmate-backend/src/test/java/com/penmate/backend/infrastructure/persistence/agent/AgentMapperGenerationTaskMysqlContractTest.java)
- Create: [`penmate-backend/src/test/java/com/penmate/backend/infrastructure/persistence/agent/AgentSessionSchemaMysqlContractTest.java`](penmate-backend/src/test/java/com/penmate/backend/infrastructure/persistence/agent/AgentSessionSchemaMysqlContractTest.java)

**Step 1: 写失败的 schema contract test**

在 [`AgentSessionSchemaMysqlContractTest.java`](penmate-backend/src/test/java/com/penmate/backend/infrastructure/persistence/agent/AgentSessionSchemaMysqlContractTest.java) 写入：

```java
@Test
void should_define_agent_session_recovery_tables() {
    assertThat(columnsOf("agent_sessions")).contains("session_id", "bound_style_id", "active_context_version", "resumed_at");
    assertThat(columnsOf("agent_turns")).contains("turn_id", "turn_seq", "resume_token", "turn_status");
    assertThat(columnsOf("agent_tasks")).contains("request_context_id", "result_id", "active_approval_id", "stream_channel_key");
    assertThat(columnsOf("agent_task_contexts")).contains("style_snapshot_json", "model_snapshot_json", "context_hash");
    assertThat(columnsOf("agent_task_results")).contains("output_structured_json", "tool_trace_json");
    assertThat(columnsOf("agent_pending_approvals")).contains("resume_payload_json", "pending_status");
}
```

**Step 2: 运行失败测试**

Run: `mvn -pl penmate-backend -Dtest=AgentSessionSchemaMysqlContractTest test`

Expected: 失败，并提示缺少 `agent_sessions` / `agent_turns` 等新表或字段

**Step 3: 重写 [`V11`](penmate-backend/src/main/resources/db/migration/V11__init_agent_and_ops_domains.sql) 与 [`V12`](penmate-backend/src/main/resources/db/migration/V12__init_pending_tool_invocations.sql)**

使用本计划第四节的完整 SQL。

**Step 4: 重新运行测试**

Run: `mvn -pl penmate-backend -Dtest=AgentSessionSchemaMysqlContractTest,AgentMapperGenerationTaskMysqlContractTest test`

Expected: 通过；输出包含 `BUILD SUCCESS`

**Step 5: 提交**

Run: `git add penmate-backend/src/main/resources/db/migration/V11__init_agent_and_ops_domains.sql penmate-backend/src/main/resources/db/migration/V12__init_pending_tool_invocations.sql penmate-backend/src/test/java/com/penmate/backend/infrastructure/persistence/agent/AgentSessionSchemaMysqlContractTest.java penmate-backend/src/test/java/com/penmate/backend/infrastructure/persistence/agent/AgentMapperGenerationTaskMysqlContractTest.java && git commit -m "refactor: rewrite agent session recovery schema"`

### Task 3: 建立后端领域模型与仓储端口

**Files:**
- Create: [`penmate-backend/src/main/java/com/penmate/backend/domain/agent/model/AgentSession.java`](penmate-backend/src/main/java/com/penmate/backend/domain/agent/model/AgentSession.java)
- Create: [`penmate-backend/src/main/java/com/penmate/backend/domain/agent/model/AgentTurn.java`](penmate-backend/src/main/java/com/penmate/backend/domain/agent/model/AgentTurn.java)
- Create: [`penmate-backend/src/main/java/com/penmate/backend/domain/agent/model/AgentTaskContext.java`](penmate-backend/src/main/java/com/penmate/backend/domain/agent/model/AgentTaskContext.java)
- Create: [`penmate-backend/src/main/java/com/penmate/backend/domain/agent/model/AgentTaskResult.java`](penmate-backend/src/main/java/com/penmate/backend/domain/agent/model/AgentSessionRecoverySnapshot.java)
- Create: [`penmate-backend/src/main/java/com/penmate/backend/domain/agent/model/AgentSessionRecoverySnapshot.java`](penmate-backend/src/main/java/com/penmate/backend/domain/agent/model/AgentSessionRecoverySnapshot.java)
- Create: [`penmate-backend/src/main/java/com/penmate/backend/domain/agent/repository/AgentSessionRepository.java`](penmate-backend/src/main/java/com/penmate/backend/domain/agent/repository/AgentSessionRepository.java)
- Test: [`penmate-backend/src/test/java/com/penmate/backend/domain/agent/AgentSessionRecoveryPolicyTest.java`](penmate-backend/src/test/java/com/penmate/backend/domain/agent/AgentSessionRecoveryPolicyTest.java)

**Step 1: 先写策略测试**

```java
@Test
void should_disallow_multiple_running_tasks_in_one_session() {
    AgentSession session = AgentSession.active(90001L, 101L, 201L, "测试会话");
    session.attachRunningTask(70001L);

    assertThatThrownBy(() -> session.attachRunningTask(70002L))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("running task");
}
```

**Step 2: 运行失败测试**

Run: `mvn -pl penmate-backend -Dtest=AgentSessionRecoveryPolicyTest test`

Expected: 失败，提示缺少 [`AgentSession`](penmate-backend/src/main/java/com/penmate/backend/domain/agent/model/AgentSession.java)

**Step 3: 编写最小领域模型**

关键代码骨架：

```java
public class AgentSession {
    private final Long sessionId;
    private final Long projectId;
    private final Long ownerUserId;
    private String title;
    private String sessionStatus;
    private Long lastTaskId;

    public static AgentSession active(Long sessionId, Long projectId, Long ownerUserId, String title) {
        return new AgentSession(sessionId, projectId, ownerUserId, title, "ACTIVE");
    }

    public void attachRunningTask(Long taskId) {
        if (this.lastTaskId != null && !this.lastTaskId.equals(taskId)) {
            throw new IllegalStateException("session already has running task");
        }
        this.lastTaskId = taskId;
    }
}
```

**Step 4: 运行测试通过**

Run: `mvn -pl penmate-backend -Dtest=AgentSessionRecoveryPolicyTest test`

Expected: `BUILD SUCCESS`

**Step 5: 提交**

Run: `git add penmate-backend/src/main/java/com/penmate/backend/domain/agent/model penmate-backend/src/main/java/com/penmate/backend/domain/agent/repository penmate-backend/src/test/java/com/penmate/backend/domain/agent/AgentSessionRecoveryPolicyTest.java && git commit -m "refactor: add agent session recovery domain models"`

### Task 4: 重写 agent API 为 session / turn / task / recovery 模型

**Files:**
- Modify: [`penmate-backend/src/main/java/com/penmate/backend/interfaces/api/agent/AgentController.java`](penmate-backend/src/main/java/com/penmate/backend/interfaces/api/agent/AgentController.java)
- Create: [`penmate-backend/src/main/java/com/penmate/backend/interfaces/api/agent/dto/CreateAgentTurnDto.java`](penmate-backend/src/main/java/com/penmate/backend/interfaces/api/agent/dto/CreateAgentTurnDto.java)
- Create: [`penmate-backend/src/main/java/com/penmate/backend/interfaces/api/agent/dto/ResumeAgentSessionDto.java`](penmate-backend/src/main/java/com/penmate/backend/interfaces/api/agent/dto/ResumeAgentSessionDto.java)
- Test: [`penmate-backend/src/test/java/com/penmate/backend/interfaces/api/agent/AgentControllerRecoveryContractTest.java`](penmate-backend/src/test/java/com/penmate/backend/interfaces/api/agent/AgentControllerRecoveryContractTest.java)

**Step 1: 写失败接口测试**

```java
@Test
void should_return_recovery_snapshot() throws Exception {
    mockMvc.perform(get("/api/v1/novels/101/agent/sessions/90001/recovery"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.session.sessionId").value(90001))
        .andExpect(jsonPath("$.data.activeTask.taskStatus").value("WAITING_APPROVAL"));
}
```

**Step 2: 运行失败测试**

Run: `mvn -pl penmate-backend -Dtest=AgentControllerRecoveryContractTest test`

Expected: 404 或编译失败

**Step 3: 重写控制器方法**

新增方法：

```java
@GetMapping("/sessions/{sessionId}/recovery")
public ApiResponse<AgentSessionRecoverySnapshot> getRecovery(@PathVariable Long projectId,
                                                             @PathVariable Long sessionId,
                                                             @RequestHeader(value = "X-Trace-Id", required = false) String traceId)

@PostMapping("/sessions/{sessionId}/resume")
public ApiResponse<AgentSessionRecoverySnapshot> resume(@PathVariable Long projectId,
                                                        @PathVariable Long sessionId,
                                                        @RequestBody ResumeAgentSessionDto dto,
                                                        @RequestHeader(value = "X-Trace-Id", required = false) String traceId)

@PostMapping("/sessions/{sessionId}/turns")
public ApiResponse<AgentTask> createTurn(@PathVariable Long projectId,
                                         @PathVariable Long sessionId,
                                         @RequestBody CreateAgentTurnDto dto,
                                         @RequestHeader(value = "X-Trace-Id", required = false) String traceId)
```

**Step 4: 运行测试**

Run: `mvn -pl penmate-backend -Dtest=AgentControllerRecoveryContractTest test`

Expected: `BUILD SUCCESS`

**Step 5: 提交**

Run: `git add penmate-backend/src/main/java/com/penmate/backend/interfaces/api/agent penmate-backend/src/test/java/com/penmate/backend/interfaces/api/agent/AgentControllerRecoveryContractTest.java && git commit -m "refactor: expose session recovery and turn endpoints"`

### Task 5: 实现恢复查询服务与审批恢复桥接

**Files:**
- Create: [`penmate-backend/src/main/java/com/penmate/backend/application/agent/query/AgentSessionRecoveryQueryService.java`](penmate-backend/src/main/java/com/penmate/backend/application/agent/query/AgentSessionRecoveryQueryService.java)
- Create: [`penmate-backend/src/main/java/com/penmate/backend/application/agent/usecase/AgentSessionRecoveryAppService.java`](penmate-backend/src/main/java/com/penmate/backend/application/agent/usecase/AgentSessionRecoveryAppService.java)
- Modify: [`penmate-backend/src/main/java/com/penmate/backend/application/approval/ApprovedToolInvocationAsyncResumer.java`](penmate-backend/src/main/java/com/penmate/backend/application/approval/ApprovedToolInvocationAsyncResumer.java)
- Test: [`penmate-backend/src/test/java/com/penmate/backend/application/agent/usecase/AgentSessionRecoveryAppServiceTest.java`](penmate-backend/src/test/java/com/penmate/backend/application/agent/usecase/AgentSessionRecoveryAppServiceTest.java)

**Step 1: 写失败测试**

```java
@Test
void should_return_pending_approval_inside_recovery_snapshot() {
    AgentSessionRecoverySnapshot snapshot = appService.resumeSession(101L, 90001L, 201L, "trace-1");

    assertThat(snapshot.getPendingApproval()).isNotNull();
    assertThat(snapshot.getActiveTask().getTaskStatus()).isEqualTo("WAITING_APPROVAL");
}
```

**Step 2: 运行失败测试**

Run: `mvn -pl penmate-backend -Dtest=AgentSessionRecoveryAppServiceTest test`

Expected: 失败，缺少 app service 或字段

**Step 3: 最小实现**

- 查询 session
- 查询最近 turn 与 task
- 查询 pending approval
- 组装 [`AgentSessionRecoverySnapshot`](penmate-backend/src/main/java/com/penmate/backend/domain/agent/model/AgentSessionRecoverySnapshot.java)

**Step 4: 运行测试**

Run: `mvn -pl penmate-backend -Dtest=AgentSessionRecoveryAppServiceTest test`

Expected: `BUILD SUCCESS`

**Step 5: 提交**

Run: `git add penmate-backend/src/main/java/com/penmate/backend/application/agent penmate-backend/src/main/java/com/penmate/backend/application/approval/ApprovedToolInvocationAsyncResumer.java penmate-backend/src/test/java/com/penmate/backend/application/agent/usecase/AgentSessionRecoveryAppServiceTest.java && git commit -m "refactor: implement session recovery snapshot query"`

### Task 6: 重写 turn 创建与 task 运行编排

**Files:**
- Create: [`penmate-backend/src/main/java/com/penmate/backend/application/agent/usecase/AgentTurnAppService.java`](penmate-backend/src/main/java/com/penmate/backend/application/agent/usecase/AgentTurnAppService.java)
- Modify: [`penmate-backend/src/main/java/com/penmate/backend/application/agent/orchestration/AgentGenerationWorkflow.java`](penmate-backend/src/main/java/com/penmate/backend/application/agent/orchestration/AgentGenerationWorkflow.java)
- Modify: [`penmate-backend/src/main/java/com/penmate/backend/application/agent/orchestration/AgentPromptAssembler.java`](penmate-backend/src/main/java/com/penmate/backend/application/agent/orchestration/AgentPromptAssembler.java)
- Test: [`penmate-backend/src/test/java/com/penmate/backend/application/agent/usecase/AgentTurnAppServiceTest.java`](penmate-backend/src/test/java/com/penmate/backend/application/agent/usecase/AgentTurnAppServiceTest.java)

**Step 1: 写失败测试**

```java
@Test
void should_create_turn_message_task_context_and_result_pipeline() {
    AgentTask task = appService.createTurn(101L, 90001L, command, "trace-1");

    assertThat(task.getTaskType()).isEqualTo("WRITE");
    assertThat(task.getRequestContextId()).isNotNull();
}
```

**Step 2: 运行失败测试**

Run: `mvn -pl penmate-backend -Dtest=AgentTurnAppServiceTest test`

Expected: 失败

**Step 3: 最小实现**

- 创建 user message
- 创建 turn
- 创建 task
- 持久化 context snapshot
- 异步派发 workflow

**Step 4: 运行测试**

Run: `mvn -pl penmate-backend -Dtest=AgentTurnAppServiceTest,AgentGenerationAppServiceTest test`

Expected: `BUILD SUCCESS`

**Step 5: 提交**

Run: `git add penmate-backend/src/main/java/com/penmate/backend/application/agent penmate-backend/src/test/java/com/penmate/backend/application/agent/usecase/AgentTurnAppServiceTest.java && git commit -m "refactor: create turns as first-class agent workflow entry"`

### Task 7: 将风格系统接入会话绑定与任务上下文

**Files:**
- Modify: [`penmate-backend/src/main/java/com/penmate/backend/interfaces/api/style/StyleController.java`](penmate-backend/src/main/java/com/penmate/backend/interfaces/api/style/StyleController.java)
- Create: [`penmate-backend/src/main/java/com/penmate/backend/application/style/usecase/SessionStyleBindingAppService.java`](penmate-backend/src/main/java/com/penmate/backend/application/style/usecase/SessionStyleBindingAppService.java)
- Modify: [`penmate-backend/src/main/java/com/penmate/backend/application/agent/orchestration/AgentPromptAssembler.java`](penmate-backend/src/main/java/com/penmate/backend/application/agent/orchestration/AgentPromptAssembler.java)
- Test: [`penmate-backend/src/test/java/com/penmate/backend/application/style/SessionStyleBindingAppServiceTest.java`](penmate-backend/src/test/java/com/penmate/backend/application/style/SessionStyleBindingAppServiceTest.java)

**Step 1: 先写失败测试**

```java
@Test
void should_bind_style_to_session_and_apply_on_next_turn() {
    bindingAppService.bind(101L, 90001L, 81L, 201L, "trace-1");

    AgentSessionRecoverySnapshot snapshot = recoveryAppService.getRecovery(101L, 90001L, "trace-1");
    assertThat(snapshot.getSession().getBoundStyle().getStyleId()).isEqualTo(81L);
}
```

**Step 2: 运行失败测试**

Run: `mvn -pl penmate-backend -Dtest=SessionStyleBindingAppServiceTest test`

Expected: 失败

**Step 3: 最小实现**

- 写入 `agent_session_style_bindings`
- 更新 `agent_sessions.bound_style_id`
- prompt assembler 从 session binding 读取 style snapshot

**Step 4: 运行测试**

Run: `mvn -pl penmate-backend -Dtest=SessionStyleBindingAppServiceTest test`

Expected: `BUILD SUCCESS`

**Step 5: 提交**

Run: `git add penmate-backend/src/main/java/com/penmate/backend/application/style penmate-backend/src/main/java/com/penmate/backend/interfaces/api/style/StyleController.java penmate-backend/src/main/java/com/penmate/backend/application/agent/orchestration/AgentPromptAssembler.java penmate-backend/src/test/java/com/penmate/backend/application/style/SessionStyleBindingAppServiceTest.java && git commit -m "refactor: bind styles to agent sessions"`

### Task 8: 重写前端 [`agent.api.ts`](penmate-frontend/src/api/modules/agent.api.ts) 契约

**Files:**
- Modify: [`penmate-frontend/src/api/modules/agent.api.ts`](penmate-frontend/src/api/modules/agent.api.ts)
- Create: [`penmate-frontend/src/api/modules/agent.api.spec.ts`](penmate-frontend/src/api/modules/agent.api.spec.ts)

**Step 1: 写失败测试**

```ts
it('builds_recovery_and_turn_endpoints', async () => {
  expect(agentApi.getSessionRecovery(101, 90001)).toContain('/v1/novels/101/agent/sessions/90001/recovery')
  expect(agentApi.getTaskStreamUrl(101, 70001)).toContain('/v1/novels/101/agent/tasks/70001/stream')
})
```

**Step 2: 运行失败测试**

Run: `npm --prefix penmate-frontend run test -- agent.api.spec.ts`

Expected: 失败，提示方法不存在

**Step 3: 最小实现**

```ts
export const agentApi = {
  listSessions(projectId: IdLike) {
    return request.get<AnyRecord[]>(`/v1/novels/${projectId}/agent/conversations`)
  },
  getSessionRecovery(projectId: IdLike, sessionId: IdLike) {
    return request.get<AnyRecord>(`/v1/novels/${projectId}/agent/sessions/${sessionId}/recovery`)
  },
  resumeSession(projectId: IdLike, sessionId: IdLike, payload: AnyRecord) {
    return request.post<AnyRecord>(`/v1/novels/${projectId}/agent/sessions/${sessionId}/resume`, payload)
  },
  createTurn(projectId: IdLike, sessionId: IdLike, payload: AnyRecord) {
    return request.post<AnyRecord>(`/v1/novels/${projectId}/agent/sessions/${sessionId}/turns`, payload)
  },
  getTask(projectId: IdLike, taskId: IdLike) {
    return request.get<AnyRecord>(`/v1/novels/${projectId}/agent/tasks/${taskId}`)
  },
  getTaskStreamUrl(projectId: IdLike, taskId: IdLike) {
    return `${resolveApiBaseUrl()}/v1/novels/${projectId}/agent/tasks/${taskId}/stream`
  },
}
```

**Step 4: 运行测试**

Run: `npm --prefix penmate-frontend run test -- agent.api.spec.ts`

Expected: 测试通过

**Step 5: 提交**

Run: `git add penmate-frontend/src/api/modules/agent.api.ts penmate-frontend/src/api/modules/agent.api.spec.ts && git commit -m "refactor: rewrite frontend agent api contract"`

### Task 9: 建立前端会话 store 与恢复编排器

**Files:**
- Create: [`penmate-frontend/src/stores/workbenchSession.ts`](penmate-frontend/src/stores/workbenchSession.ts)
- Create: [`penmate-frontend/src/composables/workbench/useWorkbenchSessionRecovery.ts`](penmate-frontend/src/composables/workbench/useWorkbenchSessionRecovery.ts)
- Test: [`penmate-frontend/src/composables/workbench/__tests__/useWorkbenchSessionRecovery.spec.ts`](penmate-frontend/src/composables/workbench/__tests__/useWorkbenchSessionRecovery.spec.ts)

**Step 1: 写失败测试**

```ts
it('hydrates_store_from_recovery_snapshot_and_reconnects_running_task', async () => {
  const recovery = {
    session: { sessionId: 90001, title: '第三章', status: 'ACTIVE', boundStyle: { styleId: 81, name: '冷峻悬疑' } },
    activeTask: { taskId: 70001, taskStatus: 'RUNNING', streamChannelKey: 'agent-task-70001' },
    pendingApproval: null,
    messages: [],
    workbenchContext: { chapterId: 301, selectedText: '', activePlugins: ['outline.search'], modelConfigId: 'mcfg-001' }
  }
  // assert store values + stream reconnect hook called
})
```

**Step 2: 运行失败测试**

Run: `npm --prefix penmate-frontend run test -- useWorkbenchSessionRecovery.spec.ts`

Expected: 失败

**Step 3: 最小实现**

- 定义 store state
- 定义 `hydrateFromRecoverySnapshot()`
- 定义 `resumeSession()`

**Step 4: 运行测试**

Run: `npm --prefix penmate-frontend run test -- useWorkbenchSessionRecovery.spec.ts`

Expected: 测试通过

**Step 5: 提交**

Run: `git add penmate-frontend/src/stores/workbenchSession.ts penmate-frontend/src/composables/workbench/useWorkbenchSessionRecovery.ts penmate-frontend/src/composables/workbench/__tests__/useWorkbenchSessionRecovery.spec.ts && git commit -m "refactor: add workbench session recovery store"`

### Task 10: 拆解聊天时间线与任务运行时

**Files:**
- Create: [`penmate-frontend/src/composables/workbench/useWorkbenchChatTimeline.ts`](penmate-frontend/src/composables/workbench/useWorkbenchChatTimeline.ts)
- Create: [`penmate-frontend/src/composables/workbench/useWorkbenchTaskRuntime.ts`](penmate-frontend/src/composables/workbench/useWorkbenchTaskRuntime.ts)
- Modify: [`penmate-frontend/src/composables/workbench/useWorkbenchChat.ts`](penmate-frontend/src/composables/workbench/useWorkbenchChat.ts)
- Test: [`penmate-frontend/src/composables/workbench/__tests__/useWorkbenchChat.spec.ts`](penmate-frontend/src/composables/workbench/__tests__/useWorkbenchChat.spec.ts)

**Step 1: 写失败测试**

新增断言：

```ts
it('restores_waiting_approval_message_from_recovery_snapshot_without_manual_listMessages', async () => {
  expect(chat.messages.value[0].approval?.id).toBe('60001')
})
```

**Step 2: 运行失败测试**

Run: `npm --prefix penmate-frontend run test -- useWorkbenchChat.spec.ts`

Expected: 失败

**Step 3: 最小实现**

- [`useWorkbenchChat`](penmate-frontend/src/composables/workbench/useWorkbenchChat.ts) 收缩为 facade
- timeline mapping 挪到 [`useWorkbenchChatTimeline.ts`](penmate-frontend/src/composables/workbench/useWorkbenchChatTimeline.ts)
- stream lifecycle 挪到 [`useWorkbenchTaskRuntime.ts`](penmate-frontend/src/composables/workbench/useWorkbenchTaskRuntime.ts)

**Step 4: 运行测试**

Run: `npm --prefix penmate-frontend run test -- useWorkbenchChat.spec.ts`

Expected: 测试通过

**Step 5: 提交**

Run: `git add penmate-frontend/src/composables/workbench/useWorkbenchChat.ts penmate-frontend/src/composables/workbench/useWorkbenchChatTimeline.ts penmate-frontend/src/composables/workbench/useWorkbenchTaskRuntime.ts penmate-frontend/src/composables/workbench/__tests__/useWorkbenchChat.spec.ts && git commit -m "refactor: split chat timeline and task runtime"`

### Task 11: 重写工作台页面初始化与历史切换恢复

**Files:**
- Modify: [`penmate-frontend/src/views/Workbench/index.vue`](penmate-frontend/src/views/Workbench/index.vue)
- Modify: [`penmate-frontend/src/views/Workbench/index.chat-binding.spec.ts`](penmate-frontend/src/views/Workbench/index.chat-binding.spec.ts)
- Modify: [`penmate-frontend/src/views/Workbench/index.refactor.spec.ts`](penmate-frontend/src/views/Workbench/index.refactor.spec.ts)

**Step 1: 写失败视图绑定测试**

```ts
it('resumes_latest_session_on_mount_and_restores_task_status', async () => {
  expect(screen.getByText('等待审批')).toBeTruthy()
})
```

**Step 2: 运行失败测试**

Run: `npm --prefix penmate-frontend run test -- index.chat-binding.spec.ts`

Expected: 失败

**Step 3: 最小实现**

- `onMounted()` 同时调用 `loadWorkbenchData()` 与 `resumeSession()`
- `handleSelectConversation()` 改为调用 `resumeSession(selectedSessionId)`
- 移除 [`ensureConversationId()`](penmate-frontend/src/views/Workbench/index.vue:464)

**Step 4: 运行测试**

Run: `npm --prefix penmate-frontend run test -- index.chat-binding.spec.ts index.refactor.spec.ts`

Expected: 测试通过

**Step 5: 提交**

Run: `git add penmate-frontend/src/views/Workbench/index.vue penmate-frontend/src/views/Workbench/index.chat-binding.spec.ts penmate-frontend/src/views/Workbench/index.refactor.spec.ts && git commit -m "refactor: resume workbench sessions on mount and history switch"`

### Task 12: 风格会话绑定前端接入

**Files:**
- Modify: [`penmate-frontend/src/components/workbench/StyleManager.vue`](penmate-frontend/src/components/workbench/StyleManager.vue)
- Modify: [`penmate-frontend/src/components/workbench/chat/AgentSessionHeader.vue`](penmate-frontend/src/components/workbench/chat/AgentSessionHeader.vue)
- Test: [`penmate-frontend/src/components/workbench/chat/AgentSessionHeader.spec.ts`](penmate-frontend/src/components/workbench/chat/AgentSessionHeader.spec.ts)

**Step 1: 写失败测试**

```ts
it('shows_bound_style_from_session_recovery_snapshot', () => {
  expect(wrapper.text()).toContain('冷峻悬疑')
})
```

**Step 2: 运行失败测试**

Run: `npm --prefix penmate-frontend run test -- AgentSessionHeader.spec.ts`

Expected: 失败

**Step 3: 最小实现**

- 会话头部显示 bound style
- 风格切换后调用 `bindSessionStyle()`
- 成功后刷新 recovery snapshot 或直接更新 store

**Step 4: 运行测试**

Run: `npm --prefix penmate-frontend run test -- AgentSessionHeader.spec.ts`

Expected: 测试通过

**Step 5: 提交**

Run: `git add penmate-frontend/src/components/workbench/StyleManager.vue penmate-frontend/src/components/workbench/chat/AgentSessionHeader.vue penmate-frontend/src/components/workbench/chat/AgentSessionHeader.spec.ts && git commit -m "feat: bind style changes to active agent session"`

### Task 13: 全量回归测试与注释审查

**Files:**
- Modify: 所有本次变更文件
- Test: 后端与前端全量回归

**Step 1: 运行后端回归**

Run: `mvn -pl penmate-backend test`

Expected: `BUILD SUCCESS`

**Step 2: 运行前端回归**

Run: `npm --prefix penmate-frontend run test`

Expected: 所有 Vitest 通过

**Step 3: 运行前端关键工作台可视回归（如项目已有）**

Run: `npm --prefix penmate-frontend run test -- workbench-visual-contract.spec.ts`

Expected: 工作台视觉契约通过

**Step 4: 注释审查**

人工检查：

- SQL 每张表/字段都有 comment
- 新 Java service/controller 有块注释
- 复杂前端 composable 的 state 与 effect 有注释

**Step 5: 提交**

Run: `git add . && git commit -m "refactor: finish full session agent style rebuild"`

---

## 十一、关键实现代码模板

### 11.1 [`CreateAgentTurnDto.java`](penmate-backend/src/main/java/com/penmate/backend/interfaces/api/agent/dto/CreateAgentTurnDto.java)

```java
@Data
public class CreateAgentTurnDto {

    @NotNull
    private Long operatorId;

    @Valid
    @NotNull
    private UserMessageDto userMessage;

    @Valid
    @NotNull
    private TaskRequestDto taskRequest;

    @Data
    public static class UserMessageDto {
        @NotBlank
        private String contentMarkdown;

        @NotBlank
        private String messageKind;
    }

    @Data
    public static class TaskRequestDto {
        @NotBlank
        private String taskType;
        private Long chapterId;
        private String selectedText;
        private Long styleId;
        @NotEmpty
        private List<String> activePlugins;
        @NotBlank
        private String modelConfigId;
    }
}
```

### 11.2 [`workbenchSession.ts`](penmate-frontend/src/stores/workbenchSession.ts)

```ts
import { reactive } from 'vue'

export type WorkbenchSessionState = {
  sessionId: number | null
  title: string
  status: string
  boundStyle: { styleId: number | null; name: string }
  activeTask: { taskId: number | null; taskStatus: string; streamChannelKey: string }
  pendingApproval: Record<string, unknown> | null
  messages: Array<Record<string, unknown>>
  workbenchContext: {
    chapterId: number | null
    selectedText: string
    activePlugins: string[]
    modelConfigId: string
  }
  resumeToken: string
}

export const createWorkbenchSessionState = (): WorkbenchSessionState => reactive({
  sessionId: null,
  title: '',
  status: 'IDLE',
  boundStyle: { styleId: null, name: '' },
  activeTask: { taskId: null, taskStatus: '', streamChannelKey: '' },
  pendingApproval: null,
  messages: [],
  workbenchContext: { chapterId: null, selectedText: '', activePlugins: [], modelConfigId: '' },
  resumeToken: '',
})
```

### 11.3 [`useWorkbenchSessionRecovery.ts`](penmate-frontend/src/composables/workbench/useWorkbenchSessionRecovery.ts)

```ts
export const useWorkbenchSessionRecovery = (deps: {
  getSessionRecovery: (projectId: number, sessionId: number) => Promise<any>
  resumeSession: (projectId: number, sessionId: number, payload: Record<string, unknown>) => Promise<any>
  openTaskStream: (projectId: number, taskId: number) => EventSource
  hydrateStore: (snapshot: any) => void
}) => {
  const restore = async (projectId: number, sessionId: number) => {
    const snapshot = await deps.resumeSession(projectId, sessionId, { trigger: 'WORKBENCH_ENTER' })
    deps.hydrateStore(snapshot)
    const taskId = Number(snapshot?.activeTask?.taskId ?? 0)
    const taskStatus = String(snapshot?.activeTask?.taskStatus ?? '')
    if (taskId > 0 && taskStatus === 'RUNNING') {
      deps.openTaskStream(projectId, taskId)
    }
    return snapshot
  }
  return { restore }
}
```

---

## 十二、验证清单

### 12.1 后端验证

- recovery 接口返回完整 snapshot
- session 恢复会更新 `resumed_at`
- WAITING_APPROVAL task 能从 [`agent_pending_approvals`](penmate-backend/src/main/resources/db/migration/V12__init_pending_tool_invocations.sql) 恢复
- style 绑定会进入新 task 的 `style_snapshot_json`

### 12.2 前端验证

- 页面初次进入自动恢复最近 session
- 点击历史记录切换后无需再次 listMessages 手工拼装即可续聊
- RUNNING task 能自动续流
- WAITING_APPROVAL 能展示审批卡并在审批后继续收到结果
- 刷新页面后不丢失 style / model / active plugins / selected chapter

### 12.3 手工验收脚本

1. 打开工作台，发送一条生成请求。
2. 在任务运行中刷新页面。
3. 确认页面自动恢复到原会话并继续显示流式输出。
4. 发起需要审批的工具操作。
5. 从历史记录重新进入该会话。
6. 确认审批卡仍存在，且审批后任务可以继续。
7. 切换风格并发送新消息。
8. 确认新消息使用新风格，旧消息不受影响。

---

## 十三、风险与回滚策略

### 13.1 风险

1. 直接重写 migration 会影响新环境初始化，需要确保测试库与开发库完全重建。
2. 前后端接口一次性切换，任何字段命名偏差都会导致工作台不可用。
3. SSE 恢复链路引入 session snapshot 后，容易出现“双流订阅”问题。

### 13.2 控制策略

1. 每个任务都先写 contract test。
2. 每个大任务提交一次，保持可回退。
3. 前端在 [`useWorkbenchTaskRuntime.ts`](penmate-frontend/src/composables/workbench/useWorkbenchTaskRuntime.ts) 内确保单例 stream 生命周期。

---

## 十四、预计工时

- SQL 与 schema contract：0.5 天
- 后端领域模型与接口重构：1.5~2 天
- 前端状态流与恢复重构：1.5~2 天
- 回归、注释、联调修正：0.5~1 天

**总计：4~5 个工作日**

---

## 十五、执行选项

Plan complete. Execute now?

1. Execute in this session ([executing-plans])
2. Execute later (user will run `/execute-plan`)
3. Manual implementation (just use plan as guide)

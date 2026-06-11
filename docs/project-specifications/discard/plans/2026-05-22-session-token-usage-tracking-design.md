# Session Token 用量追踪与展示设计

> **目标：** 追踪当前 session 已使用了多少上下文 token，结合模型最大上下文窗口配置，展示给用户一个"用量进度条"。

---

## 设计原则

1. **不新建表** — `max_context_tokens` 放模型配置表，session 累计用量放 session 表
2. **精确值来源** — 从 LLM API 响应的 `usage` 字段获取，零额外依赖
3. **纯增量改造** — 不改动现有 `ConversationWindowBuilder` 轮次截断逻辑
4. **渐进式** — 先做追踪和展示，未来可基于此做超限预警或自动截断

---

## 一、数据库改动

### 1.1 `model_user_configurations` 新增 `max_context_tokens`

```sql
-- V10 原表修改（直接改 DDL）
ALTER TABLE model_user_configurations
ADD COLUMN max_context_tokens INT UNSIGNED NOT NULL DEFAULT 128000
    COMMENT '模型最大上下文窗口 token 数，用户可按实际模型设置';
```

修改后完整 DDL：

```sql
CREATE TABLE IF NOT EXISTS model_user_configurations (
    id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
    model_config_id BIGINT UNSIGNED NOT NULL,
    user_id BIGINT UNSIGNED NOT NULL,
    provider_id BIGINT UNSIGNED NOT NULL,
    model_name VARCHAR(120) NOT NULL,
    base_url VARCHAR(255) NULL,
    key_source_type VARCHAR(20) NOT NULL,
    user_key_id BIGINT UNSIGNED NULL,
    official_key_id BIGINT UNSIGNED NULL,
    context_window_turns INT UNSIGNED NOT NULL DEFAULT 6 COMMENT '发送给 LLM 的历史对话轮数，0 表示禁用历史窗口',
    max_context_tokens INT UNSIGNED NOT NULL DEFAULT 128000 COMMENT '模型最大上下文窗口 token 数，用户可按实际模型设置',
    status VARCHAR(20) NOT NULL DEFAULT 'active',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted_at DATETIME(3) NULL,
    UNIQUE KEY uk_model_user_configurations_model_config_id (model_config_id),
    KEY idx_model_user_config_user_deleted (user_id, deleted_at),
    KEY idx_model_user_config_provider_deleted (provider_id, deleted_at),
    KEY idx_model_user_config_user_key (user_key_id),
    KEY idx_model_user_config_official_key (official_key_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

### 1.2 `agent_sessions` 新增 token 用量累计列

```sql
-- V11 原表修改（直接改 DDL）
ALTER TABLE agent_sessions
ADD COLUMN total_prompt_tokens INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '本 session 累计输入 token 数',
ADD COLUMN total_completion_tokens INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '本 session 累计输出 token 数',
ADD COLUMN total_tokens INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '本 session 累计总 token 数';
```

修改后完整 DDL：

```sql
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
    total_prompt_tokens INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '本 session 累计输入 token 数',
    total_completion_tokens INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '本 session 累计输出 token 数',
    total_tokens INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '本 session 累计总 token 数',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted_at DATETIME(3) NULL COMMENT '软删除时间',
    UNIQUE KEY uk_agent_sessions_session_id (session_id),
    KEY idx_agent_sessions_project_updated (project_id, updated_at),
    KEY idx_agent_sessions_project_status_deleted (project_id, session_status, deleted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent 会话主表';
```

---

## 二、Token 计数数据流

```
LLM API 响应
    │
    ▼
NativeOpenAiStyleHttpProviderChatClient.extractTurnResponse()
    → 解析 response.usage { prompt_tokens, completion_tokens, total_tokens }
    → 封装到 AgentLlmTurnResponse 新增字段 LlmTokenUsage
    │
    ▼
AgentToolLoopRunner.execute()
    → 每轮 LLM 调用累加 token 用量
    → 返回 AgentToolLoopIterationResult（新增 tokenUsage 字段）
    │
    ▼
AgentGenerationWorkflow.finalizeTask()
    → 写入 agent_task_results.token_usage_json（已有字段）
    → 累加到 agent_sessions.total_*_tokens
    → 通过 RuntimeStatusPublisher 推送给前端
```

---

## 三、领域模型新增

### 3.1 值对象：`LlmTokenUsage`

```java
package com.penmate.backend.application.agent.llm;

/**
 * 单次 LLM 调用的 token 用量，从 API 响应 usage 字段解析。
 */
public record LlmTokenUsage(
    int promptTokens,
    int completionTokens,
    int totalTokens
) {
    public static final LlmTokenUsage ZERO = new LlmTokenUsage(0, 0, 0);

    public LlmTokenUsage add(LlmTokenUsage other) {
        return new LlmTokenUsage(
            this.promptTokens + other.promptTokens,
            this.completionTokens + other.completionTokens,
            this.totalTokens + other.totalTokens
        );
    }
}
```

### 3.2 视图对象：`SessionTokenUsageView`

```java
package com.penmate.backend.application.agent.runtime;

/**
 * Session 级别的 token 用量视图，用于前端展示。
 */
public record SessionTokenUsageView(
    Long sessionId,
    int usedTokens,
    int maxContextTokens,
    double usageRatio,
    int promptTokens,
    int completionTokens
) {}
```

### 3.3 `AgentLlmTurnResponse` 新增字段

```java
public record AgentLlmTurnResponse(
    String finishReason,
    String assistantText,
    List<AgentLlmToolCall> toolCalls,
    String rawResponseBody,
    LlmTokenUsage tokenUsage  // 新增
) {
    // ...existing methods...
}
```

### 3.4 `AgentToolLoopIterationResult` 新增字段

```java
// 新增 tokenUsage 字段，记录本次 tool loop 所有 LLM 调用的累计 token
public record AgentToolLoopIterationResult(
    // ...existing fields...
    LlmTokenUsage tokenUsage
) {}
```

---

## 四、改造点清单

### 4.1 基础设施层

**`NativeOpenAiStyleHttpProviderChatClient.extractTurnResponse()`**

```java
// 在解析 choices 之后，新增：
JSONObject usage = root.getJSONObject("usage");
int promptTokens = usage == null ? 0 : usage.getInt("prompt_tokens", 0);
int completionTokens = usage == null ? 0 : usage.getInt("completion_tokens", 0);
LlmTokenUsage tokenUsage = new LlmTokenUsage(promptTokens, completionTokens, promptTokens + completionTokens);

return new AgentLlmTurnResponse(finishReason, content, calls, responseBody, tokenUsage);
```

### 4.2 应用层 - Tool Loop

**`AgentToolLoopRunner.execute()`**

```java
// 在 loop 开始前初始化累加器
LlmTokenUsage accumulatedUsage = LlmTokenUsage.ZERO;

// 每次 generateTurn 后累加
AgentLlmTurnResponse response = agentLlmGateway.generateTurn(...);
accumulatedUsage = accumulatedUsage.add(response.tokenUsage());

// 返回结果时携带
return AgentToolLoopIterationResult.completed(response.assistantText(), totalToolCalls, toolContext, accumulatedUsage);
```

### 4.3 应用层 - Workflow

**`AgentGenerationWorkflow.finalizeTask()`**

```java
// 1. 写入 task result 的 token_usage_json（已有字段）
String tokenUsageJson = toTokenUsageJson(loopResult.tokenUsage());
agentTaskResultRecorder.recordAssistantResult(task, finalText, toolTraceJson, tokenUsageJson);

// 2. 累加到 session
agentRepository.incrementSessionTokenUsage(
    task.getSessionId(),
    loopResult.tokenUsage().promptTokens(),
    loopResult.tokenUsage().completionTokens(),
    loopResult.tokenUsage().totalTokens()
);
```

**Session 累加 SQL：**

```sql
UPDATE agent_sessions
SET total_prompt_tokens = total_prompt_tokens + #{promptTokens},
    total_completion_tokens = total_completion_tokens + #{completionTokens},
    total_tokens = total_tokens + #{totalTokens},
    updated_at = CURRENT_TIMESTAMP(3)
WHERE session_id = #{sessionId} AND deleted_at IS NULL
```

### 4.4 接口层 - 查询 API

```
GET /api/sessions/{sessionId}/token-usage

Response:
{
  "usedTokens": 32450,
  "maxContextTokens": 128000,
  "usageRatio": 0.2535,
  "promptTokens": 28200,
  "completionTokens": 4250,
  "modelName": "gpt-4o-mini"
}
```

### 4.5 实时推送

复用现有 `TaskRuntimeStatusPublisher`，在 `publishDone()` 的 `RuntimeStatusView` 中附带 token 用量：

```java
// RuntimeStatusView 新增可选字段
private SessionTokenUsageView tokenUsage;
```

前端收到 SSE `done` 事件时自动刷新进度条。

---

## 五、前端展示方案

### 位置

放在 Workbench 右侧面板顶部或底部状态栏（`EditorStatusbar` 组件附近）。

### UI

```
┌─────────────────────────────────────────┐
│ 上下文用量  ████████████░░░░░░  25.4%    │
│            32,450 / 128,000 tokens      │
└─────────────────────────────────────────┘
```

### 颜色分级

- `< 50%` → 绿色（安全）
- `50% ~ 80%` → 黄色（注意）
- `> 80%` → 红色（接近上限）

### 刷新时机

- 每次 Agent 任务完成后通过 SSE 推送自动刷新
- 页面加载时调用 `GET /api/sessions/{sessionId}/token-usage` 初始化

---

## 六、配置表用户可设置

用户在"模型设置"页面可以修改 `max_context_tokens`：

- 前端 `ProfileModelPreferencePanel` 或 `ModelSettings` 组件新增输入框
- 后端 `UpdateUserModelConfigDto` 新增 `maxContextTokens` 字段
- 常见预设值：GPT-4o = 128K, Claude 3.5 = 200K, DeepSeek = 64K, GPT-4.1 = 1M

---

## 七、改造影响面

| 层 | 改动 | 复杂度 |
|----|------|--------|
| 数据库 | `model_user_configurations` 加 1 列，`agent_sessions` 加 3 列 | 低 |
| 基础设施层 | `extractTurnResponse()` 解析 `usage` 字段 | 低 |
| 应用层 | `AgentLlmTurnResponse` + `AgentToolLoopIterationResult` 加字段 | 低 |
| 应用层 | `AgentGenerationWorkflow` 完成后写入用量 | 低 |
| 接口层 | 新增 token-usage 查询接口 | 低 |
| 前端 | 新增进度条组件 + SSE 监听 | 低 |
| 现有逻辑 | `ConversationWindowBuilder` 不改动 | 零 |

---

## 八、注意事项

1. **Tool loop 多轮调用**：一次 task 可能触发多次 LLM 调用（preflight + execution + revision），所有调用的 token 都要累加
2. **Preflight 调用**：preflight 用的是 dirty-work 模型，token 也应计入 session 总量
3. **`usage` 字段兼容性**：部分 provider 可能不返回 `usage`，此时 fallback 为 0，不影响功能
4. **session 重置**：用户新建 session 时 token 计数自然归零（新行）
5. **并发安全**：`INCREMENT` 操作天然幂等，MySQL 行锁保证并发安全

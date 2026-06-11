# Agent Event Stream 技术方案

## 1. 背景

Agent 在执行长任务时，会经历计划生成、任务更新、工具调用、用户审批、结果输出等多个阶段。  
如果只在任务结束后返回最终结果，用户无法感知执行进度，系统也难以调试、恢复和审计。

因此需要引入 Event Stream，将 Agent 执行过程中的关键动作抽象为结构化事件，并同时用于：

- 前端实时展示进度
- 后端持久化执行历史
- 失败排查与审计
- 与 Todo、Checkpoint、Artifact 联动

## 2. 目标

- 支持 Agent 执行过程的实时进度推送
- 支持前端断线重连后补齐事件
- 支持事件持久化，便于回放和调试
- 避免将大内容直接写入事件表
- 保证同一个 Run 内事件有序

## 3. 非目标

- 不通过 Event Stream 保存完整上下文
- 不在事件表中存储大文件、网页全文、长日志
- 不依赖前端事件回放作为唯一状态来源

## 4. 核心设计

Agent Runtime 每发生一个关键动作，就生成一条事件：

```text
Agent Runtime
  -> emit event
  -> 写入 agent_events
  -> 发布到 Redis / 内存 EventBus
  -> SSE / WebSocket 推送前端
```

前端通过事件流更新 UI，例如：

- Todo 状态变化
- 工具调用开始 / 完成
- Agent 输出流式文本
- 当前任务完成
- Run 失败或结束

## 5. 事件结构

```json
{
  "id": "evt_001",
  "run_id": "run_123",
  "task_id": "task_456",
  "sequence": 12,
  "type": "task.updated",
  "payload": {
    "status": "in_progress"
  },
  "created_at": "2026-06-10T10:00:00Z"
}
```

字段说明：

| 字段 | 说明 |
|---|---|
| id | 事件唯一 ID |
| run_id | Agent 任务 ID |
| task_id | 关联 Todo，可为空 |
| sequence | Run 内递增序号 |
| type | 事件类型 |
| payload | 事件内容 |
| created_at | 创建时间 |

## 6. 事件类型

### Run 事件

```text
run.started
run.paused
run.resumed
run.completed
run.failed
```

### Task 事件

```text
task.created
task.updated
task.started
task.completed
task.failed
task.blocked
```

### Tool 事件

```text
tool.call.started
tool.call.completed
tool.call.failed
```

### Message 事件

```text
message.delta
message.completed
```

### Artifact 事件

```text
artifact.created
artifact.updated
```

## 7. 数据模型

```sql
CREATE TABLE agent_events (
  id TEXT PRIMARY KEY,
  run_id TEXT NOT NULL,
  task_id TEXT,
  sequence BIGINT NOT NULL,
  type TEXT NOT NULL,
  payload_json JSONB NOT NULL,
  created_at TIMESTAMP DEFAULT now(),

  UNIQUE(run_id, sequence)
);

CREATE INDEX idx_agent_events_run_seq
ON agent_events(run_id, sequence);
```

## 8. 推送协议

### 推荐方案

普通网站场景优先使用 SSE：

```text
GET /api/agent-runs/{run_id}/events/stream?after={sequence}
```

原因：

- 实现简单
- 天然适合服务端单向推送
- 支持浏览器自动重连
- 适合 Agent 进度、日志、流式文本

如果需要双向实时控制，例如远程接管、多人协作，可以使用 WebSocket。

## 9. 断线重连

前端记录最后收到的 `sequence`。

重连时请求：

```text
GET /api/agent-runs/run_123/events/stream?after=152
```

后端处理流程：

```text
1. 查询 sequence > 152 的历史事件
2. 按 sequence 升序补发
3. 继续订阅实时事件
```

## 10. 大内容处理

事件 payload 必须保持轻量。

建议限制：

| 内容 | 策略 |
|---|---|
| payload <= 8KB | 可直接写入事件 |
| payload > 8KB | 尽量瘦身 |
| payload > 16KB | 写 Blob，事件只存引用 |
| payload > 64KB | 禁止直接写入事件 |

示例：

```json
{
  "type": "tool.call.completed",
  "payload": {
    "tool": "fetch_url",
    "blob_id": "blob_789",
    "preview": "页面主要介绍了产品定价。",
    "size_bytes": 512000
  }
}
```

## 11. 一致性要求

对于会改变系统状态的事件，必须在同一个事务中完成：

```text
更新状态表
写入 agent_events
提交事务
发布实时事件
```

示例：

```text
task.status = completed
+
event.type = task.completed
```

避免出现 UI 已显示完成，但数据库状态仍然未完成的问题。

## 12. 推荐实现

```text
Postgres: agent_events 持久化
Redis Pub/Sub: 实时广播
SSE: 推送前端
Frontend Reducer: 根据事件更新 UI
```

整体链路：

```text
Agent Runtime
  -> Postgres agent_events
  -> Redis publish
  -> SSE endpoint
  -> Frontend reducer
  -> Todo / Log / Message UI
```

## 13. 关键原则

- Event 是过程日志，不是完整状态
- Event 必须有序，使用 run_id + sequence 保证顺序
- 大内容不进事件表，只存 Blob 引用
- 前端可以通过事件更新 UI，但后端状态表仍是可信来源
- 事件流要支持断线补发

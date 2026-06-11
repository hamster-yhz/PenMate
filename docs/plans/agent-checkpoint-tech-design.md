# Agent Checkpoint 技术方案

## 1. 背景

Agent 长任务可能会因为页面刷新、Worker 重启、模型调用失败、工具超时、用户暂停审批等原因中断。  
为了支持任务恢复，需要保存 Checkpoint。

Checkpoint 的作用不是保存全部上下文，而是保存 Agent 恢复运行所需的最小状态。

## 2. 目标

- 支持 Agent Run 中断后继续执行
- 降低恢复时的数据库读取成本
- 避免每一步保存完整上下文
- 支持与 Event Stream 配合恢复当前状态
- 支持长期任务的压缩存储

## 3. 非目标

- 不在 Checkpoint 中保存完整聊天历史
- 不保存完整工具结果、网页全文、文件全文
- 不替代 Event Stream
- 不替代 Artifact / Blob 存储

## 4. 核心设计

Checkpoint 采用：

```text
最新 Checkpoint + Checkpoint 之后的 Events = 当前 Runtime State
```

恢复时不从第一条事件开始回放，而是：

```text
1. 读取最新 checkpoint
2. 读取 checkpoint.last_event_seq 之后的事件
3. 回放少量增量事件
4. 恢复当前运行状态
5. 由 Context Builder 构造本轮 Agent 上下文
```

## 5. Checkpoint 内容

Checkpoint 只保存最小可恢复状态。

示例：

```json
{
  "run_id": "run_123",
  "checkpoint_no": 8,
  "last_event_seq": 152,
  "status": "running",
  "current_task_id": "task_7",
  "state": {
    "goal": "分析竞品并生成报告",
    "todo_summary": {
      "completed": 5,
      "in_progress": 1,
      "pending": 3
    },
    "current_task": {
      "id": "task_7",
      "title": "整理价格差异",
      "status": "in_progress"
    },
    "memory_summary": "已完成 A/B/C 三个竞品的信息收集，重点差异在价格、API 和企业版功能。",
    "recent_messages": [
      {
        "role": "user",
        "content": "继续分析价格差异"
      }
    ],
    "artifact_refs": [
      {
        "type": "research_notes",
        "blob_id": "blob_abc"
      }
    ]
  }
}
```

## 6. 不应保存的内容

以下内容不应直接进入 Checkpoint：

| 内容 | 存储位置 |
|---|---|
| 完整历史消息 | messages 表 |
| 工具原始结果 | Blob |
| 网页全文 / HTML | Blob |
| 文件全文 | Blob / 文件存储 |
| 长日志 | Blob |
| 大模型 raw response | Blob |
| 完整事件历史 | agent_events |
| 向量数据 | Vector Store |

Checkpoint 中只保存这些内容的引用、摘要或预览。

## 7. 数据模型

```sql
CREATE TABLE agent_checkpoints (
  id TEXT PRIMARY KEY,
  run_id TEXT NOT NULL,
  checkpoint_no BIGINT NOT NULL,
  last_event_seq BIGINT NOT NULL,
  state_json JSONB NOT NULL,
  state_size_bytes INT,
  created_at TIMESTAMP DEFAULT now(),

  UNIQUE(run_id, checkpoint_no)
);

CREATE INDEX idx_agent_checkpoints_run_latest
ON agent_checkpoints(run_id, checkpoint_no DESC);
```

`agent_runs` 中保存最新 Checkpoint 引用：

```sql
ALTER TABLE agent_runs
ADD COLUMN latest_checkpoint_id TEXT,
ADD COLUMN latest_event_seq BIGINT DEFAULT 0;
```

## 8. 生成时机

不需要每一步都保存完整 Checkpoint。

推荐策略：

| 时机 | 是否生成 |
|---|---|
| Run 开始后 | 是 |
| 每 10~20 个关键事件 | 是 |
| 每个阶段完成后 | 是 |
| 用户暂停 / 审批前 | 是 |
| Worker 即将退出 | 是 |
| 每个 token 输出 | 否 |
| 每次 message.delta | 否 |

## 9. 大小控制

建议限制：

| Checkpoint 大小 | 策略 |
|---|---|
| <= 64KB | 直接写 JSONB |
| 64KB ~ 256KB | 压缩后写 DB，或写 Blob |
| > 256KB | 写 Blob |
| > 1MB | 需要重构 Checkpoint 内容 |

如果 Checkpoint 超过 1MB，通常说明错误地保存了完整工具结果、完整文件或完整历史消息。

## 10. 恢复流程

```text
1. 用户打开 Run 或 Worker 重启
2. 查询 agent_runs.latest_checkpoint_id
3. 加载最新 checkpoint
4. 查询 sequence > checkpoint.last_event_seq 的 events
5. 对 checkpoint.state 应用增量 events
6. 恢复 Runtime State
7. Context Builder 生成下一轮 Agent 输入
8. Agent 继续执行
```

## 11. Context Builder

恢复 Runtime State 后，不应直接把完整状态塞给模型。

Context Builder 只构造本轮必要上下文：

```text
- 用户原始目标
- 当前任务
- Todo 摘要
- 最近完成事项
- 最近用户消息
- 最近工具观察
- 必要 Artifact 摘要或引用
```

示例：

```text
用户目标：
分析竞品并生成报告。

当前任务：
整理价格差异。

当前进度：
已完成竞品信息收集和功能对比。
待完成报告撰写和风险总结。

最近观察：
B 产品企业版价格未公开，需要标注为“需销售咨询”。
```

## 12. 存储优化

推荐分层：

```text
Postgres:
  run 状态、task 状态、event、轻量 checkpoint

Object Storage:
  大文本、文件、网页、工具原始结果

Redis:
  运行中状态缓存

Vector Store:
  可检索长期记忆和文档片段
```

## 13. 关键原则

- Checkpoint 不是完整上下文备份
- Checkpoint 只保存最小可恢复状态
- 大内容必须使用 Blob 引用
- 恢复时使用最新 Checkpoint + 少量 Events
- 给模型的上下文由 Context Builder 动态生成
- Checkpoint 应定期生成，但不能每步全量复制

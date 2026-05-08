# Agent Session Recovery Contract

> 来源计划：[`docs/plans/2026-05-07-penmate-session-agent-style-full-refactor-plan.md`](docs/plans/2026-05-07-penmate-session-agent-style-full-refactor-plan.md)
>
> 本文档仅用于冻结 Task 1 的目标 contract 与命名骨架，不展开 Task 2/3 的数据库迁移、后端实现与前端消费细节。

## Session Summary

- `sessionId`
- `title`
- `status`
- `boundStyle`
- `lastTaskStatus`

## Recovery Snapshot

- `session`
- `activeTask`
- `pendingApproval`
- `messages`
- `workbenchContext`

## Turn Create Contract

- `operatorId`
- `userMessage`
- `taskRequest`

## Scope Guard

- Task 1 只冻结字段名、章节名与文档骨架。
- Task 1 不定义 SQL 列、索引、migration 细节。
- Task 1 不定义 Java DTO、Mapper、Controller 签名。
- Task 1 不定义前端 store/composable 的具体状态结构。

## Plan Alignment Checklist

- 文档包含 `Session Summary`
- 文档包含 `Recovery Snapshot`
- 文档包含 `Turn Create Contract`
- 文档未扩展到 Task 2/3 的实现文件

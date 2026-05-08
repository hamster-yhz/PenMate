# PenMate session / agent / style 全面重构说明

## 1. 文档目的

本文用于归档说明 PenMate 围绕会话恢复、Agent 任务流、风格绑定与工作台状态编排所做的全面重构，重点覆盖：做了什么改动、当前整体架构、关键数据模型与表职责、前后端数据流程、用户交互流程、恢复/续聊/风格绑定/审批恢复/流式执行机制，以及关键文件职责。

参考资料：[`docs/plans/2026-05-07-penmate-session-agent-style-full-refactor-plan.md`](docs/plans/2026-05-07-penmate-session-agent-style-full-refactor-plan.md)、[`docs/analysis/2026-05-07-agent-session-recovery-contract.md`](docs/analysis/2026-05-07-agent-session-recovery-contract.md)。

---

## 2. 这次重构做了什么改动

### 2.1 核心目标

本次重构的核心目标，是把 session、agent、style 三条原本相对分散的状态链路，统一收敛到“会话恢复快照”这一单一事实源上。

重构前，历史消息、运行中任务、审批阻塞、风格选择、工作台上下文分别存在于不同接口与前端局部状态里；用户返回历史会话时，前端只能恢复部分消息，无法稳定恢复任务状态、审批状态、风格状态与章节/插件等上下文。

重构后，系统围绕恢复快照重新设计接口、用例、状态结构与页面编排，前端不再本地拼装“猜测中的当前状态”，而是直接消费后端恢复结果。

### 2.2 后端改动摘要

- 统一 Agent 入口到 [`AgentController`](penmate-backend/src/main/java/com/penmate/backend/interfaces/api/agent/AgentController.java)，提供恢复、resume、create turn 三类核心接口。
- 新增恢复用例 [`AgentSessionRecoveryAppService`](penmate-backend/src/main/java/com/penmate/backend/application/agent/usecase/AgentSessionRecoveryAppService.java)；恢复快照成为对外契约中心。
- 新增 turn 创建用例 [`AgentTurnAppService`](penmate-backend/src/main/java/com/penmate/backend/application/agent/usecase/AgentTurnAppService.java)，替代旧的 message / generation 双入口模式。
- 将风格从“临时 payload 字段”提升为 session 级显式绑定，由 [`SessionStyleBindingAppService`](penmate-backend/src/main/java/com/penmate/backend/application/style/usecase/SessionStyleBindingAppService.java) 维护。
- 长流程生成统一收敛到 [`AgentGenerationWorkflow`](penmate-backend/src/main/java/com/penmate/backend/application/agent/orchestration/AgentGenerationWorkflow.java)，负责状态推进、RAG、Prompt 装配、Tool Loop、结果发布与失败收口。
- 实时事件统一由 [`RealtimeEventServiceImpl`](penmate-backend/src/main/java/com/penmate/backend/infrastructure/realtime/RealtimeEventServiceImpl.java) 负责发布到 WebSocket 与 SSE。

### 2.3 前端改动摘要

- 工作台会话恢复编排抽到 [`useWorkbenchSessionRecovery`](penmate-frontend/src/composables/workbench/useWorkbenchSessionRecovery.ts)。
- 聊天状态与任务运行态统一收敛到 [`useWorkbenchChat`](penmate-frontend/src/composables/workbench/useWorkbenchChat.ts)。
- 任务流式消费、轮询兜底、状态归一化抽到 [`useWorkbenchTaskRuntime`](penmate-frontend/src/composables/workbench/useWorkbenchTaskRuntime.ts)。
- 恢复快照前端状态结构抽为 [`workbenchSession.ts`](penmate-frontend/src/stores/workbenchSession.ts)。
- 风格切换接口 [`styleApi.switchStyle()`](penmate-frontend/src/api/modules/style.api.ts:22) 支持携带 `sessionId`，让风格切换直接联动当前会话绑定。

---

## 3. 重构后的整体架构

### 3.1 第一原则：恢复快照是唯一事实源

当前架构的核心原则是：工作台运行态只能由后端恢复快照定义，前端不能再通过“消息列表 + 本地缓存 + 当前页面状态”自行拼接恢复结果。

恢复快照至少包含以下信息：

- `session`
- `activeTask`
- `pendingApproval`
- `messages`
- `workbenchContext`

这套结构在契约文档 [`docs/analysis/2026-05-07-agent-session-recovery-contract.md`](docs/analysis/2026-05-07-agent-session-recovery-contract.md) 中被冻结。

### 3.2 后端架构分层

后端当前可以概括为以下分层：

1. **接口层**：[`AgentController`](penmate-backend/src/main/java/com/penmate/backend/interfaces/api/agent/AgentController.java)、[`StyleController`](penmate-backend/src/main/java/com/penmate/backend/interfaces/api/style/StyleController.java)
2. **应用层**：[`AgentSessionRecoveryAppService`](penmate-backend/src/main/java/com/penmate/backend/application/agent/usecase/AgentSessionRecoveryAppService.java)、[`AgentTurnAppService`](penmate-backend/src/main/java/com/penmate/backend/application/agent/usecase/AgentTurnAppService.java)、[`SessionStyleBindingAppService`](penmate-backend/src/main/java/com/penmate/backend/application/style/usecase/SessionStyleBindingAppService.java)
3. **编排层**：[`AgentGenerationWorkflow`](penmate-backend/src/main/java/com/penmate/backend/application/agent/orchestration/AgentGenerationWorkflow.java)
4. **仓储层**：[`AgentSessionRepository`](penmate-backend/src/main/java/com/penmate/backend/domain/agent/repository/AgentSessionRepository.java)、[`AgentSessionRepositoryImpl`](penmate-backend/src/main/java/com/penmate/backend/infrastructure/persistence/agent/AgentSessionRepositoryImpl.java)
5. **实时发布层**：[`RealtimeEventServiceImpl`](penmate-backend/src/main/java/com/penmate/backend/infrastructure/realtime/RealtimeEventServiceImpl.java)

### 3.3 前端架构分层

前端当前是“页面壳 + composable 编排”模式：

- 页面壳：[`Workbench/index.vue`](penmate-frontend/src/views/Workbench/index.vue)
- 会话恢复编排：[`useWorkbenchSessionRecovery`](penmate-frontend/src/composables/workbench/useWorkbenchSessionRecovery.ts)
- 聊天/会话状态：[`useWorkbenchChat`](penmate-frontend/src/composables/workbench/useWorkbenchChat.ts)
- 任务流运行时：[`useWorkbenchTaskRuntime`](penmate-frontend/src/composables/workbench/useWorkbenchTaskRuntime.ts)
- 会话恢复态模型：[`workbenchSession.ts`](penmate-frontend/src/stores/workbenchSession.ts)

前端最重要的变化，是把“消息时间线恢复”和“任务运行态恢复”拆开处理：消息由 timeline 投影，运行态由 runtime 管理，避免两者互相污染。

---

## 4. 关键数据模型与表 / 对象职责

### 4.1 `AgentSession`

职责：表示一个工作台会话的主身份与摘要状态，包括标题、所属项目、当前绑定风格、最近 turn / task、最近恢复时间等。它是恢复入口的根节点。

### 4.2 `AgentTurn`

职责：表示一次完整的用户轮次。一个 turn 绑定本轮主用户消息、主助手消息与对应 task。turn 的引入，让“继续对话”和“任务执行”有了明确的中间层。

### 4.3 `AgentMessage`

职责：表示时间线中的具体消息节点。重构后，消息不仅包含文本，还要承载消息类型、渲染块、审批挂载关系、投递状态等恢复相关元数据。

### 4.4 `AgentGenerationTask`

职责：表示一次实际运行中的生成任务，对应用户看到的“生成中 / 等待审批 / 已完成 / 已失败”状态。任务是流式事件与审批阻塞的直接主体。

### 4.5 `AgentTaskContext`

职责：表示任务启动时冻结的输入上下文快照，定义见 [`AgentTaskContext`](penmate-backend/src/main/java/com/penmate/backend/domain/agent/model/AgentTaskContext.java)。

它承载的关键信息包括：

- `taskStatus`
- `activeApprovalId`
- `chapterId`
- `selectedText`
- `outlineSnapshotJson`
- `cardsSnapshotJson`
- `ragSnapshotJson`
- `pluginBindingsJson`
- `styleSnapshotJson`
- `modelSnapshotJson`
- `contextHash`

该对象的意义在于：任务执行、任务恢复、审批继续执行都依赖同一份冻结上下文，而不再依赖页面当时的瞬时状态。

### 4.6 `StyleProfile`

职责：表示项目级可维护的风格档案，由 [`StyleController`](penmate-backend/src/main/java/com/penmate/backend/interfaces/api/style/StyleController.java) 提供 CRUD、切换、样本分析能力。

### 4.7 `SessionStyleBinding`

职责：表示 session 与 style 之间的显式绑定关系。当前绑定逻辑由 [`SessionStyleBindingAppService.bind()`](penmate-backend/src/main/java/com/penmate/backend/application/style/usecase/SessionStyleBindingAppService.java:24) 执行，包括：

1. 更新 session 当前 `boundStyleId`
2. 写入一条绑定历史

这使得风格能参与恢复、参与 task context 快照，也能稳定影响后续 turn。

### 4.8 核心表职责

根据计划文档 [`docs/plans/2026-05-07-penmate-session-agent-style-full-refactor-plan.md`](docs/plans/2026-05-07-penmate-session-agent-style-full-refactor-plan.md)，核心持久化表包括：

- `agent_sessions`：会话主表
- `agent_turns`：轮次表
- `agent_messages`：消息时间线表
- `agent_tasks`：任务主表
- `agent_task_contexts`：任务输入快照表
- `agent_task_results`：任务输出结果表
- `session_style_bindings`：会话风格绑定历史表
- `pending_tool_invocations`：等待审批的工具调用挂起表

这些表共同构成“可恢复会话”的最小数据闭环。

---

## 5. 前后端数据流程

### 5.1 进入工作台 / 恢复会话

1. 前端通过 [`resumeSession()`](penmate-frontend/src/api/modules/agent.api.ts:25) 调用恢复入口。
2. 后端 [`AgentController.resume()`](penmate-backend/src/main/java/com/penmate/backend/interfaces/api/agent/AgentController.java:52) 接收请求。
3. [`AgentSessionRecoveryAppService.resumeSession()`](penmate-backend/src/main/java/com/penmate/backend/application/agent/usecase/AgentSessionRecoveryAppService.java:29) 返回恢复快照。
4. 前端 [`useWorkbenchSessionRecovery.restore()`](penmate-frontend/src/composables/workbench/useWorkbenchSessionRecovery.ts:36) 回填 store。
5. 若 `activeTask.taskStatus = RUNNING`，则立即调用 [`openTaskStream()`](penmate-frontend/src/api/modules/agent.api.ts:38) 重连任务流。

### 5.2 发送消息 / 创建 turn

1. 前端 [`sendMessage()`](penmate-frontend/src/composables/workbench/useWorkbenchChat.ts:277) 先本地插入用户消息。
2. 前端调用 [`createTurn()`](penmate-frontend/src/api/modules/agent.api.ts:28)，携带 `operatorId`、`userMessage` 与 `taskRequest`。
3. 后端 [`AgentController.createTurn()`](penmate-backend/src/main/java/com/penmate/backend/interfaces/api/agent/AgentController.java:69) 将 DTO 转为 [`AgentTurnCommand`](penmate-backend/src/main/java/com/penmate/backend/application/agent/usecase/AgentTurnCommand.java)。
4. [`AgentTurnAppService.createTurn()`](penmate-backend/src/main/java/com/penmate/backend/application/agent/usecase/AgentTurnAppService.java:22) 创建用户消息、任务、上下文，并读取当前会话绑定风格。
5. 前端得到 `taskId` 后创建空助手消息，随后通过 [`consumeGenerationStream()`](penmate-frontend/src/composables/workbench/useWorkbenchTaskRuntime.ts:84) 消费流式结果。

### 5.3 任务执行与实时推送

生成主链路由 [`AgentGenerationWorkflow.runInternal()`](penmate-backend/src/main/java/com/penmate/backend/application/agent/orchestration/AgentGenerationWorkflow.java:50) 驱动：

1. 查询任务
2. 状态推进到 `RUNNING`
3. 发布 [`generation.started`](penmate-backend/src/main/java/com/penmate/backend/infrastructure/realtime/RealtimeEventServiceImpl.java:58)
4. RAG 检索
5. 构建任务上下文并补入风格快照，见 [`buildTaskContext()`](penmate-backend/src/main/java/com/penmate/backend/application/agent/orchestration/AgentGenerationWorkflow.java:106)
6. Prompt 装配、Tool Loop、模型调用
7. 如需审批，置为 `WAITING_APPROVAL`
8. 完成则发布 `generation.done`
9. 失败则发布 `generation.failed`

实时事件由 [`RealtimeEventServiceImpl`](penmate-backend/src/main/java/com/penmate/backend/infrastructure/realtime/RealtimeEventServiceImpl.java) 同时投递到项目 WebSocket 与任务 SSE。

---

## 6. 用户工作流程 / 交互流程

### 6.1 进入历史会话

用户进入工作台或从历史面板选择某个会话时，页面不会只拉取消息，而是执行完整恢复流程，恢复：

- 消息时间线
- 当前绑定风格
- 运行中任务
- 待处理审批
- 章节 / 插件等工作台上下文

### 6.2 继续对话

用户发送消息后，系统以 turn 为单入口启动完整链路，不再依赖旧的 `createMessage + createGeneration` 两段式流程。页面进入“生成中”，然后根据实时事件切换为“等待审批”或“完成”。

### 6.3 切换风格

用户在 [`StyleManager.vue`](penmate-frontend/src/components/workbench/StyleManager.vue) 中切换风格时，前端会把当前 `sessionId` 一并提交给 [`styleApi.switchStyle()`](penmate-frontend/src/api/modules/style.api.ts:22)。后端先更新项目默认风格，再绑定当前 session。新风格只影响后续新 turn，不回写历史上下文。

### 6.4 处理审批

当任务执行中遇到需要人工确认的工具调用时，后端发布 `generation.waiting_approval` 事件；前端 runtime 收到后将任务切换到 `waiting_approval`，并在聊天区展示审批卡片。用户审批后，系统基于原 task context 继续执行，而不是重新拼一份新上下文。

---

## 7. 关键能力分别如何工作

### 7.1 历史会话恢复

历史会话恢复通过 [`GET /sessions/{sessionId}/recovery`](penmate-backend/src/main/java/com/penmate/backend/interfaces/api/agent/AgentController.java:41) 或 [`POST /sessions/{sessionId}/resume`](penmate-backend/src/main/java/com/penmate/backend/interfaces/api/agent/AgentController.java:52) 完成。前端依据恢复快照中的 `messages`、`activeTask`、`pendingApproval`、`workbenchContext` 直接恢复页面。

### 7.2 继续对话

继续对话通过 [`POST /sessions/{sessionId}/turns`](penmate-backend/src/main/java/com/penmate/backend/interfaces/api/agent/AgentController.java:69) 完成。turn 创建时就同时确定用户消息、task、上下文、绑定风格，前端只需拿到 `taskId` 进入流式消费。

### 7.3 风格绑定

风格绑定以 session 为中心：

1. UI 切换风格
2. 前端传 `sessionId`
3. 后端切项目默认风格
4. 后端绑定当前 session
5. 新 task 从 session 读取绑定风格，并写入 [`styleSnapshotJson`](penmate-backend/src/main/java/com/penmate/backend/domain/agent/model/AgentTaskContext.java:134)

### 7.4 审批恢复

审批恢复依赖以下断点信息：

- [`AgentTaskContext.activeApprovalId`](penmate-backend/src/main/java/com/penmate/backend/domain/agent/model/AgentTaskContext.java:14)
- 恢复快照里的 `pendingApproval`
- 消息时间线中的审批元数据

因此用户离开页面后再次进入，系统仍可知道哪个任务阻塞、阻塞在哪个审批点、页面应展示什么操作。

### 7.5 任务流式执行

前端通过 [`agentApi.openTaskStream()`](penmate-frontend/src/api/modules/agent.api.ts:38) 建立 SSE，消费：

- `generation.started`
- `generation.token`
- `generation.tool_call`
- `generation.waiting_approval`
- `generation.done`
- `generation.failed`

消费逻辑集中在 [`consumeGenerationStream()`](penmate-frontend/src/composables/workbench/useWorkbenchTaskRuntime.ts:84)。如果流中断，还可通过 [`pollGenerationAsFallback()`](penmate-frontend/src/composables/workbench/useWorkbenchTaskRuntime.ts:63) 做短期轮询兜底。

---

## 8. 关键文件与职责

### 8.1 后端

- [`AgentController.java`](penmate-backend/src/main/java/com/penmate/backend/interfaces/api/agent/AgentController.java)：恢复、resume、createTurn 统一入口
- [`AgentSessionRecoveryAppService.java`](penmate-backend/src/main/java/com/penmate/backend/application/agent/usecase/AgentSessionRecoveryAppService.java)：恢复快照应用层映射
- [`AgentTurnAppService.java`](penmate-backend/src/main/java/com/penmate/backend/application/agent/usecase/AgentTurnAppService.java)：turn 创建、上下文装配、风格读取
- [`SessionStyleBindingAppService.java`](penmate-backend/src/main/java/com/penmate/backend/application/style/usecase/SessionStyleBindingAppService.java)：会话风格绑定与绑定历史写入
- [`StyleController.java`](penmate-backend/src/main/java/com/penmate/backend/interfaces/api/style/StyleController.java)：风格 CRUD / 切换 / 样本分析
- [`AgentGenerationWorkflow.java`](penmate-backend/src/main/java/com/penmate/backend/application/agent/orchestration/AgentGenerationWorkflow.java)：生成任务长流程编排
- [`AgentTaskContext.java`](penmate-backend/src/main/java/com/penmate/backend/domain/agent/model/AgentTaskContext.java)：任务上下文快照模型
- [`AgentSessionRepository.java`](penmate-backend/src/main/java/com/penmate/backend/domain/agent/repository/AgentSessionRepository.java)：恢复相关仓储端口
- [`AgentSessionRepositoryImpl.java`](penmate-backend/src/main/java/com/penmate/backend/infrastructure/persistence/agent/AgentSessionRepositoryImpl.java)：恢复仓储实现
- [`RealtimeEventServiceImpl.java`](penmate-backend/src/main/java/com/penmate/backend/infrastructure/realtime/RealtimeEventServiceImpl.java)：统一实时事件发布

### 8.2 前端

- [`agent.api.ts`](penmate-frontend/src/api/modules/agent.api.ts)：恢复、resume、createTurn、task 查询、SSE 连接
- [`style.api.ts`](penmate-frontend/src/api/modules/style.api.ts)：风格接口与 session 级绑定切换
- [`workbenchSession.ts`](penmate-frontend/src/stores/workbenchSession.ts)：恢复快照前端状态模型
- [`useWorkbenchSessionRecovery.ts`](penmate-frontend/src/composables/workbench/useWorkbenchSessionRecovery.ts)：工作台恢复编排器
- [`useWorkbenchChat.ts`](penmate-frontend/src/composables/workbench/useWorkbenchChat.ts)：聊天状态、历史面板、发送消息、恢复投影
- [`useWorkbenchTaskRuntime.ts`](penmate-frontend/src/composables/workbench/useWorkbenchTaskRuntime.ts)：任务流消费、状态归一化、轮询兜底
- [`Workbench/index.vue`](penmate-frontend/src/views/Workbench/index.vue)：工作台总装配入口
- [`StyleManager.vue`](penmate-frontend/src/components/workbench/StyleManager.vue)：风格管理与会话级绑定交互入口

---

## 9. 总结

这次重构的本质，不是简单替换几个接口，而是把 PenMate 的 agent 工作台从“局部状态拼接型实现”重构为“以恢复快照为中心的会话运行时系统”。

其结果是：

- 历史会话可以被完整恢复，而不是只恢复消息。
- 新对话入口统一为 turn，减少状态分裂。
- 风格从临时 payload 升级为 session 级业务绑定。
- 审批阻塞成为可恢复断点，而不是一次性 UI 状态。
- 流式任务执行、恢复重连、审批继续执行共享同一套任务模型。

这为后续继续补强任务结果持久化、审批恢复细节、上下文快照一致性校验与更完整的历史工作台回放能力提供了稳定基础。

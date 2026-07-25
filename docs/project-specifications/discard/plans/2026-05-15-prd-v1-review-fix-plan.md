# PRD v1 Review Closed-Loop Fix Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use [executing-plans] mode to implement this plan task-by-task.

**Goal:** 修复 [`2026-05-15 PRD v1 改造结果复核审计`](docs/analysis/2026-05-15-prd-v1-review.md) 中已确认的 Critical / Important 闭环缺口，使 live runtime、recovery、Workbench 展示与测试夹具重新对齐，并以 fresh verification 作为唯一完成依据。

**Architecture:** 继续维持由 [`AgentGenerationWorkflow`](penmate-backend/src/main/java/com/penmate/backend/application/agent/orchestration/AgentGenerationWorkflow.java:822) 作为唯一长流程主控的单主控架构，不新增第二个 orchestrator、不新增并行主流程。修复重点放在“协议适配层 + recovery 聚合层 + 前端 presenter/consumer + 合同测试矩阵”四段链路，通过后端出站归一化与前端兼容消费共同消除字段错位，并把已落库的结构化快照完整暴露到 recovery contract。

**Tech Stack:** Java 21、Spring Boot 3.3、MyBatis、Jackson、Vue 3、TypeScript、Vitest、JUnit 5、Mockito

---

## Scope

本计划**只**覆盖审计结论中已确认的以下问题：

1. Todo 的 `recommendedNextAction` / `nextAction` 协议错位。
2. 失败原因 `message` / `errorMsg` 协议错位。
3. [`AgentGenerationWorkflow.buildRuntimeStatusView()`](penmate-backend/src/main/java/com/penmate/backend/application/agent/orchestration/AgentGenerationWorkflow.java:822) 未完整填充 `storyBibleApproval` / `todoPlan`。
4. 已持久化的结构化快照在 recovery 输出中暴露不完整。
5. 测试夹具比真实后端 payload 更理想化，削弱了前述 Critical / Important 问题的证明力。

## Non-Goals

- 不新增主控、子编排器、第二条 runtime 发布链路。
- 不改动数据库 schema；当前问题不是“没落库”，而是“已落库但未完整出站”。
- 不扩展新的 Workbench 卡片类型；只修复现有 [`RuntimeStatusCard.vue`](penmate-frontend/src/components/workbench/RuntimeStatusCard.vue)、[`TodoPlanCard.vue`](penmate-frontend/src/components/workbench/TodoPlanCard.vue)、[`StoryBibleApprovalCard.vue`](penmate-frontend/src/components/workbench/StoryBibleApprovalCard.vue) 的真实数据来源。
- 不处理审计文档中未列入 Critical / Important 的其他优化项。

## Global Delivery Rules

1. **所有任务必须使用 [test-driven-development] 思路执行**：先补失败测试，再做最小实现，再 fresh 复跑。
2. **所有“完成”声明前必须执行 fresh verification**，不得复用旧结果。
3. **任何协议修复必须同时更新真实契约测试与前端消费测试**，防止只改一侧。
4. **外部展示 contract 可以兼容别名，但内部真源不要漂移**：
   - Todo 内部结构化真源继续以 [`TodoPlanView.recommendedNextAction`](penmate-backend/src/main/java/com/penmate/backend/application/todo/TodoPlanView.java:10) 为准。
   - Workbench 出站 / presenter 可以兼容 `nextAction` 别名，但不能再依赖理想化夹具掩盖问题。
5. **所有任务完成后必须执行一次后端 + 前端联合验证批次**。

## Issue Matrix

| Issue | Severity | Root Cause | Primary Impact |
|---|---|---|---|
| Todo `recommendedNextAction` / `nextAction` 错位 | Critical | 后端 tool/result summary 真源输出 `recommendedNextAction`，前端 presenter 主要读取 `nextAction` | Todo 卡片 next action 在 live/recovery 下缺失 |
| 失败原因 `message` / `errorMsg` 错位 | Critical | runtime status 使用 `message`，前端 failure path 主要消费 `errorMsg` | 失败卡、失败文案、流式失败展示不稳定 |
| `storyBibleApproval` / `todoPlan` 未填充 | Critical + Important | [`RuntimeStatusView`](penmate-backend/src/main/java/com/penmate/backend/application/agent/runtime/RuntimeStatusView.java:15) 已声明字段，但 [`buildRuntimeStatusView()`](penmate-backend/src/main/java/com/penmate/backend/application/agent/orchestration/AgentGenerationWorkflow.java:822) 长期传 `null` | live waiting approval / todo review 卡片信息不足 |
| persisted snapshot recovery 暴露不完整 | Important | [`AgentSessionRepositoryImpl.buildWorkbenchContext()`](penmate-backend/src/main/java/com/penmate/backend/infrastructure/persistence/agent/AgentSessionRepositoryImpl.java:247) 只拼部分上下文，未把 `taskProfileJson` / `promptPlanJson` / `contextPackageJson` 透出 | recovery contract 不完整，影响恢复后调试与展示一致性 |
| 测试夹具理想化 | 为 Critical / Important 提供验证兜底 | 前端 / E2E 测试手写了比后端更完整或字段名更“正确”的 payload | 掩盖真实协议不一致 |

---

## Task 1: 修复 Todo nextAction 与失败原因协议错位

Use [test-driven-development] mode for this task.

**Root Cause:**
- Todo 真源在 [`TodoPlannerToolHandler`](penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/handler/TodoPlannerToolHandler.java:198) 与 [`TodoPlanView`](penmate-backend/src/main/java/com/penmate/backend/application/todo/TodoPlanView.java:10) 中使用 `recommendedNextAction`。
- 前端 [`createWorkbenchRuntimePresenter()`](penmate-frontend/src/composables/workbench/useWorkbenchRuntimePresenter.ts:296) 中的 [`resolveNextAction()`](penmate-frontend/src/composables/workbench/useWorkbenchRuntimePresenter.ts:198) 和 [`buildTodoPlanCard()`](penmate-frontend/src/composables/workbench/useWorkbenchRuntimePresenter.ts:235) 主要消费 `nextAction`。
- 前端 [`createTaskRuntime()`](penmate-frontend/src/composables/workbench/useWorkbenchTaskRuntime.ts:79) 在失败路径主要消费 `errorMsg`，但 runtime status 失败原因可能只存在于 `message`。

**Impact Scope:**
- live runtime: Todo 卡片 next action 丢失；失败状态文案可能为空。
- recovery: Todo summary 已有数据但 presenter 不读正确字段。
- chat/runtime binding: 失败状态回放与失败卡可能出现“状态已失败但原因空白”。

**Files:**
- Modify: [`penmate-frontend/src/composables/workbench/useWorkbenchRuntimePresenter.ts`](penmate-frontend/src/composables/workbench/useWorkbenchRuntimePresenter.ts:198)
- Modify: [`penmate-frontend/src/composables/workbench/useWorkbenchTaskRuntime.ts`](penmate-frontend/src/composables/workbench/useWorkbenchTaskRuntime.ts:50)
- Modify: [`penmate-frontend/src/api/types.ts`](penmate-frontend/src/api/types.ts:50)
- Modify: [`penmate-frontend/src/composables/workbench/useWorkbenchRuntimePresenter.spec.ts`](penmate-frontend/src/composables/workbench/useWorkbenchRuntimePresenter.spec.ts)
- Modify: [`penmate-frontend/src/composables/workbench/useWorkbenchTaskRuntime.spec.ts`](penmate-frontend/src/composables/workbench/useWorkbenchTaskRuntime.spec.ts)
- Modify: [`penmate-frontend/src/composables/workbench/__tests__/useWorkbenchChat.spec.ts`](penmate-frontend/src/composables/workbench/__tests__/useWorkbenchChat.spec.ts)
- Modify: [`penmate-frontend/src/views/Workbench/index.runtime-e2e.spec.ts`](penmate-frontend/src/views/Workbench/index.runtime-e2e.spec.ts)
- Reference true source: [`penmate-backend/src/main/java/com/penmate/backend/application/todo/TodoPlanView.java`](penmate-backend/src/main/java/com/penmate/backend/application/todo/TodoPlanView.java:10)
- Reference true source: [`penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/handler/TodoPlannerToolHandler.java`](penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/handler/TodoPlannerToolHandler.java:198)

**Step 1: Write the failing tests**

新增/改造以下断言：

1. 在 [`useWorkbenchRuntimePresenter.spec.ts`](penmate-frontend/src/composables/workbench/useWorkbenchRuntimePresenter.spec.ts) 增加 case：
   - `todoSummary` 只含 `recommendedNextAction`，不含 `nextAction`。
   - 断言 `todoPlanCard.nextActionText === 'apply_todo_plan'`。
2. 在 [`useWorkbenchRuntimePresenter.spec.ts`](penmate-frontend/src/composables/workbench/useWorkbenchRuntimePresenter.spec.ts) 增加 case：
   - runtime `phase='failed'`，只传 `message='质量审查超时'`，不传 `errorMsg`。
   - 断言 `status.failureReasonText === '质量审查超时'`。
3. 在 [`useWorkbenchTaskRuntime.spec.ts`](penmate-frontend/src/composables/workbench/useWorkbenchTaskRuntime.spec.ts) 增加 case：
   - `generation.failed` 事件 payload 只有 `message`。
   - 断言 `setAgentStatusDetailText` 与 reject error 使用 `message` 回退。
4. 在 [`useWorkbenchChat.spec.ts`](penmate-frontend/src/composables/workbench/__tests__/useWorkbenchChat.spec.ts) 增加 case：
   - 失败流事件只给 `message` 时，聊天失败占位消息仍显示“生成失败：质量审查超时”。
5. 在 [`index.runtime-e2e.spec.ts`](penmate-frontend/src/views/Workbench/index.runtime-e2e.spec.ts) 把 Todo recovery 夹具改成后端真实形态：`recommendedNextAction` 为真源，验证页面仍显示正确 next action。

**Step 2: Run tests to verify they fail**

Run: [`npm test -- --run src/composables/workbench/useWorkbenchRuntimePresenter.spec.ts src/composables/workbench/useWorkbenchTaskRuntime.spec.ts src/composables/workbench/__tests__/useWorkbenchChat.spec.ts src/views/Workbench/index.runtime-e2e.spec.ts`](penmate-frontend/package.json)

Expected:
- 至少 1 个断言失败，典型失败表现为：
  - `expected "" to be "apply_todo_plan"`
  - `expected "" to be "质量审查超时"`

**Step 3: Write minimal implementation**

1. 在 [`useWorkbenchRuntimePresenter.ts`](penmate-frontend/src/composables/workbench/useWorkbenchRuntimePresenter.ts:198) 新增统一别名解析函数，例如：
   - `resolveTodoNextAction(summary)`：优先 `summary.nextAction`，回退 `summary.recommendedNextAction`。
   - `resolveFailureReason(runtime, recovery)`：优先 `runtime.errorMsg`，回退 `runtime.message`，再回退 tool call `errorMessage`。
2. 在 [`buildTodoPlanCard()`](penmate-frontend/src/composables/workbench/useWorkbenchRuntimePresenter.ts:235) 使用别名解析，不再直接硬编码 `summary?.nextAction`。
3. 在 [`resolveNextAction()`](penmate-frontend/src/composables/workbench/useWorkbenchRuntimePresenter.ts:198) 中加入 `todoSummary.recommendedNextAction` 回退。
4. 在 [`toRuntimeEventSource()`](penmate-frontend/src/composables/workbench/useWorkbenchTaskRuntime.ts:64) 保持现有字段，但消费端统一使用 `errorMsg || message`。
5. 在 [`generation.failed` listener](penmate-frontend/src/composables/workbench/useWorkbenchTaskRuntime.ts:187) 中，把错误提取统一成一个 helper：
   - `payload.errorMsg || payload.message || payload.errorCode || '生成失败'`。
6. 如有必要，在 [`WorkbenchRuntimeEventSource`](penmate-frontend/src/api/types.ts:50) 与 Todo summary 类型中补上 `recommendedNextAction?: string | null`，但**不要**删除 `nextAction`，先做兼容收口。

**Step 4: Run tests to verify they pass**

Run: [`npm test -- --run src/composables/workbench/useWorkbenchRuntimePresenter.spec.ts src/composables/workbench/useWorkbenchTaskRuntime.spec.ts src/composables/workbench/__tests__/useWorkbenchChat.spec.ts src/views/Workbench/index.runtime-e2e.spec.ts`](penmate-frontend/package.json)

Expected:
- 以上文件全部通过。

**Step 5: Test strategy**

- 单测覆盖别名解析优先级。
- runtime spec 覆盖 live failure 事件只有 `message` 的场景。
- e2e spec 覆盖 recovery Todo summary 使用后端真实字段名的场景。

**Step 6: Acceptance criteria**

- Todo 卡片在 live runtime 与 recovery 下都能显示 next action。
- failure card / runtime status / chat fallback 在只收到 `message` 时不再空白。
- 前端不再依赖理想化 `nextAction` / `errorMsg` 才能工作。

**Step 7: Commit**

Run: [`git add penmate-frontend/src/composables/workbench/useWorkbenchRuntimePresenter.ts penmate-frontend/src/composables/workbench/useWorkbenchTaskRuntime.ts penmate-frontend/src/api/types.ts penmate-frontend/src/composables/workbench/useWorkbenchRuntimePresenter.spec.ts penmate-frontend/src/composables/workbench/useWorkbenchTaskRuntime.spec.ts penmate-frontend/src/composables/workbench/__tests__/useWorkbenchChat.spec.ts penmate-frontend/src/views/Workbench/index.runtime-e2e.spec.ts && git commit -m "fix(workbench): tolerate runtime protocol aliases"`](.gitignore)

---

## Task 2: 在单主控内补全 `storyBibleApproval` / `todoPlan` live runtime 载荷

Use [test-driven-development] mode for this task.

**Root Cause:**
- [`RuntimeStatusView`](penmate-backend/src/main/java/com/penmate/backend/application/agent/runtime/RuntimeStatusView.java:15) 已定义 `storyBibleApproval` 与 `todoPlan`。
- 但 [`AgentGenerationWorkflow.buildRuntimeStatusView()`](penmate-backend/src/main/java/com/penmate/backend/application/agent/orchestration/AgentGenerationWorkflow.java:822) 目前固定传 `null`，导致 runtime status 虽声明字段，却没有真实业务填充。
- 前端测试依靠手写夹具提供 `entryKeys` / `nextAction` / Todo 详情，掩盖了 live backend payload 不完整的问题。

**Impact Scope:**
- `waiting_approval` 与 `story_bible_review` 阶段，故事圣经卡片可能只能靠 recovery 或 tool output 兜底。
- `todo_review` 阶段，Todo 卡片对 live runtime 不稳定，依赖 tool output parse 与测试夹具。

**Files:**
- Modify: [`penmate-backend/src/main/java/com/penmate/backend/application/agent/orchestration/AgentGenerationWorkflow.java`](penmate-backend/src/main/java/com/penmate/backend/application/agent/orchestration/AgentGenerationWorkflow.java:822)
- Modify: [`penmate-backend/src/test/java/com/penmate/backend/application/agent/orchestration/AgentGenerationWorkflowTest.java`](penmate-backend/src/test/java/com/penmate/backend/application/agent/orchestration/AgentGenerationWorkflowTest.java)
- Modify: [`penmate-backend/src/test/java/com/penmate/backend/application/agent/runtime/TaskRuntimeStatusPublisherTest.java`](penmate-backend/src/test/java/com/penmate/backend/application/agent/runtime/TaskRuntimeStatusPublisherTest.java)
- Modify: [`penmate-backend/src/test/java/com/penmate/backend/infrastructure/realtime/RealtimeEventServiceImplTest.java`](penmate-backend/src/test/java/com/penmate/backend/infrastructure/realtime/RealtimeEventServiceImplTest.java)
- Modify: [`penmate-frontend/src/composables/workbench/useWorkbenchRuntimePresenter.ts`](penmate-frontend/src/composables/workbench/useWorkbenchRuntimePresenter.ts:226)
- Modify: [`penmate-frontend/src/composables/workbench/useWorkbenchRuntimePresenter.spec.ts`](penmate-frontend/src/composables/workbench/useWorkbenchRuntimePresenter.spec.ts)
- Modify: [`penmate-frontend/src/views/Workbench/index.chat-binding.spec.ts`](penmate-frontend/src/views/Workbench/index.chat-binding.spec.ts)
- Modify: [`penmate-frontend/src/views/Workbench/index.runtime-e2e.spec.ts`](penmate-frontend/src/views/Workbench/index.runtime-e2e.spec.ts)

**Step 1: Write the failing tests**

新增/改造以下断言：

1. 在 [`AgentGenerationWorkflowTest`](penmate-backend/src/test/java/com/penmate/backend/application/agent/orchestration/AgentGenerationWorkflowTest.java) 为 Todo 场景增加断言：
   - runtime status JSON 中 `todoPlan.planTitle`、`todoPlan.items`、`todoPlan.nextAction`（或 `recommendedNextAction` 转出的展示别名）存在。
2. 在 [`AgentGenerationWorkflowTest`](penmate-backend/src/test/java/com/penmate/backend/application/agent/orchestration/AgentGenerationWorkflowTest.java) 为等待审批场景增加断言：
   - runtime status JSON 中 `storyBibleApproval.proposalSummary`、`storyBibleApproval.entryKeys`、`storyBibleApproval.nextAction` 存在。
3. 在 [`RealtimeEventServiceImplTest`](penmate-backend/src/test/java/com/penmate/backend/infrastructure/realtime/RealtimeEventServiceImplTest.java) 把当前 `storyBibleApproval=null` / `todoPlan=null` 的序列化夹具改为非空真实值，并先让测试失败。
4. 在 [`useWorkbenchRuntimePresenter.spec.ts`](penmate-frontend/src/composables/workbench/useWorkbenchRuntimePresenter.spec.ts) 增加 case：
   - presenter 优先消费 `runtime.todoPlan` / `runtime.storyBibleApproval`，而不是只能从 `toolCall.output` 或 recovery 提取。

**Step 2: Run tests to verify they fail**

Run: [`mvn -Dtest=AgentGenerationWorkflowTest,TaskRuntimeStatusPublisherTest,RealtimeEventServiceImplTest test`](penmate-backend/pom.xml)

Expected:
- 失败，典型原因为 `storyBibleApproval` / `todoPlan` 仍为 `null`。

**Step 3: Write minimal implementation**

1. 在 [`AgentGenerationWorkflow`](penmate-backend/src/main/java/com/penmate/backend/application/agent/orchestration/AgentGenerationWorkflow.java:822) 内新增两个 helper，不创建新主控：
   - `resolveRuntimeTodoPlan(AgentTaskContext taskContext)`
   - `resolveRuntimeStoryBibleApproval(AgentTaskContext taskContext)`
2. `resolveRuntimeTodoPlan(...)` 的数据来源优先级：
   - 当前 `activeToolCallsSnapshot` 中 `todo_planner` 的 `output`
   - 若没有 live output，则从已记录的结构化结果 / task result 摘要回填
   - 出站时补一个面向 Workbench 的 `nextAction` 别名，来源仍取 `recommendedNextAction`
3. `resolveRuntimeStoryBibleApproval(...)` 的数据来源优先级：
   - [`resolveApprovalStatus()`](penmate-backend/src/main/java/com/penmate/backend/application/agent/orchestration/AgentGenerationWorkflow.java:893) 已解析的 `approvalSummary`
   - `pendingToolInvocationRepository.findByApprovalId(...)` 中的 `approvalSummaryJson`
   - 如存在 tool output，合并 `proposalSummary` / `entryKeys`
4. 让 [`buildRuntimeStatusView()`](penmate-backend/src/main/java/com/penmate/backend/application/agent/orchestration/AgentGenerationWorkflow.java:822) 真正填入 `storyBibleApproval` / `todoPlan`。
5. 前端 [`resolveTodoSummary()`](penmate-frontend/src/composables/workbench/useWorkbenchRuntimePresenter.ts:226) 与 [`resolveStoryBibleSummary()`](penmate-frontend/src/composables/workbench/useWorkbenchRuntimePresenter.ts:253) 增加对 `runtime.todoPlan` / `runtime.storyBibleApproval` 的优先消费。
6. 如需扩展 TS 类型，在 [`WorkbenchRuntimeEventSource`](penmate-frontend/src/api/types.ts:50) 增加 `todoPlan` / `storyBibleApproval` 字段定义。

**Step 4: Run tests to verify they pass**

Run: [`mvn -Dtest=AgentGenerationWorkflowTest,TaskRuntimeStatusPublisherTest,RealtimeEventServiceImplTest test`](penmate-backend/pom.xml)

Run: [`npm test -- --run src/composables/workbench/useWorkbenchRuntimePresenter.spec.ts src/views/Workbench/index.chat-binding.spec.ts src/views/Workbench/index.runtime-e2e.spec.ts`](penmate-frontend/package.json)

Expected:
- 后端与前端相关测试全部通过。

**Step 5: Test strategy**

- 后端单测冻结 `RuntimeStatusView` 非空字段的真实发布行为。
- 前端 presenter / 页面测试验证 live runtime 已可独立驱动卡片，不再依赖 recovery 才完整。

**Step 6: Acceptance criteria**

- `story_bible_review` / `waiting_approval` live runtime 事件包含完整故事圣经审批卡片信息。
- `todo_review` live runtime 事件包含完整 Todo 计划卡片信息。
- 整个修复仍由 [`AgentGenerationWorkflow`](penmate-backend/src/main/java/com/penmate/backend/application/agent/orchestration/AgentGenerationWorkflow.java:822) 单点驱动，无新增 orchestrator。

**Step 7: Commit**

Run: [`git add penmate-backend/src/main/java/com/penmate/backend/application/agent/orchestration/AgentGenerationWorkflow.java penmate-backend/src/test/java/com/penmate/backend/application/agent/orchestration/AgentGenerationWorkflowTest.java penmate-backend/src/test/java/com/penmate/backend/application/agent/runtime/TaskRuntimeStatusPublisherTest.java penmate-backend/src/test/java/com/penmate/backend/infrastructure/realtime/RealtimeEventServiceImplTest.java penmate-frontend/src/composables/workbench/useWorkbenchRuntimePresenter.ts penmate-frontend/src/composables/workbench/useWorkbenchRuntimePresenter.spec.ts penmate-frontend/src/views/Workbench/index.chat-binding.spec.ts penmate-frontend/src/views/Workbench/index.runtime-e2e.spec.ts penmate-frontend/src/api/types.ts && git commit -m "fix(agent): publish complete runtime approval and todo payloads"`](.gitignore)

---

## Task 3: 打通 persisted structured snapshot 的 recovery 暴露

Use [test-driven-development] mode for this task.

**Root Cause:**
- [`AgentSessionRepositoryImpl.buildActiveTask()`](penmate-backend/src/main/java/com/penmate/backend/infrastructure/persistence/agent/AgentSessionRepositoryImpl.java:169) 已把 `taskProfileJson` / `promptPlanJson` / `contextPackageJson` 读入 `AgentTaskContext`。
- 但 [`buildWorkbenchContext()`](penmate-backend/src/main/java/com/penmate/backend/infrastructure/persistence/agent/AgentSessionRepositoryImpl.java:247) 只输出 `chapterId`、`selectedText`、`activePlugins`、`modelConfigId`、`outlineSnapshot`、`activeTaskRuntime`、`resultSummary`。
- recovery API 因此无法完整反映已持久化快照。

**Impact Scope:**
- recovery contract 无法完整重建任务上下文。
- 后续调试、回放、前端展示与审计比对缺乏结构化依据。

**Files:**
- Modify: [`penmate-backend/src/main/java/com/penmate/backend/infrastructure/persistence/agent/AgentSessionRepositoryImpl.java`](penmate-backend/src/main/java/com/penmate/backend/infrastructure/persistence/agent/AgentSessionRepositoryImpl.java:247)
- Modify: [`penmate-backend/src/main/java/com/penmate/backend/application/agent/query/AgentSessionRecoveryQueryService.java`](penmate-backend/src/main/java/com/penmate/backend/application/agent/query/AgentSessionRecoveryQueryService.java:34)
- Modify: [`penmate-backend/src/main/java/com/penmate/backend/application/agent/usecase/AgentSessionRecoveryAppService.java`](penmate-backend/src/main/java/com/penmate/backend/application/agent/usecase/AgentSessionRecoveryAppService.java:33)
- Modify: [`penmate-backend/src/test/java/com/penmate/backend/application/agent/query/AgentSessionRecoveryQueryServiceTest.java`](penmate-backend/src/test/java/com/penmate/backend/application/agent/query/AgentSessionRecoveryQueryServiceTest.java)
- Modify: [`penmate-backend/src/test/java/com/penmate/backend/interfaces/api/agent/AgentWorkflowEndToEndContractTest.java`](penmate-backend/src/test/java/com/penmate/backend/interfaces/api/agent/AgentWorkflowEndToEndContractTest.java:221)
- Modify: [`penmate-frontend/src/api/types.ts`](penmate-frontend/src/api/types.ts:90)
- Optional test-only helper update: [`penmate-frontend/src/composables/workbench/__tests__/useWorkbenchSessionRecovery.spec.ts`](penmate-frontend/src/composables/workbench/__tests__/useWorkbenchSessionRecovery.spec.ts)

**Step 1: Write the failing tests**

1. 在 [`AgentWorkflowEndToEndContractTest`](penmate-backend/src/test/java/com/penmate/backend/interfaces/api/agent/AgentWorkflowEndToEndContractTest.java:221) 的 Case D 增加以下断言：
   - `$.data.workbenchContext.taskProfile.executionProfile`
   - `$.data.workbenchContext.promptPlan.finalProfile`
   - `$.data.workbenchContext.contextPackage.storyBibleEntries[0]`
2. 在 [`AgentSessionRecoveryQueryServiceTest`](penmate-backend/src/test/java/com/penmate/backend/application/agent/query/AgentSessionRecoveryQueryServiceTest.java) 增加 case：
   - repository 返回包含 `taskProfileJson` / `promptPlanJson` / `contextPackageJson` 的 task context。
   - 断言 query / app service 最终产物把这些 JSON 以对象形式向外暴露，而不是丢弃。
3. 如果前端已消费 recovery 类型，在 [`types.ts`](penmate-frontend/src/api/types.ts:90) 对应 spec 增加字段存在性校验。

**Step 2: Run tests to verify they fail**

Run: [`mvn -Dtest=AgentSessionRecoveryQueryServiceTest,AgentWorkflowEndToEndContractTest test`](penmate-backend/pom.xml)

Expected:
- Case D 或新增 query test 失败，提示 `taskProfile` / `promptPlan` / `contextPackage` 路径不存在。

**Step 3: Write minimal implementation**

1. 在 [`buildWorkbenchContext()`](penmate-backend/src/main/java/com/penmate/backend/infrastructure/persistence/agent/AgentSessionRepositoryImpl.java:247) 新增三段输出：
   - `taskProfile`
   - `promptPlan`
   - `contextPackage`
2. 输出形式必须是 `parseJsonOrRaw(...)` 后的结构化对象，而不是原始字符串。
3. 不改动数据库 schema，不改动 `AgentTaskContext` 持久化路径，只补 recovery 出站聚合。
4. 在 [`AgentSessionRecoveryAppService`](penmate-backend/src/main/java/com/penmate/backend/application/agent/usecase/AgentSessionRecoveryAppService.java:33) 保持统一 `parseJsonOrRaw(...)` 输出，避免把新字段重新字符串化。
5. 在 [`WorkbenchRecoveryContextSnapshot`](penmate-frontend/src/api/types.ts:90) 增加：
   - `taskProfile?: Record<string, unknown> | null`
   - `promptPlan?: Record<string, unknown> | null`
   - `contextPackage?: Record<string, unknown> | null`

**Step 4: Run tests to verify they pass**

Run: [`mvn -Dtest=AgentSessionRecoveryQueryServiceTest,AgentWorkflowEndToEndContractTest test`](penmate-backend/pom.xml)

Expected:
- 通过，Case D 能看到完整 recovery context。

**Step 5: Test strategy**

- query service test 验证 enrichment 前后不会吞掉 snapshot 字段。
- end-to-end contract test 验证控制器实际返回 JSON 路径，而不是只验证内部对象。

**Step 6: Acceptance criteria**

- recovery 输出中存在 `workbenchContext.taskProfile`、`workbenchContext.promptPlan`、`workbenchContext.contextPackage`。
- 这些字段与已落库 JSON 结构一致，不是空对象、空字符串或重复字段。
- 无新增 migration。

**Step 7: Commit**

Run: [`git add penmate-backend/src/main/java/com/penmate/backend/infrastructure/persistence/agent/AgentSessionRepositoryImpl.java penmate-backend/src/main/java/com/penmate/backend/application/agent/query/AgentSessionRecoveryQueryService.java penmate-backend/src/main/java/com/penmate/backend/application/agent/usecase/AgentSessionRecoveryAppService.java penmate-backend/src/test/java/com/penmate/backend/application/agent/query/AgentSessionRecoveryQueryServiceTest.java penmate-backend/src/test/java/com/penmate/backend/interfaces/api/agent/AgentWorkflowEndToEndContractTest.java penmate-frontend/src/api/types.ts && git commit -m "fix(agent): expose persisted runtime snapshots in recovery"`](.gitignore)

---

## Task 4: 去理想化测试夹具，恢复“真实后端协议驱动”的证明力

Use [test-driven-development] mode for this task.

**Root Cause:**
- 多个前端 / E2E 测试手工写入比真实后端更理想化的 payload，例如：
  - Todo 使用 `nextAction` 而非真实真源 `recommendedNextAction`
  - failure payload 同时给 `message` 与 `errorMsg`
  - waiting approval runtime 直接给足 `entryKeys` / `nextAction`，而真实 live payload 之前并未补齐
- 导致 Critical / Important 问题被“测试先修好”而不是“系统先修好”。

**Impact Scope:**
- 现有测试通过不再等价于真实链路通过。
- 后续回归可能再次引入协议漂移而不被发现。

**Files:**
- Modify: [`penmate-frontend/src/views/Workbench/index.runtime-e2e.spec.ts`](penmate-frontend/src/views/Workbench/index.runtime-e2e.spec.ts)
- Modify: [`penmate-frontend/src/views/Workbench/index.chat-binding.spec.ts`](penmate-frontend/src/views/Workbench/index.chat-binding.spec.ts)
- Modify: [`penmate-frontend/src/composables/workbench/useWorkbenchRuntimePresenter.spec.ts`](penmate-frontend/src/composables/workbench/useWorkbenchRuntimePresenter.spec.ts)
- Modify: [`penmate-frontend/src/composables/workbench/useWorkbenchTaskRuntime.spec.ts`](penmate-frontend/src/composables/workbench/useWorkbenchTaskRuntime.spec.ts)
- Modify: [`penmate-backend/src/test/java/com/penmate/backend/interfaces/api/agent/AgentWorkflowEndToEndContractTest.java`](penmate-backend/src/test/java/com/penmate/backend/interfaces/api/agent/AgentWorkflowEndToEndContractTest.java:322)
- Create: [`penmate-frontend/src/test/fixtures/workbenchRuntimeContract.ts`](penmate-frontend/src/test/fixtures/workbenchRuntimeContract.ts)

**Step 1: Write the failing tests**

1. 先抽出一套**后端真实 contract 夹具**到 [`workbenchRuntimeContract.ts`](penmate-frontend/src/test/fixtures/workbenchRuntimeContract.ts)，其字段来源必须对齐：
   - [`AgentWorkflowEndToEndContractTest.caseBWorkbenchContext()`](penmate-backend/src/test/java/com/penmate/backend/interfaces/api/agent/AgentWorkflowEndToEndContractTest.java:360)
   - [`caseCWorkbenchContext()`](penmate-backend/src/test/java/com/penmate/backend/interfaces/api/agent/AgentWorkflowEndToEndContractTest.java:392)
   - [`caseEWorkbenchContext()`](penmate-backend/src/test/java/com/penmate/backend/interfaces/api/agent/AgentWorkflowEndToEndContractTest.java:428)
2. 将前端相关 spec 全部改成从该夹具生成 payload，而不是在测试内手写理想化对象。
3. 故意让旧断言失败，证明当前测试依赖的理想化字段已被移除。

**Step 2: Run tests to verify they fail**

Run: [`npm test -- --run src/composables/workbench/useWorkbenchRuntimePresenter.spec.ts src/composables/workbench/useWorkbenchTaskRuntime.spec.ts src/views/Workbench/index.chat-binding.spec.ts src/views/Workbench/index.runtime-e2e.spec.ts`](penmate-frontend/package.json)

Expected:
- 在未完成 Task 1 / Task 2 之前，这批测试应失败，且失败原因能直接指出真实协议缺口。

**Step 3: Write minimal implementation**

1. 仅保留一套“后端真实协议形状”的测试夹具。
2. 删除或替换所有会掩盖问题的理想化字段：
   - Todo: 不再凭空注入 `nextAction`，除非该字段来自 recovery / runtime adapter 的公开 contract。
   - Failure: 不再同时强塞 `message` 与 `errorMsg`。
   - Story Bible approval: 不再手工补齐 live payload 中尚未由后端发布的字段。
3. 在 [`AgentWorkflowEndToEndContractTest`](penmate-backend/src/test/java/com/penmate/backend/interfaces/api/agent/AgentWorkflowEndToEndContractTest.java:322) 中维持与前端夹具一一对应的 case helper，确保前后端 fixture shape 可对照。

**Step 4: Run tests to verify they pass**

Run: [`npm test -- --run src/composables/workbench/useWorkbenchRuntimePresenter.spec.ts src/composables/workbench/useWorkbenchTaskRuntime.spec.ts src/views/Workbench/index.chat-binding.spec.ts src/views/Workbench/index.runtime-e2e.spec.ts`](penmate-frontend/package.json)

Expected:
- 所有 spec 在真实协议夹具下通过。

**Step 5: Test strategy**

- 所有前端 runtime / presenter / page spec 都必须由“真实协议夹具”生成输入。
- 后端 E2E contract helper 与前端 fixture 互相映照，避免双边各自漂移。

**Step 6: Acceptance criteria**

- 前端测试不再依赖手工理想化 payload。
- 若将来后端再次输出错误字段名，至少一组前端 / E2E 测试会直接失败。
- 该任务只作为 Critical / Important 修复的验证加固，不扩大产品范围。

**Step 7: Commit**

Run: [`git add penmate-frontend/src/test/fixtures/workbenchRuntimeContract.ts penmate-frontend/src/composables/workbench/useWorkbenchRuntimePresenter.spec.ts penmate-frontend/src/composables/workbench/useWorkbenchTaskRuntime.spec.ts penmate-frontend/src/views/Workbench/index.chat-binding.spec.ts penmate-frontend/src/views/Workbench/index.runtime-e2e.spec.ts penmate-backend/src/test/java/com/penmate/backend/interfaces/api/agent/AgentWorkflowEndToEndContractTest.java && git commit -m "test(workbench): align fixtures with real runtime contract"`](.gitignore)

---

## Final Fresh Verification Batch

Use [verification-before-completion] mode for this batch.

### Step 1: Run backend verification

Run: [`mvn -Dtest=AgentGenerationWorkflowTest,AgentTaskResultRecorderTest,AgentSessionRecoveryQueryServiceTest,AgentWorkflowEndToEndContractTest,TaskRuntimeStatusPublisherTest,RealtimeEventServiceImplTest test`](penmate-backend/pom.xml)

Expected:
- `BUILD SUCCESS`
- 所有新增 contract / query / runtime tests 通过。

### Step 2: Run frontend verification

Run: [`npm test -- --run src/composables/workbench/useWorkbenchTaskRuntime.spec.ts src/composables/workbench/useWorkbenchRuntimePresenter.spec.ts src/components/workbench/WorkbenchRightPanel.runtime.spec.ts src/views/Workbench/index.chat-binding.spec.ts src/views/Workbench/index.runtime-e2e.spec.ts src/composables/workbench/__tests__/useWorkbenchChat.spec.ts`](penmate-frontend/package.json)

Expected:
- 所有目标文件 passed。
- 不再需要理想化 payload 才能通过。

### Step 3: Spot-check architecture guardrail

Run: [`git diff --name-only`](.gitignore)

Expected:
- 变更集中在现有 runtime / recovery / presenter / tests。
- **没有**新增第二个 orchestrator、第二条 runtime 主流程、第二套 recovery 主控。

### Step 4: Final commit

Run: [`git add penmate-backend penmate-frontend && git commit -m "fix(agent): close prd v1 runtime and recovery gaps"`](.gitignore)

---

## Done Definition

只有同时满足以下条件，才能宣布本计划执行完成：

1. Todo `recommendedNextAction` / `nextAction` 在 live runtime 与 recovery 下都不再丢失。
2. 失败原因在只提供 `message` 或只提供 `errorMsg` 的情况下都能稳定展示。
3. live runtime 事件中 `storyBibleApproval` / `todoPlan` 均可真实驱动前端卡片。
4. recovery API 暴露 `taskProfile` / `promptPlan` / `contextPackage`。
5. 前端测试夹具以真实后端协议为基线，而不是继续理想化。
6. 后端与前端 fresh verification 全部通过。
7. 全程保持单主控架构，未引入新主控。

---

## Estimated Effort

- Task 1: 45-60 分钟
- Task 2: 60-90 分钟
- Task 3: 30-45 分钟
- Task 4: 30-45 分钟
- Final fresh verification: 20-30 分钟

**Total:** 3.0-4.5 小时

---

## Execution Options

1. 在本会话使用 [executing-plans] / [subagent-driven-development] 按任务执行。
2. 后续由执行者通过 `/execute-plan` 加载 [`docs/plans/2026-05-15-prd-v1-review-fix-plan.md`](docs/plans/2026-05-15-prd-v1-review-fix-plan.md) 再执行。
3. 作为人工实施清单直接逐任务落地，但仍必须遵守 [test-driven-development] 与 [verification-before-completion]。

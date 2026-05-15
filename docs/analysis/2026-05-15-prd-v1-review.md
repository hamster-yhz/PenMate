# 2026-05-15 PRD v1 改造结果复核审计

## 开发成果总览

本次复核依据 `docs/project-specifications/prd/prd-v1.0.md`、`docs/plans/2026-05-12-prd-v1-batch-1-orchestrator-story-bible-context.md` 与 `docs/plans/2026-05-12-prd-v1-batch-2-tools-todo-runtime-frontend.md`。重点抽查了主编排、结果快照、恢复聚合、实时事件与 Workbench 运行态展示链路。

结论先行：本次改造不是“只实现架子”。`AgentGenerationWorkflow`、`AgentTaskResultRecorder`、恢复接口、前端运行态卡片与端到端测试矩阵都已经形成真实链路；但仍存在数个必须修复的闭环缺口，当前不应判定为完全验收通过。

## 模块级技术实现细节

### 1. 主编排
- `penmate-backend/src/main/java/com/penmate/backend/application/agent/orchestration/AgentGenerationWorkflow.java` 仍是唯一长流程中心。
- 链路顺序已形成：preflight -> prompt compose -> context route -> tool loop -> result record -> runtime publish。
- `persistStructuredSnapshots` 会把 TaskProfile、PromptPlan、ContextPackage 写入 task context 快照。

### 2. 结果快照与恢复
- `penmate-backend/src/main/java/com/penmate/backend/application/agent/orchestration/AgentTaskResultRecorder.java` 会从 tool trace 中提取 draftSummary、qualityReportSummary、todoSummary、storyBibleProposalSummary。
- `penmate-backend/src/main/java/com/penmate/backend/infrastructure/persistence/agent/AgentSessionRepositoryImpl.java` 会把 chapterId、selectedText、activePlugins、modelConfigId、ragRefs、activeTaskRuntime、resultSummary 聚合进 recovery 输出。

### 3. 前端运行态展示
- `penmate-frontend/src/composables/workbench/useWorkbenchTaskRuntime.ts` 负责 SSE 订阅与事件归一化。
- `penmate-frontend/src/composables/workbench/useWorkbenchRuntimePresenter.ts` 负责 runtime/recovery -> 卡片 view model。
- `penmate-frontend/src/components/workbench/WorkbenchRightPanel.vue` 负责渲染运行状态卡、工具卡、Todo 卡与 Story Bible 卡。

## 业务链路

1. 创作主链路：TaskProfile -> PromptPlan -> ContextPackage -> Tool Loop -> Result。
2. Todo 链路：TodoPlannerToolHandler 输出 TodoPlanView，主编排解析后调用 TodoCrudApplicationService 持久化。
3. Story Bible 链路：handleStoryBibleProposals 会区分低风险 proposal 与高风险 approval，不会自动改 canon。
4. 恢复链路：AgentSessionRecoveryQueryService 会把 session、activeTask、pendingApproval、workbenchContext 聚合成单一快照。

## 数据流转

- 运行前：TaskProfile / PromptPlan / ContextPackage 进入 agent_task_contexts。
- 运行中：activeToolCallsSnapshot / lastRuntimeStatus / recoveryCursor 进入 agent_task_contexts。
- 运行后：draft / quality / todo / story bible proposal 摘要进入 agent_task_results。
- 恢复时：AgentSessionRepositoryImpl 再把这些摘要拼回 workbenchContext.resultSummary。

## 测试与验证证据

已 fresh 执行后端验证：
- `mvn -Dtest=AgentGenerationWorkflowTest,AgentTaskResultRecorderTest,AgentSessionRecoveryQueryServiceTest,AgentWorkflowEndToEndContractTest test`
- 结果：55 tests passed，BUILD SUCCESS。

已 fresh 执行前端验证：
- `npm test -- --run src/composables/workbench/useWorkbenchTaskRuntime.spec.ts src/composables/workbench/useWorkbenchRuntimePresenter.spec.ts src/components/workbench/WorkbenchRightPanel.runtime.spec.ts src/views/Workbench/index.chat-binding.spec.ts src/views/Workbench/index.runtime-e2e.spec.ts`
- 结果：5 files passed，36 tests passed。

## 合理性评估

合理之处：
- 保住了单主编排架构，没有出现第二个 orchestrator。
- 结构化快照、恢复 contract、前端 presenter 分层总体正确。
- Todo / Story Bible / Quality Review 已有真实业务闭环，不是纯 DTO 或静态卡片。

不足之处：
- 运行态事件协议与前端 presenter 之间仍有字段错位。
- 部分快照字段虽然已落库，但恢复 API 没有完整暴露。
- 部分验收测试夹具比真实后端 payload 更“理想化”，掩盖了协议不一致。

## 潜在漏实现 / 风险点

### Critical Issues

1. Todo 下一步动作字段不一致。
后端真源使用 `recommendedNextAction`，见 `TodoPlannerToolHandler` 与 `AgentGenerationWorkflow.parseTodoPlan`；前端 `useWorkbenchRuntimePresenter` 只读取 `summary.nextAction`。真实 runtime 与 recovery 下，Todo 卡片 next action 文案会丢失。

2. 失败原因字段不一致。
后端失败事件通过 RuntimeStatusView 的 `message` 传递错误内容；前端 `useWorkbenchTaskRuntime` / `useWorkbenchRuntimePresenter` 却主要消费 `errorMsg`。live failure 场景下，失败卡原因可能为空，只是测试夹具手工塞了 `errorMsg`。

3. Story Bible 等待审批事件信息不完整。
RuntimeStatusView 定义了 `storyBibleApproval` / `todoPlan`，但 `AgentGenerationWorkflow.buildRuntimeStatusView` 实际始终传 null；waiting approval 时前端测试夹具提供了比真实后端更完整的 entryKeys / nextAction，真实 live runtime 卡片信息可能不足。

### Important Issues

1. `taskProfileJson` / `promptPlanJson` / `contextPackageJson` 已持久化，但 recovery 输出未完整暴露，属于“有状态字段但恢复链路没打通”。
2. `RuntimeStatusView` 中声明的 `storyBibleApproval` / `todoPlan` 没有被前后端真正消费，属于“接口有字段但业务未闭环”。

### Minor Issues

1. 端到端与 presenter 测试中存在手工构造 payload 字段覆盖真实后端契约的情况，降低了测试证明力。

## 结论

Review Result: CHANGES REQUESTED

问题统计：Critical 3 / Important 2 / Minor 1。

综合判断：主体实现成立，fresh verification 也通过；但运行态事件协议、Todo 下一步动作、失败原因展示、Story Bible live 审批卡片与恢复字段暴露仍存在真实闭环缺口，建议修复后再做一次复核。

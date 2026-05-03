# Agent Tool 审批链路与 Book CRUD Tool 讲解指南

## 1. 文档目的

本文面向需要理解并讲述新增审批体系实现的开发者，重点解释当前代码中的关键角色、职责边界与调用时序。

相比方案文档 [`tool-approval-integration-architecture-plan.md`](docs/plans/tool-approval-integration-architecture-plan.md:1)，本文更偏源码走读，聚焦以下对象：

- [`ToolInvocationGateway`](penmate-backend/src/main/java/com/penmate/backend/application/agent/ToolInvocationGateway.java:28)
- [`DefaultApprovalPolicyEngine`](penmate-backend/src/main/java/com/penmate/backend/application/approval/DefaultApprovalPolicyEngine.java:15)
- [`PendingToolInvocationSnapshot`](penmate-backend/src/main/java/com/penmate/backend/domain/agent/model/PendingToolInvocationSnapshot.java:22)
- [`ApprovalApplicationService`](penmate-backend/src/main/java/com/penmate/backend/application/approval/ApprovalApplicationService.java:31)
- [`BookCrudAgentToolHandler`](penmate-backend/src/main/java/com/penmate/backend/application/agent/BookCrudAgentToolHandler.java:28)

---

## 2. 一句话总览

这套实现的核心思路是：

**所有 agent tool 调用先进入统一网关；高风险调用不会直接执行，
而是先创建审批单并把原始调用冻结成快照；审批通过后，
系统恢复的是 agent tool loop 上下文，并继续后续 tool/LLM turn，而不只是孤立重放某一个 handler。**

这套设计解决了四个关键问题：

1. 审批判断不散落在各个 tool 内部
2. 高风险操作在审批前不会真正落业务
3. 审批通过后能够恢复“原始那一次 loop 上下文”
4. tool handler 只做业务，不做审批状态机

---

## 3. 五个核心角色分别做什么

### 3.1 [`ToolInvocationGateway`](penmate-backend/src/main/java/com/penmate/backend/application/agent/ToolInvocationGateway.java:28)

它是 tool 调用总闸口，统一负责：

- 读取 tool 元数据
- 查找对应 handler
- 调用 handler 做参数预校验
- 调用审批策略引擎做风险评估
- 决定直通执行或挂起等待审批
- 审批通过后恢复执行

可以把它理解为“调用分发器 + 审批门禁 + 恢复入口”的组合体。

### 3.2 [`DefaultApprovalPolicyEngine.evaluate()`](penmate-backend/src/main/java/com/penmate/backend/application/approval/DefaultApprovalPolicyEngine.java:26)

它是审批规则裁判，回答的问题只有一个：

> 这一次工具调用，到底要不要审批？

当前实现已经体现出一个重要思想：

**审批不是只看 tool 名字，还要看调用参数；
但当前实现对 `book_crud.delete` 的参数识别仍是轻量文本匹配。**

以当前实现为例，同样是 `book_crud`：

- create：直通
- list：直通
- update：直通
- delete：当 `toolArgsJson` 文本精确包含 `"operation":"delete"` 时进入审批

### 3.3 [`PendingToolInvocationSnapshot`](penmate-backend/src/main/java/com/penmate/backend/domain/agent/model/PendingToolInvocationSnapshot.java:22)

它是审批挂起时保存的执行现场。

审批单适合描述“这件事为什么要审”；快照负责描述“审批通过后系统应该怎么继续执行”。

快照中保存的信息包括：

- `toolCode`
- `toolArgsJson`
- `projectId` / `taskId` / `conversationId`
- `traceId`
- `idempotencyKey`
- `status`
- `loopRunId`
- `llmTurnIndex`
- `toolCallId`
- `assistantToolCallsJson`
- `conversationMessagesJson`
- `resumeMode`

这意味着恢复执行时不需要重新猜测原意，而是可以直接取回原始 loop 现场，包括：

- 该轮 assistant 发出的全部 `tool_calls`
- 审批前已经成功执行的前序 tool result
- 当前待审批的 `tool_call_id`
- 审批通过后继续发给 LLM 的消息序列

### 3.4 [`ApprovalApplicationService`](penmate-backend/src/main/java/com/penmate/backend/application/approval/ApprovalApplicationService.java:31)

它是审批领域应用入口，同时负责在审批通过后触发恢复链路；当前主路径会把恢复委派给 [`ApprovedToolInvocationAsyncResumer`](penmate-backend/src/main/java/com/penmate/backend/application/approval/ApprovedToolInvocationAsyncResumer.java:27)，再按 [`resumeMode`](penmate-backend/src/main/java/com/penmate/backend/domain/agent/model/PendingToolInvocationSnapshot.java:22) 进入 loop 恢复。

它负责：

- 创建审批单
- 查询审批单
- 审核通过/驳回
- 发布审批事件
- 审批通过后取回快照并恢复执行

可以把它理解为“审批领域入口 + 恢复触发器”。

### 3.5 [`BookCrudAgentToolHandler`](penmate-backend/src/main/java/com/penmate/backend/application/agent/BookCrudAgentToolHandler.java:28)

它是书籍 CRUD 的业务执行器，只负责：

- 解析 `toolArgsJson`
- 校验参数
- 识别 `operation`
- 调用 [`NovelApplicationService`](penmate-backend/src/main/java/com/penmate/backend/application/novel/NovelApplicationService.java)
- 把领域对象转换为 tool 输出 JSON

它不负责创建审批单，也不负责等待审批与恢复状态机。

---

## 4. 首次调用链路：如何从请求走到“等待审批”

下面以 `book_crud.delete` 为例。

### 第 1 步：请求进入 [`ToolInvocationGateway.invoke()`](penmate-backend/src/main/java/com/penmate/backend/application/agent/ToolInvocationGateway.java:77)

网关先做三件基础动作：

1. 通过 [`StaticToolMetadataRegistry.getRequired()`](penmate-backend/src/main/java/com/penmate/backend/application/agent/StaticToolMetadataRegistry.java) 读取元数据
2. 定位目标 handler
3. 调用 handler 的 `validate()` 进行参数预校验

这一步先保证两个前提：

- 目标 tool 存在
- 请求参数至少是结构合法的

### 第 2 步：调用 [`DefaultApprovalPolicyEngine.evaluate()`](penmate-backend/src/main/java/com/penmate/backend/application/approval/DefaultApprovalPolicyEngine.java:26)

当前实现中，`book_crud` 是否按删除处理，并不是先把 JSON 结构化解析出 `operation`，而是通过 [`containsDeleteOperation()`](penmate-backend/src/main/java/com/penmate/backend/application/approval/DefaultApprovalPolicyEngine.java:49) 检查原始文本里是否精确包含 `"operation":"delete"`。

这说明策略引擎已经开始支持“同一个 tool 的不同 operation 风险不同”，但当前这部分能力仍依赖调用参数的序列化格式，不应理解成稳定的语义级识别。

### 第 3 步：调用 [`ApprovalApplicationService.create()`](penmate-backend/src/main/java/com/penmate/backend/application/approval/ApprovalApplicationService.java:72)

一旦命中审批，网关不会直接执行业务，而是先创建审批单。

审批单解决的是“给人审核”的问题，例如：

- 哪个项目发起的
- 哪个任务发起的
- 审批类型是什么
- 风险等级是什么
- 原始 payload 是什么

### 第 4 步：保存 [`PendingToolInvocationSnapshot`](penmate-backend/src/main/java/com/penmate/backend/domain/agent/model/PendingToolInvocationSnapshot.java:22)

这一步解决的是“审批通过后如何继续执行整个 loop”。

如果只有审批单，没有快照，那么系统知道“有一件事被通过了”，但不知道“原来具体该调用哪个 tool、带什么参数、位于第几轮 turn、前面已经执行过哪些 tool result”。

所以两者职责不同：

- 审批单：给人看
- 调用快照：给机器恢复执行

### 第 5 步：回写任务状态为 `waiting_approval`

网关随后更新任务状态为等待审批，相关逻辑位于 [`ToolInvocationGateway.invoke()`](penmate-backend/src/main/java/com/penmate/backend/application/agent/ToolInvocationGateway.java:125) 附近。

这样前端、编排器和审批恢复逻辑都能明确感知当前任务卡在人工决策点。

### 第 6 步：广播等待审批事件

网关调用 [`RealtimeEventService.publishGenerationWaitingApproval()`](penmate-backend/src/main/java/com/penmate/backend/domain/shared/service/RealtimeEventService.java:19)，前端即可据此展示审批卡片。

至此，首次调用链路从“请求进入”切换到了“等待人工审批”。

---

## 5. 审批通过后的恢复链路：如何从等待态回到执行态

恢复链路的入口在 [`ApprovalApplicationService.approve()`](penmate-backend/src/main/java/com/penmate/backend/application/approval/ApprovalApplicationService.java:135)。

当前默认恢复模式是 `RESUME_LOOP`：审批通过后恢复的是 loop 状态，而不是 legacy 的单次工具重放。

### 第 1 步：把审批单更新为 approved

审批服务先更新审批单状态。只有当前仍是 `pending` 的审批单才能通过，避免重复审批或非法状态覆盖。

### 第 2 步：发布审批完成事件

审批状态落库后，服务会广播 `approval.reviewed` 事件，让前端和其他订阅方知道人工决策已经完成。

### 第 3 步：进入 [`resumeToolInvocationAfterApproved()`](penmate-backend/src/main/java/com/penmate/backend/application/approval/ApprovalApplicationService.java:194)

这里才是真正的恢复执行主链路。

该方法会先按 `approvalId` 读取对应的 [`PendingToolInvocationSnapshot`](penmate-backend/src/main/java/com/penmate/backend/domain/agent/model/PendingToolInvocationSnapshot.java:22)。

这一步体现的关键原则是：

**审批通过恢复执行，不是重新拼请求，而是取回原始调用快照。**

### 第 4 步：先把快照从 `pending` 抢占到 `executing`

审批服务会先调用仓储把快照状态从 `pending` 改为 `executing`。

这个动作本质上是一次防重保护：

- 第一个成功推进状态的人拿到执行权
- 后续重复回调或并发恢复不会再次执行业务

这也是当前实现与旧版描述的关键差异：**先 claim 快照，再进入异步恢复**，从而避免“任务先改为 `running`、快照却未成功抢占”的状态裂缝。

### 第 5 步：由 [`ApprovedToolInvocationAsyncResumer.resumeApprovedInvocation()`](penmate-backend/src/main/java/com/penmate/backend/application/approval/ApprovedToolInvocationAsyncResumer.java:51) 恢复任务并继续 loop

异步恢复器在确认快照仍处于 `executing` 后，才会把 generation task 从 `waiting_approval` 回写为 `running`，然后根据 `resumeMode` 继续执行。

这保证了状态机前后一致：

- 审批前：`running`
- 等审批：`waiting_approval`
- 审批通过继续执行：`running`

### 第 6 步：按 `resumeMode=RESUME_LOOP` 调用 [`AgentToolLoopController.resumeFromPending()`](penmate-backend/src/main/java/com/penmate/backend/application/agent/loop/AgentToolLoopController.java:119)

loop controller 会先恢复当前待审批 tool，再把同轮剩余未执行的 tool calls 继续补齐，最后再向 LLM 发起下一轮 turn。

这里要特别强调三点：

1. 恢复阶段不会再次审批
2. 恢复阶段不会重新拼业务参数
3. 恢复阶段恢复的是原始 loop 上下文，不是脱离上下文的单次 handler 调用

在这条链路里，底层单个 tool 的真正恢复执行仍通过 [`ToolInvocationGateway.resume()`](penmate-backend/src/main/java/com/penmate/backend/application/agent/ToolInvocationGateway.java:196) 完成，但它已经变成 loop 恢复流程中的一个步骤，而不是最终恢复语义本身。

### 第 7 步：根据执行结果封口快照，并让 generation task 继续走向终态

恢复执行完成后：

- 成功：快照状态从 `executing` 变为 `completed`
- 失败：快照状态从 `executing` 变为 `failed`，同时任务改为 `failed`

因此快照状态不仅服务于恢复，还服务于排障和幂等控制。

### 第 8 步：保守阈值防止 loop 失控

当前 loop 实现采用保守阈值：

- `MAX_TOOL_TURNS = 4`
- `MAX_TOOL_CALLS_PER_TURN = 3`
- 同时只允许 1 个 pending approval

这样做的目的不是限制未来能力，而是在首版多步 tool-calling 落地时，优先防止：

1. 模型持续请求工具导致无限循环
2. 单轮返回过多 tool calls，扩大审批与恢复复杂度
3. 同一 generation task 出现多个并发待审批分叉

---

## 6. 审批驳回链路：为什么直接终止任务

驳回入口在 [`ApprovalApplicationService.reject()`](penmate-backend/src/main/java/com/penmate/backend/application/approval/ApprovalApplicationService.java:161)。

驳回后不会调用恢复逻辑，而是进入 [`markTaskFailedAfterRejected()`](penmate-backend/src/main/java/com/penmate/backend/application/approval/ApprovalApplicationService.java:233)。

这条链路的语义非常明确：

1. 仅当任务仍处于 `waiting_approval` 才继续处理
2. 校验 `waiting_approval -> failed` 的状态流转合法性
3. 任务状态更新为 `failed`
4. 广播失败事件

这说明“审批驳回”不是“稍后重试”，而是“当前高风险操作被明确拒绝，任务直接终止”。

---

## 7. [`BookCrudAgentToolHandler`](penmate-backend/src/main/java/com/penmate/backend/application/agent/BookCrudAgentToolHandler.java:28) 的职责边界

讲解时最容易混淆的是：既然 `book_crud.delete` 需要审批，审批逻辑是不是在 handler 里？

答案是否定的。

### 它负责什么

- 解析 `toolArgsJson`
- 识别 `operation`
- 校验 delete 必要字段
- 调用 [`NovelApplicationService.createProject()`](penmate-backend/src/main/java/com/penmate/backend/application/novel/NovelApplicationService.java)
- 调用 [`NovelApplicationService.listProjects()`](penmate-backend/src/main/java/com/penmate/backend/application/novel/NovelApplicationService.java)
- 调用 [`NovelApplicationService.updateProject()`](penmate-backend/src/main/java/com/penmate/backend/application/novel/NovelApplicationService.java)
- 调用 [`NovelApplicationService.deleteProject()`](penmate-backend/src/main/java/com/penmate/backend/application/novel/NovelApplicationService.java)
- 把领域对象转换为统一 JSON 输出

### 它不负责什么

- 不判断是否需要审批
- 不创建审批单
- 不保存挂起快照
- 不切任务状态为 `waiting_approval`
- 不负责审批通过后的恢复调度

### 为什么必须这样分层

如果把审批逻辑塞进 handler，会立刻出现这些问题：

1. 每个高风险 tool 都要重复实现审批创建与快照保存
2. 审批恢复缺少统一入口
3. handler 同时承担业务与状态机，维护成本高
4. 新增其他高风险 tool 时很难保证一致性

因此当前架构坚持：

- 横切审批逻辑放网关和审批服务
- 具体业务逻辑留在 handler

---

## 8. 一段可以直接讲给同事听的话

如果需要口头讲述这套流程，可以直接这样说：

> 我们现在把 agent 的工具调用先统一收口到 [`ToolInvocationGateway`](penmate-backend/src/main/java/com/penmate/backend/application/agent/ToolInvocationGateway.java:28)。
> 网关先查元数据，再让 [`DefaultApprovalPolicyEngine`](penmate-backend/src/main/java/com/penmate/backend/application/approval/DefaultApprovalPolicyEngine.java:15) 判断这次调用要不要审批。
> 对 `book_crud.delete` 来说，当前审批识别还是基于原始 JSON 文本里是否精确包含 `"operation":"delete"`，还不是完整的结构化语义判断。
> 如果不用审批，就直接分发给具体 handler，比如 [`BookCrudAgentToolHandler`](penmate-backend/src/main/java/com/penmate/backend/application/agent/BookCrudAgentToolHandler.java:28)。
> 如果需要审批，网关会先通过 [`ApprovalApplicationService`](penmate-backend/src/main/java/com/penmate/backend/application/approval/ApprovalApplicationService.java:31) 创建审批单，再把原始调用现场保存成 [`PendingToolInvocationSnapshot`](penmate-backend/src/main/java/com/penmate/backend/domain/agent/model/PendingToolInvocationSnapshot.java:22)，把任务切到 `waiting_approval`。
> 审批通过后，审批服务按 `approvalId` 找回这份快照，再恢复原来的 loop 上下文。这样继续的是同一次 tool-calling 对话，不是重新拼出来的一份新请求，也不是只重放某一个 tool handler。

这段话已经覆盖了当前实现最重要的设计主线。

---

## 9. 推荐源码阅读顺序

如果要带着代码讲解，建议按下面顺序看：

1. [`ToolInvocationGateway.invoke()`](penmate-backend/src/main/java/com/penmate/backend/application/agent/ToolInvocationGateway.java:77)
2. [`DefaultApprovalPolicyEngine.evaluate()`](penmate-backend/src/main/java/com/penmate/backend/application/approval/DefaultApprovalPolicyEngine.java:26)
3. [`PendingToolInvocationSnapshot`](penmate-backend/src/main/java/com/penmate/backend/domain/agent/model/PendingToolInvocationSnapshot.java:22)
4. [`ApprovalApplicationService.approve()`](penmate-backend/src/main/java/com/penmate/backend/application/approval/ApprovalApplicationService.java:135)
5. [`ApprovedToolInvocationAsyncResumer.resumeApprovedInvocation()`](penmate-backend/src/main/java/com/penmate/backend/application/approval/ApprovedToolInvocationAsyncResumer.java:51)
6. [`AgentToolLoopController.resumeFromPending()`](penmate-backend/src/main/java/com/penmate/backend/application/agent/loop/AgentToolLoopController.java:118)
7. [`ToolInvocationGateway.resume()`](penmate-backend/src/main/java/com/penmate/backend/application/agent/ToolInvocationGateway.java:196)
8. [`BookCrudAgentToolHandler.execute()`](penmate-backend/src/main/java/com/penmate/backend/application/agent/BookCrudAgentToolHandler.java:75)

按这个顺序最容易串起“首次调用 → 审批挂起 → 审批通过 → 恢复执行”的完整闭环。

---

## 10. 当前实现最值得强调的设计价值

- **统一入口**：审批门禁集中在 [`ToolInvocationGateway`](penmate-backend/src/main/java/com/penmate/backend/application/agent/ToolInvocationGateway.java:28)
- **轻量动态判定**：当前审批可根据参数内容做初步判断，但对 `book_crud.delete` 仍依赖原始 JSON 文本匹配
- **可恢复**：审批通过后能按原始上下文继续执行
- **幂等防重**：通过快照状态推进避免重复恢复执行
- **职责解耦**：审批状态机与业务 handler 清晰分层

这也是向他人讲解这套实现时最适合作为结论的五个关键词。

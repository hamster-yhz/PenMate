# PenMate Agent Loop / Tool / SSE / RAG 技术说明

## 文档目的

本文基于当前 PenMate 前后端实际代码，对以下 4 个问题给出结论：

1. 当前如何进入下一轮 agent loop，如何结束一个 loop，是否通过调用“结束工具”；
2. 当前 agent tool 能力是否真实传递到基建层 LangChain4j 再发给大模型；
3. 如果要做当前状态推送，应该怎么做，是否直接走当前聊天 SSE；
4. 当前 RAG 在应用层是否已彻底做好，是强制耦合给大模型调用，还是通过工具提供给大模型。

本文**不深挖基建层细节实现**，但会结合现有后端/前端主链代码做工程判断。

---

## 一、结论摘要

### 1）关于下一轮 agent loop 与 loop 结束

当前项目里，**下一轮 loop 不是通过某个“结束工具/继续工具”显式触发的**，而是由后端编排器根据大模型返回结果自动决定是否继续。

- 首次进入 loop：前端调用 [`createTurn()`](penmate-backend/src/main/java/com/penmate/backend/interfaces/api/agent/AgentController.java:132)，后端在 [`AgentController.createTurn()`](penmate-backend/src/main/java/com/penmate/backend/interfaces/api/agent/AgentController.java:132) 中调用 [`dispatchInitialRun()`](penmate-backend/src/main/java/com/penmate/backend/application/agent/orchestration/AgentGenerationWorkflowDispatcher.java:20) 异步启动工作流。
- loop 内继续下一轮：[`AgentToolLoopRunner.execute()`](penmate-backend/src/main/java/com/penmate/backend/application/agent/orchestration/AgentToolLoopRunner.java:48) 在 `for (turnIndex...)` 循环中反复调用模型；若模型返回 `tool_calls`，则执行 tool，把 tool result 追加回消息，再自然进入下一次 LLM turn。
- 审批后恢复继续：审批通过后走 [`dispatchResumeAfterApproval()`](penmate-backend/src/main/java/com/penmate/backend/application/agent/orchestration/AgentGenerationWorkflowDispatcher.java:25)，进入 [`ToolCallResumeService.resumeFromPending()`](penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/runtime/ToolCallResumeService.java:42)，恢复挂起工具、补齐剩余工具结果，并再次进入 LLM 多轮循环。
- loop 结束条件：当模型返回“不再请求 tool calls”时，[`AgentToolLoopRunner.execute()`](penmate-backend/src/main/java/com/penmate/backend/application/agent/orchestration/AgentToolLoopRunner.java:68) 直接返回 `completed(...)`；随后主工作流写入结果、发布 done 事件、任务置为 done。

所以：**当前 loop 的结束不是靠“调用一个结束工具”，而是靠模型在某一轮返回普通 assistant 文本且不再请求 tool calls**。

### 2）关于 tool 能力是否真实下传到 LangChain4j / 模型

结论是：**是，已真实下传，而且不是停留在应用层概念对象。**

- tool schema 来源于 [`AgentToolDefinitionSource.listLlmSchemas()`](penmate-backend/src/main/java/com/penmate/backend/application/agent/orchestration/AgentToolLoopRunner.java:56)；
- 这些 schema 被放进 [`AgentLlmTurnRequest`](penmate-backend/src/main/java/com/penmate/backend/application/agent/orchestration/AgentToolLoopRunner.java:61)；
- 应用层网关 [`LangChain4jAgentLlmGateway.generateTurn()`](penmate-backend/src/main/java/com/penmate/backend/infrastructure/llm/langchain4j/LangChain4jAgentLlmGateway.java:32) 将请求交给 provider client；
- provider 实现 [`NativeOpenAiStyleHttpProviderChatClient.buildTurnRequestBody()`](penmate-backend/src/main/java/com/penmate/backend/infrastructure/llm/langchain4j/provider/NativeOpenAiStyleHttpProviderChatClient.java:127) 会把 tools 组装成 OpenAI-compatible HTTP body 中的 `tools` 和 `tool_choice`；
- provider 返回后，[`extractTurnResponse()`](penmate-backend/src/main/java/com/penmate/backend/infrastructure/llm/langchain4j/provider/NativeOpenAiStyleHttpProviderChatClient.java:175) 还会从响应里解析 `tool_calls` 回应用层。

因此，**tool 能力已经真实越过应用层，进入基建 provider，并作为请求体发给大模型**。

### 3）关于“当前状态推送”应该怎么做

结论是：**优先直接复用当前 turn 级聊天 SSE，不建议另起一套平行的聊天状态推送机制。**

- 后端已有 turn 级 SSE 入口 [`/sessions/{sessionId}/turns/{turnId}/stream`](penmate-backend/src/main/java/com/penmate/backend/interfaces/api/agent/AgentController.java:154)；
- 实际 SSE hub 在 [`GenerationSseEmitterHub`](penmate-backend/src/main/java/com/penmate/backend/infrastructure/realtime/GenerationSseEmitterHub.java:21)；
- 实时事件统一由 [`RealtimeEventServiceImpl`](penmate-backend/src/main/java/com/penmate/backend/infrastructure/realtime/RealtimeEventServiceImpl.java:62) 发布，包括：
  - `generation.started`
  - `generation.token`
  - `generation.tool_call`
  - `generation.waiting_approval`
  - `generation.done`
  - `generation.failed`
- 前端 [`createTaskRuntime().consumeGenerationStream()`](penmate-frontend/src/composables/workbench/useWorkbenchTaskRuntime.ts:84) 已经消费这些事件；
- 前端发消息后会直接打开 [`agentApi.openTurnStream()`](penmate-frontend/src/api/modules/agent.api.ts:61)，见 [`useWorkbenchChat.sendMessage()`](penmate-frontend/src/composables/workbench/useWorkbenchChat.ts:268) 与 [`consumeGenerationStream()`](penmate-frontend/src/composables/workbench/useWorkbenchTaskRuntime.ts:84)。

所以如果要做“当前状态推送”，**最顺的做法是在现有 turn SSE 上继续扩展事件负载或增加事件名**，而不是绕过它再造一条“当前聊天状态 SSE”。

### 4）关于 RAG 是否已在应用层彻底做好、是否强制耦合给模型

结论分两层：

1. **RAG 已经接入 agent 主生成链路，但不是通过 tool 暴露给模型调用；**
2. **它在应用层是“系统先检索、再把结果拼进 prompt”的前置增强，而不是让大模型自主决定是否调用某个 RAG tool。**

证据如下：

- [`AgentGenerationWorkflow.runInternal()`](penmate-backend/src/main/java/com/penmate/backend/application/agent/orchestration/AgentGenerationWorkflow.java:50) 会先调用 [`ragRetrievalService.retrieve()`](penmate-backend/src/main/java/com/penmate/backend/application/agent/orchestration/AgentGenerationWorkflow.java:69)；
- 检索到的 `ragChunks` 再交给 [`AgentPromptAssembler.buildInitialMessages()`](penmate-backend/src/main/java/com/penmate/backend/application/agent/orchestration/AgentPromptAssembler.java:18)；
- prompt assembler 明确把 RAG 片段以“知识库参考”文本方式写进首轮 user message；
- loop 内模型拿到的是“已经注入 RAG 结果的消息”，不是“一个叫 rag_search 的工具”；
- 我没有在 agent tool loop、tool definition、tool gateway 主链中找到 RAG tool 的直接接线证据；
- 前端 [`ragApi`](penmate-frontend/src/api/modules/rag.api.ts:5) 目前对应的是 RAG 文档管理与检索日志查询接口，而不是 chat runtime 中的大模型工具调用接口。

因此，**当前 RAG 不是“强制要求大模型调用某个 RAG tool”，也不是“通过工具开放给模型按需调用”的主模式；当前模式是应用层预检索 + prompt 注入。**

---

## 二、问题 1：当前如何进入下一轮 agent loop，如何结束一个 loop

## 2.1 首次进入 loop 的链路

前端在 [`useWorkbenchChat.sendMessage()`](penmate-frontend/src/composables/workbench/useWorkbenchChat.ts:268) 中调用 [`createTurn`](penmate-frontend/src/composables/workbench/useWorkbenchChat.ts:107)，对应后端 [`AgentController.createTurn()`](penmate-backend/src/main/java/com/penmate/backend/interfaces/api/agent/AgentController.java:132)。

该接口完成两件事：

1. 通过 [`agentTurnAppService.createTurn(...)`](penmate-backend/src/main/java/com/penmate/backend/interfaces/api/agent/AgentController.java:139) 创建 turn / task；
2. 若存在 activeTask，则立即调用 [`agentGenerationWorkflowDispatcher.dispatchInitialRun(...)`](penmate-backend/src/main/java/com/penmate/backend/interfaces/api/agent/AgentController.java:145) 异步启动编排。

而 [`AgentGenerationWorkflowDispatcher`](penmate-backend/src/main/java/com/penmate/backend/application/agent/orchestration/AgentGenerationWorkflowDispatcher.java:16) 自身并不含业务判断，只把调用切入异步线程，最终进入 [`AgentGenerationWorkflow.run()`](penmate-backend/src/main/java/com/penmate/backend/application/agent/orchestration/AgentGenerationWorkflow.java:42)。

这意味着：**进入 loop 的正式入口是“create turn -> dispatchInitialRun -> AgentGenerationWorkflow.runInternal”**。

## 2.2 loop 内如何自然进入“下一轮”

核心在 [`AgentToolLoopRunner.execute()`](penmate-backend/src/main/java/com/penmate/backend/application/agent/orchestration/AgentToolLoopRunner.java:48)。

它的行为非常明确：

- 用 `for (int turnIndex = 0; turnIndex < MAX_TOOL_TURNS; turnIndex++)` 做最多 4 轮 LLM turn；
- 每一轮调用 [`agentLlmGateway.generateTurn(...)`](penmate-backend/src/main/java/com/penmate/backend/application/agent/orchestration/AgentToolLoopRunner.java:61)；
- 如果模型要求 tool calls，则：
  - 先把 assistant 的 tool_call message 记入 `messages`；
  - 再逐个执行 tool；
  - 每个 tool 执行成功后，把 `role=tool` 的结果消息再追加回 `messages`；
  - 然后循环自然进入下一轮 `generateTurn(...)`。

换句话说，**“进入下一轮 loop”不是一个单独 API，也不是单独工具，而是同一个后端循环体在消息上下文被 tool result 扩展后再次调用模型。**

## 2.3 审批打断后的 loop 恢复

如果某个 tool 命中审批策略，[`ToolCallApplicationService.executeToolCall()`](penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/gateway/ToolCallApplicationService.java:70) 会：

- 创建审批单；
- 保存 [`PendingToolInvocationSnapshot`](penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/gateway/ToolCallApplicationService.java:93)；
- 把任务状态改成 `waiting_approval`；
- 发布 [`generation.waiting_approval`](penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/gateway/ToolCallApplicationService.java:121) 实时事件。

此时 [`AgentToolLoopRunner.execute()`](penmate-backend/src/main/java/com/penmate/backend/application/agent/orchestration/AgentToolLoopRunner.java:100) 会返回 `waitingApproval(...)`，主工作流在 [`AgentGenerationWorkflow.runInternal()`](penmate-backend/src/main/java/com/penmate/backend/application/agent/orchestration/AgentGenerationWorkflow.java:88) 中把状态停在 `WAITING_APPROVAL` 并直接返回。

审批通过后，再通过 [`dispatchResumeAfterApproval()`](penmate-backend/src/main/java/com/penmate/backend/application/agent/orchestration/AgentGenerationWorkflowDispatcher.java:25) 重进主流程；底层恢复逻辑落在 [`ToolCallResumeService.resumeFromPending()`](penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/runtime/ToolCallResumeService.java:42)。

这个恢复服务会：

1. 还原当时的 conversation messages；
2. 先执行原来挂起的那个 tool；
3. 再把同一批 assistant tool_calls 中剩余未执行的 tool 继续补完；
4. 最后重新进入后续 LLM turn 循环。

所以从工程语义看，**审批恢复并不是新开一个全新任务，而是恢复原 loop 上下文继续跑。**

## 2.4 loop 如何结束，是否靠“结束工具”

当前代码没有体现一个“显式结束 loop 的工具”。

真正结束条件在 [`AgentToolLoopRunner.execute()`](penmate-backend/src/main/java/com/penmate/backend/application/agent/orchestration/AgentToolLoopRunner.java:68)：

- 若 [`response.requestsToolCalls()`](penmate-backend/src/main/java/com/penmate/backend/application/agent/orchestration/AgentToolLoopRunner.java:68) 为 `false`，说明模型当前轮不再请求工具；
- 这时直接返回 [`AgentToolLoopIterationResult.completed(...)`](penmate-backend/src/main/java/com/penmate/backend/application/agent/orchestration/AgentToolLoopRunner.java:69)；
- 主工作流随后发布 token、落库 assistant 结果、置任务 done，并发出 [`publishGenerationDone(...)`](penmate-backend/src/main/java/com/penmate/backend/application/agent/orchestration/AgentGenerationWorkflow.java:106)。

因此可以明确回答：

> **当前不是通过调用某个“结束工具”来结束 loop，而是由模型在某轮返回普通文本且不再请求 tool_calls，从而自然结束。**

---

## 三、问题 2：agent tool 能力是否真实传递到 LangChain4j / 大模型

结论是肯定的，而且证据链完整。

## 3.1 tool schema 的来源

[`AgentToolLoopRunner.execute()`](penmate-backend/src/main/java/com/penmate/backend/application/agent/orchestration/AgentToolLoopRunner.java:56) 会先读取 [`toolDefinitionSource.listLlmSchemas()`](penmate-backend/src/main/java/com/penmate/backend/application/agent/orchestration/AgentToolLoopRunner.java:56)。

这说明 agent tool 并不是只在本地 handler 注册，而是至少存在一层“可给 LLM 看的 schema 抽象”。

## 3.2 tool schema 如何进入模型请求

同一个方法里，模型调用使用的是：

- [`new AgentLlmTurnRequest(messages, tools, "auto")`](penmate-backend/src/main/java/com/penmate/backend/application/agent/orchestration/AgentToolLoopRunner.java:62)

也就是说，tool schema 已经进入应用层的统一大模型请求对象。

## 3.3 LangChain4j 网关是否继续向下透传

[`LangChain4jAgentLlmGateway.generateTurn()`](penmate-backend/src/main/java/com/penmate/backend/infrastructure/llm/langchain4j/LangChain4jAgentLlmGateway.java:32) 只做配置校验和 provider 分发，最终直接调用 [`providerChatClient.generateTurn(request, executionConfig)`](penmate-backend/src/main/java/com/penmate/backend/infrastructure/llm/langchain4j/LangChain4jAgentLlmGateway.java:47)。

这一步说明：**应用层 request 没有在这里被截断或丢弃 tools。**

## 3.4 provider 是否真的把 tools 发给模型

关键证据在 [`NativeOpenAiStyleHttpProviderChatClient.buildTurnRequestBody()`](penmate-backend/src/main/java/com/penmate/backend/infrastructure/llm/langchain4j/provider/NativeOpenAiStyleHttpProviderChatClient.java:127)。

代码明确做了：

- `body.put("messages", request.messages())`
- 若 `request.tools()` 非空：
  - 遍历每个 [`AgentLlmToolSchema`](penmate-backend/src/main/java/com/penmate/backend/infrastructure/llm/langchain4j/provider/NativeOpenAiStyleHttpProviderChatClient.java:134)
  - 组装 OpenAI 风格 `tools: [{ type: "function", function: { name, description, parameters } }]`
  - 再写入 `body.put("tools", tools)`
  - 同时写入 `body.put("tool_choice", request.toolChoice())`

随后这个 body 会通过 HTTP POST 发到 `/chat/completions`。

这意味着：

> **tool 定义已经以 OpenAI-compatible function calling 协议真实进入底层请求体。**

## 3.5 provider 是否解析模型返回的 tool_calls

[`extractTurnResponse()`](penmate-backend/src/main/java/com/penmate/backend/infrastructure/llm/langchain4j/provider/NativeOpenAiStyleHttpProviderChatClient.java:175) 还会从响应中的 `message.tool_calls` 解析出：

- `id`
- `function.name`
- `function.arguments`

并组装为 [`AgentLlmToolCall`](penmate-backend/src/main/java/com/penmate/backend/infrastructure/llm/langchain4j/provider/NativeOpenAiStyleHttpProviderChatClient.java:190)。

所以这不是“单向宣称支持 tools”，而是**请求与响应都打通了**。

---

## 四、问题 3：如果要做当前状态推送，应该怎么做，是否直接走当前聊天 SSE

结论：**应该优先直接走当前 turn SSE。**

## 4.1 后端已经有完整的任务级实时推送主链

HTTP 入口在 [`AgentController.openTurnStream()`](penmate-backend/src/main/java/com/penmate/backend/interfaces/api/agent/AgentController.java:154)，它根据 `projectId + sessionId + turnId` 找到 task，再调用 [`generationStreamService.openStream(taskId)`](penmate-backend/src/main/java/com/penmate/backend/interfaces/api/agent/AgentController.java:168)。

真正的连接中心是 [`GenerationSseEmitterHub`](penmate-backend/src/main/java/com/penmate/backend/infrastructure/realtime/GenerationSseEmitterHub.java:21)：

- 为 task 创建/维护多个 `SseEmitter`；
- 支持 buffered replay；
- 支持 task 完成后 late subscriber replay；
- 支持 `complete(taskId)` 主动收口。

## 4.2 当前可推送的状态类型已经比较完整

[`RealtimeEventServiceImpl`](penmate-backend/src/main/java/com/penmate/backend/infrastructure/realtime/RealtimeEventServiceImpl.java:62) 已经覆盖：

- [`publishGenerationStarted()`](penmate-backend/src/main/java/com/penmate/backend/infrastructure/realtime/RealtimeEventServiceImpl.java:62)
- [`publishGenerationToken()`](penmate-backend/src/main/java/com/penmate/backend/infrastructure/realtime/RealtimeEventServiceImpl.java:78)
- [`publishGenerationToolCall()`](penmate-backend/src/main/java/com/penmate/backend/infrastructure/realtime/RealtimeEventServiceImpl.java:101)
- [`publishGenerationWaitingApproval()`](penmate-backend/src/main/java/com/penmate/backend/infrastructure/realtime/RealtimeEventServiceImpl.java:151)
- [`publishGenerationDone()`](penmate-backend/src/main/java/com/penmate/backend/infrastructure/realtime/RealtimeEventServiceImpl.java:198)
- [`publishGenerationFailed()`](penmate-backend/src/main/java/com/penmate/backend/infrastructure/realtime/RealtimeEventServiceImpl.java:213)

也就是说，从“生成开始 -> token -> tool 执行 -> 等审批 -> 完成/失败”的生命周期看，主干事件已经具备。

## 4.3 前端已经严格绑定这条 SSE 主链

前端 [`agentApi.openTurnStream()`](penmate-frontend/src/api/modules/agent.api.ts:61) 是直接 new [`EventSource`](penmate-frontend/src/api/modules/agent.api.ts:63)。

真正的消费逻辑在 [`createTaskRuntime().consumeGenerationStream()`](penmate-frontend/src/composables/workbench/useWorkbenchTaskRuntime.ts:84)：

- 监听 `generation.started`
- 监听 `generation.token`
- 监听 `generation.tool_call`
- 监听 `generation.waiting_approval`
- 监听 `generation.done`
- 监听 `generation.failed`

并且 [`useWorkbenchChat.sendMessage()`](penmate-frontend/src/composables/workbench/useWorkbenchChat.ts:341) 与 [`resumeRunningTask()`](penmate-frontend/src/composables/workbench/useWorkbenchChat.ts:448) 都复用了这套 runtime。

## 4.4 对“当前状态推送”的建议

如果要补“当前状态推送”，建议优先按以下方式做：

1. **继续沿用 turn SSE 通道**，不要重新发明第二套聊天 SSE；
2. 如果要表达更细粒度状态，就在 [`RealtimeEventServiceImpl`](penmate-backend/src/main/java/com/penmate/backend/infrastructure/realtime/RealtimeEventServiceImpl.java:24) 中新增事件名或扩充 payload；
3. 前端只需在 [`useWorkbenchTaskRuntime.ts`](penmate-frontend/src/composables/workbench/useWorkbenchTaskRuntime.ts:36) 新增相应 listener；
4. 如果是“项目级广播”而非“单 turn 订阅”，现有后端其实还保留了 project WebSocket 广播通路，但聊天主链目前明显以 turn SSE 为准，因此不建议把 chat runtime 切到 project WebSocket。

可见：

> **当前聊天状态推送的正确承载体，就是现有 turn 级 SSE。**

---

## 五、问题 4：当前 RAG 在应用层是否已彻底做好，是强制耦合给大模型调用，还是通过工具提供给大模型

这个问题需要分“是否已接进主链”和“以什么方式接”两个层面回答。

## 5.1 RAG 已经接入 agent 生成主链

[`AgentGenerationWorkflow.runInternal()`](penmate-backend/src/main/java/com/penmate/backend/application/agent/orchestration/AgentGenerationWorkflow.java:50) 在启动生成后，明确先调用：

- [`ragRetrievalService.retrieve(projectId, taskId, task.getPromptSnapshot(), traceId)`](penmate-backend/src/main/java/com/penmate/backend/application/agent/orchestration/AgentGenerationWorkflow.java:69)

这表明 RAG 不是边缘模块，而是已经进入 agent 编排主线。

## 5.2 RAG 的接入方式是“系统先检索，再注入 prompt”

RAG 结果不会直接变成某个 tool call，而是被 [`AgentPromptAssembler.buildInitialMessages()`](penmate-backend/src/main/java/com/penmate/backend/application/agent/orchestration/AgentPromptAssembler.java:18) 拼成：

- `知识库参考：...`
- 再与 `用户指令：...` 一起组成首轮 user message。

因此模型看到的是**已经混入上下文的知识块**，而不是“你可以调用一个 RAG 工具”。

## 5.3 当前并非“通过工具提供给大模型”

尽管系统已经有完整的 tool loop，但从当前已读主链看：

- tool loop 来源是 [`AgentToolDefinitionSource`](penmate-backend/src/main/java/com/penmate/backend/application/agent/orchestration/AgentToolLoopRunner.java:44)；
- RAG 生成主链则在 [`AgentGenerationWorkflow`](penmate-backend/src/main/java/com/penmate/backend/application/agent/orchestration/AgentGenerationWorkflow.java:34) 直接依赖 [`RagRetrievalService`](penmate-backend/src/main/java/com/penmate/backend/application/rag/RagRetrievalService.java:16)；
- 前端 [`ragApi`](penmate-frontend/src/api/modules/rag.api.ts:5) 也只体现文档、解析、向量化、索引状态、检索日志查询；
- 没有看到 agent tool definition 中以“RAG 检索工具”形式暴露给模型的证据。

所以目前最合理的判断是：

> **RAG 当前不是通过 tool 提供给大模型调用，而是系统应用层在模型调用前主动执行的前置检索增强。**

## 5.4 当前 RAG 是否“已彻底做好”

如果“彻底做好”的定义是“已在 agent 生成主链生效”，答案偏向 **是**；
如果定义是“已经把 RAG 做成模型自主调用、可多轮决策的工具化能力”，答案则是 **否**。

更准确地说：

- **已做好部分**：
  - 有 RAG 文档管理接口 [`RagController`](penmate-backend/src/main/java/com/penmate/backend/interfaces/api/rag/RagController.java:29)
  - 有检索仓储 [`RagRetrievalRepository`](penmate-backend/src/main/java/com/penmate/backend/domain/rag/repository/RagRetrievalRepository.java:8)
  - 有检索服务 [`RagRetrievalService`](penmate-backend/src/main/java/com/penmate/backend/application/rag/RagRetrievalService.java:20)
  - 有 agent 编排接线 [`AgentGenerationWorkflow`](penmate-backend/src/main/java/com/penmate/backend/application/agent/orchestration/AgentGenerationWorkflow.java:69)
  - 有 prompt 注入 [`AgentPromptAssembler`](penmate-backend/src/main/java/com/penmate/backend/application/agent/orchestration/AgentPromptAssembler.java:30)
- **尚未体现为**：
  - 模型按需触发的 RAG tool；
  - tool loop 中独立的“检索-再推理-再检索”型知识工具闭环；
  - 前端 chat runtime 对 RAG 调用过程的单独可视化。

所以最终判断应写成：

> **当前 RAG 已接入 agent 生成应用主链，但实现形态是“应用层前置检索 + prompt 注入”，不是强制让大模型自己调用，也不是以 tool 形式开放给模型自主决策。**

---

## 六、补充观察：前端恢复机制与 loop / SSE 的关系

前端 session 恢复不是简单拉历史，而是带有运行态恢复语义。

[`useWorkbenchSessionRecovery.restore()`](penmate-frontend/src/composables/workbench/useWorkbenchSessionRecovery.ts:38) 会调用后端 [`/resume`](penmate-frontend/src/api/modules/agent.api.ts:49)，然后：

- 回填 snapshot；
- 若 activeTask 状态为 `RUNNING`，则自动重新接入 [`resumeRunningTask()`](penmate-frontend/src/composables/workbench/useWorkbenchChat.ts:448)；
- 若状态为 `WAITING_APPROVAL`，则前端进入等待审批态而非继续 streaming，见 [`hydrateFromRecoverySnapshot()`](penmate-frontend/src/composables/workbench/useWorkbenchChat.ts:389)。

这说明当前系统对 loop 的理解已经不是“单次 HTTP 请求”，而是“可恢复的长任务会话”。这也进一步支持前面的判断：**状态推送应继续围绕 task/turn SSE 架构扩展，而不是退回普通聊天轮询。**

---

## 七、最终回答（适合直接对外同步）

1. **下一轮 agent loop 如何进入、如何结束**  
   当前由后端 [`AgentGenerationWorkflow`](penmate-backend/src/main/java/com/penmate/backend/application/agent/orchestration/AgentGenerationWorkflow.java:26) + [`AgentToolLoopRunner`](penmate-backend/src/main/java/com/penmate/backend/application/agent/orchestration/AgentToolLoopRunner.java:37) 自动驱动。模型若返回 `tool_calls`，系统执行 tool 并把 tool result 回灌消息后自动进入下一轮；若模型不再请求 tool，则 loop 自然结束。**不是靠调用“结束工具”结束。**

2. **agent tool 能力是否真实下传到基建层 LangChain4j 并发给模型**  
   **是。** tool schema 从应用层进入 [`AgentLlmTurnRequest`](penmate-backend/src/main/java/com/penmate/backend/application/agent/orchestration/AgentToolLoopRunner.java:61)，经 [`LangChain4jAgentLlmGateway.generateTurn()`](penmate-backend/src/main/java/com/penmate/backend/infrastructure/llm/langchain4j/LangChain4jAgentLlmGateway.java:32) 下传到 provider，再由 [`NativeOpenAiStyleHttpProviderChatClient.buildTurnRequestBody()`](penmate-backend/src/main/java/com/penmate/backend/infrastructure/llm/langchain4j/provider/NativeOpenAiStyleHttpProviderChatClient.java:127) 真实写入 HTTP 请求体的 `tools` 字段。

3. **如果要做当前状态推送，应怎么做，是否走当前聊天 SSE**  
   **应直接复用当前 turn 级 SSE。** 后端已有 [`/turns/{turnId}/stream`](penmate-backend/src/main/java/com/penmate/backend/interfaces/api/agent/AgentController.java:154)，前端已有 [`consumeGenerationStream()`](penmate-frontend/src/composables/workbench/useWorkbenchTaskRuntime.ts:84) 消费 started/token/tool_call/waiting_approval/done/failed。新增状态时优先扩展这套事件协议。

4. **当前 RAG 是否已彻底做好、是强耦合给模型还是通过工具提供**  
   **当前 RAG 已进入 agent 生成主链，但形态是“应用层先检索，再拼进 prompt”，不是通过工具开放给模型自主调用。** 也就是说，它已经不是旁路能力，但也还不是模型可自由决策的 RAG tool 模式。


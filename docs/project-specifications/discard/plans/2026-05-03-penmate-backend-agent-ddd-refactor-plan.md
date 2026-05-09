# PenMate Backend Agent Application 域 DDD 重构 Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use [executing-plans] mode to implement this plan task-by-task.

**Goal:** 重整 [`application/agent`](penmate-backend/src/main/java/com/penmate/backend/application/agent) 及相关调用链，使 Agent 能力按 DDD 分层清晰拆分，消除编排、工具执行、审批恢复、JSON/LLM 适配等职责混杂问题。

**Architecture:** 当前 Agent 实现把“用例入口”“长流程编排”“工具运行时”“审批恢复编排”“模型执行配置”“插件工具调用”“JSON 序列化工具”都堆叠在 [`application/agent`](penmate-backend/src/main/java/com/penmate/backend/application/agent) 下，导致包级语义模糊、边界跨层。目标方案将 Agent 应用层拆为 usecase / orchestration / tool / model-routing / dto 五类对象；把可沉入领域的状态规则下推到领域服务，把 JSON/LLM/provider 细节显式界定为基础设施适配，形成“接口层 → 应用用例 → 领域规则/仓储端口 → 基础设施实现”的稳定依赖方向。

**Tech Stack:** Java 21, Spring Boot, Lombok, MyBatis-style Repository, Hutool JSON, LangChain4j-style LLM provider adapter, SSE/WebSocket realtime events

---

## 一、现状诊断

### 1.1 主要问题总览

1. **应用层职责混杂**
   - [`AgentApplicationService`](penmate-backend/src/main/java/com/penmate/backend/application/agent/AgentApplicationService.java) 同时承担 CRUD 用例、输入标准化、异步编排触发。
   - [`AgentOrchestrator`](penmate-backend/src/main/java/com/penmate/backend/application/agent/AgentOrchestrator.java) 同时承担任务状态迁移、RAG 拼装、SSE 推送、assistant message 落库、成本估算、异常封口。
   - [`AgentToolLoopController`](penmate-backend/src/main/java/com/penmate/backend/application/agent/loop/AgentToolLoopController.java) 同时承担 LLM turn 驱动、tool message 组装、审批挂起恢复、快照解析、idempotency key 生成、执行配置恢复。
   - [`ToolInvocationGateway`](penmate-backend/src/main/java/com/penmate/backend/application/agent/ToolInvocationGateway.java) 同时承担 handler 路由、审批决策、审批单创建、快照持久化、任务状态回写、实时事件广播。

2. **分层边界不清**
   - [`AgentTaskStateMachine`](penmate-backend/src/main/java/com/penmate/backend/application/agent/AgentTaskStateMachine.java) 本质是领域规则，但放在应用层。
   - [`AgentLlmGateway`](penmate-backend/src/main/java/com/penmate/backend/application/agent/llm/AgentLlmGateway.java) 作为端口是合理的，但其请求/响应/工具调用 DTO 也混在 application.agent 包内，与工具运行时对象交织。
   - [`AgentJsons`](penmate-backend/src/main/java/com/penmate/backend/application/agent/json/AgentJsons.java) 被应用层与基础设施层共同依赖，且命名带强业务前缀，实质却是通用 JSON codec。
   - [`PluginToolCoordinator`](penmate-backend/src/main/java/com/penmate/backend/application/agent/PluginToolCoordinator.java) 既像应用服务又像工具执行适配器，还直接广播实时事件。

3. **命名不一致 / 抽象粒度不齐**
   - `Orchestrator`、`Dispatcher`、`Controller`、`Gateway`、`Coordinator`、`Handler` 混用，没有统一约定。
   - [`AgentToolLoopController`](penmate-backend/src/main/java/com/penmate/backend/application/agent/loop/AgentToolLoopController.java) 实际不是 Web controller，而是 loop runner / workflow。
   - [`ToolInvocationGatewayResult`](penmate-backend/src/main/java/com/penmate/backend/application/agent/ToolInvocationGatewayResult.java) 与 [`ToolExecutionResult`](penmate-backend/src/main/java/com/penmate/backend/application/agent/ToolExecutionResult.java) 名义相近但语义层次不同，容易误导。
   - [`ToolMetadata`](penmate-backend/src/main/java/com/penmate/backend/application/agent/ToolMetadata.java) 当前同时服务于“LLM 工具声明”和“审批策略元数据”，概念耦合。

4. **跨子域耦合集中在 Agent 应用层**
   - Agent 直接依赖 [`RagRetrievalService`](penmate-backend/src/main/java/com/penmate/backend/application/rag/RagRetrievalService.java)、[`NovelApplicationService`](penmate-backend/src/main/java/com/penmate/backend/application/novel/NovelApplicationService.java)、[`PluginApplicationService`](penmate-backend/src/main/java/com/penmate/backend/application/plugin/PluginApplicationService.java)、[`ApprovalApplicationService`](penmate-backend/src/main/java/com/penmate/backend/application/approval/ApprovalApplicationService.java) 等多个应用服务。
   - 这会让应用服务互相调用形成“应用层对应用层编排网络”，后续很难收敛边界。

### 1.2 重点问题逐类定位

#### A. 用例入口与长流程编排混在同一子包
- [`AgentApplicationService`](penmate-backend/src/main/java/com/penmate/backend/application/agent/AgentApplicationService.java) 是典型 use case service。
- [`AgentOrchestrator`](penmate-backend/src/main/java/com/penmate/backend/application/agent/AgentOrchestrator.java)、[`AgentOrchestrationDispatcher`](penmate-backend/src/main/java/com/penmate/backend/application/agent/AgentOrchestrationDispatcher.java)、[`AgentToolLoopController`](penmate-backend/src/main/java/com/penmate/backend/application/agent/loop/AgentToolLoopController.java) 属于 process / workflow 层。
- 当前三者与 CRUD 用例同层级，导致包语义泛化为“什么都能放”。

#### B. 工具运行时对象和工具业务对象混在一起
- 运行时请求/结果：[`ToolInvocationRequest`](penmate-backend/src/main/java/com/penmate/backend/application/agent/ToolInvocationRequest.java)、[`ToolInvocationGatewayResult`](penmate-backend/src/main/java/com/penmate/backend/application/agent/ToolInvocationGatewayResult.java)
- 工具执行占位对象：[`ToolExecutionRequest`](penmate-backend/src/main/java/com/penmate/backend/application/agent/ToolExecutionRequest.java)、[`ToolExecutionResult`](penmate-backend/src/main/java/com/penmate/backend/application/agent/ToolExecutionResult.java)
- 工具元数据：[`ToolMetadata`](penmate-backend/src/main/java/com/penmate/backend/application/agent/ToolMetadata.java)、[`StaticToolMetadataRegistry`](penmate-backend/src/main/java/com/penmate/backend/application/agent/StaticToolMetadataRegistry.java)
- 工具实现：[`ContextEnhancerAgentToolHandler`](penmate-backend/src/main/java/com/penmate/backend/application/agent/ContextEnhancerAgentToolHandler.java)、[`BookCrudAgentToolHandler`](penmate-backend/src/main/java/com/penmate/backend/application/agent/BookCrudAgentToolHandler.java)
- 插件协调器：[`PluginToolCoordinator`](penmate-backend/src/main/java/com/penmate/backend/application/agent/PluginToolCoordinator.java)

这些对象属于至少三个不同层次：
1. tool runtime orchestration
2. tool capability catalog
3. concrete tool adapters/use cases

#### C. 审批恢复链路横跨 agent / approval 两个应用包，但没有稳定的编排边界
- [`ToolInvocationGateway`](penmate-backend/src/main/java/com/penmate/backend/application/agent/ToolInvocationGateway.java) 在 agent 包里直接创建审批单。
- [`ApprovalApplicationService`](penmate-backend/src/main/java/com/penmate/backend/application/approval/ApprovalApplicationService.java) 反向依赖 [`ToolInvocationGateway`](penmate-backend/src/main/java/com/penmate/backend/application/agent/ToolInvocationGateway.java) 与 [`AgentToolLoopController`](penmate-backend/src/main/java/com/penmate/backend/application/agent/loop/AgentToolLoopController.java) 恢复执行。
- [`ApprovedToolInvocationAsyncResumer`](penmate-backend/src/main/java/com/penmate/backend/application/approval/ApprovedToolInvocationAsyncResumer.java) 实际是 agent tool workflow 的一部分，却放在 approval 包。

这说明“审批”与“Agent 工具恢复”之间缺少明确的应用服务协作边界，形成双向渗透。

#### D. 领域规则未下沉
- [`AgentTaskStateMachine`](penmate-backend/src/main/java/com/penmate/backend/application/agent/AgentTaskStateMachine.java) 只处理状态规则，应迁移为领域服务或领域 policy。
- [`AgentOrchestrator`](penmate-backend/src/main/java/com/penmate/backend/application/agent/AgentOrchestrator.java) 中的状态迁移封装可由领域对象/领域服务表达，而不应由流程类持有全部状态合法性知识。

#### E. 基础设施细节泄露到应用语义
- [`AgentJsons`](penmate-backend/src/main/java/com/penmate/backend/application/agent/json/AgentJsons.java) 是 Hutool JSON 的胶水层，建议沉到 infrastructure/support 或 shared codec。
- [`LangChain4jAgentLlmGateway`](penmate-backend/src/main/java/com/penmate/backend/infrastructure/llm/langchain4j/LangChain4jAgentLlmGateway.java) 同时实现 `turn` 与 `text generate` 两套入口，而当前编排只真实使用 [`generateTurn()`](penmate-backend/src/main/java/com/penmate/backend/application/agent/llm/AgentLlmGateway.java:18)，存在旧模型接口残留。

---

## 二、目标目录结构

目标不是一次把所有 Agent 代码搬进 domain，而是先把 **应用层职责切清**，再把真正的领域规则下沉。

```text
penmate-backend/src/main/java/com/penmate/backend/
├─ application/
│  ├─ agent/
│  │  ├─ command/
│  │  │  ├─ CreateAgentConversationCommand.java
│  │  │  ├─ CreateAgentMessageCommand.java
│  │  │  ├─ CreateAgentGenerationCommand.java
│  │  │  └─ ApplyAgentGenerationCommand.java
│  │  ├─ query/
│  │  │  └─ AgentQueryService.java
│  │  ├─ usecase/
│  │  │  ├─ AgentConversationAppService.java
│  │  │  ├─ AgentMessageAppService.java
│  │  │  └─ AgentGenerationAppService.java
│  │  ├─ orchestration/
│  │  │  ├─ AgentGenerationWorkflow.java
│  │  │  ├─ AgentGenerationWorkflowDispatcher.java
│  │  │  ├─ AgentToolLoopRunner.java
│  │  │  ├─ AgentPromptAssembler.java
│  │  │  ├─ AgentResultPublisher.java
│  │  │  └─ AgentTaskRuntimeUpdater.java
│  │  ├─ tool/
│  │  │  ├─ runtime/
│  │  │  │  ├─ ToolCallRequest.java
│  │  │  │  ├─ ToolCallResult.java
│  │  │  │  ├─ ToolApprovalContext.java
│  │  │  │  ├─ ToolCallSnapshotMapper.java
│  │  │  │  └─ ToolCallResumeService.java
│  │  │  ├─ catalog/
│  │  │  │  ├─ AgentToolDefinition.java
│  │  │  │  ├─ AgentToolCatalog.java
│  │  │  │  └─ StaticAgentToolCatalog.java
│  │  │  ├─ handler/
│  │  │  │  ├─ AgentToolHandler.java
│  │  │  │  ├─ ContextEnhancerToolHandler.java
│  │  │  │  └─ BookCrudToolHandler.java
│  │  │  ├─ gateway/
│  │  │  │  └─ ToolCallApplicationService.java
│  │  │  └─ plugin/
│  │  │     ├─ PluginToolExecutor.java
│  │  │     ├─ PluginToolExecuteCommand.java
│  │  │     └─ PluginToolExecuteResult.java
│  │  ├─ llm/
│  │  │  ├─ AgentLlmGateway.java
│  │  │  ├─ AgentLlmExecutionConfig.java
│  │  │  ├─ AgentLlmTurnRequest.java
│  │  │  ├─ AgentLlmTurnResponse.java
│  │  │  ├─ AgentLlmToolCall.java
│  │  │  └─ AgentLlmToolSchema.java
│  │  └─ routing/
│  │     └─ AgentModelRoutingService.java
│  └─ approval/
│     ├─ command/
│     ├─ usecase/
│     │  └─ ApprovalApplicationService.java
│     └─ coordination/
│        └─ ApprovalAgentResumeCoordinator.java
├─ domain/
│  ├─ agent/
│  │  ├─ model/
│  │  ├─ repository/
│  │  └─ service/
│  │     ├─ AgentTaskTransitionPolicy.java
│  │     ├─ AgentToolApprovalPolicy.java
│  │     └─ AgentGenerationCostPolicy.java
│  └─ shared/
├─ infrastructure/
│  ├─ agent/
│  │  ├─ codec/
│  │  │  └─ AgentJsonCodec.java
│  │  ├─ llm/
│  │  └─ tool/
│  │     └─ snapshot/
│  └─ ...
└─ interfaces/
   └─ api/agent/
```

---

## 三、类归属建议（逐个对象）

### 3.1 保留在应用层，但拆分包位

#### 1) [`AgentApplicationService`](penmate-backend/src/main/java/com/penmate/backend/application/agent/AgentApplicationService.java)
**问题：** 一个类包揽 conversation/message/generation 三类用例。  
**建议：** 拆成 3 个 use case service。

- 新增 [`AgentConversationAppService`](penmate-backend/src/main/java/com/penmate/backend/application/agent/usecase/AgentConversationAppService.java)
  - `listConversations()`
  - `createConversation()`
- 新增 [`AgentMessageAppService`](penmate-backend/src/main/java/com/penmate/backend/application/agent/usecase/AgentMessageAppService.java)
  - `listMessages()`
  - `createMessage()`
- 新增 [`AgentGenerationAppService`](penmate-backend/src/main/java/com/penmate/backend/application/agent/usecase/AgentGenerationAppService.java)
  - `createGeneration()`
  - `getGeneration()`
  - `applyGeneration()`

`normalizeJsonField()` 提炼为输入标准化组件：
- 新增 [`AgentJsonInputNormalizer`](penmate-backend/src/main/java/com/penmate/backend/application/agent/usecase/AgentJsonInputNormalizer.java)

#### 2) [`AgentOrchestrator`](penmate-backend/src/main/java/com/penmate/backend/application/agent/AgentOrchestrator.java)
**问题：** 真正语义是 generation workflow，不只是“orchestrator”。  
**建议：** 重命名并拆职能。

- 重命名为 [`AgentGenerationWorkflow`](penmate-backend/src/main/java/com/penmate/backend/application/agent/orchestration/AgentGenerationWorkflow.java)
- 拆出：
  - [`AgentPromptAssembler`](penmate-backend/src/main/java/com/penmate/backend/application/agent/orchestration/AgentPromptAssembler.java)
  - [`AgentResultPublisher`](penmate-backend/src/main/java/com/penmate/backend/application/agent/orchestration/AgentResultPublisher.java)
  - [`AgentTaskRuntimeUpdater`](penmate-backend/src/main/java/com/penmate/backend/application/agent/orchestration/AgentTaskRuntimeUpdater.java)

其中：
- `buildInitialMessages()` → [`AgentPromptAssembler`](penmate-backend/src/main/java/com/penmate/backend/application/agent/orchestration/AgentPromptAssembler.java)
- `splitToChunks()` / SSE 分发 → [`AgentResultPublisher`](penmate-backend/src/main/java/com/penmate/backend/application/agent/orchestration/AgentResultPublisher.java)
- token/cost/runtime update → [`AgentTaskRuntimeUpdater`](penmate-backend/src/main/java/com/penmate/backend/application/agent/orchestration/AgentTaskRuntimeUpdater.java)

#### 3) [`AgentOrchestrationDispatcher`](penmate-backend/src/main/java/com/penmate/backend/application/agent/AgentOrchestrationDispatcher.java)
**建议：**
- 重命名为 [`AgentGenerationWorkflowDispatcher`](penmate-backend/src/main/java/com/penmate/backend/application/agent/orchestration/AgentGenerationWorkflowDispatcher.java)
- 方法命名统一为：
  - `dispatchInitialRun()`
  - `dispatchResumeAfterApproval()`

#### 4) [`AgentToolLoopController`](penmate-backend/src/main/java/com/penmate/backend/application/agent/loop/AgentToolLoopController.java)
**问题：** 命名错误且职责超载。  
**建议：**
- 重命名为 [`AgentToolLoopRunner`](penmate-backend/src/main/java/com/penmate/backend/application/agent/orchestration/AgentToolLoopRunner.java)
- 将以下职责拆出：
  - message/tool_call JSON 组装解析 → [`ToolCallSnapshotMapper`](penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/runtime/ToolCallSnapshotMapper.java)
  - approval resume 流程 → [`ToolCallResumeService`](penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/runtime/ToolCallResumeService.java)
  - llm turn loop 纯循环控制保留在 runner 内

### 3.2 下沉到 Agent tool 子包

#### 5) [`ToolInvocationGateway`](penmate-backend/src/main/java/com/penmate/backend/application/agent/ToolInvocationGateway.java)
**问题：** 不是“基础设施 gateway”，而是应用层 tool call use case。  
**建议：**
- 重命名为 [`ToolCallApplicationService`](penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/gateway/ToolCallApplicationService.java)
- `invoke()` 改名 `executeToolCall()`
- `resume()` 改名 `resumeApprovedToolCall()`

并拆出：
- 审批判定上下文对象 [`ToolApprovalContext`](penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/runtime/ToolApprovalContext.java)
- handler 定位器 [`AgentToolHandlerRegistry`](penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/handler/AgentToolHandlerRegistry.java)

#### 6) [`StaticToolMetadataRegistry`](penmate-backend/src/main/java/com/penmate/backend/application/agent/StaticToolMetadataRegistry.java)
**问题：** 元数据类型不清，既含审批元数据又含 LLM schema。  
**建议：**
- 重命名为 [`StaticAgentToolCatalog`](penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/catalog/StaticAgentToolCatalog.java)
- 新建领域更清晰的定义对象：
  - [`AgentToolDefinition`](penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/catalog/AgentToolDefinition.java)

建议字段：
- `toolCode`
- `displayName`
- `llmDescription`
- `inputSchemaJson`
- `approvalRequired`
- `approvalType`
- `riskLevel`

这样不再需要“ToolMetadata + AgentLlmToolSchema”双轨拼装。

#### 7) [`ToolMetadata`](penmate-backend/src/main/java/com/penmate/backend/application/agent/ToolMetadata.java)
**建议：** 删除，改为 [`AgentToolDefinition`](penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/catalog/AgentToolDefinition.java)。

#### 8) [`AgentToolHandler`](penmate-backend/src/main/java/com/penmate/backend/application/agent/AgentToolHandler.java)
**建议：** 移动到 [`application/agent/tool/handler/AgentToolHandler.java`](penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/handler/AgentToolHandler.java)。

#### 9) [`ContextEnhancerAgentToolHandler`](penmate-backend/src/main/java/com/penmate/backend/application/agent/ContextEnhancerAgentToolHandler.java)
**建议：**
- 移动并重命名为 [`ContextEnhancerToolHandler`](penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/handler/ContextEnhancerToolHandler.java)
- 仅保留参数解释与调用 executor，不负责日志事件拼装。

#### 10) [`BookCrudAgentToolHandler`](penmate-backend/src/main/java/com/penmate/backend/application/agent/BookCrudAgentToolHandler.java)
**建议：**
- 移动并重命名为 [`BookCrudToolHandler`](penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/handler/BookCrudToolHandler.java)
- 中长期再考虑把“book_crud”拆成更细的领域能力，而不是一个 operation 多态工具。

#### 11) [`PluginToolCoordinator`](penmate-backend/src/main/java/com/penmate/backend/application/agent/PluginToolCoordinator.java)
**问题：** 实际是工具执行适配器。  
**建议：**
- 重命名为 [`PluginToolExecutor`](penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/plugin/PluginToolExecutor.java)
- 把 [`ToolExecutionRequest`](penmate-backend/src/main/java/com/penmate/backend/application/agent/ToolExecutionRequest.java) / [`ToolExecutionResult`](penmate-backend/src/main/java/com/penmate/backend/application/agent/ToolExecutionResult.java) 一并迁入 `tool/plugin`
- 实时事件发布建议上移回 [`ToolCallApplicationService`](penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/gateway/ToolCallApplicationService.java)，避免 executor 直接感知外部事件通道

### 3.3 迁到 routing / llm / shared 更清晰位置

#### 12) [`AgentModelRoutingService`](penmate-backend/src/main/java/com/penmate/backend/application/agent/AgentModelRoutingService.java)
**建议：**
- 移动到 [`application/agent/routing/AgentModelRoutingService.java`](penmate-backend/src/main/java/com/penmate/backend/application/agent/routing/AgentModelRoutingService.java)
- 保留在应用层合理，因为它聚合多个仓储与解密服务形成“执行配置”

#### 13) [`AgentLlmGateway`](penmate-backend/src/main/java/com/penmate/backend/application/agent/llm/AgentLlmGateway.java)
**建议：**
- 保持为应用层端口，但清理过时的 [`generate()`](penmate-backend/src/main/java/com/penmate/backend/application/agent/llm/AgentLlmGateway.java:31) 文本生成接口
- 如果当前真实链路只用 turn-based tool-calling，则只保留 [`generateTurn()`](penmate-backend/src/main/java/com/penmate/backend/application/agent/llm/AgentLlmGateway.java:18)

#### 14) [`AgentJsons`](penmate-backend/src/main/java/com/penmate/backend/application/agent/json/AgentJsons.java)
**建议：**
- 迁移到 [`infrastructure/agent/codec/AgentJsonCodec.java`](penmate-backend/src/main/java/com/penmate/backend/infrastructure/agent/codec/AgentJsonCodec.java)
- 如果希望更通用，可进一步去掉 `Agent` 前缀，改为 [`JsonCodec`](penmate-backend/src/main/java/com/penmate/backend/infrastructure/agent/codec/AgentJsonCodec.java)
- 应用层只依赖接口或静态 helper，避免包名看起来像业务对象却实际是基础设施工具

### 3.4 下沉到领域层

#### 15) [`AgentTaskStateMachine`](penmate-backend/src/main/java/com/penmate/backend/application/agent/AgentTaskStateMachine.java)
**建议：**
- 迁移到 [`domain/agent/service/AgentTaskTransitionPolicy.java`](penmate-backend/src/main/java/com/penmate/backend/domain/agent/service/AgentTaskTransitionPolicy.java)
- 命名为 `Policy` 比 `StateMachine` 更符合当前无状态守卫实现

#### 16) 成本估算逻辑
- [`AgentOrchestrator#estimateCost()`](penmate-backend/src/main/java/com/penmate/backend/application/agent/AgentOrchestrator.java:204) 不应硬编码在 workflow 类中
- 新增 [`domain/agent/service/AgentGenerationCostPolicy.java`](penmate-backend/src/main/java/com/penmate/backend/domain/agent/service/AgentGenerationCostPolicy.java)

---

## 四、应用层 / 领域层 / 基础设施层边界建议

### 4.1 应用层应该负责什么

应用层负责：
1. 接收接口层 command/query
2. 调用仓储端口读取聚合
3. 调用领域 policy 校验规则
4. 协调 RAG / approval / tool / realtime 等跨模块流程
5. 调用基础设施端口（LLM、实时事件、加密）

应用层**不应**负责：
- JSON 序列化细节
- 状态迁移规则定义本身
- provider-specific 协议细节
- 通用 tool snapshot 解析细节与字符串拼装细节过多堆积

### 4.2 领域层应该负责什么

领域层负责：
- 生成任务状态流转合法性 [`AgentTaskTransitionPolicy`](penmate-backend/src/main/java/com/penmate/backend/domain/agent/service/AgentTaskTransitionPolicy.java)
- 高风险工具是否必须审批的业务规则抽象（若后续从静态表演进）
- 成本估算、任务终态规则等纯业务规则

领域层**不应**直接依赖：
- Spring `@Component`
- Hutool JSON
- LangChain4j Provider
- SSE/Realtime event service

### 4.3 基础设施层应该负责什么

基础设施层负责：
- [`LangChain4jAgentLlmGateway`](penmate-backend/src/main/java/com/penmate/backend/infrastructure/llm/langchain4j/LangChain4jAgentLlmGateway.java) 及 provider adapters
- JSON codec
- Repository impl / Mapper
- Realtime 推送实现

基础设施层**不应**承载：
- tool 审批策略决策
- generation workflow 规则
- agent task transition rule

### 4.4 approval 子域与 agent 子域的边界

建议边界：
- **Approval 子域** 只负责审批单生命周期：创建、审核、状态变更。
- **Agent 子域/应用层** 负责“审批通过后如何恢复 Agent tool 流程”。

因此：
- [`ApprovedToolInvocationAsyncResumer`](penmate-backend/src/main/java/com/penmate/backend/application/approval/ApprovedToolInvocationAsyncResumer.java) 应迁回 agent orchestration / tool runtime 侧
- approval 层通过一个协调器接口通知 agent：
  - 新增 [`ApprovalAgentResumeCoordinator`](penmate-backend/src/main/java/com/penmate/backend/application/approval/coordination/ApprovalAgentResumeCoordinator.java)

依赖方向建议：
- [`ApprovalApplicationService`](penmate-backend/src/main/java/com/penmate/backend/application/approval/ApprovalApplicationService.java) → `ApprovalAgentResumeCoordinator` 接口
- Agent 提供实现，例如 [`AgentApprovalResumeCoordinator`](penmate-backend/src/main/java/com/penmate/backend/application/agent/orchestration/AgentApprovalResumeCoordinator.java)

这样审批层不再直接依赖 [`AgentToolLoopController`](penmate-backend/src/main/java/com/penmate/backend/application/agent/loop/AgentToolLoopController.java) 与 [`ToolInvocationGateway`](penmate-backend/src/main/java/com/penmate/backend/application/agent/ToolInvocationGateway.java)。

---

## 五、需要新增 / 迁移 / 重命名的对象清单

### 5.1 新增对象

- [`application/agent/usecase/AgentConversationAppService.java`](penmate-backend/src/main/java/com/penmate/backend/application/agent/usecase/AgentConversationAppService.java)
- [`application/agent/usecase/AgentMessageAppService.java`](penmate-backend/src/main/java/com/penmate/backend/application/agent/usecase/AgentMessageAppService.java)
- [`application/agent/usecase/AgentGenerationAppService.java`](penmate-backend/src/main/java/com/penmate/backend/application/agent/usecase/AgentGenerationAppService.java)
- [`application/agent/usecase/AgentJsonInputNormalizer.java`](penmate-backend/src/main/java/com/penmate/backend/application/agent/usecase/AgentJsonInputNormalizer.java)
- [`application/agent/orchestration/AgentGenerationWorkflow.java`](penmate-backend/src/main/java/com/penmate/backend/application/agent/orchestration/AgentGenerationWorkflow.java)
- [`application/agent/orchestration/AgentGenerationWorkflowDispatcher.java`](penmate-backend/src/main/java/com/penmate/backend/application/agent/orchestration/AgentGenerationWorkflowDispatcher.java)
- [`application/agent/orchestration/AgentPromptAssembler.java`](penmate-backend/src/main/java/com/penmate/backend/application/agent/orchestration/AgentPromptAssembler.java)
- [`application/agent/orchestration/AgentResultPublisher.java`](penmate-backend/src/main/java/com/penmate/backend/application/agent/orchestration/AgentResultPublisher.java)
- [`application/agent/orchestration/AgentTaskRuntimeUpdater.java`](penmate-backend/src/main/java/com/penmate/backend/application/agent/orchestration/AgentTaskRuntimeUpdater.java)
- [`application/agent/orchestration/AgentToolLoopRunner.java`](penmate-backend/src/main/java/com/penmate/backend/application/agent/orchestration/AgentToolLoopRunner.java)
- [`application/agent/tool/catalog/AgentToolDefinition.java`](penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/catalog/AgentToolDefinition.java)
- [`application/agent/tool/catalog/AgentToolCatalog.java`](penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/catalog/AgentToolCatalog.java)
- [`application/agent/tool/handler/AgentToolHandlerRegistry.java`](penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/handler/AgentToolHandlerRegistry.java)
- [`application/agent/tool/runtime/ToolApprovalContext.java`](penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/runtime/ToolApprovalContext.java)
- [`application/agent/tool/runtime/ToolCallSnapshotMapper.java`](penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/runtime/ToolCallSnapshotMapper.java)
- [`application/agent/tool/runtime/ToolCallResumeService.java`](penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/runtime/ToolCallResumeService.java)
- [`application/approval/coordination/ApprovalAgentResumeCoordinator.java`](penmate-backend/src/main/java/com/penmate/backend/application/approval/coordination/ApprovalAgentResumeCoordinator.java)
- [`domain/agent/service/AgentTaskTransitionPolicy.java`](penmate-backend/src/main/java/com/penmate/backend/domain/agent/service/AgentTaskTransitionPolicy.java)
- [`domain/agent/service/AgentGenerationCostPolicy.java`](penmate-backend/src/main/java/com/penmate/backend/domain/agent/service/AgentGenerationCostPolicy.java)
- [`infrastructure/agent/codec/AgentJsonCodec.java`](penmate-backend/src/main/java/com/penmate/backend/infrastructure/agent/codec/AgentJsonCodec.java)

### 5.2 迁移 / 重命名对象

- [`AgentApplicationService`](penmate-backend/src/main/java/com/penmate/backend/application/agent/AgentApplicationService.java) → 拆分后删除
- [`AgentOrchestrator`](penmate-backend/src/main/java/com/penmate/backend/application/agent/AgentOrchestrator.java) → [`AgentGenerationWorkflow`](penmate-backend/src/main/java/com/penmate/backend/application/agent/orchestration/AgentGenerationWorkflow.java)
- [`AgentOrchestrationDispatcher`](penmate-backend/src/main/java/com/penmate/backend/application/agent/AgentOrchestrationDispatcher.java) → [`AgentGenerationWorkflowDispatcher`](penmate-backend/src/main/java/com/penmate/backend/application/agent/orchestration/AgentGenerationWorkflowDispatcher.java)
- [`AgentToolLoopController`](penmate-backend/src/main/java/com/penmate/backend/application/agent/loop/AgentToolLoopController.java) → [`AgentToolLoopRunner`](penmate-backend/src/main/java/com/penmate/backend/application/agent/orchestration/AgentToolLoopRunner.java)
- [`ToolInvocationGateway`](penmate-backend/src/main/java/com/penmate/backend/application/agent/ToolInvocationGateway.java) → [`ToolCallApplicationService`](penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/gateway/ToolCallApplicationService.java)
- [`StaticToolMetadataRegistry`](penmate-backend/src/main/java/com/penmate/backend/application/agent/StaticToolMetadataRegistry.java) → [`StaticAgentToolCatalog`](penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/catalog/StaticAgentToolCatalog.java)
- [`ToolMetadata`](penmate-backend/src/main/java/com/penmate/backend/application/agent/ToolMetadata.java) → [`AgentToolDefinition`](penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/catalog/AgentToolDefinition.java)
- [`ContextEnhancerAgentToolHandler`](penmate-backend/src/main/java/com/penmate/backend/application/agent/ContextEnhancerAgentToolHandler.java) → [`ContextEnhancerToolHandler`](penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/handler/ContextEnhancerToolHandler.java)
- [`BookCrudAgentToolHandler`](penmate-backend/src/main/java/com/penmate/backend/application/agent/BookCrudAgentToolHandler.java) → [`BookCrudToolHandler`](penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/handler/BookCrudToolHandler.java)
- [`PluginToolCoordinator`](penmate-backend/src/main/java/com/penmate/backend/application/agent/PluginToolCoordinator.java) → [`PluginToolExecutor`](penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/plugin/PluginToolExecutor.java)
- [`ToolExecutionRequest`](penmate-backend/src/main/java/com/penmate/backend/application/agent/ToolExecutionRequest.java) → [`PluginToolExecuteCommand`](penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/plugin/PluginToolExecuteCommand.java)
- [`ToolExecutionResult`](penmate-backend/src/main/java/com/penmate/backend/application/agent/ToolExecutionResult.java) → [`PluginToolExecuteResult`](penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/plugin/PluginToolExecuteResult.java)
- [`ToolInvocationRequest`](penmate-backend/src/main/java/com/penmate/backend/application/agent/ToolInvocationRequest.java) → [`ToolCallRequest`](penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/runtime/ToolCallRequest.java)
- [`ToolInvocationGatewayResult`](penmate-backend/src/main/java/com/penmate/backend/application/agent/ToolInvocationGatewayResult.java) → [`ToolCallResult`](penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/runtime/ToolCallResult.java)
- [`AgentTaskStateMachine`](penmate-backend/src/main/java/com/penmate/backend/application/agent/AgentTaskStateMachine.java) → [`AgentTaskTransitionPolicy`](penmate-backend/src/main/java/com/penmate/backend/domain/agent/service/AgentTaskTransitionPolicy.java)
- [`AgentJsons`](penmate-backend/src/main/java/com/penmate/backend/application/agent/json/AgentJsons.java) → [`AgentJsonCodec`](penmate-backend/src/main/java/com/penmate/backend/infrastructure/agent/codec/AgentJsonCodec.java)
- [`ApprovedToolInvocationAsyncResumer`](penmate-backend/src/main/java/com/penmate/backend/application/approval/ApprovedToolInvocationAsyncResumer.java) → 迁入 agent orchestration/tool runtime

### 5.3 保持不动但更新引用

- [`AgentController`](penmate-backend/src/main/java/com/penmate/backend/interfaces/api/agent/AgentController.java)
- [`LangChain4jAgentLlmGateway`](penmate-backend/src/main/java/com/penmate/backend/infrastructure/llm/langchain4j/LangChain4jAgentLlmGateway.java)
- provider clients in [`infrastructure/llm/langchain4j/provider`](penmate-backend/src/main/java/com/penmate/backend/infrastructure/llm/langchain4j/provider)
- repository interfaces in [`domain/agent/repository`](penmate-backend/src/main/java/com/penmate/backend/domain/agent/repository)

---

## 六、迁移策略与顺序

采用 **保守分阶段迁移**，优先重构包结构与命名，再清理旧抽象，避免一次性重写 tool/approval 主链路。

### Task 1: 建立新包骨架与命名约定

**Files:**
- Create: [`penmate-backend/src/main/java/com/penmate/backend/application/agent/usecase`](penmate-backend/src/main/java/com/penmate/backend/application/agent/usecase)
- Create: [`penmate-backend/src/main/java/com/penmate/backend/application/agent/orchestration`](penmate-backend/src/main/java/com/penmate/backend/application/agent/orchestration)
- Create: [`penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/catalog`](penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/catalog)
- Create: [`penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/handler`](penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/handler)
- Create: [`penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/runtime`](penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/runtime)
- Create: [`penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/plugin`](penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/plugin)
- Create: [`penmate-backend/src/main/java/com/penmate/backend/application/agent/routing`](penmate-backend/src/main/java/com/penmate/backend/application/agent/routing)
- Create: [`penmate-backend/src/main/java/com/penmate/backend/domain/agent/service`](penmate-backend/src/main/java/com/penmate/backend/domain/agent/service)
- Create: [`penmate-backend/src/main/java/com/penmate/backend/infrastructure/agent/codec`](penmate-backend/src/main/java/com/penmate/backend/infrastructure/agent/codec)

**Step 1: Write the failing test**
创建一个架构约束测试，至少校验：
- application.agent.usecase 不依赖 infrastructure.llm.provider 具体实现
- domain.agent.service 不依赖 `org.springframework`
- approval 不直接 import [`AgentToolLoopController`](penmate-backend/src/main/java/com/penmate/backend/application/agent/loop/AgentToolLoopController.java)

建议测试文件：
- Create: [`penmate-backend/src/test/java/com/penmate/backend/architecture/AgentArchitectureDependencyTest.java`](penmate-backend/src/test/java/com/penmate/backend/architecture/AgentArchitectureDependencyTest.java)

**Step 2: Run test to verify it fails**
Run: [`mvn -pl penmate-backend -Dtest=AgentArchitectureDependencyTest test`](penmate-backend/pom.xml)
Expected: 当前依赖关系不满足，测试失败。

**Step 3: Write minimal implementation**
先仅创建新包与空壳类/接口，不迁移逻辑。

**Step 4: Run test to verify it passes**
Run: [`mvn -pl penmate-backend -Dtest=AgentArchitectureDependencyTest test`](penmate-backend/pom.xml)
Expected: 结构约束测试转为通过，或至少能覆盖已迁出的边界。

**Step 5: Commit**
Run: `git add penmate-backend/src/main/java/com/penmate/backend/application/agent penmate-backend/src/test/java/com/penmate/backend/architecture && git commit -m "refactor(agent): scaffold ddd package layout"`

### Task 2: 拆分 Agent 用例服务

**Files:**
- Modify: [`penmate-backend/src/main/java/com/penmate/backend/interfaces/api/agent/AgentController.java`](penmate-backend/src/main/java/com/penmate/backend/interfaces/api/agent/AgentController.java)
- Create: [`penmate-backend/src/main/java/com/penmate/backend/application/agent/usecase/AgentConversationAppService.java`](penmate-backend/src/main/java/com/penmate/backend/application/agent/usecase/AgentConversationAppService.java)
- Create: [`penmate-backend/src/main/java/com/penmate/backend/application/agent/usecase/AgentMessageAppService.java`](penmate-backend/src/main/java/com/penmate/backend/application/agent/usecase/AgentMessageAppService.java)
- Create: [`penmate-backend/src/main/java/com/penmate/backend/application/agent/usecase/AgentGenerationAppService.java`](penmate-backend/src/main/java/com/penmate/backend/application/agent/usecase/AgentGenerationAppService.java)
- Create: [`penmate-backend/src/main/java/com/penmate/backend/application/agent/usecase/AgentJsonInputNormalizer.java`](penmate-backend/src/main/java/com/penmate/backend/application/agent/usecase/AgentJsonInputNormalizer.java)
- Delete later: [`penmate-backend/src/main/java/com/penmate/backend/application/agent/AgentApplicationService.java`](penmate-backend/src/main/java/com/penmate/backend/application/agent/AgentApplicationService.java)

**Step 1: Write the failing test**
为 conversation/message/generation 各补一个 controller/service 协作测试，确保重构前后 HTTP 行为不变。

**Step 2: Run test to verify it fails**
Run: [`mvn -pl penmate-backend -Dtest=AgentControllerTest test`](penmate-backend/pom.xml)
Expected: 因依赖切换未完成而失败。

**Step 3: Write minimal implementation**
- 直接把旧 [`AgentApplicationService`](penmate-backend/src/main/java/com/penmate/backend/application/agent/AgentApplicationService.java) 方法原样迁入对应新类
- [`AgentController`](penmate-backend/src/main/java/com/penmate/backend/interfaces/api/agent/AgentController.java) 改为注入三个 usecase service
- [`normalizeJsonField()`](penmate-backend/src/main/java/com/penmate/backend/application/agent/AgentApplicationService.java:225) 提炼为独立 normalizer

**Step 4: Run test to verify it passes**
Run: [`mvn -pl penmate-backend -Dtest=AgentControllerTest test`](penmate-backend/pom.xml)
Expected: 接口行为保持一致。

**Step 5: Commit**
Run: `git add penmate-backend/src/main/java/com/penmate/backend/application/agent penmate-backend/src/main/java/com/penmate/backend/interfaces/api/agent && git commit -m "refactor(agent): split application use cases"`

### Task 3: 下沉任务状态规则到领域层

Use [test-driven-development] mode for this task.

**Files:**
- Create: [`penmate-backend/src/main/java/com/penmate/backend/domain/agent/service/AgentTaskTransitionPolicy.java`](penmate-backend/src/main/java/com/penmate/backend/domain/agent/service/AgentTaskTransitionPolicy.java)
- Modify: [`penmate-backend/src/main/java/com/penmate/backend/application/agent/AgentApplicationService.java`](penmate-backend/src/main/java/com/penmate/backend/application/agent/AgentApplicationService.java)
- Modify: [`penmate-backend/src/main/java/com/penmate/backend/application/agent/AgentOrchestrator.java`](penmate-backend/src/main/java/com/penmate/backend/application/agent/AgentOrchestrator.java)
- Modify: [`penmate-backend/src/main/java/com/penmate/backend/application/approval/ApprovalApplicationService.java`](penmate-backend/src/main/java/com/penmate/backend/application/approval/ApprovalApplicationService.java)
- Modify: [`penmate-backend/src/main/java/com/penmate/backend/application/approval/ApprovedToolInvocationAsyncResumer.java`](penmate-backend/src/main/java/com/penmate/backend/application/approval/ApprovedToolInvocationAsyncResumer.java)
- Modify: [`penmate-backend/src/main/java/com/penmate/backend/application/approval/PendingToolInvocationTimeoutGuard.java`](penmate-backend/src/main/java/com/penmate/backend/application/approval/PendingToolInvocationTimeoutGuard.java)

**Step 1: Write the failing test**
新增状态迁移策略测试：
- `pending -> running` allowed
- `running -> waiting_approval` allowed
- `done -> running` rejected
- invalid raw status rejected

测试文件：
- Create: [`penmate-backend/src/test/java/com/penmate/backend/domain/agent/service/AgentTaskTransitionPolicyTest.java`](penmate-backend/src/test/java/com/penmate/backend/domain/agent/service/AgentTaskTransitionPolicyTest.java)

**Step 2: Run test to verify it fails**
Run: [`mvn -pl penmate-backend -Dtest=AgentTaskTransitionPolicyTest test`](penmate-backend/pom.xml)
Expected: 类不存在，失败。

**Step 3: Write minimal implementation**
把 [`AgentTaskStateMachine`](penmate-backend/src/main/java/com/penmate/backend/application/agent/AgentTaskStateMachine.java) 逻辑平移到领域 policy；应用层改依赖新 policy。

**Step 4: Run test to verify it passes**
Run: [`mvn -pl penmate-backend -Dtest=AgentTaskTransitionPolicyTest test`](penmate-backend/pom.xml)
Expected: 测试通过。

**Step 5: Commit**
Run: `git add penmate-backend/src/main/java/com/penmate/backend/domain/agent/service penmate-backend/src/test/java/com/penmate/backend/domain/agent/service && git commit -m "refactor(agent): move task transition rule to domain policy"`

### Task 4: 重构 Agent generation workflow

**Files:**
- Create: [`penmate-backend/src/main/java/com/penmate/backend/application/agent/orchestration/AgentGenerationWorkflow.java`](penmate-backend/src/main/java/com/penmate/backend/application/agent/orchestration/AgentGenerationWorkflow.java)
- Create: [`penmate-backend/src/main/java/com/penmate/backend/application/agent/orchestration/AgentPromptAssembler.java`](penmate-backend/src/main/java/com/penmate/backend/application/agent/orchestration/AgentPromptAssembler.java)
- Create: [`penmate-backend/src/main/java/com/penmate/backend/application/agent/orchestration/AgentResultPublisher.java`](penmate-backend/src/main/java/com/penmate/backend/application/agent/orchestration/AgentResultPublisher.java)
- Create: [`penmate-backend/src/main/java/com/penmate/backend/application/agent/orchestration/AgentTaskRuntimeUpdater.java`](penmate-backend/src/main/java/com/penmate/backend/application/agent/orchestration/AgentTaskRuntimeUpdater.java)
- Create: [`penmate-backend/src/main/java/com/penmate/backend/domain/agent/service/AgentGenerationCostPolicy.java`](penmate-backend/src/main/java/com/penmate/backend/domain/agent/service/AgentGenerationCostPolicy.java)
- Modify: [`penmate-backend/src/main/java/com/penmate/backend/application/agent/AgentOrchestrationDispatcher.java`](penmate-backend/src/main/java/com/penmate/backend/application/agent/AgentOrchestrationDispatcher.java)
- Delete later: [`penmate-backend/src/main/java/com/penmate/backend/application/agent/AgentOrchestrator.java`](penmate-backend/src/main/java/com/penmate/backend/application/agent/AgentOrchestrator.java)

**Step 1: Write the failing test**
围绕 workflow 写单测，至少覆盖：
- task 不存在直接返回
- loop waiting approval 时任务置为 waiting_approval
- loop 完成时写 runtime、persist assistant message、publish done
- 异常时状态置 failed

**Step 2: Run test to verify it fails**
Run: [`mvn -pl penmate-backend -Dtest=AgentGenerationWorkflowTest test`](penmate-backend/pom.xml)
Expected: 新 workflow 未实现，失败。

**Step 3: Write minimal implementation**
- 以旧 [`AgentOrchestrator`](penmate-backend/src/main/java/com/penmate/backend/application/agent/AgentOrchestrator.java) 为基础迁移
- 只做命名与组件拆分，不改业务语义

**Step 4: Run test to verify it passes**
Run: [`mvn -pl penmate-backend -Dtest=AgentGenerationWorkflowTest test`](penmate-backend/pom.xml)
Expected: 核心编排行为保持一致。

**Step 5: Commit**
Run: `git add penmate-backend/src/main/java/com/penmate/backend/application/agent/orchestration penmate-backend/src/main/java/com/penmate/backend/domain/agent/service && git commit -m "refactor(agent): extract generation workflow components"`

### Task 5: 重构 tool runtime 与 catalog

**Files:**
- Create: [`penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/catalog/AgentToolDefinition.java`](penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/catalog/AgentToolDefinition.java)
- Create: [`penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/catalog/AgentToolCatalog.java`](penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/catalog/AgentToolCatalog.java)
- Create: [`penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/catalog/StaticAgentToolCatalog.java`](penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/catalog/StaticAgentToolCatalog.java)
- Create: [`penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/runtime/ToolCallRequest.java`](penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/runtime/ToolCallRequest.java)
- Create: [`penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/runtime/ToolCallResult.java`](penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/runtime/ToolCallResult.java)
- Create: [`penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/gateway/ToolCallApplicationService.java`](penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/gateway/ToolCallApplicationService.java)
- Modify: [`penmate-backend/src/main/java/com/penmate/backend/application/agent/ToolInvocationGateway.java`](penmate-backend/src/main/java/com/penmate/backend/application/agent/ToolInvocationGateway.java)

**Step 1: Write the failing test**
测试点：
- handler 不存在返回失败
- validate 失败返回 `TOOL_VALIDATION_FAILED`
- approvalRequired 时创建审批并落快照
- 不需要审批时执行 handler 成功

**Step 2: Run test to verify it fails**
Run: [`mvn -pl penmate-backend -Dtest=ToolCallApplicationServiceTest test`](penmate-backend/pom.xml)
Expected: 新对象不存在或行为不匹配而失败。

**Step 3: Write minimal implementation**
- 先复制旧 [`ToolInvocationGateway`](penmate-backend/src/main/java/com/penmate/backend/application/agent/ToolInvocationGateway.java) 行为
- 再逐步替换命名与依赖

**Step 4: Run test to verify it passes**
Run: [`mvn -pl penmate-backend -Dtest=ToolCallApplicationServiceTest test`](penmate-backend/pom.xml)
Expected: 工具调用/审批挂起行为不变。

**Step 5: Commit**
Run: `git add penmate-backend/src/main/java/com/penmate/backend/application/agent/tool && git commit -m "refactor(agent): separate tool runtime and catalog"`

### Task 6: 重构 tool handlers 与 plugin executor

**Files:**
- Move: [`penmate-backend/src/main/java/com/penmate/backend/application/agent/AgentToolHandler.java`](penmate-backend/src/main/java/com/penmate/backend/application/agent/AgentToolHandler.java)
- Move: [`penmate-backend/src/main/java/com/penmate/backend/application/agent/ContextEnhancerAgentToolHandler.java`](penmate-backend/src/main/java/com/penmate/backend/application/agent/ContextEnhancerAgentToolHandler.java)
- Move: [`penmate-backend/src/main/java/com/penmate/backend/application/agent/BookCrudAgentToolHandler.java`](penmate-backend/src/main/java/com/penmate/backend/application/agent/BookCrudAgentToolHandler.java)
- Move: [`penmate-backend/src/main/java/com/penmate/backend/application/agent/PluginToolCoordinator.java`](penmate-backend/src/main/java/com/penmate/backend/application/agent/PluginToolCoordinator.java)
- Move: [`penmate-backend/src/main/java/com/penmate/backend/application/agent/ToolExecutionRequest.java`](penmate-backend/src/main/java/com/penmate/backend/application/agent/ToolExecutionRequest.java)
- Move: [`penmate-backend/src/main/java/com/penmate/backend/application/agent/ToolExecutionResult.java`](penmate-backend/src/main/java/com/penmate/backend/application/agent/ToolExecutionResult.java)

**Step 1: Write the failing test**
- `context_enhancer` handler 调 plugin executor 成功/失败
- `book_crud` delete extra field 校验失败
- plugin executor 无 install 时返回空成功

**Step 2: Run test to verify it fails**
Run: [`mvn -pl penmate-backend -Dtest=ContextEnhancerToolHandlerTest,BookCrudToolHandlerTest,PluginToolExecutorTest test`](penmate-backend/pom.xml)
Expected: 新类不存在，失败。

**Step 3: Write minimal implementation**
只做类迁移与命名收敛，不立即改业务逻辑。

**Step 4: Run test to verify it passes**
Run: [`mvn -pl penmate-backend -Dtest=ContextEnhancerToolHandlerTest,BookCrudToolHandlerTest,PluginToolExecutorTest test`](penmate-backend/pom.xml)
Expected: 行为保持一致。

**Step 5: Commit**
Run: `git add penmate-backend/src/main/java/com/penmate/backend/application/agent/tool && git commit -m "refactor(agent): reorganize tool handlers and plugin executor"`

### Task 7: 解耦 approval 与 agent 恢复链路

**Files:**
- Create: [`penmate-backend/src/main/java/com/penmate/backend/application/approval/coordination/ApprovalAgentResumeCoordinator.java`](penmate-backend/src/main/java/com/penmate/backend/application/approval/coordination/ApprovalAgentResumeCoordinator.java)
- Create: [`penmate-backend/src/main/java/com/penmate/backend/application/agent/orchestration/AgentApprovalResumeCoordinator.java`](penmate-backend/src/main/java/com/penmate/backend/application/agent/orchestration/AgentApprovalResumeCoordinator.java)
- Modify: [`penmate-backend/src/main/java/com/penmate/backend/application/approval/ApprovalApplicationService.java`](penmate-backend/src/main/java/com/penmate/backend/application/approval/ApprovalApplicationService.java)
- Move: [`penmate-backend/src/main/java/com/penmate/backend/application/approval/ApprovedToolInvocationAsyncResumer.java`](penmate-backend/src/main/java/com/penmate/backend/application/approval/ApprovedToolInvocationAsyncResumer.java)
- Modify: [`penmate-backend/src/main/java/com/penmate/backend/application/approval/PendingToolInvocationTimeoutGuard.java`](penmate-backend/src/main/java/com/penmate/backend/application/approval/PendingToolInvocationTimeoutGuard.java)

**Step 1: Write the failing test**
测试点：
- approval 通过后仅调用协调器接口，不直接依赖 tool loop 实现
- 快照 claim 成功后才触发 resume
- reject 仍能正确封口任务

**Step 2: Run test to verify it fails**
Run: [`mvn -pl penmate-backend -Dtest=ApprovalApplicationServiceTest test`](penmate-backend/pom.xml)
Expected: 依赖切换未完成，失败。

**Step 3: Write minimal implementation**
- 从 approval 包移除对具体 agent runtime 类的直接依赖
- 由协调器实现持有 loop runner / tool call service

**Step 4: Run test to verify it passes**
Run: [`mvn -pl penmate-backend -Dtest=ApprovalApplicationServiceTest test`](penmate-backend/pom.xml)
Expected: 审批行为不变，但依赖方向更清晰。

**Step 5: Commit**
Run: `git add penmate-backend/src/main/java/com/penmate/backend/application/approval penmate-backend/src/main/java/com/penmate/backend/application/agent/orchestration && git commit -m "refactor(approval): decouple approval from agent runtime"`

### Task 8: 收敛 JSON codec 与 LLM port

**Files:**
- Create: [`penmate-backend/src/main/java/com/penmate/backend/infrastructure/agent/codec/AgentJsonCodec.java`](penmate-backend/src/main/java/com/penmate/backend/infrastructure/agent/codec/AgentJsonCodec.java)
- Modify: 所有引用 [`AgentJsons`](penmate-backend/src/main/java/com/penmate/backend/application/agent/json/AgentJsons.java) 的类
- Modify: [`penmate-backend/src/main/java/com/penmate/backend/application/agent/llm/AgentLlmGateway.java`](penmate-backend/src/main/java/com/penmate/backend/application/agent/llm/AgentLlmGateway.java)
- Modify: [`penmate-backend/src/main/java/com/penmate/backend/infrastructure/llm/langchain4j/LangChain4jAgentLlmGateway.java`](penmate-backend/src/main/java/com/penmate/backend/infrastructure/llm/langchain4j/LangChain4jAgentLlmGateway.java)

**Step 1: Write the failing test**
- 结构测试：application.agent 不再依赖 `application.agent.json`
- gateway 合约测试：只验证 turn-based 入口

**Step 2: Run test to verify it fails**
Run: [`mvn -pl penmate-backend -Dtest=AgentArchitectureDependencyTest,LangChain4jAgentLlmGatewayTest test`](penmate-backend/pom.xml)
Expected: 旧类引用仍存在，失败。

**Step 3: Write minimal implementation**
- `AgentJsons` 标记 deprecated 并逐步替换
- 删除 [`AgentLlmGateway#generate()`](penmate-backend/src/main/java/com/penmate/backend/application/agent/llm/AgentLlmGateway.java:31) 及相关死代码

**Step 4: Run test to verify it passes**
Run: [`mvn -pl penmate-backend -Dtest=AgentArchitectureDependencyTest,LangChain4jAgentLlmGatewayTest test`](penmate-backend/pom.xml)
Expected: 应用层依赖收敛。

**Step 5: Commit**
Run: `git add penmate-backend/src/main/java/com/penmate/backend/application/agent penmate-backend/src/main/java/com/penmate/backend/infrastructure/agent penmate-backend/src/main/java/com/penmate/backend/infrastructure/llm && git commit -m "refactor(agent): isolate json codec and simplify llm port"`

### Task 9: 删除旧类并做全量回归

**Files:**
- Delete: 旧命名类与过渡适配类
- Modify: import、构造注入、Spring wiring、测试引用

**Step 1: Write the failing test**
补一条端到端回归测试，覆盖：
- create generation → run workflow → tool loop → waiting approval / done
- approve → resume → done 或 failed

**Step 2: Run test to verify it fails**
Run: [`mvn -pl penmate-backend -Dtest=AgentApprovalIntegrationTest test`](penmate-backend/pom.xml)
Expected: 旧新类混用时失败。

**Step 3: Write minimal implementation**
删除过渡层、修正注入，清理所有已废弃类。

**Step 4: Run test to verify it passes**
Run: [`mvn -pl penmate-backend test`](penmate-backend/pom.xml)
Expected: 全量测试通过。

**Step 5: Commit**
Run: `git add penmate-backend && git commit -m "refactor(agent): finalize ddd package reorganization"`

---

## 七、主要风险与缓解措施

### 风险 1：审批恢复链路最脆弱
**原因：** 涉及 [`ApprovalApplicationService`](penmate-backend/src/main/java/com/penmate/backend/application/approval/ApprovalApplicationService.java)、[`ApprovedToolInvocationAsyncResumer`](penmate-backend/src/main/java/com/penmate/backend/application/approval/ApprovedToolInvocationAsyncResumer.java)、[`PendingToolInvocationTimeoutGuard`](penmate-backend/src/main/java/com/penmate/backend/application/approval/PendingToolInvocationTimeoutGuard.java)、[`AgentToolLoopController`](penmate-backend/src/main/java/com/penmate/backend/application/agent/loop/AgentToolLoopController.java) 多处协作。  
**缓解：** 先做接口抽象和 façade 适配，不直接改状态流语义；保留集成测试覆盖 claim / executing / completed / failed。

### 风险 2：Spring 注入因大规模重命名而失效
**缓解：** 每完成一个阶段就运行对应测试；旧类先保留 deprecated façade 一小段时间，不要一步删光。

### 风险 3：tool metadata 与 LLM schema 合并后影响 prompt/tool-calling 行为
**缓解：** 对 [`StaticToolMetadataRegistry#toLlmToolSchemas()`](penmate-backend/src/main/java/com/penmate/backend/application/agent/StaticToolMetadataRegistry.java:42) 当前输出做 golden file 断言，重构前后保持一致。

### 风险 4：JSON codec 迁移造成序列化格式微变
**缓解：** 为 [`AgentJsons#toJson()`](penmate-backend/src/main/java/com/penmate/backend/application/agent/json/AgentJsons.java:29) 当前行为补兼容测试，确保 null/string/object/array 输出不变。

### 风险 5：用例服务拆分后控制器装配变化过大
**缓解：** 保持 [`AgentController`](penmate-backend/src/main/java/com/penmate/backend/interfaces/api/agent/AgentController.java) 路由与 DTO 完全不变，只替换注入目标。

---

## 八、建议的最终包职责说明

### [`application/agent/usecase`](penmate-backend/src/main/java/com/penmate/backend/application/agent/usecase)
只放同步用例服务，直接对应 controller 调用。

### [`application/agent/orchestration`](penmate-backend/src/main/java/com/penmate/backend/application/agent/orchestration)
只放长流程、异步调度、恢复流程、结果发布。

### [`application/agent/tool`](penmate-backend/src/main/java/com/penmate/backend/application/agent/tool)
只放 tool runtime / catalog / handlers / plugin executor。

### [`application/agent/llm`](penmate-backend/src/main/java/com/penmate/backend/application/agent/llm)
只放应用层 LLM 端口与 DTO，不放 provider-specific 逻辑。

### [`domain/agent/service`](penmate-backend/src/main/java/com/penmate/backend/domain/agent/service)
只放与 Agent 任务本身强相关的纯业务规则。

### [`infrastructure/agent`](penmate-backend/src/main/java/com/penmate/backend/infrastructure/agent)
只放 codec、snapshot 映射细节、具体技术实现。

---

## 九、验收标准

重构完成后，满足以下标准：

1. [`application/agent`](penmate-backend/src/main/java/com/penmate/backend/application/agent) 根包下不再堆放 10+ 个杂类，目录能一眼看出 usecase / orchestration / tool / llm / routing 分工。
2. approval 应用层不再直接依赖 agent tool loop 具体实现类。
3. 状态流转规则已移动到 [`domain/agent/service`](penmate-backend/src/main/java/com/penmate/backend/domain/agent/service)。
4. JSON codec 不再位于业务应用包。
5. LLM port 清晰且只有当前真实使用的能力。
6. `ToolInvocation*` 与 `ToolExecution*` 命名混乱被消除。
7. 现有 controller API、数据库模型、审批/恢复主流程行为不变。
8. [`mvn -pl penmate-backend test`](penmate-backend/pom.xml) 全量通过。

---

## 十、实施建议

建议按以下节奏推进：

1. **第一批：** 目录骨架 + 类迁移 + 纯重命名，不改语义。
2. **第二批：** 把状态规则、成本规则下沉到领域层。
3. **第三批：** 拆 approval 与 agent 恢复耦合。
4. **第四批：** 清理遗留接口与 JSON/LLM 死代码。

整个重构建议在独立分支完成，并在每个任务结束后请求一次 [requesting-code-review] 复核依赖方向。

---

Plan complete. Execute now?

Options:
1. Execute in this session ([executing-plans] mode)
2. Execute later (user will run `/execute-plan`)
3. Manual implementation (just use plan as guide)

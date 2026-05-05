# Agent Tool 与 LangChain4j 职责边界分析

> 速读结论：
> - 业务路由不是重复造轮子；
> - provider 工厂是合理薄层；
> - 原生 HTTP tool-calling 适配最接近重复建设；
> - 核心业务 tool 不建议切到注解式 `@Tool`；
> - 下一步先统一 tool 元数据真源，再视 provider 支持度决定是否回收原生 HTTP 层。

## 1. 背景与目标

本文基于当前 PenMate 后端实现，统一回答以下五个问题：

- agent tool 应如何新增与实现；
- tool 注册是否需要再抽一个类；
- LangChain4j 在当前工具调用实现下应承担什么职责；
- 是否适合使用 LangChain4j 注解式 tool；
- 当前模型调用策略 / 路由工厂是否属于重复造轮子。
- 当前模型调用策略工厂 / 路由工厂是否与 LangChain4j 重复造轮子。

文档采用“现状代码证据 → LangChain4j 可承担职责 → 当前系统必须自担职责 → 重叠度判断 → 演进建议”的结构，避免抽象讨论。

## 2. 当前实现概览

### 2.1 分析对象

本次结论主要基于以下代码对象：

- [`AgentLlmGateway`](../../penmate-backend/src/main/java/com/penmate/backend/application/agent/llm/AgentLlmGateway.java:13)
- [`LangChain4jAgentLlmGateway`](../../penmate-backend/src/main/java/com/penmate/backend/infrastructure/llm/langchain4j/LangChain4jAgentLlmGateway.java:24)
- [`AgentModelRoutingService`](../../penmate-backend/src/main/java/com/penmate/backend/application/agent/AgentModelRoutingService.java:24)
- [`ProviderChatClientFactory`](../../penmate-backend/src/main/java/com/penmate/backend/infrastructure/llm/langchain4j/provider/ProviderChatClientFactory.java:8)
- [`ProviderChatClient`](../../penmate-backend/src/main/java/com/penmate/backend/infrastructure/llm/langchain4j/provider/ProviderChatClient.java:11)
- [`AbstractOpenAiCompatibleProviderChatClient`](../../penmate-backend/src/main/java/com/penmate/backend/infrastructure/llm/langchain4j/provider/AbstractOpenAiCompatibleProviderChatClient.java:11)
- [`NativeOpenAiStyleHttpProviderChatClient`](../../penmate-backend/src/main/java/com/penmate/backend/infrastructure/llm/langchain4j/provider/NativeOpenAiStyleHttpProviderChatClient.java:27)
- [`StaticAgentToolCatalog`](../../penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/catalog/StaticAgentToolCatalog.java:12)
- [`AgentToolHandler`](../../penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/handler/AgentToolHandler.java:6)
- [`BookCrudToolHandler`](../../penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/handler/BookCrudToolHandler.java:18)
- [`ContextEnhancerToolHandler`](../../penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/handler/ContextEnhancerToolHandler.java:13)
- [`ToolCallApplicationService`](../../penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/gateway/ToolCallApplicationService.java:25)
- [`DefaultApprovalPolicyEngine`](../../penmate-backend/src/main/java/com/penmate/backend/application/approval/DefaultApprovalPolicyEngine.java:19)
- [`AgentToolLoopRunner`](../../penmate-backend/src/main/java/com/penmate/backend/application/agent/orchestration/AgentToolLoopRunner.java:32)
- [`AgentLlmToolCall`](../../penmate-backend/src/main/java/com/penmate/backend/application/agent/llm/AgentLlmToolCall.java:6)

这些类已经覆盖了本次问题的四个关键链路：
1. 模型执行配置如何从业务数据解析出来；
2. 解析后的 provider 如何映射到具体 LLM client；
3. tool schema 如何暴露给模型，以及 tool call 如何回流到业务执行；
4. 审批、挂起、恢复这类业务约束是否应交给 LangChain4j。

因此，判断“是否重复造轮子”无需泛泛讨论概念，而应直接对照这些实际实现。

### 2.2 当前 agent tool 链路已经分为三层

当前实现已经自然分成三层：

1. tool 暴露层：[`StaticAgentToolCatalog`](../../penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/catalog/StaticAgentToolCatalog.java:12) 决定哪些 tool schema 暴露给模型；
2. tool 执行层：[`AgentToolHandler`](../../penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/handler/AgentToolHandler.java:6) 及其实现类负责参数校验与真正执行业务；
3. tool 治理层：[`ToolCallApplicationService`](../../penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/gateway/ToolCallApplicationService.java:25) 负责审批、挂起、恢复前置、幂等键、状态流转。

这说明当前系统并不是把所有东西都压成一个“tool method”，而是有意识地把“模型可见性”“业务执行”“治理编排”拆开了。

### 2.3 如何新增一个 agent tool

基于当前代码，新增一个 agent tool 的最小落地路径应当是：

1. 在 [`StaticAgentToolCatalog`](../../penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/catalog/StaticAgentToolCatalog.java:26) 增加 `toolCode -> AgentToolDefinition` 元数据；
2. 视是否要暴露给模型，在 [`StaticAgentToolCatalog.toLlmToolSchemas()`](../../penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/catalog/StaticAgentToolCatalog.java:42) 增加对应 schema；
3. 新增一个实现 [`AgentToolHandler`](../../penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/handler/AgentToolHandler.java:6) 的处理器，至少实现 [`toolCode()`](../../penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/handler/AgentToolHandler.java:8) 与 [`execute()`](../../penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/handler/AgentToolHandler.java:14)，必要时覆写 [`validate()`](../../penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/handler/AgentToolHandler.java:10)；
4. 若 tool 有审批风险，补齐 [`AgentToolDefinition`](../../penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/catalog/AgentToolDefinition.java) 中的审批声明，并确认 [`DefaultApprovalPolicyEngine.evaluate()`](../../penmate-backend/src/main/java/com/penmate/backend/application/approval/DefaultApprovalPolicyEngine.java:28) 是否需要参数级规则；
5. 确认新 tool 的输出可被 [`AgentToolLoopRunner`](../../penmate-backend/src/main/java/com/penmate/backend/application/agent/orchestration/AgentToolLoopRunner.java:106) 回填为 tool result message。

从现有实现看，[`BookCrudToolHandler`](../../penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/handler/BookCrudToolHandler.java:18) 展示了“参数校验 + 多 operation 分发 + 业务应用服务调用”的典型写法，[`ContextEnhancerToolHandler`](../../penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/handler/ContextEnhancerToolHandler.java:13) 则展示了“轻量代理外部插件执行器”的写法。

需要特别指出一个当前事实：[`StaticAgentToolCatalog.registry`](../../penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/catalog/StaticAgentToolCatalog.java:26) 已登记 [`book_crud`](../../penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/handler/BookCrudToolHandler.java:28) 元数据，但 [`StaticAgentToolCatalog.toLlmToolSchemas()`](../../penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/catalog/StaticAgentToolCatalog.java:42) 仅返回 `context_enhancer` 的 schema。这说明 `book_crud` 当前只是“应用层已识别的 tool”，并未真正暴露给模型；同时也意味着面向模型的参数 schema / description 说明尚未补齐。

### 2.4 什么是“多 operation 分发”

先给直白定义：

- **多 operation 分发**：指“对外只暴露一个 toolCode，但在 tool 参数里再带一个 `operation` 字段，然后由同一个 handler 按 `operation` 值把请求分发到不同业务动作”。

当前代码里的典型例子就是 [`BookCrudToolHandler`](../../penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/handler/BookCrudToolHandler.java:18)：

- [`validate()`](../../penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/handler/BookCrudToolHandler.java:32) 先解析 [`toolArgsJson`](../../penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/runtime/ToolCallRequest.java) 中的 `operation`；
- [`execute()`](../../penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/handler/BookCrudToolHandler.java:47) 再按 `operation` 分支：
  - [`create`](../../penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/handler/BookCrudToolHandler.java:51) → 调 [`NovelApplicationService.createProject()`](../../penmate-backend/src/main/java/com/penmate/backend/application/novel/NovelApplicationService.java:26)；
  - [`list`](../../penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/handler/BookCrudToolHandler.java:64) → 调 [`NovelApplicationService.listProjects()`](../../penmate-backend/src/main/java/com/penmate/backend/application/novel/NovelApplicationService.java:36)；
  - [`update`](../../penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/handler/BookCrudToolHandler.java:69) → 调 [`NovelApplicationService.updateProject()`](../../penmate-backend/src/main/java/com/penmate/backend/application/novel/NovelApplicationService.java:50)；
  - [`delete`](../../penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/handler/BookCrudToolHandler.java:79) → 调 [`NovelApplicationService.deleteProject()`](../../penmate-backend/src/main/java/com/penmate/backend/application/novel/NovelApplicationService.java:64)；
  - 其他值统一返回 [`UNSUPPORTED_OPERATION`](../../penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/handler/BookCrudToolHandler.java:88)。

因此，这里的“分发”不是把一个请求广播给多个 handler，也不是并行执行多个动作，而是**同一个 handler 内部做一次二级路由**。

再说得更直白一点：

- `toolCode=book_crud` 只是一级入口；
- `operation=create/list/update/delete` 才决定最终具体干哪件事；
- 也就是说，当前 [`book_crud`](../../penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/handler/BookCrudToolHandler.java:28) 本质上是一个“复合工具”，而不是四个完全独立的 tool。

这类做法的好处是：

- LLM 暴露面更少，schema 数量更少；
- 同类资源操作可以共用一套参数解析、返回格式和审批元数据入口；
- [`ToolCallApplicationService`](../../penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/gateway/ToolCallApplicationService.java:25) 仍然只需要先按 `toolCode` 找到 handler，再把更细的动作判断留给 handler 内部。

它的代价也很明确：

- schema 会更“胖”，需要在一个 tool 里表达多种子动作；
- 审批粒度默认先挂在 `toolCode` 上，如果后续希望 `delete` 比 `list` 更严格，就要把差异继续下沉到 [`DefaultApprovalPolicyEngine.evaluate()`](../../penmate-backend/src/main/java/com/penmate/backend/application/approval/DefaultApprovalPolicyEngine.java:28) 这类参数级规则里；
- handler 会逐渐变成一个“小型命令分发器”，如果 `operation` 持续增多，后面就需要考虑是否拆成多个独立 tool。

## 3. 当前 Agent 包结构与 DDD 分层说明

### 3.1 `application/agent` 当前子包职责

当前 `agent` 相关代码已经不再是“一个大 application service + 若干工具类”的扁平结构，而是按应用层职责继续拆成多个子包：

- [`command`](../../penmate-backend/src/main/java/com/penmate/backend/application/agent/command/AgentCommands.java:1)：承载应用层输入命令，负责把接口层 DTO 转成稳定的用例入参；
- [`usecase`](../../penmate-backend/src/main/java/com/penmate/backend/application/agent/usecase/AgentGenerationAppService.java:18)：面向接口层暴露用例入口，负责创建会话、追加消息、创建生成任务、应用生成结果等应用服务；
- [`orchestration`](../../penmate-backend/src/main/java/com/penmate/backend/application/agent/orchestration/AgentGenerationWorkflow.java:22)：承载跨对象、跨外部能力的流程编排，例如状态推进、RAG 检索、prompt 装配、tool loop、结果发布；
- [`llm`](../../penmate-backend/src/main/java/com/penmate/backend/application/agent/llm/AgentLlmGateway.java:13)：定义应用层所需的统一 LLM 端口与交互模型，把上层从具体 SDK / provider 抽离出来；
- [`tool/catalog`](../../penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/catalog/StaticAgentToolCatalog.java:12)：定义 agent tool 的目录、暴露元数据与面向 LLM 的 schema；
- [`tool/handler`](../../penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/handler/AgentToolHandler.java:6)：定义具体 tool 的业务执行入口，把每个 tool 的参数校验与动作执行隔离为独立 handler；
- [`tool/gateway`](../../penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/gateway/ToolCallApplicationService.java:25)：承接 tool 调用治理，统一处理元数据查找、审批判断、待审批快照落库、状态切换与执行分发；
- [`tool/runtime`](../../penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/runtime/ToolCallResumeService.java:15)：承载运行时恢复模型与快照映射，服务于审批后的 loop 恢复；
- [`tool/plugin`](../../penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/plugin/PluginToolExecutor.java:6)：承载应用层定义的插件工具执行端口与命令/结果模型；
- [`AgentModelRoutingService`](../../penmate-backend/src/main/java/com/penmate/backend/application/agent/AgentModelRoutingService.java:24) 与 [`AgentTaskStateMachine`](../../penmate-backend/src/main/java/com/penmate/backend/application/agent/AgentTaskStateMachine.java:11)：分别负责模型执行配置聚合、领域状态迁移规则的应用层落地；
- [`AgentDomainConfig`](../../penmate-backend/src/main/java/com/penmate/backend/application/agent/AgentDomainConfig.java:13)：负责把领域服务装配进 Spring 容器。

这类拆分的关键意义在于：`application` 层不再按“技术框架”分组，而是按“用例入口 / 编排流程 / 外部端口 / tool 子能力”分组，更贴近 DDD 中应用层的职责边界。

### 3.2 与 `domain / infrastructure / interfaces` 的边界关系

当前边界大体可以概括为四句话：

1. [`interfaces/api/agent/AgentController`](../../penmate-backend/src/main/java/com/penmate/backend/interfaces/api/agent/AgentController.java:31) 只处理 HTTP 协议、参数接收与 DTO → command 转换，不直接编排 tool loop、审批或模型调用；
2. [`application/agent/usecase/AgentGenerationAppService.createGeneration()`](../../penmate-backend/src/main/java/com/penmate/backend/application/agent/usecase/AgentGenerationAppService.java:25) 这样的应用服务，负责调用仓储、检查前置条件、发起异步编排，但不承载底层 JSON 编解码或 HTTP provider 细节；
3. [`domain/agent/service/AgentTaskTransitionPolicy`](../../penmate-backend/src/main/java/com/penmate/backend/domain/agent/service/AgentTaskTransitionPolicy.java:12) 只表达“状态能否迁移”这种业务规则，不依赖 Spring、Controller、LangChain4j 或基础设施实现；
4. [`infrastructure/agent/codec/AgentJsonCodec`](../../penmate-backend/src/main/java/com/penmate/backend/infrastructure/agent/codec/AgentJsonCodec.java:8) 与 [`infrastructure/llm/langchain4j`](../../penmate-backend/src/main/java/com/penmate/backend/infrastructure/llm/langchain4j) 则承接具体技术实现，把 JSON 库与 LLM SDK / 原生 HTTP 协议适配限制在基础设施层。

如果顺着主链路看，这个边界关系也很清楚：

- 接口层通过 [`AgentController.createGeneration()`](../../penmate-backend/src/main/java/com/penmate/backend/interfaces/api/agent/AgentController.java:103) 接收请求；
- 应用层通过 [`AgentGenerationAppService.createGeneration()`](../../penmate-backend/src/main/java/com/penmate/backend/application/agent/usecase/AgentGenerationAppService.java:25) 创建任务并派发工作流；
- 编排层通过 [`AgentGenerationWorkflow.run()`](../../penmate-backend/src/main/java/com/penmate/backend/application/agent/orchestration/AgentGenerationWorkflow.java:37) 组织 RAG、LLM、tool loop 与结果回写；
- 领域层通过 [`AgentTaskTransitionPolicy.assertTransition()`](../../penmate-backend/src/main/java/com/penmate/backend/domain/agent/service/AgentTaskTransitionPolicy.java:30) 约束任务状态；
- 基础设施层再去实现实际的 JSON 处理、provider 调用与持久化细节。

这符合典型 DDD / 分层架构的依赖方向：接口层依赖应用层，应用层依赖领域抽象与仓储端口，基础设施层实现技术细节，而不是让 Controller 或 SDK 反向裹挟业务流程。

### 3.3 为什么这种拆分比之前更符合 DDD

如果把当前结构与“把 agent 功能都塞进一个大而全的应用服务 / orchestrator”相比，当前拆分更符合 DDD，主要体现在四点：

1. **业务规则回到领域层**：像 [`AgentTaskTransitionPolicy`](../../penmate-backend/src/main/java/com/penmate/backend/domain/agent/service/AgentTaskTransitionPolicy.java:12) 这样的状态迁移规则，已经从纯流程代码里抽离出来，避免“谁改状态谁顺手写规则”；
2. **应用层聚焦用例编排**：[`AgentGenerationAppService`](../../penmate-backend/src/main/java/com/penmate/backend/application/agent/usecase/AgentGenerationAppService.java:18) 只负责用例入口、前置校验与派发，不把 LLM 细节、tool 审批和 provider 报文揉成一个类；
3. **跨能力流程集中在 orchestration，而不是散落在 controller / handler**：[`AgentGenerationWorkflow`](../../penmate-backend/src/main/java/com/penmate/backend/application/agent/orchestration/AgentGenerationWorkflow.java:22) 明确承担“长流程协调者”角色，这比把流程写进 [`AgentController`](../../penmate-backend/src/main/java/com/penmate/backend/interfaces/api/agent/AgentController.java:31) 或单个 tool handler 更符合应用层编排定位；
4. **端口与实现分离更明确**：[`AgentLlmGateway`](../../penmate-backend/src/main/java/com/penmate/backend/application/agent/llm/AgentLlmGateway.java:13) 和 [`PluginToolExecutor`](../../penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/plugin/PluginToolExecutor.java:6) 这类接口把应用层需要的能力先抽成端口，再由基础设施实现，明显优于直接在业务代码里绑定 LangChain4j 或某个具体插件调用器。

从 DDD 角度看，这意味着当前 `agent` 模块已经开始具备：

- 领域规则集中表达；
- 应用服务以用例为中心；
- 基础设施依赖被隔离在边界外；
- 复杂长流程通过 orchestration 显式建模。

因此，现在讨论“是否重复造轮子”时，不能只看类名数量变多；更重要的是这些类是否把**领域规则、应用编排、技术适配**分离得更清楚。按当前代码看，这种拆分总体是在向更标准的 DDD 结构收敛，而不是单纯为了重构而重构。

### 3.4 当前仍保留的边界折中项

不过，当前实现并不是教科书式纯 DDD，仍然保留了几个现实折中：

1. [`ToolCallApplicationService`](../../penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/gateway/ToolCallApplicationService.java:25) 同时依赖 [`StaticAgentToolCatalog`](../../penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/catalog/StaticAgentToolCatalog.java:12)、审批应用服务、领域仓储、实时事件与 handler 列表，说明 tool 治理还处于“高协调度应用服务”阶段，边界清晰但聚合度仍偏高；
2. [`application/agent/tool/catalog/StaticAgentToolCatalog`](../../penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/catalog/StaticAgentToolCatalog.java:12) 仍是静态目录，tool 元数据、schema 真源与 handler 装配尚未完全统一，说明该处还没有形成最彻底的领域化建模；
3. [`application/agent/usecase/AgentJsonInputNormalizer`](../../penmate-backend/src/main/java/com/penmate/backend/application/agent/usecase/AgentJsonInputNormalizer.java:1) 与 [`infrastructure/agent/codec/AgentJsonCodec`](../../penmate-backend/src/main/java/com/penmate/backend/infrastructure/agent/codec/AgentJsonCodec.java:8) 共同存在，反映出 JSON 规范化与 JSON 技术编解码目前被拆在应用层/基础设施层两个点上，这是一种实用折中，而不是完全纯化的值对象建模；
4. [`AgentTaskStateMachine`](../../penmate-backend/src/main/java/com/penmate/backend/application/agent/AgentTaskStateMachine.java:11) 作为应用层包装器调用 [`AgentTaskTransitionPolicy`](../../penmate-backend/src/main/java/com/penmate/backend/domain/agent/service/AgentTaskTransitionPolicy.java:12)，本质上是为了适配 Spring 与日志/异常处理习惯，这种壳层在工程上合理，但从纯 DDD 角度看属于基础设施友好的适配层；
5. [`NativeOpenAiStyleHttpProviderChatClient`](../../penmate-backend/src/main/java/com/penmate/backend/infrastructure/llm/langchain4j/provider/NativeOpenAiStyleHttpProviderChatClient.java:27) 仍然说明 LLM 协议适配没有完全收敛到 LangChain4j 标准能力中，这个折中虽然位于 `infrastructure` 层内、没有污染领域边界，但会增加维护成本与重复实现风险。

因此，更准确的判断不是“当前已经完全 DDD 化”，而是“核心边界已基本对齐 DDD，但在 tool 注册真源、JSON 表达、LLM provider 适配上仍保留了工程性折中”。这也解释了为什么本文后续建议会优先收敛元数据真源与协议适配层，而不是反向削弱已经成型的应用/领域边界。

## 4. LangChain4j 能承担什么

### 4.1 LangChain4j 在当前系统中最合适的职责

LangChain4j 更适合承担三类职责：

1. 标准模型客户端封装：如 OpenAI 兼容模型的 SDK 适配，当前对应 [`AbstractOpenAiCompatibleProviderChatClient.generate()`](../../penmate-backend/src/main/java/com/penmate/backend/infrastructure/llm/langchain4j/provider/AbstractOpenAiCompatibleProviderChatClient.java:14)；
2. 标准消息对象与 tool-calling 协议适配；
3. 少量通用能力复用，例如 provider builder、模型调用封装。

在当前代码中，[`LangChain4jAgentLlmGateway`](../../penmate-backend/src/main/java/com/penmate/backend/infrastructure/llm/langchain4j/LangChain4jAgentLlmGateway.java:24) 的合理职责就是：

- 校验 [`AgentLlmExecutionConfig`](../../penmate-backend/src/main/java/com/penmate/backend/application/agent/llm/AgentLlmExecutionConfig.java:8) 是否完整；
- 根据 provider 选择具体 [`ProviderChatClient`](../../penmate-backend/src/main/java/com/penmate/backend/infrastructure/llm/langchain4j/provider/ProviderChatClient.java:11)；
- 把统一请求交给 SDK / provider 适配层处理。

换言之，LangChain4j 应位于“LLM 协议适配层”，而不应侵入 PenMate 的业务编排层。

### 4.2 是否应切换到 LangChain4j 注解式 tool

短结论：当前阶段不建议把核心 Agent tool 改成 LangChain4j 注解式 `@Tool` 作为主实现。

原因不是注解式 tool 不可用，而是它的默认抽象更适合“让模型直接调用一个普通 Java 方法”，但 PenMate 当前的 tool 调用并不是单纯方法调用，而是一个带有治理状态机的业务过程：

- 调用前要做元数据查找，见 [`ToolCallApplicationService.executeToolCall()`](../../penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/gateway/ToolCallApplicationService.java:51)；
- 调用前要做审批策略判断，见 [`DefaultApprovalPolicyEngine.evaluate()`](../../penmate-backend/src/main/java/com/penmate/backend/application/approval/DefaultApprovalPolicyEngine.java:28)；
- 命中审批后要落库为 `PendingToolInvocationSnapshot`，见 [`ToolCallApplicationService.executeToolCall()`](../../penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/gateway/ToolCallApplicationService.java:96)；
- 任务状态要切到 `waiting_approval`，见 [`ToolCallApplicationService.executeToolCall()`](../../penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/gateway/ToolCallApplicationService.java:116)；
- 审批后还要恢复 loop，见 [`AgentToolLoopRunner.resumeFromPending()`](../../penmate-backend/src/main/java/com/penmate/backend/application/agent/orchestration/AgentToolLoopRunner.java:115)。

LangChain4j 注解式 tool 无法天然表达这套“可挂起、可恢复、可审批”的业务协议。因此它更适合作为轻量无副作用工具或 PoC，而不适合作为当前核心业务 tool 的主抽象。

## 5. 当前系统必须自己承担什么

### 5.1 当前审批是怎么做的

先给直白定义：

- **当前审批**：指“当某个 tool call 命中风险策略后，系统不会立刻执行该 tool，而是先创建审批单、保存一份待恢复快照、把任务挂起到 `waiting_approval`；只有人工审批通过后，系统才会异步恢复并继续执行原来的 tool 调用或原来的 tool loop”。

按当前代码，完整链路如下：

1. tool 调用先进入 [`ToolCallApplicationService.executeToolCall()`](../../penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/gateway/ToolCallApplicationService.java:51)；
2. 该服务先查 [`StaticAgentToolCatalog.getRequired()`](../../penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/catalog/StaticAgentToolCatalog.java:32) 拿到 tool 元数据，再按 [`findHandler()`](../../penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/gateway/ToolCallApplicationService.java:122) 找到对应 handler；
3. handler 先做参数校验，例如 [`BookCrudToolHandler.validate()`](../../penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/handler/BookCrudToolHandler.java:32)；
4. 然后由 [`DefaultApprovalPolicyEngine.evaluate()`](../../penmate-backend/src/main/java/com/penmate/backend/application/approval/DefaultApprovalPolicyEngine.java:28) 判断这次 tool call 是否需要审批；
5. 如果**不需要审批**，[`ToolCallApplicationService.executeToolCall()`](../../penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/gateway/ToolCallApplicationService.java:119) 直接调用 handler 的 [`execute()`](../../penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/handler/AgentToolHandler.java:14)；
6. 如果**需要审批**，[`ToolCallApplicationService.executeToolCall()`](../../penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/gateway/ToolCallApplicationService.java:82) 会调用 [`ApprovalApplicationService.create()`](../../penmate-backend/src/main/java/com/penmate/backend/application/approval/ApprovalApplicationService.java:48) 创建审批单；
7. 同一处代码还会把当前 tool 调用上下文保存为 [`PendingToolInvocationSnapshot`](../../penmate-backend/src/main/java/com/penmate/backend/domain/agent/model/PendingToolInvocationSnapshot.java)，包括 `toolCode`、`toolArgsJson`、`conversationMessagesJson`、`toolCallId`、`resumeMode` 等恢复所需信息，见 [`ToolCallApplicationService.executeToolCall()`](../../penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/gateway/ToolCallApplicationService.java:96)；
8. 然后任务状态被更新为 `waiting_approval`，见 [`ToolCallApplicationService.executeToolCall()`](../../penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/gateway/ToolCallApplicationService.java:116)，并返回 [`ToolCallResult.waitingApproval()`](../../penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/runtime/ToolCallResult.java:30)；
9. 审批侧如果人工点“通过”，会进入 [`ApprovalApplicationService.approve()`](../../penmate-backend/src/main/java/com/penmate/backend/application/approval/ApprovalApplicationService.java:92)，先把审批单状态更新为 approved，再调用 [`resumeToolInvocationAfterApproved()`](../../penmate-backend/src/main/java/com/penmate/backend/application/approval/ApprovalApplicationService.java:130)；
10. [`resumeToolInvocationAfterApproved()`](../../penmate-backend/src/main/java/com/penmate/backend/application/approval/ApprovalApplicationService.java:130) 会先按 `approvalId` 找回快照，再通过 [`markStatus()`](../../penmate-backend/src/main/java/com/penmate/backend/domain/agent/repository/PendingToolInvocationRepository.java) 把快照从 `pending` 原子 claim 成 `executing`，避免重复恢复；
11. claim 成功后，审批服务把恢复动作交给异步执行器 [`ApprovedToolInvocationAsyncResumer.resumeApprovedInvocation()`](../../penmate-backend/src/main/java/com/penmate/backend/application/approval/ApprovedToolInvocationAsyncResumer.java:46)；
12. 异步执行器会先确认快照仍处于 `executing`，再在需要时把任务从 `waiting_approval` 切回 `running`，见 [`markTaskRunningIfNeeded()`](../../penmate-backend/src/main/java/com/penmate/backend/application/approval/ApprovedToolInvocationAsyncResumer.java:76)；
13. 真正恢复执行时，不是审批服务自己重跑业务，而是交给 [`AgentApprovalResumeCoordinator.resumeApprovedInvocation()`](../../penmate-backend/src/main/java/com/penmate/backend/application/approval/coordination/AgentApprovalResumeCoordinator.java:21) 决定恢复策略：
    - 如果 `resumeMode=RESUME_LOOP`，就调用 [`AgentToolLoopRunner.resumeFromPending()`](../../penmate-backend/src/main/java/com/penmate/backend/application/agent/orchestration/AgentToolLoopRunner.java:115) 恢复原来的 tool loop；
    - 否则就重建 [`ToolCallRequest`](../../penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/runtime/ToolCallRequest.java) 并再次调用 [`ToolCallApplicationService.executeToolCall()`](../../penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/gateway/ToolCallApplicationService.java:51)；
14. 恢复成功后，快照状态会被封口为 `completed`，见 [`ApprovedToolInvocationAsyncResumer.resumeApprovedInvocation()`](../../penmate-backend/src/main/java/com/penmate/backend/application/approval/ApprovedToolInvocationAsyncResumer.java:59)；
15. 如果审批被拒绝，则 [`ApprovalApplicationService.reject()`](../../penmate-backend/src/main/java/com/penmate/backend/application/approval/ApprovalApplicationService.java:111) 会把快照标成 `failed`、发布失败事件，并把 generation task 状态推进到 `failed`，见 [`markTaskFailedAfterRejected()`](../../penmate-backend/src/main/java/com/penmate/backend/application/approval/ApprovalApplicationService.java:145)。

这条链路说明：当前审批并不是“在 tool 方法前弹一个确认框”这么简单，而是一个包含**审批单持久化、待恢复快照、任务挂起、人工审核、异步恢复、失败封口**的完整业务协议。

以下部分不应期待由 LangChain4j 接管：

- [`AgentModelRoutingService.resolveExecutionConfig()`](../../penmate-backend/src/main/java/com/penmate/backend/application/agent/AgentModelRoutingService.java:49) 的项目策略解析、密钥来源选择、密钥解密与执行配置聚合；
- [`DefaultApprovalPolicyEngine.evaluate()`](../../penmate-backend/src/main/java/com/penmate/backend/application/approval/DefaultApprovalPolicyEngine.java:28) 的审批判断；
- [`ToolCallApplicationService.executeToolCall()`](../../penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/gateway/ToolCallApplicationService.java:51) 的审批单创建、待处理快照落库、任务状态切换；
- [`AgentToolLoopRunner.execute()`](../../penmate-backend/src/main/java/com/penmate/backend/application/agent/orchestration/AgentToolLoopRunner.java:43) 的多轮 tool loop、单轮调用上限、审批中断与恢复衔接。

这些都是 PenMate 的业务协议，而不是通用 LLM SDK 的能力边界。

## 6. 是否重复造轮子：逐项判断

### 6.1 先区分两类“路由”

当前代码里的“模型调用策略工厂 / 路由”其实包含两层：

- 业务路由：把项目策略、模型配置、密钥来源解析成一次可执行的 [`AgentLlmExecutionConfig`](../../penmate-backend/src/main/java/com/penmate/backend/application/agent/llm/AgentLlmExecutionConfig.java:8)。这部分由 [`AgentModelRoutingService`](../../penmate-backend/src/main/java/com/penmate/backend/application/agent/AgentModelRoutingService.java:24) 完成；
- Provider 适配路由：拿到 `providerCode` 后，选择具体的 chat client 去调用供应商接口。这部分由 [`ProviderChatClientFactory`](../../penmate-backend/src/main/java/com/penmate/backend/infrastructure/llm/langchain4j/provider/ProviderChatClientFactory.java:8) 和 [`ProviderChatClient`](../../penmate-backend/src/main/java/com/penmate/backend/infrastructure/llm/langchain4j/provider/ProviderChatClient.java:11) 完成。

若不先拆开，就会错误地把业务配置解析和 SDK 客户端分发都称为“重复造轮子”。

### 6.2 业务级模型路由：不是重复造轮子，而是业务必要层

[`AgentModelRoutingService`](../../penmate-backend/src/main/java/com/penmate/backend/application/agent/AgentModelRoutingService.java:49) 做的不是 SDK 意义上的 model builder，而是：

1. 根据 `projectId + modelConfigId` 读取项目策略；
2. 校验策略是否绑定可用模型名；
3. 从用户密钥或官方密钥反推出 provider；
4. 解密 API Key；
5. 选取 baseUrl；
6. 聚合为最终执行配置。

这些动作都强依赖 PenMate 自己的领域对象、存储结构、密钥体系与失败策略。LangChain4j 并不知道什么是项目策略、用户密钥、官方密钥，也不负责从业务库中做授权决策或密钥解密。因此这部分不是造轮子，必须由业务系统自己承担。

### 6.3 ProviderChatClientFactory：只有“轻度重复”，且目前仍可接受

[`ProviderChatClientFactory.get()`](../../penmate-backend/src/main/java/com/penmate/backend/infrastructure/llm/langchain4j/provider/ProviderChatClientFactory.java:17) 本质是一个 `providerCode -> ProviderChatClient` 的分发表。单看这个动作，它与很多 SDK 内部的 provider 选择能力有一定重叠；如果全项目统一采用 LangChain4j 的标准模型实现，这一层甚至可以进一步收薄。

但在当前代码里，这个工厂并不只是“多余封装”，而是承担了一个现实职责：

- 有的 provider 走 LangChain4j 标准模型，如 [`AbstractOpenAiCompatibleProviderChatClient`](../../penmate-backend/src/main/java/com/penmate/backend/infrastructure/llm/langchain4j/provider/AbstractOpenAiCompatibleProviderChatClient.java:11)；
- 有的 provider 为了 tool-calling / 报文控制走原生 HTTP，如 [`NativeOpenAiStyleHttpProviderChatClient`](../../penmate-backend/src/main/java/com/penmate/backend/infrastructure/llm/langchain4j/provider/NativeOpenAiStyleHttpProviderChatClient.java:27)。

因此，这个 factory 当前不是严重重复，而是一个“混合适配层”。真正可优化的不是是否保留工厂，而是是否减少 provider 实现风格的分裂。

### 6.4 原生 HTTP provider：这里是最需要警惕的重复建设点

[`NativeOpenAiStyleHttpProviderChatClient`](../../penmate-backend/src/main/java/com/penmate/backend/infrastructure/llm/langchain4j/provider/NativeOpenAiStyleHttpProviderChatClient.java:27) 已经自己实现了：

- 请求体拼装，见 [`buildTurnRequestBody()`](../../penmate-backend/src/main/java/com/penmate/backend/infrastructure/llm/langchain4j/provider/NativeOpenAiStyleHttpProviderChatClient.java:127)；
- `tools` 数组拼装，见 [`buildTurnRequestBody()`](../../penmate-backend/src/main/java/com/penmate/backend/infrastructure/llm/langchain4j/provider/NativeOpenAiStyleHttpProviderChatClient.java:132)；
- `tool_choice` 注入，见 [`buildTurnRequestBody()`](../../penmate-backend/src/main/java/com/penmate/backend/infrastructure/llm/langchain4j/provider/NativeOpenAiStyleHttpProviderChatClient.java:146)；
- 响应中 `tool_calls` 提取，见 [`extractTurnResponse()`](../../penmate-backend/src/main/java/com/penmate/backend/infrastructure/llm/langchain4j/provider/NativeOpenAiStyleHttpProviderChatClient.java:175)；
- `finish_reason` 解析，见 [`extractTurnResponse()`](../../penmate-backend/src/main/java/com/penmate/backend/infrastructure/llm/langchain4j/provider/NativeOpenAiStyleHttpProviderChatClient.java:197)；
- 异常映射，见 [`generateTurn()`](../../penmate-backend/src/main/java/com/penmate/backend/infrastructure/llm/langchain4j/provider/NativeOpenAiStyleHttpProviderChatClient.java:68)。

这部分能力与 LangChain4j 的 tool-calling / chat model 适配已经明显接近。也就是说，真正最像“重复造轮子”的，不是业务路由，而是这块原生 HTTP 协议层。

当前之所以还能成立，前提是：团队需要精确控制 OpenAI 风格报文，或某些 provider 在 LangChain4j 支持度上不足。若这个前提减弱，优先回收的应是这层原生 HTTP 代码，而不是上层业务编排。

## 7. 关于 tool 实现与注册方式的统一结论

### 7.1 是否需要把 tool 注册再抽一层

短结论：可以抽，但暂时不必为了抽象而抽象。

理由：

- 当前 [`StaticAgentToolCatalog`](../../penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/catalog/StaticAgentToolCatalog.java:26) 同时承载元数据注册与给 LLM 的 schema 输出；
- 在 tool 数量很少时，这种集中式静态目录可读性高；
- 当前还存在“已登记但未暴露”的分叉案例：[`book_crud`](../../penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/catalog/StaticAgentToolCatalog.java:28) 已进入元数据目录，但未进入 [`toLlmToolSchemas()`](../../penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/catalog/StaticAgentToolCatalog.java:42)；
- 真正的痛点不是“没有抽象类”，而是 tool 元数据、schema、handler 之间还未形成单一真源。

因此更值得做的抽象不是再包一层 registry interface，而是未来把以下三项对齐成同一套定义：

- `toolCode` 与显示名；
- JSON Schema；
- 审批 / 风险元数据。

也就是说，下一步应该追求“单一声明源”，而不是优先追求“再多一个抽象层”。

### 7.2 当前 agent tool 应如何实现

短结论：继续采用“catalog + handler + gateway/approval orchestration”三段式，而不是直接暴露注解方法。

推荐实现规范：

1. 先定义 tool 的稳定 `toolCode`、输入 schema、风险等级与审批策略；
2. 再实现 [`AgentToolHandler`](../../penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/handler/AgentToolHandler.java:6)，把参数校验放在 [`validate()`](../../penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/handler/AgentToolHandler.java:10)，把副作用执行放在 [`execute()`](../../penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/handler/AgentToolHandler.java:14)；
3. 让 [`ToolCallApplicationService`](../../penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/gateway/ToolCallApplicationService.java:51) 统一接管审批、挂起、恢复前的治理逻辑；
4. 只让 [`AgentToolLoopRunner`](../../penmate-backend/src/main/java/com/penmate/backend/application/agent/orchestration/AgentToolLoopRunner.java:77) 负责 loop 内调用，不让 handler 自己感知审批状态机。

这种方式的关键收益是：tool 的业务逻辑与 tool 的运行治理分离，后续更容易调整审批机制或恢复机制，而不必逐个改 handler。

### 7.3 LangChain4j 在当前系统中最合适的职责

LangChain4j 不适合承担的职责包括：

- 项目级模型路由；
- 用户 / 官方密钥解析与解密；
- tool 审批决策；
- 待审批 tool 调用持久化；
- 审批后的异步恢复与任务状态流转。

因此，当前系统应把 LangChain4j 约束在“模型协议适配”边界内，而把 Agent 业务编排保留在 PenMate 应用层。

## 8. 建议的后续演进路径

### 8.1 方案 A：保留现状边界，仅收敛元数据真源

适用条件：tool 数量仍少，审批与恢复流程还在频繁变化。

做法：

- 保留 [`AgentToolHandler`](../../penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/handler/AgentToolHandler.java:6)；
- 保留 [`ToolCallApplicationService`](../../penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/gateway/ToolCallApplicationService.java:25)；
- 重构 [`StaticAgentToolCatalog`](../../penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/catalog/StaticAgentToolCatalog.java:12)，使 schema / 风险 / 展示名同点声明。

### 8.2 方案 B：继续保留业务 tool 编排，但尽量回归 LangChain4j 的 provider 能力

适用条件：团队确认主流 provider 已被 LangChain4j 足够覆盖。

做法：

- 缩减 [`NativeOpenAiStyleHttpProviderChatClient`](../../penmate-backend/src/main/java/com/penmate/backend/infrastructure/llm/langchain4j/provider/NativeOpenAiStyleHttpProviderChatClient.java:27) 的使用范围；
- 尽量统一到 LangChain4j 标准 `ChatLanguageModel` / provider builder；
- 让 [`ProviderChatClientFactory`](../../penmate-backend/src/main/java/com/penmate/backend/infrastructure/llm/langchain4j/provider/ProviderChatClientFactory.java:17) 只负责薄分发。

### 8.3 方案 C：仅把低风险只读工具试点迁移到注解式 tool

适用条件：希望验证 LangChain4j 注解工具的开发体验，但不影响审批主链路。

做法：

- 只选择无审批、无挂起、无持久化副作用工具；
- 不改造 [`ToolCallApplicationService`](../../penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/gateway/ToolCallApplicationService.java:25) 承担的核心治理能力；
- 用 PoC 验证收益，再决定是否扩大范围。

## 9. 最终结论

统一结论如下：

1. Agent tool 的业务执行、审批、挂起、恢复，应该继续由 PenMate 自己掌控，不应下沉给 LangChain4j；
2. 当前审批的直白定义是：**tool 先风险判定，命中后先挂起并生成审批单，人工通过后再异步恢复原调用 / 原 loop**，而不是同步直接执行；
3. 多 operation 分发的直白定义是：**一个 `toolCode` 下再用 `operation` 做二级路由，由同一个 handler 分发到多个具体动作**，它不是并行执行，也不是多个 handler 广播；
4. tool 注册现在可以先不继续抽象出更多层，而应先解决 schema、元数据、审批策略的单一真源问题；
5. LangChain4j 最适合承担的是 LLM SDK / provider 适配职责，而不是业务编排职责；
6. 若问当前哪里最像“重复造轮子”，答案不是 [`AgentModelRoutingService`](../../penmate-backend/src/main/java/com/penmate/backend/application/agent/AgentModelRoutingService.java:24)，而是 [`NativeOpenAiStyleHttpProviderChatClient`](../../penmate-backend/src/main/java/com/penmate/backend/infrastructure/llm/langchain4j/provider/NativeOpenAiStyleHttpProviderChatClient.java:27) 这类原生 HTTP tool-calling 适配代码；
7. 因此后续优化优先级应是“收敛协议适配层的重复实现”，而不是“削弱业务编排层”。

结论：当前后端真正不属于重复造轮子的，是业务级模型路由；存在一定重复建设倾向但尚属合理折中的，是 provider client 工厂与部分原生 HTTP provider 实现；若后续继续扩大原生 HTTP 分支，而不复用 LangChain4j 的标准能力，这一块才会逐步演变为明显的重复造轮子。

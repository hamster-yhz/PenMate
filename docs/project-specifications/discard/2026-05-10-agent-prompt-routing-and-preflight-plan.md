# Agent Prompt Routing And Preflight Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use [executing-plans] mode to implement this plan task-by-task.

**Goal:** 将 PenMate 后端 agent 改造成“Markdown 目录装配系统提示词 + 前置行为判定 agent + 故事圣经路由预留”的双阶段编排架构。

**Architecture:** 当前 [`AgentGenerationWorkflow`](penmate-backend/src/main/java/com/penmate/backend/application/agent/orchestration/AgentGenerationWorkflow.java:25) 直接通过 [`AgentPromptAssembler.buildInitialMessages()`](penmate-backend/src/main/java/com/penmate/backend/application/agent/orchestration/AgentPromptAssembler.java:18) 把风格与用户指令拼成单阶段消息，再交给 [`AgentToolLoopRunner.execute()`](penmate-backend/src/main/java/com/penmate/backend/application/agent/orchestration/AgentToolLoopRunner.java:48)。本方案将流程拆为 preflight 与 execution 两段：preflight 负责行为判定、系统提示词 profile 选择、上下文路由决策；execution 负责按决策装配 `system/user/tool` 消息并继续现有 ReAct/tool-calling 主循环。故事圣经本体本期不实现，仅提供上下文 provider 接口、空实现与快照字段预留，避免未来继续硬编码进工作流。

**Tech Stack:** Java 17, Spring Boot, JUnit 5, Mockito, Flyway, OpenAI-style chat completions, Markdown prompt assets

---

## 1. 现状与改造动机

### 1.1 当前编排链路

1. [`AgentTurnAppService.createTurn()`](penmate-backend/src/main/java/com/penmate/backend/application/agent/usecase/AgentTurnAppService.java:44) 创建用户消息、生成任务、任务上下文。
2. [`AgentGenerationWorkflowDispatcher.dispatchInitialRun()`](penmate-backend/src/main/java/com/penmate/backend/application/agent/orchestration/AgentGenerationWorkflowDispatcher.java:21) 异步触发工作流。
3. [`AgentGenerationWorkflow.runInternal()`](penmate-backend/src/main/java/com/penmate/backend/application/agent/orchestration/AgentGenerationWorkflow.java:48) 负责任务状态推进、模型配置解析、prompt 装配、tool loop、结果发布。
4. [`AgentPromptAssembler.buildInitialMessages()`](penmate-backend/src/main/java/com/penmate/backend/application/agent/orchestration/AgentPromptAssembler.java:18) 当前仅构造一条 `user` 消息，把风格快照、RAG 片段和用户指令拼接在一起。
5. [`AgentToolLoopRunner.execute()`](penmate-backend/src/main/java/com/penmate/backend/application/agent/orchestration/AgentToolLoopRunner.java:48) 基于 [`AgentLlmGateway.generateTurn()`](penmate-backend/src/main/java/com/penmate/backend/application/agent/llm/AgentLlmGateway.java:18) 执行 ReAct/tool-calling 主循环。
6. [`NativeOpenAiStyleHttpProviderChatClient.buildTurnRequestBody()`](penmate-backend/src/main/java/com/penmate/backend/infrastructure/llm/langchain4j/provider/NativeOpenAiStyleHttpProviderChatClient.java:134) 会原样透传上游 `messages`，因此天然支持新增 `role=system` 消息，无需改 provider。

### 1.2 当前问题

- 系统提示词没有独立资产层，也没有真正的 `system` role。
- 提示词内容无法按目录、顺序、阶段、任务类型拆分管理。
- 只有执行 agent，没有“先判断怎么做，再决定装哪些上下文”的前置 agent。
- [`AgentTaskContext`](penmate-backend/src/main/java/com/penmate/backend/domain/agent/model/AgentTaskContext.java:30) 缺少 preflight 决策和 prompt bundle 快照字段。
- 故事圣经尚未落地，但如果现在不定义路由接口，后续大概率会再次耦合进 [`AgentGenerationWorkflow`](penmate-backend/src/main/java/com/penmate/backend/application/agent/orchestration/AgentGenerationWorkflow.java:25)。

---

## 2. 目标架构

### 2.1 总体架构图

```text
AgentTurnAppService
  -> AgentGenerationWorkflowDispatcher
    -> AgentGenerationWorkflow
      -> AgentPreflightCoordinator
        -> SystemPromptProvider(preflight)
        -> PreflightDecisionAgent
      -> AgentContextRoutingFacade
        -> Style context
        -> Rag context
        -> StoryBible context (stub)
      -> SystemPromptProvider(execution)
      -> AgentPromptAssembler
      -> AgentToolLoopRunner
      -> AgentResultPublisher / AgentTaskResultRecorder
```

### 2.2 双 agent 职责边界

#### Preflight agent

职责：
- 识别用户本轮行为意图。
- 决定 execution 阶段使用哪个 prompt profile。
- 决定是否需要装配 style / RAG / story bible 上下文。
- 只输出结构化决策结果。

不负责：
- 不直接生成用户最终可见正文。
- 不参与工具调用循环。
- 不推进审批流。

#### Execution agent

职责：
- 接收 preflight 决策结果。
- 装配 `system/user/tool-context` 消息。
- 继续复用 [`AgentToolLoopRunner`](penmate-backend/src/main/java/com/penmate/backend/application/agent/orchestration/AgentToolLoopRunner.java:37) 完成 ReAct/tool-calling。
- 生成最终 assistant 输出。

不负责：
- 不做高层行为判定。
- 不决定是否启用故事圣经路由。

### 2.3 故事圣经预留原则

本期仅实现：
- provider 接口；
- 空实现；
- 路由标志；
- 上下文结果对象；
- 持久化快照字段预留。

本期不实现：
- 故事圣经存储模型；
- 故事圣经检索逻辑；
- 前端故事圣经管理界面。

---

## 3. 模块划分与具体改造点

### 3.1 Prompt 资产与装配模块

**Files:**
- Create: [`penmate-backend/src/main/resources/prompts/agent/system/preflight/default/00-base-role.md`](penmate-backend/src/main/resources/prompts/agent/system/preflight/default/00-base-role.md)
- Create: [`penmate-backend/src/main/resources/prompts/agent/system/preflight/default/10-routing-rules.md`](penmate-backend/src/main/resources/prompts/agent/system/preflight/default/10-routing-rules.md)
- Create: [`penmate-backend/src/main/resources/prompts/agent/system/preflight/default/20-output-contract.md`](penmate-backend/src/main/resources/prompts/agent/system/preflight/default/20-output-contract.md)
- Create: [`penmate-backend/src/main/resources/prompts/agent/system/execution/default/00-base-role.md`](penmate-backend/src/main/resources/prompts/agent/system/execution/default/00-base-role.md)
- Create: [`penmate-backend/src/main/resources/prompts/agent/system/execution/default/10-writing-rules.md`](penmate-backend/src/main/resources/prompts/agent/system/execution/default/10-writing-rules.md)
- Create: [`penmate-backend/src/main/resources/prompts/agent/system/execution/default/20-tool-use-policy.md`](penmate-backend/src/main/resources/prompts/agent/system/execution/default/20-tool-use-policy.md)
- Create: [`penmate-backend/src/main/resources/prompts/agent/system/execution/world-build/00-base-role.md`](penmate-backend/src/main/resources/prompts/agent/system/execution/world-build/00-base-role.md)
- Create: [`penmate-backend/src/main/resources/prompts/agent/system/execution/rewrite/00-base-role.md`](penmate-backend/src/main/resources/prompts/agent/system/execution/rewrite/00-base-role.md)
- Create: [`penmate-backend/src/main/java/com/penmate/backend/application/agent/prompt/SystemPromptDocument.java`](penmate-backend/src/main/java/com/penmate/backend/application/agent/prompt/SystemPromptDocument.java)
- Create: [`penmate-backend/src/main/java/com/penmate/backend/application/agent/prompt/SystemPromptBundle.java`](penmate-backend/src/main/java/com/penmate/backend/application/agent/prompt/SystemPromptBundle.java)
- Create: [`penmate-backend/src/main/java/com/penmate/backend/application/agent/prompt/SystemPromptProvider.java`](penmate-backend/src/main/java/com/penmate/backend/application/agent/prompt/SystemPromptProvider.java)
- Create: [`penmate-backend/src/main/java/com/penmate/backend/infrastructure/agent/prompt/ClasspathMarkdownSystemPromptProvider.java`](penmate-backend/src/main/java/com/penmate/backend/infrastructure/agent/prompt/ClasspathMarkdownSystemPromptProvider.java)
- Modify: [`penmate-backend/src/main/java/com/penmate/backend/application/agent/orchestration/AgentPromptAssembler.java`](penmate-backend/src/main/java/com/penmate/backend/application/agent/orchestration/AgentPromptAssembler.java:16)

**改造目标：**
- 将“系统提示词”从 Java 字符串拼装迁移到 classpath Markdown 目录。
- 允许按 `stage + profile` 读取文档集合。
- 按文件名字典序装配，例如 `00-`, `10-`, `20-`。
- 由 [`AgentPromptAssembler`](penmate-backend/src/main/java/com/penmate/backend/application/agent/orchestration/AgentPromptAssembler.java:16) 输出真正的 `role=system` 消息，而不再把约束内联进单一 `user` 消息。

**建议接口：**

```java
public interface SystemPromptProvider {

    SystemPromptBundle loadBundle(String stage, String profile);
}
```

```java
public record SystemPromptBundle(
        String stage,
        String profile,
        List<SystemPromptDocument> documents,
        String assembledPrompt
) {
}
```

### 3.2 Preflight 决策模块

**Files:**
- Create: [`penmate-backend/src/main/java/com/penmate/backend/application/agent/orchestration/preflight/AgentBehaviorType.java`](penmate-backend/src/main/java/com/penmate/backend/application/agent/orchestration/preflight/AgentBehaviorType.java)
- Create: [`penmate-backend/src/main/java/com/penmate/backend/application/agent/orchestration/preflight/AgentPreflightRequest.java`](penmate-backend/src/main/java/com/penmate/backend/application/agent/orchestration/preflight/AgentPreflightRequest.java)
- Create: [`penmate-backend/src/main/java/com/penmate/backend/application/agent/orchestration/preflight/AgentPreflightDecision.java`](penmate-backend/src/main/java/com/penmate/backend/application/agent/orchestration/preflight/AgentPreflightDecision.java)
- Create: [`penmate-backend/src/main/java/com/penmate/backend/application/agent/orchestration/preflight/AgentPreflightCoordinator.java`](penmate-backend/src/main/java/com/penmate/backend/application/agent/orchestration/preflight/AgentPreflightCoordinator.java)
- Create: [`penmate-backend/src/main/java/com/penmate/backend/application/agent/orchestration/preflight/DefaultAgentPreflightCoordinator.java`](penmate-backend/src/main/java/com/penmate/backend/application/agent/orchestration/preflight/DefaultAgentPreflightCoordinator.java)

**改造目标：**
- 从执行主流程中剥离“行为判断”和“上下文路由决策”。
- 用结构化对象而不是散文 prompt 说明来表达 preflight 结果。
- 第一版输出 JSON 合同，应用层解析后得到 [`AgentPreflightDecision`](penmate-backend/src/main/java/com/penmate/backend/application/agent/orchestration/preflight/AgentPreflightDecision.java)。

**建议决策对象字段：**

```java
public record AgentPreflightDecision(
        AgentBehaviorType behaviorType,
        String executionPromptProfile,
        boolean includeStyleContext,
        boolean includeRagContext,
        boolean includeStoryBibleContext,
        String reasoningSummary,
        String decisionTraceJson
) {
}
```

**行为类型建议：**
- `WRITE`
- `REWRITE`
- `WORLD_BUILD`
- `QUESTION_ANSWER`
- `STORY_BIBLE_QUERY_CANDIDATE`

### 3.3 上下文路由模块

**Files:**
- Create: [`penmate-backend/src/main/java/com/penmate/backend/application/agent/context/AgentContextRoutingRequest.java`](penmate-backend/src/main/java/com/penmate/backend/application/agent/context/AgentContextRoutingRequest.java)
- Create: [`penmate-backend/src/main/java/com/penmate/backend/application/agent/context/AgentContextRoutingResult.java`](penmate-backend/src/main/java/com/penmate/backend/application/agent/context/AgentContextRoutingResult.java)
- Create: [`penmate-backend/src/main/java/com/penmate/backend/application/agent/context/AgentContextRoutingFacade.java`](penmate-backend/src/main/java/com/penmate/backend/application/agent/context/AgentContextRoutingFacade.java)
- Create: [`penmate-backend/src/main/java/com/penmate/backend/application/agent/context/DefaultAgentContextRoutingFacade.java`](penmate-backend/src/main/java/com/penmate/backend/application/agent/context/DefaultAgentContextRoutingFacade.java)
- Create: [`penmate-backend/src/main/java/com/penmate/backend/application/agent/context/StoryBibleContextProvider.java`](penmate-backend/src/main/java/com/penmate/backend/application/agent/context/StoryBibleContextProvider.java)
- Create: [`penmate-backend/src/main/java/com/penmate/backend/application/agent/context/StoryBibleContextResult.java`](penmate-backend/src/main/java/com/penmate/backend/application/agent/context/StoryBibleContextResult.java)
- Create: [`penmate-backend/src/main/java/com/penmate/backend/application/agent/context/NoopStoryBibleContextProvider.java`](penmate-backend/src/main/java/com/penmate/backend/application/agent/context/NoopStoryBibleContextProvider.java)

**改造目标：**
- 将 style、RAG、story bible 三类上下文装配解耦。
- preflight 只给出是否需要，routing facade 才真正执行装配。
- 故事圣经采用空 provider 返回 `enabled=false / source="noop" / content=""`。

**建议接口：**

```java
public interface StoryBibleContextProvider {

    StoryBibleContextResult loadContext(Long projectId,
                                        Long conversationId,
                                        Long chapterId,
                                        String userMessage,
                                        AgentPreflightDecision decision);
}
```

### 3.4 工作流编排模块

**Files:**
- Modify: [`penmate-backend/src/main/java/com/penmate/backend/application/agent/orchestration/AgentGenerationWorkflow.java`](penmate-backend/src/main/java/com/penmate/backend/application/agent/orchestration/AgentGenerationWorkflow.java:25)
- Modify: [`penmate-backend/src/test/java/com/penmate/backend/application/agent/orchestration/AgentGenerationWorkflowTest.java`](penmate-backend/src/test/java/com/penmate/backend/application/agent/orchestration/AgentGenerationWorkflowTest.java:34)

**改造目标：**
- 在 [`AgentGenerationWorkflow.runInternal()`](penmate-backend/src/main/java/com/penmate/backend/application/agent/orchestration/AgentGenerationWorkflow.java:48) 中插入 preflight 与 routing 两个阶段。
- 将原来的 [`AgentPromptAssembler.buildInitialMessages()`](penmate-backend/src/main/java/com/penmate/backend/application/agent/orchestration/AgentPromptAssembler.java:18) 替换为 execution messages 装配。
- 保持 [`AgentToolLoopRunner.execute()`](penmate-backend/src/main/java/com/penmate/backend/application/agent/orchestration/AgentToolLoopRunner.java:48) 不承担业务决策，只消费最终消息列表。

**建议新流程：**
1. 读取 task。
2. 构建 taskContext。
3. 解析执行模型配置。
4. 调用 preflight coordinator。
5. 调用 context routing facade。
6. 加载 execution prompt bundle。
7. 调用 prompt assembler 生成 messages。
8. 交给 tool loop。
9. 回写输出与状态。

---

## 4. 数据流设计

### 4.1 输入源

来自 [`AgentTurnAppService.createTaskContext()`](penmate-backend/src/main/java/com/penmate/backend/application/agent/usecase/AgentTurnAppService.java:193) 的已有字段：
- `userMessage`
- `chapterId`
- `selectedText`
- `styleSnapshotJson`
- `modelSnapshotJson`
- `contextHash`

### 4.2 新数据流

```text
AgentGenerationTask + AgentTaskContext
  -> PreflightRequest
  -> SystemPromptProvider(preflight/default)
  -> AgentPreflightDecision
  -> AgentContextRoutingRequest
  -> style / rag / story-bible providers
  -> AgentContextRoutingResult
  -> SystemPromptProvider(execution/{profile})
  -> AgentPromptAssembler.buildExecutionMessages()
  -> AgentToolLoopRunner.execute()
  -> finalAssistantText
```

### 4.3 持久化建议

**Files:**
- Create: [`penmate-backend/src/main/resources/db/migration/V13__extend_agent_task_context_for_preflight_and_prompt_bundle.sql`](penmate-backend/src/main/resources/db/migration/V13__extend_agent_task_context_for_preflight_and_prompt_bundle.sql)
- Modify: [`penmate-backend/src/main/java/com/penmate/backend/domain/agent/model/AgentTaskContext.java`](penmate-backend/src/main/java/com/penmate/backend/domain/agent/model/AgentTaskContext.java:18)
- Modify: [`penmate-backend/src/main/java/com/penmate/backend/infrastructure/persistence/agent/AgentSessionMapper.java`](penmate-backend/src/main/java/com/penmate/backend/infrastructure/persistence/agent/AgentSessionMapper.java:243)
- Modify: [`penmate-backend/src/main/java/com/penmate/backend/infrastructure/persistence/agent/AgentSessionRepositoryImpl.java`](penmate-backend/src/main/java/com/penmate/backend/infrastructure/persistence/agent/AgentSessionRepositoryImpl.java:184)

**新增字段：**
- `preflight_decision_json`
- `story_bible_snapshot_json`
- `prompt_bundle_json`

用途：
- 支持 session recovery 与审计。
- 为故事圣经上线前后的行为差异比对保留证据。
- 让 execution 恢复时可重建 prompt 来源，而不是依赖瞬时内存对象。

---

## 5. 测试策略

### 5.1 单元测试层次

#### A. Prompt provider 测试

**Files:**
- Create: [`penmate-backend/src/test/java/com/penmate/backend/infrastructure/agent/prompt/ClasspathMarkdownSystemPromptProviderTest.java`](penmate-backend/src/test/java/com/penmate/backend/infrastructure/agent/prompt/ClasspathMarkdownSystemPromptProviderTest.java)

覆盖点：
- 按文件名字典序读取 Markdown。
- `stage/profile` 目录选择正确。
- 缺失目录时快速失败。
- 拼接文本包含文档来源顺序。

#### B. Preflight coordinator 测试

**Files:**
- Create: [`penmate-backend/src/test/java/com/penmate/backend/application/agent/orchestration/preflight/DefaultAgentPreflightCoordinatorTest.java`](penmate-backend/src/test/java/com/penmate/backend/application/agent/orchestration/preflight/DefaultAgentPreflightCoordinatorTest.java)

覆盖点：
- 能把 LLM JSON 输出解析为 [`AgentPreflightDecision`](penmate-backend/src/main/java/com/penmate/backend/application/agent/orchestration/preflight/AgentPreflightDecision.java)。
- 字段缺失时抛出异常。
- `WORLD_BUILD` 能映射到 `world-build` prompt profile。
- `STORY_BIBLE_QUERY_CANDIDATE` 能开启 story bible 路由标志。

#### C. Context routing 测试

**Files:**
- Create: [`penmate-backend/src/test/java/com/penmate/backend/application/agent/context/DefaultAgentContextRoutingFacadeTest.java`](penmate-backend/src/test/java/com/penmate/backend/application/agent/context/DefaultAgentContextRoutingFacadeTest.java)

覆盖点：
- `includeStyleContext=true` 时装配风格快照。
- `includeStoryBibleContext=false` 时不调用 provider。
- 使用 [`NoopStoryBibleContextProvider`](penmate-backend/src/main/java/com/penmate/backend/application/agent/context/NoopStoryBibleContextProvider.java) 时结果为空但结构稳定。

#### D. Prompt assembler 测试

**Files:**
- Create: [`penmate-backend/src/test/java/com/penmate/backend/application/agent/orchestration/AgentPromptAssemblerTest.java`](penmate-backend/src/test/java/com/penmate/backend/application/agent/orchestration/AgentPromptAssemblerTest.java)

覆盖点：
- 第一条消息必须是 `role=system`。
- 第二条消息必须保留原始用户输入。
- style / RAG / story bible 内容出现于约定段落中。
- 无 story bible 时不输出空标题段。

#### E. Workflow 集成式单元测试

**Files:**
- Modify: [`penmate-backend/src/test/java/com/penmate/backend/application/agent/orchestration/AgentGenerationWorkflowTest.java`](penmate-backend/src/test/java/com/penmate/backend/application/agent/orchestration/AgentGenerationWorkflowTest.java:34)

新增用例：
- preflight 成功后再调用 tool loop。
- preflight 指定 `rewrite` profile 时应加载对应 execution prompt bundle。
- preflight 开启 story bible 路由时应调用 story bible provider，但空实现不应导致失败。
- preflight 失败时任务应转 `FAILED`。

### 5.2 回归测试命令

**Backend only:**
- Run: [`mvn -Dtest=ClasspathMarkdownSystemPromptProviderTest,DefaultAgentPreflightCoordinatorTest,DefaultAgentContextRoutingFacadeTest,AgentPromptAssemblerTest,AgentGenerationWorkflowTest test`](penmate-backend/pom.xml)

**Full backend verification:**
- Run: [`mvn test`](penmate-backend/pom.xml)

期望：
- 新增测试全绿。
- 现有 [`AgentGenerationWorkflowTest`](penmate-backend/src/test/java/com/penmate/backend/application/agent/orchestration/AgentGenerationWorkflowTest.java:34) 通过少量重构继续通过。

---

## 6. 分阶段实施步骤

### Task 1: 建立 Markdown prompt 资产读取能力

Use [test-driven-development] mode for this task.

**Files:**
- Create: [`penmate-backend/src/test/java/com/penmate/backend/infrastructure/agent/prompt/ClasspathMarkdownSystemPromptProviderTest.java`](penmate-backend/src/test/java/com/penmate/backend/infrastructure/agent/prompt/ClasspathMarkdownSystemPromptProviderTest.java)
- Create: [`penmate-backend/src/main/java/com/penmate/backend/application/agent/prompt/SystemPromptDocument.java`](penmate-backend/src/main/java/com/penmate/backend/application/agent/prompt/SystemPromptDocument.java)
- Create: [`penmate-backend/src/main/java/com/penmate/backend/application/agent/prompt/SystemPromptBundle.java`](penmate-backend/src/main/java/com/penmate/backend/application/agent/prompt/SystemPromptBundle.java)
- Create: [`penmate-backend/src/main/java/com/penmate/backend/application/agent/prompt/SystemPromptProvider.java`](penmate-backend/src/main/java/com/penmate/backend/application/agent/prompt/SystemPromptProvider.java)
- Create: [`penmate-backend/src/main/java/com/penmate/backend/infrastructure/agent/prompt/ClasspathMarkdownSystemPromptProvider.java`](penmate-backend/src/main/java/com/penmate/backend/infrastructure/agent/prompt/ClasspathMarkdownSystemPromptProvider.java)
- Create: [`penmate-backend/src/main/resources/prompts/agent/system/preflight/default/00-base-role.md`](penmate-backend/src/main/resources/prompts/agent/system/preflight/default/00-base-role.md)
- Create: [`penmate-backend/src/main/resources/prompts/agent/system/preflight/default/10-routing-rules.md`](penmate-backend/src/main/resources/prompts/agent/system/preflight/default/10-routing-rules.md)
- Create: [`penmate-backend/src/main/resources/prompts/agent/system/preflight/default/20-output-contract.md`](penmate-backend/src/main/resources/prompts/agent/system/preflight/default/20-output-contract.md)
- Create: [`penmate-backend/src/main/resources/prompts/agent/system/execution/default/00-base-role.md`](penmate-backend/src/main/resources/prompts/agent/system/execution/default/00-base-role.md)
- Create: [`penmate-backend/src/main/resources/prompts/agent/system/execution/default/10-writing-rules.md`](penmate-backend/src/main/resources/prompts/agent/system/execution/default/10-writing-rules.md)
- Create: [`penmate-backend/src/main/resources/prompts/agent/system/execution/default/20-tool-use-policy.md`](penmate-backend/src/main/resources/prompts/agent/system/execution/default/20-tool-use-policy.md)

**Step 1: Write the failing test**
编写 [`ClasspathMarkdownSystemPromptProviderTest`](penmate-backend/src/test/java/com/penmate/backend/infrastructure/agent/prompt/ClasspathMarkdownSystemPromptProviderTest.java)，断言：
- `loadBundle("preflight", "default")` 成功；
- 文档顺序是 `00 -> 10 -> 20`；
- 输出文本含“前置路由决策代理”；
- 缺失目录时抛异常。

**Step 2: Run test to verify it fails**
Run: `mvn -Dtest=ClasspathMarkdownSystemPromptProviderTest test`
Expected: 编译失败或测试失败，因为 provider 与 prompt 文件尚不存在。

**Step
# PenMate Agent Tool 与 LangChain4j 职责边界分析文档实施方案

> **For Claude:** REQUIRED SUB-SKILL: Use [executing-plans] mode to implement this plan task-by-task.

**Goal:** 基于当前后端实现，产出一份统一文档，明确 Agent tool、tool 注册、审批、模型路由/策略工厂与 LangChain4j 的职责边界，并判断当前实现中哪些部分存在重复造轮子、哪些部分属于必要的业务编排。

**Architecture:** 文档以“现状代码证据 → LangChain4j 可承担职责 → 当前系统必须自担职责 → 重叠度判定 → 演进建议”五段式展开。分析同时区分两类路由：一类是业务级模型配置解析与密钥解析，另一类是 SDK/Provider 适配层分发，避免把二者混为一个“工厂”。

**Tech Stack:** Java, Spring Boot, LangChain4j, OpenAI-compatible HTTP API, Markdown

---

### Task 1: 固化分析范围与代码证据目录

**Files:**
- Create: `docs/plans/2026-05-03-langchain4j-boundary-analysis-plan.md`
- Create: `docs/analysis/2026-05-03-agent-tool-langchain4j-boundary.md`
- Modify: `docs/plans/2026-05-03-langchain4j-boundary-analysis-plan.md:1-999`

**Step 1: 写出文档的分析对象列表**
把下列类写入目标文档的“分析对象”章节，作为后续所有结论的证据来源：

- [`AgentLlmGateway`](penmate-backend/src/main/java/com/penmate/backend/application/agent/llm/AgentLlmGateway.java:13)
- [`LangChain4jAgentLlmGateway`](penmate-backend/src/main/java/com/penmate/backend/infrastructure/llm/langchain4j/LangChain4jAgentLlmGateway.java:24)
- [`ProviderChatClientFactory`](penmate-backend/src/main/java/com/penmate/backend/infrastructure/llm/langchain4j/provider/ProviderChatClientFactory.java:8)
- [`ProviderChatClient`](penmate-backend/src/main/java/com/penmate/backend/infrastructure/llm/langchain4j/provider/ProviderChatClient.java:11)
- [`NativeOpenAiStyleHttpProviderChatClient`](penmate-backend/src/main/java/com/penmate/backend/infrastructure/llm/langchain4j/provider/NativeOpenAiStyleHttpProviderChatClient.java:27)
- [`AbstractOpenAiCompatibleProviderChatClient`](penmate-backend/src/main/java/com/penmate/backend/infrastructure/llm/langchain4j/provider/AbstractOpenAiCompatibleProviderChatClient.java:11)
- [`AgentModelRoutingService`](penmate-backend/src/main/java/com/penmate/backend/application/agent/AgentModelRoutingService.java:24)
- [`StaticAgentToolCatalog`](penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/catalog/StaticAgentToolCatalog.java:12)
- [`AgentToolHandler`](penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/handler/AgentToolHandler.java:6)
- [`BookCrudToolHandler`](penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/handler/BookCrudToolHandler.java:18)
- [`ContextEnhancerToolHandler`](penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/handler/ContextEnhancerToolHandler.java:13)
- [`ToolCallApplicationService`](penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/gateway/ToolCallApplicationService.java:25)
- [`DefaultApprovalPolicyEngine`](penmate-backend/src/main/java/com/penmate/backend/application/approval/DefaultApprovalPolicyEngine.java:19)
- [`AgentToolLoopRunner`](penmate-backend/src/main/java/com/penmate/backend/application/agent/orchestration/AgentToolLoopRunner.java:32)

**Step 2: 在文档中补一段“为什么只看这些类就足够”**
完整文案：

```md
这些类已经覆盖了本次问题的四个关键链路：
1. 模型执行配置如何从业务数据解析出来；
2. 解析后的 provider 如何映射到具体 LLM client；
3. tool schema 如何暴露给模型，以及 tool call 如何回流到业务执行；
4. 审批、挂起、恢复这类业务约束是否应交给 LangChain4j。
因此，判断“是否重复造轮子”无需泛泛讨论概念，而应直接对照这些实际实现。
```

**Step 3: 运行一次最小核对命令，确认文档目录存在**
Run: `if not exist docs\analysis mkdir docs\analysis`
Expected: 命令成功返回；若目录已存在则无副作用。

**Step 4: 生成目标文档空骨架**
将以下完整 Markdown 初稿写入 [`docs/analysis/2026-05-03-agent-tool-langchain4j-boundary.md`](docs/analysis/2026-05-03-agent-tool-langchain4j-boundary.md)：

```md
# Agent Tool 与 LangChain4j 职责边界分析

## 1. 背景与目标

## 2. 当前实现概览

## 3. LangChain4j 能承担什么

## 4. 当前系统必须自己承担什么

## 5. 是否重复造轮子：逐项判断

## 6. 关于 tool 实现与注册方式的统一结论

## 7. 建议的后续演进路径

## 8. 最终结论
```

**Step 5: Commit**
Run:
`git add docs/plans/2026-05-03-langchain4j-boundary-analysis-plan.md docs/analysis/2026-05-03-agent-tool-langchain4j-boundary.md && git commit -m "docs: scaffold langchain4j boundary analysis"`
Expected: 生成仅包含文档脚手架的提交。

### Task 2: 写清楚“模型调用策略工厂/路由工厂”是否重复 LangChain4j

**Files:**
- Modify: `docs/analysis/2026-05-03-agent-tool-langchain4j-boundary.md:1-220`
- Test: `penmate-backend/src/main/java/com/penmate/backend/application/agent/AgentModelRoutingService.java`
- Test: `penmate-backend/src/main/java/com/penmate/backend/infrastructure/llm/langchain4j/provider/ProviderChatClientFactory.java`
- Test: `penmate-backend/src/main/java/com/penmate/backend/infrastructure/llm/langchain4j/LangChain4jAgentLlmGateway.java`

**Step 1: 先写出“必须拆成两类工厂/路由”的结论**
把以下完整内容写入文档：

```md
### 5.1 先区分两类“路由”

当前代码里的“模型调用策略工厂/路由”其实包含两层：

- 业务路由：把项目策略、模型配置、密钥来源解析成一次可执行的 [`AgentLlmExecutionConfig`](penmate-backend/src/main/java/com/penmate/backend/application/agent/llm/AgentLlmExecutionConfig.java:10)。这部分由 [`AgentModelRoutingService`](penmate-backend/src/main/java/com/penmate/backend/application/agent/AgentModelRoutingService.java:24) 完成。
- Provider 适配路由：拿到 providerCode 之后，选择具体的 chat client 去调用供应商接口。这部分由 [`ProviderChatClientFactory`](penmate-backend/src/main/java/com/penmate/backend/infrastructure/llm/langchain4j/provider/ProviderChatClientFactory.java:8) 和 [`ProviderChatClient`](penmate-backend/src/main/java/com/penmate/backend/infrastructure/llm/langchain4j/provider/ProviderChatClient.java:11) 完成。

若不先拆开，就会错误地把业务配置解析和 SDK 客户端分发都称为“重复造轮子”。
```

**Step 2: 写出对业务路由的判断**
把以下完整内容写入文档：

```md
### 5.2 业务级模型路由：不是重复造轮子，而是业务必要层

[`AgentModelRoutingService`](penmate-backend/src/main/java/com/penmate/backend/application/agent/AgentModelRoutingService.java:49) 做的不是 SDK 意义上的 model builder，而是：

1. 根据 `projectId + modelConfigId` 读取项目策略；
2. 校验策略是否绑定可用模型名；
3. 从用户密钥或官方密钥反推出 provider；
4. 解密 API Key；
5. 选取 baseUrl；
6. 聚合为最终执行配置。

这些动作都强依赖 PenMate 自己的领域对象、存储结构、密钥体系与失败策略。LangChain4j 并不知道什么是项目策略、用户密钥、官方密钥，也不负责从业务库中做授权决策或密钥解密。因此这部分不是造轮子，必须由业务系统自己承担。
```

**Step 3: 写出对 provider 工厂的判断**
把以下完整内容写入文档：

```md
### 5.3 ProviderChatClientFactory：只有“轻度重复”，且目前仍可接受

[`ProviderChatClientFactory`](penmate-backend/src/main/java/com/penmate/backend/infrastructure/llm/langchain4j/provider/ProviderChatClientFactory.java:17) 本质是一个 `providerCode -> ProviderChatClient` 的分发表。单看这个动作，它与很多 SDK 内部的 provider 选择能力有一定重叠；如果全项目统一采用 LangChain4j 的标准模型实现，这一层甚至可以进一步收薄。

但在当前代码里，这个工厂并不只是“多余封装”，而是承担了一个现实职责：
- 有的 provider 走 LangChain4j 标准模型，如 [`AbstractOpenAiCompatibleProviderChatClient`](penmate-backend/src/main/java/com/penmate/backend/infrastructure/llm/langchain4j/provider/AbstractOpenAiCompatibleProviderChatClient.java:11)；
- 有的 provider 为了 tool-calling/报文控制走原生 HTTP，如 [`NativeOpenAiStyleHttpProviderChatClient`](penmate-backend/src/main/java/com/penmate/backend/infrastructure/llm/langchain4j/provider/NativeOpenAiStyleHttpProviderChatClient.java:27)。

因此，这个 factory 当前不是严重重复，而是一个“混合适配层”。真正可优化的不是是否保留工厂，而是是否减少 provider 实现风格的分裂。
```

**Step 4: 写出核心结论句**
把以下句子原样写入“最终结论”章节：

```md
结论：当前后端真正不属于重复造轮子的，是业务级模型路由；存在一定重复建设倾向但尚属合理折中的，是 provider client 工厂与部分原生 HTTP provider 实现；若后续继续扩大原生 HTTP 分支，而不复用 LangChain4j 的标准能力，这一块才会逐步演变为明显的重复造轮子。
```

**Step 5: Run test to verify it fails**
Run: `findstr /n /c:"### 5.2 业务级模型路由" docs\analysis\2026-05-03-agent-tool-langchain4j-boundary.md`
Expected: 在写入前查不到目标标题。

**Step 6: Run test to verify it passes**
Run: `findstr /n /c:"### 5.2 业务级模型路由" docs\analysis\2026-05-03-agent-tool-langchain4j-boundary.md`
Expected: 能查到对应标题行号。

**Step 7: Commit**
Run:
`git add docs/analysis/2026-05-03-agent-tool-langchain4j-boundary.md && git commit -m "docs: analyze model routing and provider factory boundary"`
Expected: 生成关于模型路由与工厂边界的提交。

### Task 3: 统一整理 tool 实现、tool 注册、抽象层次与 LangChain4j 注解式 tool 的判断

**Files:**
- Modify: `docs/analysis/2026-05-03-agent-tool-langchain4j-boundary.md:221-420`
- Test: `penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/catalog/StaticAgentToolCatalog.java`
- Test: `penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/handler/AgentToolHandler.java`
- Test: `penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/gateway/ToolCallApplicationService.java`

**Step 1: 写清楚当前 tool 架构的三层**
完整内容：

```md
## 6. 关于 tool 实现与注册方式的统一结论

当前实现已经自然分成三层：

1. tool 暴露层：[`StaticAgentToolCatalog`](penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/catalog/StaticAgentToolCatalog.java:12) 决定哪些 tool schema 暴露给模型；
2. tool 执行层：[`AgentToolHandler`](penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/handler/AgentToolHandler.java:6) 及其实现类负责参数校验与真正执行业务；
3. tool 治理层：[`ToolCallApplicationService`](penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/gateway/ToolCallApplicationService.java:25) 负责审批、挂起、恢复前置、幂等键、状态流转。

这说明当前系统并不是把所有东西都压成一个“tool method”，而是有意识地把“模型可见性”、“业务执行”、“治理编排”拆开了。
```

**Step 2: 写出“是否要把 tool 注册进一步抽类”的判断**
完整内容：

```md
### 6.1 是否需要把 tool 注册再抽一层

短结论：可以抽，但暂时不必为了抽象而抽象。

理由：
- 当前 [`StaticAgentToolCatalog`](penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/catalog/StaticAgentToolCatalog.java:26) 同时承载元数据注册与给 LLM 的 schema 输出；
- 在 tool 数量很少时，这种集中式静态目录可读性高；
- 真正的痛点不是“没有抽象类”，而是 tool 元数据、schema、handler 之间还未形成单一真源。

因此更值得做的抽象不是再包一层 registry interface，而是未来把以下三项对齐成同一套定义：
- toolCode 与显示名；
- JSON Schema；
- 审批/风险元数据。

也就是说，下一步应该追求“单一声明源”，而不是优先追求“再多一个抽象层”。
```

**Step 3: 写出“是否应使用 LangChain4j 注解式 tool”的判断**
完整内容：

```md
### 6.2 是否应切换到 LangChain4j 注解式 tool

短结论：当前阶段不建议把核心 Agent tool 改成 LangChain4j 注解式 `@Tool` 作为主实现。

原因不是注解式 tool 不可用，而是它的默认抽象更适合“让模型直接调用一个普通 Java 方法”，但 PenMate 当前的 tool 调用并不是单纯方法调用，而是一个带有治理状态机的业务过程：

- 调用前要做元数据查找；
- 调用前要做审批策略判断；
- 命中审批后要落库为 [`PendingToolInvocationSnapshot`](penmate-backend/src/main/java/com/penmate/backend/domain/agent/model/PendingToolInvocationSnapshot.java:1)；
- 任务状态要切到 `waiting_approval`；
- 审批后还要恢复 loop。

这些都体现在 [`ToolCallApplicationService`](penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/gateway/ToolCallApplicationService.java:51) 与 [`AgentToolLoopRunner`](penmate-backend/src/main/java/com/penmate/backend/application/agent/orchestration/AgentToolLoopRunner.java:43) 中。LangChain4j 注解式 tool 无法天然表达这套“可挂起、可恢复、可审批”的业务协议。

因此，LangChain4j 注解式 tool 更适合作为轻量无副作用工具，或内部 PoC；对于当前这类需要审批和恢复的核心业务 tool，不应作为主抽象。
```

**Step 4: 写出“LangChain4j 应承担什么职责”的统一判断**
完整内容：

```md
### 6.3 LangChain4j 在当前系统中最合适的职责

LangChain4j 更适合承担三类职责：

1. 标准模型客户端封装：如 OpenAI / Gemini / Claude 的 SDK 适配；
2. 标准消息对象与 tool-calling 协议适配；
3. 少量通用能力复用，例如重试、序列化、provider builder。

LangChain4j 不适合承担的职责包括：
- 项目级模型路由；
- 用户/官方密钥解析与解密；
- tool 审批决策；
- 待审批 tool 调用持久化；
- 审批后的异步恢复与任务状态流转。

换言之，LangChain4j 应位于“LLM 协议适配层”，而不应侵入 PenMate 的业务编排层。
```

**Step 5: Run test to verify it fails**
Run: `findstr /n /c:"### 6.2 是否应切换到 LangChain4j 注解式 tool" docs\analysis\2026-05-03-agent-tool-langchain4j-boundary.md`
Expected: 在写入前查不到目标标题。

**Step 6: Run test to verify it passes**
Run: `findstr /n /c:"### 6.2 是否应切换到 LangChain4j 注解式 tool" docs\analysis\2026-05-03-agent-tool-langchain4j-boundary.md`
Expected: 能查到对应标题行号。

**Step 7: Commit**
Run:
`git add docs/analysis/2026-05-03-agent-tool-langchain4j-boundary.md && git commit -m "docs: unify tool and langchain4j boundary guidance"`
Expected: 生成关于 tool 边界的提交。

### Task 4: 补全审批、loop、原生 HTTP 实现的“重复度判断”与演进建议

**Files:**
- Modify: `docs/analysis/2026-05-03-agent-tool-langchain4j-boundary.md:421-680`
- Test: `penmate-backend/src/main/java/com/penmate/backend/application/approval/DefaultApprovalPolicyEngine.java`
- Test: `penmate-backend/src/main/java/com/penmate/backend/application/agent/orchestration/AgentToolLoopRunner.java`
- Test: `penmate-backend/src/main/java/com/penmate/backend/infrastructure/llm/langchain4j/provider/NativeOpenAiStyleHttpProviderChatClient.java`

**Step 1: 写明哪些部分明确不属于 LangChain4j 责任**
完整内容：

```md
## 4. 当前系统必须自己承担什么

以下部分不应期待由 LangChain4j 接管：

- [`DefaultApprovalPolicyEngine`](penmate-backend/src/main/java/com/penmate/backend/application/approval/DefaultApprovalPolicyEngine.java:28) 的审批判断；
- [`ToolCallApplicationService`](penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/gateway/ToolCallApplicationService.java:51) 的审批单创建、待处理快照落库、任务状态切换；
- [`AgentToolLoopRunner`](penmate-backend/src/main/java/com/penmate/backend/application/agent/orchestration/AgentToolLoopRunner.java:55) 的多轮 tool loop、单轮调用上限、审批中断与恢复衔接。

这些都是 PenMate 的业务协议，而不是通用 LLM SDK 的能力边界。
```

**Step 2: 写明哪些部分有重复建设倾向**
完整内容：

```md
### 5.4 原生 HTTP provider：这里是最需要警惕的重复建设点

[`NativeOpenAiStyleHttpProviderChatClient`](penmate-backend/src/main/java/com/penmate/backend/infrastructure/llm/langchain4j/provider/NativeOpenAiStyleHttpProviderChatClient.java:27) 已经自己实现了：
- 请求体拼装；
- tools 数组拼装；
- tool_choice 注入；
- 响应中 `tool_calls` 提取；
- finish_reason 解析；
- 异常映射。

这部分能力与 LangChain4j 的 tool-calling / chat model 适配已经明显接近。也就是说，真正最像“重复造轮子”的，不是业务路由，而是这块原生 HTTP 协议层。

当前之所以还能成立，前提是：团队需要精确控制 OpenAI 风格报文，或某些 provider 在 LangChain4j 支持度上不足。若这个前提减弱，优先回收的应是这层原生 HTTP 代码，而不是上层业务编排。
```

**Step 3: 写出演进建议矩阵**
完整内容：

```md
## 7. 建议的后续演进路径

### 方案 A：保留现状边界，仅收敛元数据真源
适用条件：tool 数量仍少，审批与恢复流程还在频繁变化。

做法：
- 保留 [`AgentToolHandler`](penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/handler/AgentToolHandler.java:6)；
- 保留 [`ToolCallApplicationService`](penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/gateway/ToolCallApplicationService.java:25)；
- 重构 [`StaticAgentToolCatalog`](penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/catalog/StaticAgentToolCatalog.java:12) 使 schema / 风险 / 展示名单点声明。

### 方案 B：继续保留业务 tool 编排，但尽量回归 LangChain4j 的 provider 能力
适用条件：团队确认主流 provider 已被 LangChain4j 足够覆盖。

做法：
- 缩减 [`NativeOpenAiStyleHttpProviderChatClient`](penmate-backend/src/main/java/com/penmate/backend/infrastructure/llm/langchain4j/provider/NativeOpenAiStyleHttpProviderChatClient.java:27) 的使用范围；
- 尽量统一到 LangChain4j 标准 `ChatLanguageModel` / provider builder；
- 让 [`ProviderChatClientFactory`](penmate-backend/src/main/java/com/penmate/backend/infrastructure/llm/langchain4j/provider/ProviderChatClientFactory.java:17) 只负责薄分发。

### 方案 C：仅把低风险只读工具试点迁移到注解式 tool
适用条件：希望验证 LangChain4j 注解工具的开发体验，但不影响审批主链路。

做法：
- 只选择无审批、无挂起、无持久化副作用工具；
- 不改造 [`ToolCallApplicationService`](penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/gateway/ToolCallApplicationService.java:25) 承担的核心治理能力；
- 用 PoC 验证收益，再决定是否扩大范围。
```

**Step 4: 补上最终摘要段**
完整内容：

```md
## 8. 最终结论

统一结论如下：

1. Agent tool 的业务执行、审批、挂起、恢复，应该继续由 PenMate 自己掌控，不应下沉给 LangChain4j；
2. tool 注册现在可以先不继续抽象出更多层，而应先解决 schema、元数据、审批策略的单一真源问题；
3. LangChain4j 最适合承担的是 LLM SDK / provider 适配职责，而不是业务编排职责；
4. 若问当前哪里最像“重复造轮子”，答案不是 [`AgentModelRoutingService`](penmate-backend/src/main/java/com/penmate/backend/application/agent/AgentModelRoutingService.java:24)，而是 [`NativeOpenAiStyleHttpProviderChatClient`](penmate-backend/src/main/java/com/penmate/backend/infrastructure/llm/langchain4j/provider/NativeOpenAiStyleHttpProviderChatClient.java:27) 这类原生 HTTP tool-calling 适配代码；
5. 因此后续优化优先级应是“收敛协议适配层的重复实现”，而不是“削弱业务编排层”。
```

**Step 5: Run test to verify it passes**
Run: `findstr /n /c:"## 8. 最终结论" docs\analysis\2026-05-03-agent-tool-langchain4j-boundary.md`
Expected: 能查到最终结论标题。

**Step 6: Commit**
Run:
`git add docs/analysis/2026-05-03-agent-tool-langchain4j-boundary.md && git commit -m "docs: finalize langchain4j boundary analysis"`
Expected: 生成最终文档分析提交。

### Task 5: 复核文档可读性并给出交付摘要

**Files:**
- Modify: `docs/analysis/2026-05-03-agent-tool-langchain4j-boundary.md:1-680`
- Test: `docs/analysis/2026-05-03-agent-tool-langchain4j-boundary.md`

**Step 1: 用统一模板检查每节是否回答了用户的五个问题**
检查并补齐以下五问是否逐条落地：

```md
- agent tool 应如何实现？
- tool 注册是否需要再抽类？
- LangChain4j 应承担什么职责？
- 是否适合使用 LangChain4j 注解式 tool？
- 当前模型调用策略工厂/路由工厂是否重复造轮子？
```

**Step 2: 在文档开头增加“速读摘要”**
完整内容：

```md
> 速读结论：
> - 业务路由不是重复造轮子；
> - provider 工厂是合理薄层；
> - 原生 HTTP tool-calling 适配最接近重复建设；
> - 核心业务 tool 不建议切到注解式 `@Tool`；
> - 下一步先统一 tool 元数据真源，再视 provider 支持度决定是否回收原生 HTTP 层。
```

**Step 3: Run verification command**
Run: `findstr /n /c:"速读结论" /c:"是否适合使用 LangChain4j 注解式 tool" /c:"模型调用策略工厂/路由工厂是否重复造轮子" docs\analysis\2026-05-03-agent-tool-langchain4j-boundary.md`
Expected: 三项关键内容均可检索到。

**Step 4: Commit**
Run:
`git add docs/analysis/2026-05-03-agent-tool-langchain4j-boundary.md && git commit -m "docs: polish agent tool and langchain4j analysis"`
Expected: 生成最终润色提交。

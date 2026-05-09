# Agent Tool 单一真源改造 Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use [executing-plans] mode to implement this plan task-by-task.

**Goal:** 将 agent tool 的 [`toolCode`](../../penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/catalog/AgentToolDefinition.java:11)、显示名、LLM JSON Schema、审批/风险元数据统一到同一套定义，并以领域化建模替代当前 [`StaticAgentToolCatalog`](../../penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/catalog/StaticAgentToolCatalog.java:22) 的静态实现。

**Architecture:** 保留当前“catalog + handler + gateway/approval orchestration”主边界，即继续由 [`ToolCallApplicationService.executeToolCall()`](../../penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/gateway/ToolCallApplicationService.java:63) 负责治理、由 [`AgentToolHandler`](../../penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/handler/AgentToolHandler.java:13) 负责业务执行，但把 tool 描述信息上收为统一的“工具定义聚合”。该聚合同时产出面向 LLM 的 [`AgentLlmToolSchema`](../../penmate-backend/src/main/java/com/penmate/backend/application/agent/llm/AgentLlmToolSchema.java:6)、面向审批的风险/审批策略元数据，以及面向前后端展示的显示字段；前端继续消费 agent/approval API 与 SSE 事件，但改为围绕统一的 tool summary/view model 演进。

**Tech Stack:** Java 21 + Spring Boot + MyBatis + LangChain4j provider adapter + Vue 3 + TypeScript + Vitest/JUnit 5

---

## 0. 现状摘要与核心问题

当前仓库已经明确了 agent tool 的三层边界：

- tool 暴露层：[`StaticAgentToolCatalog`](../../penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/catalog/StaticAgentToolCatalog.java:22)
- tool 执行层：[`AgentToolHandler`](../../penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/handler/AgentToolHandler.java:13) 与 [`BookCrudToolHandler`](../../penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/handler/BookCrudToolHandler.java:25)
- tool 治理层：[`ToolCallApplicationService`](../../penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/gateway/ToolCallApplicationService.java:63)

但当前“工具定义”被拆散在多处：

1. [`AgentToolDefinition`](../../penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/catalog/AgentToolDefinition.java:10) 仅包含 `toolCode / displayName / approvalRequired / approvalType / riskLevel`；
2. LLM 暴露 schema 由 [`StaticAgentToolCatalog.toLlmToolSchemas()`](../../penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/catalog/StaticAgentToolCatalog.java:15) 额外构造；
3. 审批细分规则又散落在 [`DefaultApprovalPolicyEngine.evaluate()`](../../penmate-backend/src/main/java/com/penmate/backend/application/approval/DefaultApprovalPolicyEngine.java:28)；
4. 运行时恢复与审计存储依赖 [`PendingToolInvocationSnapshot`](../../penmate-backend/src/main/java/com/penmate/backend/domain/agent/model/PendingToolInvocationSnapshot.java:30) 保存 `toolCode/toolArgsJson`；
5. 前端审批卡片只消费 [`approvalId`](../../penmate-frontend/src/composables/workbench/useWorkbenchChat.ts:107)、[`approvalType`](../../penmate-frontend/src/composables/workbench/useWorkbenchChat.ts:110)、[`approvalPreview`](../../penmate-frontend/src/composables/workbench/useWorkbenchChat.ts:93)，没有稳定的 tool 级展示模型。

这造成三个直接问题：

- 同一个 tool 的“显示名 / schema / 风险配置”无法保证一起演进；
- 新增 tool 时需要同时修改 catalog、policy、测试、可能还有前端展示拼装；
- 当未来工具数量增加、出现更多复合 tool / 参数级风险规则时，静态目录会继续膨胀。

---

## 1. 目标架构

### 1.1 统一真源设计

引入新的“工具定义真源”抽象，例如：

- [`AgentToolDefinitionSource`](../../penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/catalog/AgentToolDefinition.java:10) 的替代接口
- [`AgentToolDescriptor`](../../penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/catalog/AgentToolDefinition.java:10) 作为统一描述对象
- [`ToolInputSchema`](../../penmate-backend/src/main/java/com/penmate/backend/application/agent/llm/AgentLlmToolSchema.java:6) / [`ToolRiskPolicy`](../../penmate-backend/src/main/java/com/penmate/backend/application/approval/ApprovalPolicyDecision.java:3) / [`ToolPresentation`](../../penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/catalog/AgentToolDefinition.java:10) 作为领域子对象

统一描述对象至少应覆盖：

- 稳定标识：`toolCode`
- 展示信息：`displayName`、可选 `summary`、可选 `group`
- 模型暴露：`exposedToLlm`、`description`、`parametersJsonSchema`
- 风险治理：默认 `riskLevel`、默认 `approvalType`、`approvalMode`
- 参数级治理扩展点：`riskHints` 或 `operationPolicies`
- 可执行绑定：`handlerCode` 或默认使用 `toolCode`

### 1.2 领域建模原则

1. **定义与执行解耦**：[`AgentToolHandler`](../../penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/handler/AgentToolHandler.java:15) 继续只关心执行，不承担 schema 拼接或展示字段维护；
2. **定义与审批策略收敛**：[`DefaultApprovalPolicyEngine.evaluate()`](../../penmate-backend/src/main/java/com/penmate/backend/application/approval/DefaultApprovalPolicyEngine.java:28) 不再硬编码特定 [`toolCode`](../../penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/runtime/ToolCallRequest.java:12)，而是解释 tool descriptor 中声明的风险策略；
3. **catalog 退化为查询投影**：当前 [`StaticAgentToolCatalog`](../../penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/catalog/StaticAgentToolCatalog.java:22) 从“静态实现”变成“定义源上的只读门面/缓存”；
4. **前端不直接理解后端内部审批逻辑**：前端仅消费稳定的审批视图字段和必要的 tool 展示元数据，不感知 handler 结构或恢复机制。

### 1.3 推荐目录重组

优先保持当前包边界，只在 `application/agent/tool` 下细化：

- Create: `penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/definition/AgentToolDescriptor.java`
- Create: `penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/definition/ToolPresentation.java`
- Create: `penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/definition/ToolExposure.java`
- Create: `penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/definition/ToolGovernancePolicy.java`
- Create: `penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/definition/ToolOperationPolicy.java`
- Create: `penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/definition/AgentToolDefinitionSource.java`
- Create: `penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/definition/InMemoryAgentToolDefinitionSource.java`
- Create: `penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/definition/ToolDefinitionViews.java`
- Modify: `penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/catalog/StaticAgentToolCatalog.java`
- Modify: `penmate-backend/src/main/java/com/penmate/backend/application/approval/DefaultApprovalPolicyEngine.java`
- Modify: `penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/gateway/ToolCallApplicationService.java`
- Modify: `penmate-backend/src/main/java/com/penmate/backend/infrastructure/realtime/RealtimeEventServiceImpl.java`

---

## 2. 前后端边界结论

### 2.1 后端边界

以下边界建议保持不变：

- HTTP 与 SSE 入口仍由 [`AgentController`](../../penmate-backend/src/main/java/com/penmate/backend/interfaces/api/agent/AgentController.java:31) 和 [`ApprovalController`](../../penmate-backend/src/main/java/com/penmate/backend/interfaces/api/approval/ApprovalController.java:27) 承担；
- tool 执行入口仍为 [`ToolCallApplicationService.executeToolCall()`](../../penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/gateway/ToolCallApplicationService.java:63)；
- loop 编排仍在 [`AgentToolLoopRunner.execute()`](../../penmate-backend/src/main/java/com/penmate/backend/application/agent/orchestration/AgentToolLoopRunner.java:48)；
- 审批恢复仍经 [`ApprovalApplicationService.approve()`](../../penmate-backend/src/main/java/com/penmate/backend/application/approval/ApprovalApplicationService.java:92) → [`ApprovedToolInvocationAsyncResumer`](../../penmate-backend/src/main/java/com/penmate/backend/application/approval/ApprovedToolInvocationAsyncResumer.java:48) → [`AgentApprovalResumeCoordinator`](../../penmate-backend/src/main/java/com/penmate/backend/application/approval/coordination/AgentApprovalResumeCoordinator.java:17)。

需要变化的是：这些链路获取 tool 元数据的方式，统一改为从新的 descriptor 真源查询，而不是分别依赖静态表和硬编码规则。

### 2.2 前端边界

前端现状：

- agent API 在 [`agentApi`](../../penmate-frontend/src/api/modules/agent.api.ts:18)
- approval API 在 [`approvalApi`](../../penmate-frontend/src/api/modules/approval.api.ts:6)
- 聊天/审批融合发生在 [`useWorkbenchChat()`](../../penmate-frontend/src/composables/workbench/useWorkbenchChat.ts:142) 与 [`useWorkbenchApprovals()`](../../penmate-frontend/src/composables/workbench/useWorkbenchApprovals.ts:17)
- 审批卡片展示结构由 [`ApprovalCardData`](../../penmate-frontend/src/components/workbench/ApprovalCard.vue:34) 定义

边界建议：

1. 前端不直接拉取“工具目录”作为首阶段必需条件；
2. 首阶段只要求后端在 generation waiting approval SSE / 审批详情返回中补齐稳定字段，例如：
   - `toolCode`
   - `toolDisplayName`
   - `toolRiskLevel`
   - `approvalPreview`
   - 可选 `operationCode`
3. 前端在 [`buildApprovalCard()`](../../penmate-frontend/src/composables/workbench/useWorkbenchChat.ts:106) 中优先消费这些稳定字段，避免继续用 `approvalType` 拼默认文案。

### 2.3 边界上的非目标

本次改造不建议同时做以下事情：

- 不把 [`AgentToolHandler`](../../penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/handler/AgentToolHandler.java:13) 改成 LangChain4j `@Tool`
- 不把审批快照 [`PendingToolInvocationSnapshot`](../../penmate-backend/src/main/java/com/penmate/backend/domain/agent/model/PendingToolInvocationSnapshot.java:30) 改造成通用 JSON 文档引擎
- 不引入数据库驱动的动态工具注册；先做内存真源抽象，后续再留出持久化扩展位

---

## 3. 数据模型方案

### 3.1 后端统一定义对象

建议将当前 [`AgentToolDefinition`](../../penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/catalog/AgentToolDefinition.java:10) 升级为组合型对象：

```java
public record AgentToolDescriptor(
        String toolCode,
        ToolPresentation presentation,
        ToolExposure exposure,
        ToolGovernancePolicy governance,
        List<ToolOperationPolicy> operationPolicies
) {}
```

其中：

```java
public record ToolPresentation(
        String displayName,
        String summary,
        String group
) {}

public record ToolExposure(
        boolean exposedToLlm,
        String llmDescription,
        String parametersJsonSchema
) {}

public record ToolGovernancePolicy(
        boolean approvalRequiredByDefault,
        String defaultApprovalType,
        Integer riskLevel
) {}

public record ToolOperationPolicy(
        String operationCode,
        boolean approvalRequired,
        String approvalType,
        Integer riskLevel,
        String matchField,
        String matchValue
) {}
```

说明：

- `operationPolicies` 主要服务 [`book_crud`](../../penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/handler/BookCrudToolHandler.java:35) 这类复合 tool；
- `matchField/matchValue` 是第一阶段最小可行设计，用于替代 [`DefaultApprovalPolicyEngine.evaluate()`](../../penmate-backend/src/main/java/com/penmate/backend/application/approval/DefaultApprovalPolicyEngine.java:33) 里的 delete 文本匹配硬编码；
- 第二阶段若要支持更复杂条件，再把它演进为策略接口，而不是在本次计划里提前过度设计。

### 3.2 查询与投影接口

为避免调用方知道 descriptor 细节，增加统一查询接口：

```java
public interface AgentToolDefinitionSource {
    AgentToolDescriptor getRequired(String toolCode);
    List<AgentToolDescriptor> listAll();
    List<AgentLlmToolSchema> listLlmSchemas();
}
```

这样：

- [`AgentToolLoopRunner.execute()`](../../penmate-backend/src/main/java/com/penmate/backend/application/agent/orchestration/AgentToolLoopRunner.java:48) 只要 `listLlmSchemas()`
- [`ToolCallApplicationService.executeToolCall()`](../../penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/gateway/ToolCallApplicationService.java:63) 只要 `getRequired()`
- 后续新增 admin/inspection API 时可直接复用 `listAll()`

### 3.3 审批与事件视图模型

建议新增一个事件/展示投影构造器，例如：

```java
public record ToolApprovalView(
        String toolCode,
        String toolDisplayName,
        Integer riskLevel,
        String approvalType,
        Map<String, Object> preview,
        String operationCode
) {}
```

用途：

- [`ToolCallApplicationService.executeToolCall()`](../../penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/gateway/ToolCallApplicationService.java:63) 创建审批时生成稳定 summary
- [`RealtimeEventServiceImpl.publishGenerationWaitingApproval()`](../../penmate-backend/src/main/java/com/penmate/backend/infrastructure/realtime/RealtimeEventServiceImpl.java:151) 直接发送规范字段
- 前端 [`buildApprovalCard()`](../../penmate-frontend/src/composables/workbench/useWorkbenchChat.ts:106) 使用统一字段渲染

### 3.4 前端类型演进

建议新增而不是立刻替换旧字段：

- Modify: `penmate-frontend/src/components/workbench/ApprovalCard.vue`
- Modify: `penmate-frontend/src/components/workbench/workbenchTypes.ts`
- Modify: `penmate-frontend/src/composables/workbench/useWorkbenchChat.ts`

新增字段建议：

```ts
export interface ApprovalCardData {
  id: string
  message: string
  time: string
  preview?: Record<string, string>
  resolved: boolean
  resolvedAction?: 'approved' | 'rejected'
  toolCode?: string
  toolDisplayName?: string
  riskLevel?: number
  operationCode?: string
}
```

---

## 4. 迁移策略

### 4.0 已完成的收尾迁移说明

截至本次清理，后端运行时代码已不再存在 [`StaticAgentToolCatalog`](../../penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/catalog/StaticAgentToolCatalog.java) 的有效消费方，旧的 [`com.penmate.backend.application.agent.tool.catalog.AgentToolDefinition`](../../penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/catalog/AgentToolDefinition.java) 兼容投影也已一并删除。

当前 agent tool 元数据的唯一真源为：

- [`AgentToolDefinitionSource`](../../penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/definition/AgentToolDefinitionSource.java)
- [`AgentToolDescriptor`](../../penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/definition/AgentToolDescriptor.java)
- [`ToolExposure`](../../penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/definition/ToolExposure.java)
- [`ToolGovernancePolicy`](../../penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/definition/ToolGovernancePolicy.java)

迁移后的约束是：任何新的治理、审批、LLM schema 或展示字段消费方，都应直接从 descriptor 真源读取，不再新增 catalog-to-definition 的兼容投影。


### 4.1 总体迁移原则

采用“三阶段兼容迁移”：

1. **引入新真源但保持旧 API/行为不变**
2. **让治理、事件、前端展示逐步切到新字段**
3. **最后删除旧静态结构和硬编码分支**

这样可以避免一次性改动 loop、审批恢复、SSE、前端卡片导致排障困难。

### 4.2 阶段 A：后端定义真源落地

目标：在不改变 API 对外契约的前提下，引入 descriptor 模型。

改造点：

- Create: `penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/definition/AgentToolDescriptor.java`
- Create: `penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/definition/AgentToolDefinitionSource.java`
- Create: `penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/definition/InMemoryAgentToolDefinitionSource.java`
- Modify: `penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/catalog/StaticAgentToolCatalog.java`

做法：

- 先把当前 [`registry`](../../penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/catalog/StaticAgentToolCatalog.java:137) 和 `toLlmToolSchemas()` 的 schema 构造逻辑整体迁入 `InMemoryAgentToolDefinitionSource`
- [`StaticAgentToolCatalog`](../../penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/catalog/StaticAgentToolCatalog.java:22) 暂时保留为适配器，内部改为委托新 source
- 保持 [`getRequired()`](../../penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/catalog/StaticAgentToolCatalog.java:142) 与 `toLlmToolSchemas()` 原方法签名，避免上层大面积同时改动

### 4.3 阶段 B：审批策略从硬编码切到 descriptor

目标：移除 [`DefaultApprovalPolicyEngine.evaluate()`](../../penmate-backend/src/main/java/com/penmate/backend/application/approval/DefaultApprovalPolicyEngine.java:28) 里对 `book_crud delete` 的硬编码判断。

改造点：

- Modify: `penmate-backend/src/main/java/com/penmate/backend/application/approval/DefaultApprovalPolicyEngine.java`
- Modify: `penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/gateway/ToolCallApplicationService.java`
- Modify: `penmate-backend/src/main/java/com/penmate/backend/application/approval/ApprovalPolicyDecision.java`

做法：

- 让 `evaluate()` 接收新的 [`AgentToolDescriptor`](../../penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/catalog/AgentToolDefinition.java:10) 等价对象
- 解析 `toolArgsJson` 中的 `operation`，匹配 `operationPolicies`
- 若未命中 operation 策略，则回退到 `ToolGovernancePolicy`
- `ApprovalPolicyDecision` 可扩展返回 `riskLevel / operationCode / displayName`，供后续事件和审计使用

### 4.4 阶段 C：审批事件与前端展示切到新视图

目标：把“待审批卡片”从仅靠 `approvalType` 推断，演进为基于 tool 视图字段渲染。

改造点：

- Modify: `penmate-backend/src/main/java/com/penmate/backend/infrastructure/realtime/RealtimeEventServiceImpl.java`
- 可选 Modify: `penmate-backend/src/main/java/com/penmate/backend/application/approval/ApprovalApplicationService.java`
- Modify: `penmate-frontend/src/composables/workbench/useWorkbenchChat.ts`
- Modify: `penmate-frontend/src/components/workbench/ApprovalCard.vue`
- Modify: `penmate-frontend/src/components/workbench/workbenchTypes.ts`

做法：

- waiting approval SSE 统一补齐：`toolCode / toolDisplayName / riskLevel / operationCode / approvalPreview`
- 前端 [`buildApprovalCard()`](../../penmate-frontend/src/composables/workbench/useWorkbenchChat.ts:106) 优先使用 `toolDisplayName` 生成文案，例如“检测到待审批工具调用（书籍 CRUD / delete）”
- 保留对旧字段 `approvalType` 的兼容分支，确保老数据仍可渲染

### 4.5 阶段 D：暴露只读工具目录查询接口（可选）

若后续需要在前端展示“当前 agent 可用工具”，再新增只读接口：

- Create: `penmate-backend/src/main/java/com/penmate/backend/interfaces/api/agent/dto/AgentToolViewDto.java`
- Create: `penmate-backend/src/main/java/com/penmate/backend/interfaces/api/agent/AgentToolQueryController.java`
- Create: `penmate-frontend/src/api/modules/agent-tool.api.ts`

该阶段不是本次必需；仅当产品需要工具目录页、调试页或审批规则可视化时再做。

---

## 5. 详细任务拆解

### Task 1: 建立统一定义真源骨架

**Files:**
- Create: `penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/definition/AgentToolDescriptor.java`
- Create: `penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/definition/ToolPresentation.java`
- Create: `penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/definition/ToolExposure.java`
- Create: `penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/definition/ToolGovernancePolicy.java`
- Create: `penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/definition/ToolOperationPolicy.java`
- Create: `penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/definition/AgentToolDefinitionSource.java`
- Create: `penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/definition/InMemoryAgentToolDefinitionSource.java`
- Test: `penmate-backend/src/test/java/com/penmate/backend/application/agent/tool/definition/InMemoryAgentToolDefinitionSourceTest.java`

**Step 1: Write the failing test**

编写测试覆盖：

- `context_enhancer` 与 `book_crud` 都能从 source 获取 descriptor
- `book_crud` 同时拥有 LLM schema 与 operation policy
- `listLlmSchemas()` 只返回 `exposedToLlm=true` 的项

**Step 2: Run test to verify it fails**

Run: [`mvn -Dtest=InMemoryAgentToolDefinitionSourceTest test`](../../penmate-backend/pom.xml)

Expected: 编译失败或测试失败，提示新类不存在。

**Step 3: Write minimal implementation**

- 用新 source 承接当前静态定义
- 让 schema 文本生成逻辑原样迁移，先不做 schema builder 抽象
- 在 `book_crud` 中声明 `delete` 的 operation policy

**Step 4: Run test to verify it passes**

Run: [`mvn -Dtest=InMemoryAgentToolDefinitionSourceTest test`](../../penmate-backend/pom.xml)

Expected: 测试通过。

**Step 5: Commit**

Run:

```bash
git add penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/definition penmate-backend/src/test/java/com/penmate/backend/application/agent/tool/definition
git commit -m "feat(agent-tool): introduce unified tool definition source"
```

### Task 2: 让 catalog 退化为新真源适配器

**Files:**
- Modify: `penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/catalog/StaticAgentToolCatalog.java`
- Test: `penmate-backend/src/test/java/com/penmate/backend/application/agent/tool/catalog/StaticAgentToolCatalogTest.java`

**Step 1: Write the failing test**

扩展 [`StaticAgentToolCatalogTest`](../../penmate-backend/src/test/java/com/penmate/backend/application/agent/tool/catalog/StaticAgentToolCatalogTest.java:14)，断言 catalog 的 schema 与 descriptor source 结果一致。

**Step 2: Run test to verify it fails**

Run: [`mvn -Dtest=StaticAgentToolCatalogTest test`](../../penmate-backend/pom.xml)

Expected: 旧实现无法满足新委托行为或构造方式不匹配。

**Step 3: Write minimal implementation**

- 为 [`StaticAgentToolCatalog`](../../penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/catalog/StaticAgentToolCatalog.java:22) 注入 `AgentToolDefinitionSource`
- 仅保留兼容方法：`getRequired()`、`toLlmToolSchemas()`
- 如有需要，旧 [`AgentToolDefinition`](../../penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/catalog/AgentToolDefinition.java:10) 暂时保留为过渡投影对象

**Step 4: Run test to verify it passes**

Run: [`mvn -Dtest=StaticAgentToolCatalogTest test`](../../penmate-backend/pom.xml)

Expected: 测试通过。

**Step 5: Commit**

Run:

```bash
git add penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/catalog/StaticAgentToolCatalog.java penmate-backend/src/test/java/com/penmate/backend/application/agent/tool/catalog/StaticAgentToolCatalogTest.java
git commit -m "refactor(agent-tool): delegate static catalog to definition source"
```

### Task 3: 让审批策略解释 descriptor 而不是硬编码 toolCode

**Files:**
- Modify: `penmate-backend/src/main/java/com/penmate/backend/application/approval/DefaultApprovalPolicyEngine.java`
- Modify: `penmate-backend/src/main/java/com/penmate/backend/application/approval/ApprovalPolicyDecision.java`
- Test: `penmate-backend/src/test/java/com/penmate/backend/application/approval/DefaultApprovalPolicyEngineTest.java`

**Step 1: Write the failing test**

新增测试：

- `book_crud + operation=delete` 命中 operation policy
- `book_crud + operation=list` 走默认免审批
- 声明默认审批的 tool 直接命中 governance policy
- 返回结果包含 `approvalType` 及必要上下文

**Step 2: Run test to verify it fails**

Run: [`mvn -Dtest=DefaultApprovalPolicyEngineTest test`](../../penmate-backend/pom.xml)

Expected: 现有构造参数与断言不匹配。

**Step 3: Write minimal implementation**

- 用 JSON 解析读取 `operation`
- 按 descriptor.operationPolicies 匹配
- 回退到 descriptor.governance
- 删除 [`DefaultApprovalPolicyEngine`](../../penmate-backend/src/main/java/com/penmate/backend/application/approval/DefaultApprovalPolicyEngine.java:33) 中的 toolCode 硬编码分支

**Step 4: Run test to verify it passes**

Run: [`mvn -Dtest=DefaultApprovalPolicyEngineTest test`](../../penmate-backend/pom.xml)

Expected: 测试通过。

**Step 5: Commit**

Run:

```bash
git add penmate-backend/src/main/java/com/penmate/backend/application/approval/DefaultApprovalPolicyEngine.java penmate-backend/src/main/java/com/penmate/backend/application/approval/ApprovalPolicyDecision.java penmate-backend/src/test/java/com/penmate/backend/application/approval/DefaultApprovalPolicyEngineTest.java
git commit -m "refactor(agent-tool): derive approval policy from tool descriptor"
```

### Task 4: 把治理层和审批快照摘要切到统一定义视图

**Files:**
- Modify: `penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/gateway/ToolCallApplicationService.java`
- Create: `penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/definition/ToolApprovalView.java`
- Create: `penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/definition/ToolApprovalViewFactory.java`
- Test: `penmate-backend/src/test/java/com/penmate/backend/application/agent/tool/gateway/ToolCallApplicationServiceTest.java`

**Step 1: Write the failing test**

新增测试覆盖：

- 待审批时保存的 `approvalSummaryJson` 包含 `toolCode/toolDisplayName/riskLevel/approvalType`
- 不再仅写入 `{ "approvalType": ... }`
- delete 等 operation 可以携带 `operationCode`

**Step 2: Run test to verify it fails**

Run: [`mvn -Dtest=ToolCallApplicationServiceTest test`](../../penmate-backend/pom.xml)

Expected: 当前 `approvalSummaryJson` 字段内容不满足断言。

**Step 3: Write minimal implementation**

- 根据 descriptor + decision 构造 `ToolApprovalView`
- 写入 [`PendingToolInvocationSnapshot`](../../penmate-backend/src/main/java/com/penmate/backend/domain/agent/model/PendingToolInvocationSnapshot.java:47) 的 `approvalSummaryJson`
- 保持快照主结构不变，避免数据库迁移

**Step 4: Run test to verify it passes**

Run: [`mvn -Dtest=ToolCallApplicationServiceTest test`](../../penmate-backend/pom.xml)

Expected: 测试通过。

**Step 5: Commit**

Run:

```bash
git add penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/gateway/ToolCallApplicationService.java penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/definition penmate-backend/src/test/java/com/penmate/backend/application/agent/tool/gateway/ToolCallApplicationServiceTest.java
git commit -m "feat(agent-tool): persist approval summary from unified descriptor"
```

### Task 5: 统一 waiting approval SSE 事件契约

**Files:**
- Modify: `penmate-backend/src/main/java/com/penmate/backend/infrastructure/realtime/RealtimeEventServiceImpl.java`
- Modify: `penmate-backend/src/main/java/com/penmate/backend/domain/shared/service/RealtimeEventService.java`
- Test: `penmate-backend/src/test/java/com/penmate/backend/infrastructure/realtime/RealtimeEventServiceImplTest.java`

**Step 1: Write the failing test**

在 [`RealtimeEventServiceImplTest`](../../penmate-backend/src/test/java/com/penmate/backend/infrastructure/realtime/RealtimeEventServiceImplTest.java:40) 增加断言：

- waiting approval 事件带 `toolCode`
- 带 `toolDisplayName`
- 带 `riskLevel`
- 老字段 `approvalType`、`approvalPreview` 仍存在

**Step 2: Run test to verify it fails**

Run: [`mvn -Dtest=RealtimeEventServiceImplTest test`](../../penmate-backend/pom.xml)

Expected: 当前事件载荷缺失新增字段。

**Step 3: Write minimal implementation**

- 增强 [`publishGenerationWaitingApproval()`](../../penmate-backend/src/main/java/com/penmate/backend/infrastructure/realtime/RealtimeEventServiceImpl.java:151)
- 让调用方显式传入 approval summary 视图，避免事件层自己猜字段

**Step 4: Run test to verify it passes**

Run: [`mvn -Dtest=RealtimeEventServiceImplTest test`](../../penmate-backend/pom.xml)

Expected: 测试通过。

**Step 5: Commit**

Run:

```bash
git add penmate-backend/src/main/java/com/penmate/backend/infrastructure/realtime/RealtimeEventServiceImpl.java penmate-backend/src/main/java/com/penmate/backend/domain/shared/service/RealtimeEventService.java penmate-backend/src/test/java/com/penmate/backend/infrastructure/realtime/RealtimeEventServiceImplTest.java
git commit -m "feat(agent-tool): enrich waiting approval event contract"
```

### Task 6: 前端审批卡片切换到统一工具视图字段

**Files:**
- Modify: `penmate-frontend/src/components/workbench/ApprovalCard.vue`
- Modify: `penmate-frontend/src/components/workbench/workbenchTypes.ts`
- Modify: `penmate-frontend/src/composables/workbench/useWorkbenchChat.ts`
- Test: `penmate-frontend/src/composables/workbench/__tests__/useWorkbenchChat.spec.ts`
- Test: `penmate-frontend/src/components/workbench/chat/ChatMessageItem.spec.ts`

**Step 1: Write the failing test**

测试点：

- SSE item 带 `toolDisplayName` 时，卡片 message 使用工具名拼装
- `riskLevel`、`operationCode` 被映射到 [`ApprovalCardData`](../../penmate-frontend/src/components/workbench/ApprovalCard.vue:34)
- 旧事件只有 `approvalType` 时仍兼容

**Step 2: Run test to verify it fails**

Run: [`npm run test -- useWorkbenchChat ChatMessageItem`](../../penmate-frontend/package.json)

Expected: 类型或断言失败。

**Step 3: Write minimal implementation**

- 扩展 [`ApprovalCardData`](../../penmate-frontend/src/components/workbench/ApprovalCard.vue:34)
- 修改 [`buildApprovalCard()`](../../penmate-frontend/src/composables/workbench/useWorkbenchChat.ts:106) 优先读取 `toolDisplayName`
- 在卡片中可选展示风险等级/operation 标签，若 UI 代价高则先仅映射不展示

**Step 4: Run test to verify it passes**

Run: [`npm run test -- useWorkbenchChat ChatMessageItem`](../../penmate-frontend/package.json)

Expected: 相关测试通过。

**Step 5: Commit**

Run:

```bash
git add penmate-frontend/src/components/workbench/ApprovalCard.vue penmate-frontend/src/components/workbench/workbenchTypes.ts penmate-frontend/src/composables/workbench/useWorkbenchChat.ts penmate-frontend/src/composables/workbench/__tests__/useWorkbenchChat.spec.ts penmate-frontend/src/components/workbench/chat/ChatMessageItem.spec.ts
git commit -m "feat(frontend): consume unified agent tool approval metadata"
```

### Task 7: 清理过渡代码并补足回归验证

**Files:**
- Modify: `penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/catalog/AgentToolDefinition.java`
- Modify: `penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/catalog/StaticAgentToolCatalog.java`
- Test: `penmate-backend/src/test/java/com/penmate/backend/application/agent/orchestration/AgentToolLoopRunnerTest.java`
- Test: `penmate-backend/src/test/java/com/penmate/backend/infrastructure/llm/langchain4j/provider/NativeOpenAiStyleHttpProviderChatClientToolModeTest.java`
- Test: `penmate-frontend/src/views/Workbench/index.chat-binding.spec.ts`

**Step 1: Write the failing test**

补充端到端回归点：

- LLM schema 输出仍包含 [`book_crud`](../../penmate-backend/src/test/java/com/penmate/backend/application/agent/tool/catalog/StaticAgentToolCatalogTest.java:17)
- waiting approval 仍可中断 loop 并恢复
- 前端工作台仍能在 waiting approval 场景下完成 approve/reject UI 流程

**Step 2: Run test to verify it fails**

Run: [`mvn -Dtest=AgentToolLoopRunnerTest,NativeOpenAiStyleHttpProviderChatClientToolModeTest test`](../../penmate-backend/pom.xml)

Run: [`npm run test -- index.chat-binding`](../../penmate-frontend/package.json)

Expected: 若仍依赖旧字段，至少一侧失败。

**Step 3: Write minimal implementation**

- 删除无用的旧构造分支
- 如确认无调用方依赖旧 [`AgentToolDefinition`](../../penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/catalog/AgentToolDefinition.java:10)，将其降级为兼容 DTO 或移除
- 保持 provider 侧 [`toolSchema.toolCode()`](../../penmate-backend/src/main/java/com/penmate/backend/infrastructure/llm/langchain4j/provider/NativeOpenAiStyleHttpProviderChatClient.java:136) 不受影响

**Step 4: Run test to verify it passes**

Run: [`mvn test`](../../penmate-backend/pom.xml)

Run: [`npm run test`](../../penmate-frontend/package.json)

Expected: 后端 agent/approval/tool 相关测试与前端 workbench 相关测试全部通过。

**Step 5: Commit**

Run:

```bash
git add penmate-backend/src/main/java/com/penmate/backend/application/agent/tool penmate-backend/src/test/java/com/penmate/backend/application penmate-frontend/src penmate-frontend/package.json
git commit -m "refactor(agent-tool): finalize single source of truth migration"
```

---

## 6. 验证策略

### 6.1 后端单元/组件验证

必须覆盖以下断言：

1. descriptor source 可同时产出治理元数据与 LLM schema；
2. [`StaticAgentToolCatalog.toLlmToolSchemas()`](../../penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/catalog/StaticAgentToolCatalog.java:15) 行为与现有 provider 兼容；
3. [`DefaultApprovalPolicyEngine.evaluate()`](../../penmate-backend/src/main/java/com/penmate/backend/application/approval/DefaultApprovalPolicyEngine.java:28) 对复合 tool 的参数级审批规则生效；
4. [`ToolCallApplicationService.executeToolCall()`](../../penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/gateway/ToolCallApplicationService.java:63) 在等待审批时正确落 `approvalSummaryJson`；
5. [`RealtimeEventServiceImpl.publishGenerationWaitingApproval()`](../../penmate-backend/src/main/java/com/penmate/backend/infrastructure/realtime/RealtimeEventServiceImpl.java:151) 对外发送新旧兼容字段；
6. [`AgentToolLoopRunner.execute()`](../../penmate-backend/src/main/java/com/penmate/backend/application/agent/orchestration/AgentToolLoopRunner.java:48) 与 [`ToolCallResumeService`](../../penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/runtime/ToolCallResumeService.java:37) 不因新定义模型而破坏恢复链路。

### 6.2 前端验证

必须覆盖：

1. [`buildApprovalCard()`](../../penmate-frontend/src/composables/workbench/useWorkbenchChat.ts:106) 新旧事件格式兼容；
2. [`useWorkbenchApprovals()`](../../penmate-frontend/src/composables/workbench/useWorkbenchApprovals.ts:17) approve/reject 行为不受字段扩展影响；
3. [`ApprovalCard.vue`](../../penmate-frontend/src/components/workbench/ApprovalCard.vue:1) 在新增字段存在/不存在两种情况下均可渲染；
4. [`views/Workbench/index.chat-binding.spec.ts`](../../penmate-frontend/src/views/Workbench/index.chat-binding.spec.ts) 保证聊天-审批联动未回归。

### 6.3 手工验证脚本

后端启动后，手工验证如下：

1. 创建会话并发起一次触发 [`book_crud`](../../penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/handler/BookCrudToolHandler.java:35) `delete` 的生成；
2. 观察 generation SSE 是否收到 `waiting_approval` 事件，且包含：
   - `approvalId`
   - `approvalType`
   - `toolCode`
   - `toolDisplayName`
   - `riskLevel`
3. 调用 [`/api/v1/novels/{projectId}/approvals/{approvalId}/approve`](../../penmate-backend/src/main/java/com/penmate/backend/interfaces/api/approval/ApprovalController.java:124)；
4. 确认任务恢复成功，快照状态封口，前端卡片转为 resolved。

### 6.4 回滚策略

若 descriptor 方案落地后出现不可快速定位的问题：

- 保留 [`StaticAgentToolCatalog`](../../penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/catalog/StaticAgentToolCatalog.java:22) 兼容门面直到全部测试通过；
- 不修改 `pending_tool_invocations` 表结构，因此可直接回退代码而不做 DB 回滚；
- 保留前端对旧 `approvalType` 文案的回退逻辑，避免灰度期间空白卡片。

---

## 7. 风险与决策记录

### 7.1 主要风险

1. **审批链路耦合风险**：[`ToolCallApplicationService`](../../penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/gateway/ToolCallApplicationService.java:47) 是高协调度服务，任何字段变更都可能影响等待审批/恢复链路；
2. **SSE 契约回归风险**：[`useWorkbenchChat`](../../penmate-frontend/src/composables/workbench/useWorkbenchChat.ts:298) 对事件字段名较敏感；
3. **复合工具策略过度抽象风险**：若第一版就上表达式引擎，会让计划失控；
4. **包职责漂移风险**：若把太多展示逻辑塞回 handler，会再次破坏边界。

### 7.2 本计划的控制策略

- 先用内存定义源，不碰动态注册与 DB 配置化；
- 参数级风险先支持 `operation` 这种明确子动作，不做通用 DSL；
- 通过 `ToolApprovalView` 统一事件/审批摘要，而不是让多个层各自手写 Map；
- 全程保持 provider tool schema 输出接口不变，降低对 [`NativeOpenAiStyleHttpProviderChatClient`](../../penmate-backend/src/main/java/com/penmate/backend/infrastructure/llm/langchain4j/provider/NativeOpenAiStyleHttpProviderChatClient.java:135) 的影响。

---

## 8. 完成标准

满足以下条件才算完成：

- 同一个 tool 的 `toolCode / displayName / JSON Schema / risk/approval metadata` 由单一 descriptor 定义驱动；
- [`DefaultApprovalPolicyEngine`](../../penmate-backend/src/main/java/com/penmate/backend/application/approval/DefaultApprovalPolicyEngine.java:28) 不再硬编码具体 tool 分支；
- [`StaticAgentToolCatalog`](../../penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/catalog/StaticAgentToolCatalog.java:22) 不再是唯一真源，而是兼容门面或被替换；
- waiting approval 的后端摘要与前端卡片使用统一 tool 视图字段；
- 后端 agent/approval/tool 测试与前端 workbench 聊天/审批测试通过；
- 迁移过程中不要求数据库 schema 变更。

---

## 9. 预估工作量

- Task 1-2：0.5 天
- Task 3-5：0.5~1 天
- Task 6-7：0.5 天
- 总计：1.5~2 天

---

## 10. 执行选项

Plan complete. Execute now?

1. Execute in this session ([executing-plans] mode)
2. Execute later (user will run `/execute-plan`)
3. Manual implementation (just use plan as guide)

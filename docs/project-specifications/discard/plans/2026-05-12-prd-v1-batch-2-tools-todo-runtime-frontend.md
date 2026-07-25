# PRD v1 Batch 2 Tools + Todo + Runtime + Frontend Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use [executing-plans] mode to implement this plan task-by-task.

**Goal:** 在第 1 批完成主编排、Story Bible 与 Context Builder 基础后，补齐 Tools、Todo、RAG/Memory、Runtime Status 与前端 Workbench 用户感知，形成可验收的端到端创作链路。

**Architecture:** 本批次继续复用现有单主编排架构，由 [`AgentGenerationWorkflow`](penmate-backend/src/main/java/com/penmate/backend/application/agent/orchestration/AgentGenerationWorkflow.java:33) 驱动 tool loop 和状态发布。Draft Generation、Quality Review、Todo Planner 作为显式工具接入现有 [`AgentToolLoopRunner`](penmate-backend/src/main/java/com/penmate/backend/application/agent/orchestration/AgentToolLoopRunner.java:37)，Todo 写库、Memory/RAG 汇总、Runtime Status 发布和前端 Workbench 渲染都围绕既有 task/session/recovery/event 通道增强，不增加第二流程中心。

**Tech Stack:** Java 21、Spring Boot 3.3、MyBatis、Flyway、LangChain4j、Vue 3、TypeScript、Vitest、JUnit 5、Mockito

---

## Scope

本批计划覆盖以下 PRD 目标：

- Draft Generation Tool
- Quality Review Tool
- Todo Planner Tool
- Todo CRUD + SQL 持久化
- RAG 混合检索增强入口
- Memory Store 结构化运行态扩展
- Task Runtime Status Publisher / 事件协议增强
- Workbench Runtime Presenter / 右侧工作台展示增强
- 测试矩阵、验收清单、反架子检查

本批依赖第 1 批已经具备：

- `TaskProfile`
- `PromptPlan`
- `ContextPackage`
- Story Bible 结构化上下文与 proposal 基础能力

---

## Current Baseline Summary

当前已存在并可复用的基础：

- tool loop 执行器在 [`AgentToolLoopRunner`](penmate-backend/src/main/java/com/penmate/backend/application/agent/orchestration/AgentToolLoopRunner.java:37)
- tool 定义真源在 [`AgentToolDefinition`](penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/definition/AgentToolDefinition.java:1) 与 [`InMemoryAgentToolDefinitionSource`](penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/definition/InMemoryAgentToolDefinitionSource.java:18)
- 当前已有工具定义参考 [`RagQueryToolDefinition`](penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/definition/RagQueryToolDefinition.java:1)
- tool 执行入口在 [`ToolCallApplicationService`](penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/gateway/ToolCallApplicationService.java:41)
- 运行态事件服务在 [`RealtimeEventServiceImpl`](penmate-backend/src/main/java/com/penmate/backend/infrastructure/realtime/RealtimeEventServiceImpl.java:22)
- 前端运行态消费在 [`useWorkbenchTaskRuntime.ts`](penmate-frontend/src/composables/workbench/useWorkbenchTaskRuntime.ts:36)
- 前端右侧面板入口在 [`WorkbenchRightPanel.vue`](penmate-frontend/src/components/workbench/WorkbenchRightPanel.vue:1)
- agent task/session 表基线在 [`V11__init_agent_and_ops_domains.sql`](penmate-backend/src/main/resources/db/migration/V11__init_agent_and_ops_domains.sql:71)
- 现有 RAG 领域与应用服务在 [`RagApplicationService`](penmate-backend/src/main/java/com/penmate/backend/application/rag/RagApplicationService.java:17)

因此本批的关键原则是：

1. 工具能力作为 tool loop 内显式 tool 接入，而不是新 Agent。
2. Todo 写库由主编排可追踪触发，不允许前端本地缓存成为真实来源。
3. Runtime status 事件必须与前端显示文案一一对应。
4. 所有中间结果要能进入消息渲染块、task result 或 recovery snapshot，不能只存在内存。

---

## Deliverables

完成本批后，应得到：

1. 三个新工具：draft_generation / quality_review / todo_planner
2. 会话强关联 Todo 表与 CRUD 用例
3. RAG 结果与 Story Bible / chapter scope 对齐的检索入口
4. Memory Store 结构化快照扩展
5. 后端状态事件覆盖 `planning / executing / tool_call / waiting_approval / done / failed / story_bible_review / quality_review / todo_review`
6. Workbench 可展示当前阶段、工具卡片、审批卡片、故事圣经确认卡片、Todo 计划卡片
7. 端到端测试与验收总表

---

## Anti-Stub / Anti-Fake Implementation Gates

本批所有任务都必须额外通过以下反虚假实现检查：

- 不能只声明 tool definition，没有 handler 与 tool loop 集成测试
- 不能只新增 Todo SQL 表，没有 API / service / repository 测试
- 不能只发状态事件，没有前端消费断言
- 不能只新增前端组件占位，没有 SSE/polling 数据驱动测试
- 不能只保存 Memory JSON blob；字段必须有明确语义和测试
- 不能只把质量审查结果写成自由文本，必须结构化可复用
- 不能只做 RAG top_k 参数；必须把章节/version/filter 进入检索调用

---

## Task 1: 新增 Draft Generation Tool 并接入 tool loop

**Files:**
- Create: [`penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/definition/DraftGenerationToolDefinition.java`](penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/definition/DraftGenerationToolDefinition.java)
- Create: [`penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/handler/DraftGenerationToolHandler.java`](penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/handler/DraftGenerationToolHandler.java)
- Create: [`penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/support/DraftGenerationCommand.java`](penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/support/DraftGenerationCommand.java)
- Create: [`penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/support/DraftResultView.java`](penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/support/DraftResultView.java)
- Modify: [`penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/definition/InMemoryAgentToolDefinitionSource.java`](penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/definition/InMemoryAgentToolDefinitionSource.java:18)
- Modify: [`penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/gateway/ToolCallApplicationService.java`](penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/gateway/ToolCallApplicationService.java:41)
- Test: [`penmate-backend/src/test/java/com/penmate/backend/application/agent/tool/handler/DraftGenerationToolHandlerTest.java`](penmate-backend/src/test/java/com/penmate/backend/application/agent/tool/handler/DraftGenerationToolHandlerTest.java)
- Test: [`penmate-backend/src/test/java/com/penmate/backend/application/agent/orchestration/AgentToolLoopRunnerTest.java`](penmate-backend/src/test/java/com/penmate/backend/application/agent/orchestration/AgentToolLoopRunnerTest.java)

**Step 1: 先写失败测试**

断言：

- `draft_generation` 暴露 schema、展示名、治理策略
- 工具入参支持生成初稿、改写、修订
- 成功时返回结构化 `DraftResultView`
- tool loop 能把工具输出回填到下一轮消息
- 发布 `generation.tool_call` 事件时状态文案对应“生成正文 / 改写正文 / 套用修订”

**Step 2: 跑失败测试**

Run: [`mvn -Dtest=DraftGenerationToolHandlerTest,AgentToolLoopRunnerTest test`](penmate-backend/pom.xml)

Expected:

- 工具未注册或输出结构不匹配导致失败

**Step 3: 最小实现**

- 复用主模型调用入口，不创建第二套 LLM 网关
- handler 只负责正文生成/编辑，不做质量打分
- 输出包含 `draftText / operation / preservedConstraints / sourceSummary`
- 工具成功/失败/等待审批都记录日志

**Step 4: 复跑测试**

Run: [`mvn -Dtest=DraftGenerationToolHandlerTest,AgentToolLoopRunnerTest test`](penmate-backend/pom.xml)

Expected:

- 通过

**Step 5: 反架子检查**

- 不能只返回 `ok` 或空字符串
- 不能把正文直接塞到非结构化日志，不返回真实工具结果

**Step 6: Commit**

Run: [`git add penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/definition/DraftGenerationToolDefinition.java penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/handler/DraftGenerationToolHandler.java penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/support penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/gateway/ToolCallApplicationService.java penmate-backend/src/test/java/com/penmate/backend/application/agent/tool/handler/DraftGenerationToolHandlerTest.java penmate-backend/src/test/java/com/penmate/backend/application/agent/orchestration/AgentToolLoopRunnerTest.java && git commit -m "feat(agent): add draft generation tool"`](.gitignore)

---

## Task 2: 新增 Quality Review Tool 与有限轮次修订支撑

**Files:**
- Create: [`penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/definition/QualityReviewToolDefinition.java`](penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/definition/QualityReviewToolDefinition.java)
- Create: [`penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/handler/QualityReviewToolHandler.java`](penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/handler/QualityReviewToolHandler.java)
- Create: [`penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/support/QualityReportView.java`](penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/support/QualityReportView.java)
- Create: [`penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/support/RevisionSuggestionView.java`](penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/support/RevisionSuggestionView.java)
- Test: [`penmate-backend/src/test/java/com/penmate/backend/application/agent/tool/handler/QualityReviewToolHandlerTest.java`](penmate-backend/src/test/java/com/penmate/backend/application/agent/tool/handler/QualityReviewToolHandlerTest.java)

**Step 1: 先写失败测试**

断言：

- 审查结果是结构化对象，不是纯文本
- 覆盖：用户要求、人设一致性、剧情逻辑、时间线、世界观、角色知识边界
- 可输出 `needsRevision` 与 `revisionSuggestions`
- 自动修订轮次受上限控制

**Step 2: 跑失败测试**

Run: [`mvn -Dtest=QualityReviewToolHandlerTest test`](penmate-backend/pom.xml)

Expected:

- 失败

**Step 3: 最小实现**

- 输出 `score / issues / passes / needsRevision / riskFlags / suggestedActions`
- 结果可进入 `agent_task_results.output_structured_json`
- 不直接自循环无限修订，把轮次控制暴露给 orchestrator 决策

**Step 4: 复跑测试**

Run: [`mvn -Dtest=QualityReviewToolHandlerTest test`](penmate-backend/pom.xml)

Expected:

- 通过

**Step 5: 反架子检查**

- 不能仅返回“质量良好”一句话
- 不能没有结构化 issue 列表

**Step 6: Commit**

Run: [`git add penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/definition/QualityReviewToolDefinition.java penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/handler/QualityReviewToolHandler.java penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/support/QualityReportView.java penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/support/RevisionSuggestionView.java penmate-backend/src/test/java/com/penmate/backend/application/agent/tool/handler/QualityReviewToolHandlerTest.java && git commit -m "feat(agent): add quality review tool"`](.gitignore)

---

## Task 3: 新增 Todo Planner Tool

**Files:**
- Create: [`penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/definition/TodoPlannerToolDefinition.java`](penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/definition/TodoPlannerToolDefinition.java)
- Create: [`penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/handler/TodoPlannerToolHandler.java`](penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/handler/TodoPlannerToolHandler.java)
- Create: [`penmate-backend/src/main/java/com/penmate/backend/application/todo/TodoPlanView.java`](penmate-backend/src/main/java/com/penmate/backend/application/todo/TodoPlanView.java)
- Create: [`penmate-backend/src/main/java/com/penmate/backend/application/todo/TodoPlanItemView.java`](penmate-backend/src/main/java/com/penmate/backend/application/todo/TodoPlanItemView.java)
- Test: [`penmate-backend/src/test/java/com/penmate/backend/application/agent/tool/handler/TodoPlannerToolHandlerTest.java`](penmate-backend/src/test/java/com/penmate/backend/application/agent/tool/handler/TodoPlannerToolHandlerTest.java)

**Step 1: 先写失败测试**

断言：

- `todo_planner` 只产出 TodoPlan，不写库
- 支持用户任务拆解、质量问题转待办、后续修改规划
- 结果包含优先级、来源、推荐状态、是否建议自动创建

**Step 2: 跑失败测试**

Run: [`mvn -Dtest=TodoPlannerToolHandlerTest test`](penmate-backend/pom.xml)

Expected:

- 失败

**Step 3: 最小实现**

- 输出结构必须可直接渲染为前端卡片
- 记录来源：`USER_REQUEST / QUALITY_REVIEW / STORY_BIBLE_UPDATE / PLANNING`
- 自动建 Todo 只输出建议，不直接持久化

**Step 4: 复跑测试**

Run: [`mvn -Dtest=TodoPlannerToolHandlerTest test`](penmate-backend/pom.xml)

Expected:

- 通过

**Step 5: 反架子检查**

- 不能直接调用 repository 写库
- 不能只有一串 bullet string

**Step 6: Commit**

Run: [`git add penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/definition/TodoPlannerToolDefinition.java penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/handler/TodoPlannerToolHandler.java penmate-backend/src/main/java/com/penmate/backend/application/todo/TodoPlanView.java penmate-backend/src/main/java/com/penmate/backend/application/todo/TodoPlanItemView.java penmate-backend/src/test/java/com/penmate/backend/application/agent/tool/handler/TodoPlannerToolHandlerTest.java && git commit -m "feat(agent): add todo planner tool"`](.gitignore)

---

## Task 4: 新增 Todo DDD、SQL migration、CRUD 与会话强关联持久化

**Files:**
- Create: [`penmate-backend/src/main/resources/db/migration/V13__init_agent_session_todos.sql`](penmate-backend/src/main/resources/db/migration/V13__init_agent_session_todos.sql)
- Create: [`penmate-backend/src/main/java/com/penmate/backend/domain/todo/model/SessionTodo.java`](penmate-backend/src/main/java/com/penmate/backend/domain/todo/model/SessionTodo.java)
- Create: [`penmate-backend/src/main/java/com/penmate/backend/domain/todo/repository/SessionTodoRepository.java`](penmate-backend/src/main/java/com/penmate/backend/domain/todo/repository/SessionTodoRepository.java)
- Create: [`penmate-backend/src/main/java/com/penmate/backend/application/todo/TodoCrudApplicationService.java`](penmate-backend/src/main/java/com/penmate/backend/application/todo/TodoCrudApplicationService.java)
- Create: [`penmate-backend/src/main/java/com/penmate/backend/infrastructure/persistence/todo/SessionTodoMapper.java`](penmate-backend/src/main/java/com/penmate/backend/infrastructure/persistence/todo/SessionTodoMapper.java)
- Create: [`penmate-backend/src/main/java/com/penmate/backend/infrastructure/persistence/todo/SessionTodoRepositoryImpl.java`](penmate-backend/src/main/java/com/penmate/backend/infrastructure/persistence/todo/SessionTodoRepositoryImpl.java)
- Create: [`penmate-backend/src/main/java/com/penmate/backend/interfaces/api/todo/TodoController.java`](penmate-backend/src/main/java/com/penmate/backend/interfaces/api/todo/TodoController.java)
- Create: [`penmate-backend/src/main/java/com/penmate/backend/interfaces/api/todo/dto/CreateTodoDto.java`](penmate-backend/src/main/java/com/penmate/backend/interfaces/api/todo/dto/CreateTodoDto.java)
- Create: [`penmate-backend/src/main/java/com/penmate/backend/interfaces/api/todo/dto/UpdateTodoDto.java`](penmate-backend/src/main/java/com/penmate/backend/interfaces/api/todo/dto/UpdateTodoDto.java)
- Test: [`penmate-backend/src/test/java/com/penmate/backend/application/todo/TodoCrudApplicationServiceTest.java`](penmate-backend/src/test/java/com/penmate/backend/application/todo/TodoCrudApplicationServiceTest.java)
- Test: [`penmate-backend/src/test/java/com/penmate/backend/infrastructure/persistence/todo/SessionTodoSchemaMysqlContractTest.java`](penmate-backend/src/test/java/com/penmate/backend/infrastructure/persistence/todo/SessionTodoSchemaMysqlContractTest.java)
- Test: [`penmate-backend/src/test/java/com/penmate/backend/interfaces/api/todo/TodoControllerTest.java`](penmate-backend/src/test/java/com/penmate/backend/interfaces/api/todo/TodoControllerTest.java)

**Step 1: 先写失败测试**

覆盖：

- 表与 `session_id / task_id / source_type / todo_status / deleted_at` 关联
- 支持创建、批量创建、更新、删除、完成、按 session 查询、按状态筛选
- 接口层继续使用字符串业务 ID
- 删除为软删除

**Step 2: 跑失败测试**

Run: [`mvn -Dtest=TodoCrudApplicationServiceTest,SessionTodoSchemaMysqlContractTest,TodoControllerTest test`](penmate-backend/pom.xml)

Expected:

- 失败

**Step 3: 最小实现**

- Todo 与 session 强关联
- Main Orchestrator 后续通过应用服务触发持久化
- 所有写库异常映射为稳定错误并记录日志

**Step 4: 复跑测试**

Run: [`mvn -Dtest=TodoCrudApplicationServiceTest,SessionTodoSchemaMysqlContractTest,TodoControllerTest test`](penmate-backend/pom.xml)

Expected:

- 通过

**Step 5: 反架子检查**

- 不能只建表，没有 API/service
- 不能只在前端 local state 保存 todo
- 不能遗漏软删除字段和按 session 查询索引

**Step 6: Commit**

Run: [`git add penmate-backend/src/main/resources/db/migration/V13__init_agent_session_todos.sql penmate-backend/src/main/java/com/penmate/backend/domain/todo penmate-backend/src/main/java/com/penmate/backend/application/todo penmate-backend/src/main/java/com/penmate/backend/infrastructure/persistence/todo penmate-backend/src/main/java/com/penmate/backend/interfaces/api/todo penmate-backend/src/test/java/com/penmate/backend/application/todo penmate-backend/src/test/java/com/penmate/backend/infrastructure/persistence/todo penmate-backend/src/test/java/com/penmate/backend/interfaces/api/todo && git commit -m "feat(todo): add session scoped todo persistence"`](.gitignore)

---

## Task 5: 增强 RAG 混合检索并接入 Context Builder

**Files:**
- Modify: [`penmate-backend/src/main/java/com/penmate/backend/application/rag/RagApplicationService.java`](penmate-backend/src/main/java/com/penmate/backend/application/rag/RagApplicationService.java:17)
- Create: [`penmate-backend/src/main/java/com/penmate/backend/application/rag/HybridRagQuery.java`](penmate-backend/src/main/java/com/penmate/backend/application/rag/HybridRagQuery.java)
- Create: [`penmate-backend/src/main/java/com/penmate/backend/application/rag/HybridRagResultView.java`](penmate-backend/src/main/java/com/penmate/backend/application/rag/HybridRagResultView.java)
- Create: [`penmate-backend/src/main/java/com/penmate/backend/application/rag/RagSearchScope.java`](penmate-backend/src/main/java/com/penmate/backend/application/rag/RagSearchScope.java)
- Modify: [`penmate-backend/src/main/java/com/penmate/backend/application/agent/context/DefaultContextBuilder.java`](penmate-backend/src/main/java/com/penmate/backend/application/agent/context/DefaultContextBuilder.java)
- Modify: [`penmate-backend/src/main/java/com/penmate/backend/application/agent/context/ContextPackage.java`](penmate-backend/src/main/java/com/penmate/backend/application/agent/context/ContextPackage.java)
- Test: [`penmate-backend/src/test/java/com/penmate/backend/application/rag/RagApplicationServiceHybridSearchTest.java`](penmate-backend/src/test/java/com/penmate/backend/application/rag/RagApplicationServiceHybridSearchTest.java)
- Test: [`penmate-backend/src/test/java/com/penmate/backend/application/agent/context/DefaultContextBuilderTest.java`](penmate-backend/src/test/java/com/penmate/backend/application/agent/context/DefaultContextBuilderTest.java)

**Step 1: 先写失败测试**

覆盖：

- 检索输入包含 `projectId / sessionId / taskId / chapterId / storyBibleVersion / activatedSkills / intentTags / userMentionedEntities / topK`
- 混合排序同时考虑向量相似度、章节版本、Story Bible canon 重要性、用户显式提及、角色/伏笔相关性
- 检索结果包含 `sourceType / sourceId / reason / staleFlag / matchedVersion / relevanceScore`
- 不符合当前章节版本的结果被标记过期或排除
- [`DefaultContextBuilder`](penmate-backend/src/main/java/com/penmate/backend/application/agent/context/DefaultContextBuilder.java) 只消费 RAG 结果并统一治理，不在 RAG 服务中拼 prompt

**Step 2: 跑失败测试**

Run: [`mvn -Dtest=RagApplicationServiceHybridSearchTest,DefaultContextBuilderTest test`](penmate-backend/pom.xml)

Expected:

- 旧 RAG 只支持普通查询或缺少章节/version/filter 字段导致失败

**Step 3: 最小实现**

- 在 application/rag 层新增混合查询入参与结果视图，不新增平行 Story Bible 检索体系
- 复用现有 RAG repository / embedding / 文档索引能力，额外传入结构化 filter
- [`RagApplicationService`](penmate-backend/src/main/java/com/penmate/backend/application/rag/RagApplicationService.java:17) 记录 top_k、过滤条件、命中来源、过期判断日志
- RAG 检索结果通过按需工具调用返回，不进入 `ContextPackage` 的首轮上下文
- 注释说明：Story Bible 是长期知识库，RAG 是检索机制，进入 prompt 前必须经过 Context Builder

**Step 4: 复跑测试**

Run: [`mvn -Dtest=RagApplicationServiceHybridSearchTest,DefaultContextBuilderTest test`](penmate-backend/pom.xml)

Expected:

- 通过
- 现有 [`RagControllerTest`](penmate-backend/src/test/java/com/penmate/backend/interfaces/api/rag/RagControllerTest.java) 不因内部 query 结构演进而破坏接口契约

**Step 5: 反架子检查**

- 不能只新增 `topK` 参数
- 不能绕开现有 RAG 领域另造 Story Bible 专用检索服务
- 不能把检索结果直接拼进 prompt，必须经过 Context Builder
- 不能忽略当前章节版本和 stale 判断

**Step 6: Commit**

Run: [`git add penmate-backend/src/main/java/com/penmate/backend/application/rag penmate-backend/src/main/java/com/penmate/backend/application/agent/context penmate-backend/src/test/java/com/penmate/backend/application/rag/RagApplicationServiceHybridSearchTest.java penmate-backend/src/test/java/com/penmate/backend/application/agent/context/DefaultContextBuilderTest.java && git commit -m "feat(rag): add hybrid retrieval for agent context"`](.gitignore)

---

## Task 6: 扩展 Memory Store 结构化运行态与恢复快照

**Files:**
- Modify: [`penmate-backend/src/main/java/com/penmate/backend/domain/agent/model/AgentTaskContext.java`](penmate-backend/src/main/java/com/penmate/backend/domain/agent/model/AgentTaskContext.java:1)
- Modify: [`penmate-backend/src/main/java/com/penmate/backend/domain/agent/model/AgentTaskResult.java`](penmate-backend/src/main/java/com/penmate/backend/domain/agent/model/AgentTaskResult.java:1)
- Modify: [`penmate-backend/src/main/java/com/penmate/backend/application/agent/orchestration/AgentTaskRuntimeUpdater.java`](penmate-backend/src/main/java/com/penmate/backend/application/agent/orchestration/AgentTaskRuntimeUpdater.java)
- Modify: [`penmate-backend/src/main/java/com/penmate/backend/application/agent/orchestration/AgentTaskResultRecorder.java`](penmate-backend/src/main/java/com/penmate/backend/application/agent/orchestration/AgentTaskResultRecorder.java)
- Modify: [`penmate-backend/src/main/java/com/penmate/backend/infrastructure/persistence/agent/AgentMapper.java`](penmate-backend/src/main/java/com/penmate/backend/infrastructure/persistence/agent/AgentMapper.java)
- Modify: [`penmate-backend/src/main/java/com/penmate/backend/infrastructure/persistence/agent/AgentRepositoryImpl.java`](penmate-backend/src/main/java/com/penmate/backend/infrastructure/persistence/agent/AgentRepositoryImpl.java)
- Modify: [`penmate-backend/src/main/resources/db/migration/V11__init_agent_and_ops_domains.sql`](penmate-backend/src/main/resources/db/migration/V11__init_agent_and_ops_domains.sql:71)
- Test: [`penmate-backend/src/test/java/com/penmate/backend/application/agent/orchestration/AgentTaskRuntimeUpdaterTest.java`](penmate-backend/src/test/java/com/penmate/backend/application/agent/orchestration/AgentTaskRuntimeUpdaterTest.java)
- Test: [`penmate-backend/src/test/java/com/penmate/backend/application/agent/orchestration/AgentTaskResultRecorderTest.java`](penmate-backend/src/test/java/com/penmate/backend/application/agent/orchestration/AgentTaskResultRecorderTest.java)
- Test: [`penmate-backend/src/test/java/com/penmate/backend/interfaces/api/agent/AgentControllerRecoveryContractTest.java`](penmate-backend/src/test/java/com/penmate/backend/interfaces/api/agent/AgentControllerRecoveryContractTest.java)

**Step 1: 先写失败测试**

断言 Memory Store 不再是万能 JSON 垃圾箱，而是结构化保存：

- `taskProfileSnapshot`
- `promptPlanSnapshot`
- `contextPackageSnapshot`
- `activeToolCallsSnapshot`
- `draftSummary`
- `qualityReportSummary`
- `todoSummary`
- `storyBibleProposalSummary`
- `lastRuntimeStatus`
- `recoveryCursor`

**Step 2: 跑失败测试**

Run: [`mvn -Dtest=AgentTaskRuntimeUpdaterTest,AgentTaskResultRecorderTest,AgentControllerRecoveryContractTest test`](penmate-backend/pom.xml)

Expected:

- 旧 task context/result 字段无法区分运行态结构，恢复接口缺失新增摘要导致失败

**Step 3: 最小实现**

- 按当前项目约束直接修改既有 migration SQL 或新增当前批次 migration，不做兼容字段兜底和双写
- [`AgentTaskRuntimeUpdater`](penmate-backend/src/main/java/com/penmate/backend/application/agent/orchestration/AgentTaskRuntimeUpdater.java) 只负责运行态快照更新，不接管 tool 调度
- [`AgentTaskResultRecorder`](penmate-backend/src/main/java/com/penmate/backend/application/agent/orchestration/AgentTaskResultRecorder.java) 保存结构化工具结果摘要，供前端恢复和最终 render blocks 使用
- 写入关键步骤打日志：taskId、sessionId、snapshotType、recoveryCursor
- 注释说明 Memory Store 管理短期运行态，不替代 Story Bible 长期事实

**Step 4: 复跑测试**

Run: [`mvn -Dtest=AgentTaskRuntimeUpdaterTest,AgentTaskResultRecorderTest,AgentControllerRecoveryContractTest test`](penmate-backend/pom.xml)

Expected:

- 通过
- 恢复接口能返回当前任务阶段、工具摘要、Todo 摘要和 Story Bible proposal 摘要

**Step 5: 反架子检查**

- 不能只增加一个 `memory_json` 字段
- 不能把长期设定写入 Memory Store 冒充 Story Bible
- 不能只写日志不进入恢复快照
- 不能保留旧字段解析 fallback 与新字段双路并存

**Step 6: Commit**

Run: [`git add penmate-backend/src/main/java/com/penmate/backend/domain/agent/model/AgentTaskContext.java penmate-backend/src/main/java/com/penmate/backend/domain/agent/model/AgentTaskResult.java penmate-backend/src/main/java/com/penmate/backend/application/agent/orchestration/AgentTaskRuntimeUpdater.java penmate-backend/src/main/java/com/penmate/backend/application/agent/orchestration/AgentTaskResultRecorder.java penmate-backend/src/main/java/com/penmate/backend/infrastructure/persistence/agent penmate-backend/src/main/resources/db/migration penmate-backend/src/test/java/com/penmate/backend/application/agent/orchestration/AgentTaskRuntimeUpdaterTest.java penmate-backend/src/test/java/com/penmate/backend/application/agent/orchestration/AgentTaskResultRecorderTest.java penmate-backend/src/test/java/com/penmate/backend/interfaces/api/agent/AgentControllerRecoveryContractTest.java && git commit -m "feat(agent): structure runtime memory snapshots"`](.gitignore)

---

## Task 7: 实现 Task Runtime Status Publisher 与事件协议增强

**Files:**
- Create: [`penmate-backend/src/main/java/com/penmate/backend/application/agent/runtime/RuntimeStatusView.java`](penmate-backend/src/main/java/com/penmate/backend/application/agent/runtime/RuntimeStatusView.java)
- Create: [`penmate-backend/src/main/java/com/penmate/backend/application/agent/runtime/ToolCallStatusView.java`](penmate-backend/src/main/java/com/penmate/backend/application/agent/runtime/ToolCallStatusView.java)
- Create: [`penmate-backend/src/main/java/com/penmate/backend/application/agent/runtime/StoryBibleApprovalView.java`](penmate-backend/src/main/java/com/penmate/backend/application/agent/runtime/StoryBibleApprovalView.java)
- Create: [`penmate-backend/src/main/java/com/penmate/backend/application/agent/runtime/TaskRuntimeStatusPublisher.java`](penmate-backend/src/main/java/com/penmate/backend/application/agent/runtime/TaskRuntimeStatusPublisher.java)
- Create: [`penmate-backend/src/main/java/com/penmate/backend/application/agent/runtime/RealtimeTaskRuntimeStatusPublisher.java`](penmate-backend/src/main/java/com/penmate/backend/application/agent/runtime/RealtimeTaskRuntimeStatusPublisher.java)
- Modify: [`penmate-backend/src/main/java/com/penmate/backend/infrastructure/realtime/RealtimeEventServiceImpl.java`](penmate-backend/src/main/java/com/penmate/backend/infrastructure/realtime/RealtimeEventServiceImpl.java:22)
- Modify: [`penmate-backend/src/main/java/com/penmate/backend/application/agent/orchestration/AgentGenerationWorkflow.java`](penmate-backend/src/main/java/com/penmate/backend/application/agent/orchestration/AgentGenerationWorkflow.java)
- Modify: [`penmate-backend/src/main/java/com/penmate/backend/application/agent/orchestration/AgentToolLoopRunner.java`](penmate-backend/src/main/java/com/penmate/backend/application/agent/orchestration/AgentToolLoopRunner.java)
- Test: [`penmate-backend/src/test/java/com/penmate/backend/application/agent/runtime/TaskRuntimeStatusPublisherTest.java`](penmate-backend/src/test/java/com/penmate/backend/application/agent/runtime/TaskRuntimeStatusPublisherTest.java)
- Test: [`penmate-backend/src/test/java/com/penmate/backend/infrastructure/realtime/RealtimeEventServiceImplTest.java`](penmate-backend/src/test/java/com/penmate/backend/infrastructure/realtime/RealtimeEventServiceImplTest.java)
- Test: [`penmate-backend/src/test/java/com/penmate/backend/application/agent/orchestration/AgentGenerationWorkflowTest.java`](penmate-backend/src/test/java/com/penmate/backend/application/agent/orchestration/AgentGenerationWorkflowTest.java)

**Step 1: 先写失败测试**

事件协议至少覆盖：

- `generation.started` → `planning`
- `generation.status` → `planning / executing / story_bible_review / quality_review / todo_review`
- `generation.tool_call` → `tool_call`
- `generation.waiting_approval` → `waiting_approval`
- `generation.done` → `done`
- `generation.failed` → `failed`

并断言 payload 包含：

- `taskId / sessionId / turnId`
- `phase`
- `message`
- `toolCall`
- `approval`
- `storyBibleApproval`
- `todoPlan`
- `recoverable`
- `nextAction`

**Step 2: 跑失败测试**

Run: [`mvn -Dtest=TaskRuntimeStatusPublisherTest,RealtimeEventServiceImplTest,AgentGenerationWorkflowTest test`](penmate-backend/pom.xml)

Expected:

- 旧事件 payload 字段不足或 workflow 未在关键节点发布状态导致失败

**Step 3: 最小实现**

- 新增 publisher 作为 application 层端口，由 infrastructure realtime 服务发布事件
- [`AgentGenerationWorkflow`](penmate-backend/src/main/java/com/penmate/backend/application/agent/orchestration/AgentGenerationWorkflow.java) 在 preflight、prompt composed、context routed、tool loop start、quality review、todo review、waiting approval、done、failed 节点发布状态
- [`AgentToolLoopRunner`](penmate-backend/src/main/java/com/penmate/backend/application/agent/orchestration/AgentToolLoopRunner.java) 在每次工具调用前后发布工具状态，不绕过 workflow 快照
- 事件文案与前端展示枚举一一对应，不把文案散落在多个服务
- 关键状态发布打日志：eventType、phase、taskId、sessionId、toolName、recoverable

**Step 4: 复跑测试**

Run: [`mvn -Dtest=TaskRuntimeStatusPublisherTest,RealtimeEventServiceImplTest,AgentGenerationWorkflowTest test`](penmate-backend/pom.xml)

Expected:

- 通过
- 失败路径也能发布 `generation.failed` 且包含可展示原因

**Step 5: 反架子检查**

- 不能只定义 DTO，不接入 workflow/tool loop
- 不能只发 `done/failed`，必须覆盖中间阶段
- 不能让前端自行猜测状态文案
- 不能新增独立事件总线或平台化设施

**Step 6: Commit**

Run: [`git add penmate-backend/src/main/java/com/penmate/backend/application/agent/runtime penmate-backend/src/main/java/com/penmate/backend/infrastructure/realtime/RealtimeEventServiceImpl.java penmate-backend/src/main/java/com/penmate/backend/application/agent/orchestration/AgentGenerationWorkflow.java penmate-backend/src/main/java/com/penmate/backend/application/agent/orchestration/AgentToolLoopRunner.java penmate-backend/src/test/java/com/penmate/backend/application/agent/runtime penmate-backend/src/test/java/com/penmate/backend/infrastructure/realtime/RealtimeEventServiceImplTest.java penmate-backend/src/test/java/com/penmate/backend/application/agent/orchestration/AgentGenerationWorkflowTest.java && git commit -m "feat(agent): publish structured runtime statuses"`](.gitignore)

---

## Task 8: 接入 Todo 持久化、质量审查与 Story Bible proposal 的主编排闭环

**Files:**
- Modify: [`penmate-backend/src/main/java/com/penmate/backend/application/agent/orchestration/AgentGenerationWorkflow.java`](penmate-backend/src/main/java/com/penmate/backend/application/agent/orchestration/AgentGenerationWorkflow.java)
- Modify: [`penmate-backend/src/main/java/com/penmate/backend/application/agent/orchestration/AgentToolLoopRunner.java`](penmate-backend/src/main/java/com/penmate/backend/application/agent/orchestration/AgentToolLoopRunner.java)
- Modify: [`penmate-backend/src/main/java/com/penmate/backend/application/agent/orchestration/AgentTaskResultRecorder.java`](penmate-backend/src/main/java/com/penmate/backend/application/agent/orchestration/AgentTaskResultRecorder.java)
- Modify: [`penmate-backend/src/main/java/com/penmate/backend/application/todo/TodoCrudApplicationService.java`](penmate-backend/src/main/java/com/penmate/backend/application/todo/TodoCrudApplicationService.java)
- Modify: [`penmate-backend/src/main/java/com/penmate/backend/application/storybible/StoryBibleUpdateProposalService.java`](penmate-backend/src/main/java/com/penmate/backend/application/storybible/StoryBibleUpdateProposalService.java)
- Test: [`penmate-backend/src/test/java/com/penmate/backend/application/agent/orchestration/AgentGenerationWorkflowTest.java`](penmate-backend/src/test/java/com/penmate/backend/application/agent/orchestration/AgentGenerationWorkflowTest.java)
- Test: [`penmate-backend/src/test/java/com/penmate/backend/application/agent/orchestration/AgentTaskResultRecorderTest.java`](penmate-backend/src/test/java/com/penmate/backend/application/agent/orchestration/AgentTaskResultRecorderTest.java)
- Test: [`penmate-backend/src/test/java/com/penmate/backend/application/todo/TodoCrudApplicationServiceTest.java`](penmate-backend/src/test/java/com/penmate/backend/application/todo/TodoCrudApplicationServiceTest.java)

**Step 1: 先写失败测试**

覆盖完整闭环：

- `todo_planner` 工具只返回 `TodoPlan`
- workflow 根据配置和审批状态调用 [`TodoCrudApplicationService`](penmate-backend/src/main/java/com/penmate/backend/application/todo/TodoCrudApplicationService.java) 持久化 Todo
- Quality Review 结果进入 task result 并可触发有限轮次修订或 TodoPlan
- Story Bible proposal 进入审批/确认链路，不直接覆盖 canon
- 任一写库失败会发布 failed 或 recoverable 状态并记录日志

**Step 2: 跑失败测试**

Run: [`mvn -Dtest=AgentGenerationWorkflowTest,AgentTaskResultRecorderTest,TodoCrudApplicationServiceTest test`](penmate-backend/pom.xml)

Expected:

- 工具结果与主编排写库未闭环导致失败

**Step 3: 最小实现**

- workflow 读取 `TaskProfile` 中的 tool/skill 策略，决定是否触发 `quality_review` 与 `todo_planner`
- Todo 写库只能通过 application service，由 workflow 明确调用
- 自动修订轮次由 workflow 控制，禁止 Quality Review handler 自循环
- Story Bible 更新建议只进入 proposal/approval，不在本任务自动改 canon
- result recorder 保存 `DraftResultView / QualityReportView / TodoPlanView / StoryBibleApprovalView` 摘要

**Step 4: 复跑测试**

Run: [`mvn -Dtest=AgentGenerationWorkflowTest,AgentTaskResultRecorderTest,TodoCrudApplicationServiceTest test`](penmate-backend/pom.xml)

Expected:

- 通过
- WAITING_APPROVAL、审批恢复、失败恢复测试仍通过

**Step 5: 反架子检查**

- 不能让工具 handler 直接写 Todo 库
- 不能让 Story Bible proposal 绕过主编排落 canon
- 不能只有工具单测，没有 workflow 集成断言
- 不能把自动修订做成无限循环

**Step 6: Commit**

Run: [`git add penmate-backend/src/main/java/com/penmate/backend/application/agent/orchestration penmate-backend/src/main/java/com/penmate/backend/application/todo/TodoCrudApplicationService.java penmate-backend/src/main/java/com/penmate/backend/application/storybible/StoryBibleUpdateProposalService.java penmate-backend/src/test/java/com/penmate/backend/application/agent/orchestration penmate-backend/src/test/java/com/penmate/backend/application/todo/TodoCrudApplicationServiceTest.java && git commit -m "feat(agent): close tool todo and proposal workflow"`](.gitignore)

---

## Task 9: 增强 Workbench Runtime Presenter 与前端状态展示

**Files:**
- Modify: [`penmate-frontend/src/api/types.ts`](penmate-frontend/src/api/types.ts)
- Modify: [`penmate-frontend/src/api/modules/agent.api.spec.ts`](penmate-frontend/src/api/modules/agent.api.spec.ts)
- Modify: [`penmate-frontend/src/composables/workbench/useWorkbenchTaskRuntime.ts`](penmate-frontend/src/composables/workbench/useWorkbenchTaskRuntime.ts:36)
- Modify: [`penmate-frontend/src/composables/workbench/useWorkbenchSessionRecovery.ts`](penmate-frontend/src/composables/workbench/useWorkbenchSessionRecovery.ts)
- Create: [`penmate-frontend/src/composables/workbench/useWorkbenchRuntimePresenter.ts`](penmate-frontend/src/composables/workbench/useWorkbenchRuntimePresenter.ts)
- Create: [`penmate-frontend/src/components/workbench/RuntimeStatusCard.vue`](penmate-frontend/src/components/workbench/RuntimeStatusCard.vue)
- Create: [`penmate-frontend/src/components/workbench/ToolCallStatusCard.vue`](penmate-frontend/src/components/workbench/ToolCallStatusCard.vue)
- Create: [`penmate-frontend/src/components/workbench/TodoPlanCard.vue`](penmate-frontend/src/components/workbench/TodoPlanCard.vue)
- Create: [`penmate-frontend/src/components/workbench/StoryBibleApprovalCard.vue`](penmate-frontend/src/components/workbench/StoryBibleApprovalCard.vue)
- Modify: [`penmate-frontend/src/components/workbench/WorkbenchRightPanel.vue`](penmate-frontend/src/components/workbench/WorkbenchRightPanel.vue:1)
- Test: [`penmate-frontend/src/composables/workbench/useWorkbenchTaskRuntime.spec.ts`](penmate-frontend/src/composables/workbench/useWorkbenchTaskRuntime.spec.ts)
- Test: [`penmate-frontend/src/composables/workbench/useWorkbenchRuntimePresenter.spec.ts`](penmate-frontend/src/composables/workbench/useWorkbenchRuntimePresenter.spec.ts)
- Test: [`penmate-frontend/src/components/workbench/WorkbenchRightPanel.runtime.spec.ts`](penmate-frontend/src/components/workbench/WorkbenchRightPanel.runtime.spec.ts)
- Test: [`penmate-frontend/src/views/Workbench/index.chat-binding.spec.ts`](penmate-frontend/src/views/Workbench/index.chat-binding.spec.ts)

**Step 1: 先写失败测试**

覆盖：

- 解析 `generation.started / generation.status / generation.tool_call / generation.waiting_approval / generation.done / generation.failed`
- 将后端 phase 映射为用户文案：“正在分析请求 / 正在规划章节 / 正在生成正文 / 正在审查质量 / 正在整理故事圣经 / 正在整理待办 / 等待审批 / 已完成 / 执行失败”
- 右侧面板展示当前阶段、工具卡片、审批卡片、故事圣经确认卡片、Todo 计划卡片
- session recovery 后恢复同样的状态卡片，不依赖内存变量
- 失败时展示原因和下一步操作，不让用户只看到卡住

**Step 2: 跑失败测试**

Run: [`npm test -- --run src/composables/workbench/useWorkbenchTaskRuntime.spec.ts src/composables/workbench/useWorkbenchRuntimePresenter.spec.ts src/components/workbench/WorkbenchRightPanel.runtime.spec.ts src/views/Workbench/index.chat-binding.spec.ts`](penmate-frontend/package.json)

Expected:

- 旧前端只识别部分运行态或缺少右侧卡片导致失败

**Step 3: 最小实现**

- `useWorkbenchTaskRuntime` 只负责事件订阅、重连和状态源维护
- `useWorkbenchRuntimePresenter` 负责把结构化运行态转成 UI view model，避免文案散落组件
- [`WorkbenchRightPanel`](penmate-frontend/src/components/workbench/WorkbenchRightPanel.vue:1) 复用现有三栏工作台，不推翻布局
- 新卡片组件只展示后端事实和 recovery snapshot，不使用前端 local state 作为 Todo 真源
- 关键状态切换处增加前端日志，注释说明与后端事件协议对应关系

**Step 4: 复跑测试**

Run: [`npm test -- --run src/composables/workbench/useWorkbenchTaskRuntime.spec.ts src/composables/workbench/useWorkbenchRuntimePresenter.spec.ts src/components/workbench/WorkbenchRightPanel.runtime.spec.ts src/views/Workbench/index.chat-binding.spec.ts`](penmate-frontend/package.json)

Expected:

- 通过
- 旧 chat binding / recovery 用例仍通过

**Step 5: 反架子检查**

- 不能只新增静态卡片占位
- 不能前端自己猜工具状态，必须由事件/recovery 驱动
- 不能把 Todo 写成本地数组作为真实来源
- 不能保留旧事件解析 fallback 与新事件协议并行混用

**Step 6: Commit**

Run: [`git add penmate-frontend/src/api/types.ts penmate-frontend/src/api/modules/agent.api.spec.ts penmate-frontend/src/composables/workbench/useWorkbenchTaskRuntime.ts penmate-frontend/src/composables/workbench/useWorkbenchSessionRecovery.ts penmate-frontend/src/composables/workbench/useWorkbenchRuntimePresenter.ts penmate-frontend/src/components/workbench/RuntimeStatusCard.vue penmate-frontend/src/components/workbench/ToolCallStatusCard.vue penmate-frontend/src/components/workbench/TodoPlanCard.vue penmate-frontend/src/components/workbench/StoryBibleApprovalCard.vue penmate-frontend/src/components/workbench/WorkbenchRightPanel.vue penmate-frontend/src/composables/workbench/useWorkbenchTaskRuntime.spec.ts penmate-frontend/src/composables/workbench/useWorkbenchRuntimePresenter.spec.ts penmate-frontend/src/components/workbench/WorkbenchRightPanel.runtime.spec.ts penmate-frontend/src/views/Workbench/index.chat-binding.spec.ts && git commit -m "feat(workbench): show structured agent runtime status"`](.gitignore)

---

## Task 10: 端到端测试矩阵、接口契约与最终验收

**Files:**
- Create: [`penmate-backend/src/test/java/com/penmate/backend/interfaces/api/agent/AgentWorkflowEndToEndContractTest.java`](penmate-backend/src/test/java/com/penmate/backend/interfaces/api/agent/AgentWorkflowEndToEndContractTest.java)
- Create: [`penmate-frontend/src/views/Workbench/index.runtime-e2e.spec.ts`](penmate-frontend/src/views/Workbench/index.runtime-e2e.spec.ts)
- Modify: [`docs/plans/2026-05-12-prd-v1-batch-2-tools-todo-runtime-frontend.md`](docs/plans/2026-05-12-prd-v1-batch-2-tools-todo-runtime-frontend.md)

**Step 1: 先写失败测试**

端到端矩阵至少覆盖：

| Case | 用户请求 | 必须经过 | 必须展示 | 必须持久化 |
| --- | --- | --- | --- | --- |
| A | “续写本章并检查人设” | profile → context → draft_generation → quality_review | 生成正文、审查质量 | draft result、quality report、runtime snapshot |
| B | “把质量问题整理成待办” | quality_review → todo_planner → todo CRUD | Todo 计划卡片 | session todos |
| C | “整理这一章新增设定” | story bible proposal → waiting approval | 故事圣经更新待确认 | proposal summary、approval snapshot |
| D | “查询伏笔并续写第 42 章” | hybrid RAG → context builder → draft_generation | 检索命中说明、生成正文 | rag refs、context snapshot |
| E | 工具调用失败 | tool loop → failed status | 失败原因和下一步 | failed runtime snapshot |
| F | 刷新/重连 | recovery snapshot | 恢复当前阶段与卡片 | 不新增重复 Todo |

**Step 2: 跑失败测试**

Run backend: [`mvn -Dtest=AgentWorkflowEndToEndContractTest test`](penmate-backend/pom.xml)

Run frontend: [`npm test -- --run src/views/Workbench/index.runtime-e2e.spec.ts`](penmate-frontend/package.json)

Expected:

- 任一链路未接入时失败

**Step 3: 最小实现修正**

- 仅修正前面任务遗漏的调用链同步问题
- 不在验收任务中新增新架构、新平台、新兼容层
- 若发现接口 DTO、recovery snapshot、事件 payload、前端 presenter 不一致，按 PRD 要求全链路同步修正

**Step 4: 复跑测试**

Run backend: [`mvn -Dtest=AgentWorkflowEndToEndContractTest test`](penmate-backend/pom.xml)

Run frontend: [`npm test -- --run src/views/Workbench/index.runtime-e2e.spec.ts`](penmate-frontend/package.json)

Expected:

- 端到端矩阵全部通过

**Step 5: 全量回归**

Run backend: [`mvn test`](penmate-backend/pom.xml)

Run frontend: [`npm test -- --run`](penmate-frontend/package.json)

Expected:

- 后端全部测试通过
- 前端全部测试通过
- 无旧事件协议、旧 Todo fallback、旧 memory blob 兼容路径残留

**Step 6: Commit**

Run: [`git add penmate-backend/src/test/java/com/penmate/backend/interfaces/api/agent/AgentWorkflowEndToEndContractTest.java penmate-frontend/src/views/Workbench/index.runtime-e2e.spec.ts docs/plans/2026-05-12-prd-v1-batch-2-tools-todo-runtime-frontend.md && git commit -m "test(agent): add runtime workflow acceptance matrix"`](.gitignore)

---

## End-to-End Acceptance Checklist

实现本批后逐项勾选：

- [ ] `draft_generation` 已注册、可调用、可回填 tool loop，并返回结构化正文结果
- [ ] `quality_review` 已注册、可调用，输出结构化质量报告和修订建议
- [ ] `todo_planner` 只生成 TodoPlan，不直接写库
- [ ] Todo DDD 分层完整：domain / application / infrastructure / interfaces 均有对应测试
- [ ] Todo 以后端 SQL 为真实来源，按 session 强关联，支持软删除和状态筛选
- [ ] RAG 支持章节/version/filter 的混合检索，并由 Context Builder 统一治理后进入 prompt
- [ ] Memory Store 保存结构化运行态快照，不成为万能 JSON 垃圾箱
- [ ] Runtime Status Publisher 覆盖 planning / executing / tool_call / waiting_approval / done / failed / story_bible_review / quality_review / todo_review
- [ ] `generation.started / generation.status / generation.tool_call / generation.waiting_approval / generation.done / generation.failed` payload 与前端 presenter 字段一致
- [ ] Workbench 右侧面板展示阶段、工具、审批、故事圣经确认、Todo 计划和失败原因
- [ ] session recovery 后能恢复运行态卡片，不重复创建 Todo
- [ ] 高风险 Story Bible 更新只进入 proposal/approval，不直接覆盖 canon
- [ ] Quality Review 自动修订轮次有上限，不会无限循环
- [ ] 关键类、关键方法、关键业务步骤有注释和日志
- [ ] 变更调用链已全链路同步：接口 DTO、应用服务、主编排、上下文快照、事件、前端展示

---

## Anti-Overengineering / Anti-Compatibility Checklist

本批实现过程中禁止：

- [ ] 新增第二主编排中心、Supervisor workflow 或常驻多 Agent 主控
- [ ] 新增独立规则中心、独立事件总线、独立工作流引擎等额外工程化设施
- [ ] 为 Todo、Memory、Runtime Status 保留旧接口、旧 DTO、旧字段 fallback 或双写兼容层
- [ ] 把 SQL 变更绕过 migration，或用代码兜底 schema 差异
- [ ] 把 Story Bible 全量塞进 prompt 或把 Memory Store 当长期事实库
- [ ] 让 tool handler 直接承担主编排职责或绕过 application service 写库
- [ ] 让前端 local state 成为 Todo 或 runtime 的真实来源
- [ ] 只新增 DTO / 组件 / prompt 文件而没有调用方和测试

---

## Final Verification Commands

后端：

Run: [`mvn test`](penmate-backend/pom.xml)

Expected:

- 全部后端测试通过
- 无 migration 冲突
- 无旧兼容路径测试残留

前端：

Run: [`npm test -- --run`](penmate-frontend/package.json)

Expected:

- 全部前端测试通过
- Workbench runtime 相关测试覆盖事件、恢复和失败状态

人工验收：

1. 启动后端和前端。
2. 进入 Workbench，发送“续写本章并检查人设，把发现的问题整理成待办”。
3. 观察右侧面板依次出现分析请求、构建上下文、生成正文、审查质量、整理待办、完成。
4. 刷新页面或断开重连，确认右侧状态卡片由 recovery snapshot 恢复。
5. 在数据库检查 Todo 与当前 session 强关联。
6. 触发 Story Bible 高风险更新，确认前端展示“故事圣经更新待确认”，且 canon 未被自动覆盖。
7. 触发工具失败，确认前端展示失败原因和下一步，而不是一直 loading。

---

## Batch Boundary Notes

本批完成后边界如下：

- 第 1 批负责 Task Profiler、Prompt Composer、Story Bible、Context Builder 基础能力。
- 第 2 批负责 tools、Todo、RAG 混合检索、Memory Store、Runtime Status 与 Workbench 用户感知。
- 本批不新增多 Agent 主控、不新增独立 workflow engine、不新增平台化规则中心。
- 本批所有写库都必须通过 DDD application service 或主编排可追踪触发。
- 本批落地后，旧事件协议、旧 Todo fallback、旧 memory blob 兼容路径应删除，不保留新旧并行。
- 后续批次如继续扩展，只能在当前单主编排与事件协议上演进，不重新开平行链路。

---

## Final Commit

Run: [`git add docs/plans/2026-05-12-prd-v1-batch-2-tools-todo-runtime-frontend.md && git commit -m "docs(plan): complete prd v1 batch 2 implementation plan"`](.gitignore)

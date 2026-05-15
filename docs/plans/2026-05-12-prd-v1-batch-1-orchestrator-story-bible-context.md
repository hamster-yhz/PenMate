# PRD v1 Batch 1 Orchestrator + Story Bible + Context Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use [executing-plans] mode to implement this plan task-by-task.

**Goal:** 在不推翻现有单 Main Orchestrator 架构的前提下，为 PenMate 主链路补齐 Task Profiler、Prompt Composer、Story Bible、Context Builder 与主编排快照/恢复扩展。

**Architecture:** 本批次围绕现有 [`AgentGenerationWorkflow`](penmate-backend/src/main/java/com/penmate/backend/application/agent/orchestration/AgentGenerationWorkflow.java:33) 扩展前置判定、提示词组合与上下文构建能力，不引入第二主控。Story Bible 作为新的 DDD 领域模块接入，通过 application/context 扩展点为主编排提供可裁剪上下文，并把关键中间产物落入 task snapshot / result / logs，供恢复、审批与前端后续展示链路复用。

**Tech Stack:** Java 21、Spring Boot 3.3、MyBatis、Flyway、LangChain4j、MySQL、JUnit 5、Mockito

---

## Scope

本批计划覆盖以下 PRD 目标：

- Task Profiler（演进现有 preflight）
- Prompt Composer / Skill Prompt Injector
- Story Bible DDD 领域建模、初始化、更新建议、版本化基础结构
- Context Builder（演进现有 context routing）
- Main Orchestrator 主链路接入与 task snapshot 扩展
- 与后续 Tools / Todo / Runtime Status / Frontend 批次对接所需的结构化数据边界

本批计划**不**直接实现以下功能，它们将在第 2 批单独落地：

- Draft Generation / Quality Review / Todo Planner tools
- Todo CRUD / 持久化
- RAG 混合检索增强
- Memory Store 扩展
- Runtime Status 新事件协议
- Workbench 前端展示与最终验收总表

---

## Current Baseline Summary

现有主链路和约束点：

- 主编排核心在 [`AgentGenerationWorkflow`](penmate-backend/src/main/java/com/penmate/backend/application/agent/orchestration/AgentGenerationWorkflow.java:33)
- 现有 preflight 在 [`DefaultAgentPreflightCoordinator`](penmate-backend/src/main/java/com/penmate/backend/application/agent/orchestration/preflight/DefaultAgentPreflightCoordinator.java:23)
- 现有 prompt 装配在 [`AgentPromptAssembler`](penmate-backend/src/main/java/com/penmate/backend/application/agent/orchestration/AgentPromptAssembler.java:20)
- 现有 context routing 在 [`DefaultAgentContextRoutingFacade`](penmate-backend/src/main/java/com/penmate/backend/application/agent/context/DefaultAgentContextRoutingFacade.java:14)
- 当前 Story Bible 只有 noop provider，占位点在 [`NoopStoryBibleContextProvider`](penmate-backend/src/main/java/com/penmate/backend/application/agent/context/NoopStoryBibleContextProvider.java:1)
- 任务快照主表在 [`V11__init_agent_and_ops_domains.sql`](penmate-backend/src/main/resources/db/migration/V11__init_agent_and_ops_domains.sql:71)
- 现有运行时上下文快照在 [`agent_task_contexts`](penmate-backend/src/main/resources/db/migration/V11__init_agent_and_ops_domains.sql:98)

因此本批次的正确改造方向是：

1. 保留 [`AgentGenerationWorkflow`](penmate-backend/src/main/java/com/penmate/backend/application/agent/orchestration/AgentGenerationWorkflow.java:33) 为唯一长流程中心。
2. 把现有 preflight 重构为可序列化的 Task Profiler，而不是新增新的 Agent supervisor。
3. 把现有 prompt 装配演进为 Prompt Composer，并保留 [`SystemPromptProvider`](penmate-backend/src/main/java/com/penmate/backend/application/agent/prompt/SystemPromptProvider.java:1) 作为底座。
4. 把 Story Bible 作为新的 domain/application/infrastructure/interfaces 模块引入，再挂接到现有 context provider 扩展点。
5. 快照字段必须直接进入现有 migration 体系，不做兼容层或双写。

---

## Deliverables

完成本批后，应得到：

1. 新的 Story Bible 表结构与仓储接口
2. 新的 TaskProfile / PromptPlan / ContextPackage / StoryBibleUpdateProposal 等稳定模型
3. [`AgentGenerationWorkflow`](penmate-backend/src/main/java/com/penmate/backend/application/agent/orchestration/AgentGenerationWorkflow.java:33) 接入 Task Profiler → Prompt Composer → Context Builder
4. task snapshot 持久化可恢复 TaskProfile / PromptPlan / ContextPackage / Story Bible proposal
5. 无 Story Bible 时可 fallback，但必须标记 missing / proposed，不可伪装成 canon
6. 单测覆盖主链路行为，防止只落类壳子

---

## Anti-Stub / Anti-Fake Implementation Gates

每个任务完成后都必须检查以下反架子点：

- 不是只新增空类；必须至少有 1 个调用方接入主链路
- 不是只新增 DTO；必须至少有 1 个测试验证真实字段流转
- 不是只改 prompt 文件；必须至少有 1 个服务真正消费新 prompt plan
- 不是只建表；必须至少有 repository / mapper / service 测试读取或写入
- 不是只做 Story Bible entity；必须至少有 context builder 按章节版本选择条目
- 不是只做 preflight 新字段；必须至少有 workflow snapshot 写入并在恢复链路可读取
- 不是只打印日志；必须有断言日志前后的行为变化

---

## Task 1: 建立 Story Bible DDD 边界与 SQL migration

**Files:**
- Create: [`penmate-backend/src/main/resources/db/migration/V12__init_story_bible_domain.sql`](penmate-backend/src/main/resources/db/migration/V12__init_story_bible_domain.sql)
- Create: [`penmate-backend/src/main/java/com/penmate/backend/domain/storybible/model/StoryBible.java`](penmate-backend/src/main/java/com/penmate/backend/domain/storybible/model/StoryBible.java)
- Create: [`penmate-backend/src/main/java/com/penmate/backend/domain/storybible/model/StoryBibleEntry.java`](penmate-backend/src/main/java/com/penmate/backend/domain/storybible/model/StoryBibleEntry.java)
- Create: [`penmate-backend/src/main/java/com/penmate/backend/domain/storybible/model/StoryBibleVersion.java`](penmate-backend/src/main/java/com/penmate/backend/domain/storybible/model/StoryBibleVersion.java)
- Create: [`penmate-backend/src/main/java/com/penmate/backend/domain/storybible/model/StoryBibleSourceRef.java`](penmate-backend/src/main/java/com/penmate/backend/domain/storybible/model/StoryBibleSourceRef.java)
- Create: [`penmate-backend/src/main/java/com/penmate/backend/domain/storybible/repository/StoryBibleRepository.java`](penmate-backend/src/main/java/com/penmate/backend/domain/storybible/repository/StoryBibleRepository.java)
- Create: [`penmate-backend/src/main/java/com/penmate/backend/infrastructure/persistence/storybible/StoryBibleMapper.java`](penmate-backend/src/main/java/com/penmate/backend/infrastructure/persistence/storybible/StoryBibleMapper.java)
- Create: [`penmate-backend/src/main/java/com/penmate/backend/infrastructure/persistence/storybible/StoryBibleRepositoryImpl.java`](penmate-backend/src/main/java/com/penmate/backend/infrastructure/persistence/storybible/StoryBibleRepositoryImpl.java)
- Test: [`penmate-backend/src/test/java/com/penmate/backend/infrastructure/persistence/storybible/StoryBibleSchemaMysqlContractTest.java`](penmate-backend/src/test/java/com/penmate/backend/infrastructure/persistence/storybible/StoryBibleSchemaMysqlContractTest.java)
- Test: [`penmate-backend/src/test/java/com/penmate/backend/infrastructure/persistence/storybible/StoryBibleRepositoryImplTest.java`](penmate-backend/src/test/java/com/penmate/backend/infrastructure/persistence/storybible/StoryBibleRepositoryImplTest.java)

**Step 1: 写失败测试，锁定表结构与仓储最小能力**

先写失败测试，覆盖：

- `story_bibles` 主表存在，关联 `project_id`
- `story_bible_entries` 支持 `entry_type / canonical_status / risk_level / valid_from_chapter_id / valid_to_chapter_id`
- `story_bible_versions` 支持版本号与变更摘要
- entry 存储 `source_refs_json`
- repository 能按 `projectId + chapterId` 查询当前有效 canon + proposed 条目

示例测试点：

- 断言 migration SQL 包含表注释、字段注释、索引
- 断言仓储 `findActiveEntries(projectId, chapterId)` 能基于章节边界过滤版本

**Step 2: 运行失败测试确认基线为空实现**

Run: [`mvn -Dtest=StoryBibleSchemaMysqlContractTest,StoryBibleRepositoryImplTest test`](penmate-backend/pom.xml)

Expected:

- 测试失败
- 报出表不存在、mapper 未实现或查询结果为空

**Step 3: 实现最小可用 schema 与持久化**

实现建议：

- `story_bibles`：项目级聚合根
- `story_bible_entries`：结构化条目，字段包含 `canon/proposed/assumption`
- `story_bible_versions`：聚合版本快照头
- 使用 MyBatis mapper 完成插入、按章节过滤查询、按风险级别查询 proposal
- 关键注释说明：Story Bible 是长期知识库，不等于 prompt 大文本

**Step 4: 运行测试验证通过**

Run: [`mvn -Dtest=StoryBibleSchemaMysqlContractTest,StoryBibleRepositoryImplTest test`](penmate-backend/pom.xml)

Expected:

- 目标测试全部通过
- 无 legacy story bible 兼容结构

**Step 5: 反架子检查**

- 不能只有 SQL 没仓储
- 不能只有 entity 没 mapper
- 不能只支持全量加载，必须支持按章节版本过滤

**Step 6: Commit**

Run: [`git add penmate-backend/src/main/resources/db/migration/V12__init_story_bible_domain.sql penmate-backend/src/main/java/com/penmate/backend/domain/storybible penmate-backend/src/main/java/com/penmate/backend/infrastructure/persistence/storybible penmate-backend/src/test/java/com/penmate/backend/infrastructure/persistence/storybible && git commit -m "feat(agent): add story bible domain schema"`](.gitignore)

---

## Task 2: 定义 TaskProfile / PromptPlan / ContextPackage 等稳定模型

**Files:**
- Create: [`penmate-backend/src/main/java/com/penmate/backend/application/agent/orchestration/profile/TaskProfile.java`](penmate-backend/src/main/java/com/penmate/backend/application/agent/orchestration/profile/TaskProfile.java)
- Create: [`penmate-backend/src/main/java/com/penmate/backend/application/agent/orchestration/profile/TaskIntentTag.java`](penmate-backend/src/main/java/com/penmate/backend/application/agent/orchestration/profile/TaskIntentTag.java)
- Create: [`penmate-backend/src/main/java/com/penmate/backend/application/agent/prompt/PromptModulePlan.java`](penmate-backend/src/main/java/com/penmate/backend/application/agent/prompt/PromptModulePlan.java)
- Create: [`penmate-backend/src/main/java/com/penmate/backend/application/agent/prompt/PromptPlan.java`](penmate-backend/src/main/java/com/penmate/backend/application/agent/prompt/PromptPlan.java)
- Create: [`penmate-backend/src/main/java/com/penmate/backend/application/agent/context/ContextPackage.java`](penmate-backend/src/main/java/com/penmate/backend/application/agent/context/ContextPackage.java)
- Create: [`penmate-backend/src/main/java/com/penmate/backend/application/storybible/StoryBibleUpdateProposal.java`](penmate-backend/src/main/java/com/penmate/backend/application/storybible/StoryBibleUpdateProposal.java)
- Test: [`penmate-backend/src/test/java/com/penmate/backend/application/agent/orchestration/profile/TaskProfileSerializationTest.java`](penmate-backend/src/test/java/com/penmate/backend/application/agent/orchestration/profile/TaskProfileSerializationTest.java)
- Test: [`penmate-backend/src/test/java/com/penmate/backend/application/agent/context/ContextPackageContractTest.java`](penmate-backend/src/test/java/com/penmate/backend/application/agent/context/ContextPackageContractTest.java)

**Step 1: 先写契约测试，约束字段命名稳定**

覆盖：

- `TaskProfile` 包含多标签 intent、executionProfile、需要的 skills、需要的 tools、hardConstraints、outputExpectation、needsApproval、includeStoryBible、includeRag、reasoningSummary
- `PromptPlan` 包含 modules、skills、finalProfile、assembledPromptPreview
- `ContextPackage` 包含 sources、missingContextFlags、conflicts、storyBibleEntries、ragRefs、styleSnapshot、chapterScope
- 所有对象可被 Jackson 序列化/反序列化

**Step 2: 跑失败测试**

Run: [`mvn -Dtest=TaskProfileSerializationTest,ContextPackageContractTest test`](penmate-backend/pom.xml)

Expected:

- 字段缺失或对象不存在导致失败

**Step 3: 实现最小模型**

要求：

- 使用不可变 record 或等价不可变对象
- 字段命名与 PRD 统一，不引入第二套语义名词
- 注释说明哪些字段进入 snapshot，哪些字段只用于运行时

**Step 4: 复跑测试**

Run: [`mvn -Dtest=TaskProfileSerializationTest,ContextPackageContractTest test`](penmate-backend/pom.xml)

Expected:

- 通过

**Step 5: 反架子检查**

- 不能只有 Java 类名，必须有 JSON round-trip 测试
- 不能让同一语义出现多个命名，如 `taskType` / `behaviorType` / `intent` 混乱并存

**Step 6: Commit**

Run: [`git add penmate-backend/src/main/java/com/penmate/backend/application/agent/orchestration/profile penmate-backend/src/main/java/com/penmate/backend/application/agent/prompt penmate-backend/src/main/java/com/penmate/backend/application/agent/context/ContextPackage.java penmate-backend/src/main/java/com/penmate/backend/application/storybible/StoryBibleUpdateProposal.java penmate-backend/src/test/java/com/penmate/backend/application/agent/orchestration/profile penmate-backend/src/test/java/com/penmate/backend/application/agent/context && git commit -m "feat(agent): define profile prompt and context contracts"`](.gitignore)

---

## Task 3: 将现有 preflight 演进为 Task Profiler

**Files:**
- Modify: [`penmate-backend/src/main/java/com/penmate/backend/application/agent/orchestration/preflight/AgentPreflightDecision.java`](penmate-backend/src/main/java/com/penmate/backend/application/agent/orchestration/preflight/AgentPreflightDecision.java)
- Modify: [`penmate-backend/src/main/java/com/penmate/backend/application/agent/orchestration/preflight/AgentBehaviorType.java`](penmate-backend/src/main/java/com/penmate/backend/application/agent/orchestration/preflight/AgentBehaviorType.java)
- Modify: [`penmate-backend/src/main/java/com/penmate/backend/application/agent/orchestration/preflight/DefaultAgentPreflightCoordinator.java`](penmate-backend/src/main/java/com/penmate/backend/application/agent/orchestration/preflight/DefaultAgentPreflightCoordinator.java:23)
- Modify: [`penmate-backend/src/main/resources/prompts/agent/system/preflight/default/20-output-contract.md`](penmate-backend/src/main/resources/prompts/agent/system/preflight/default/20-output-contract.md)
- Create: [`penmate-backend/src/main/java/com/penmate/backend/application/agent/orchestration/profile/TaskProfileMapper.java`](penmate-backend/src/main/java/com/penmate/backend/application/agent/orchestration/profile/TaskProfileMapper.java)
- Test: [`penmate-backend/src/test/java/com/penmate/backend/application/agent/orchestration/preflight/DefaultAgentPreflightCoordinatorTest.java`](penmate-backend/src/test/java/com/penmate/backend/application/agent/orchestration/preflight/DefaultAgentPreflightCoordinatorTest.java)
- Test: [`penmate-backend/src/test/java/com/penmate/backend/application/agent/orchestration/profile/TaskProfileMapperTest.java`](penmate-backend/src/test/java/com/penmate/backend/application/agent/orchestration/profile/TaskProfileMapperTest.java)

**Step 1: 先补失败测试，要求 preflight 输出真正结构化**

新增测试断言：

- 能解析多标签意图
- 能解析 hard constraints
- 能解析 skill/tool enablement
- 能解析 `needsStoryBibleUpdate`
- 严重歧义时标记 `needsClarification`，轻歧义不阻断执行
- 关键日志包含 behaviorType、executionProfile、storyBibleFlag、ragFlag、approvalFlag

**Step 2: 跑失败测试**

Run: [`mvn -Dtest=DefaultAgentPreflightCoordinatorTest,TaskProfileMapperTest test`](penmate-backend/pom.xml)

Expected:

- 旧版 decision 字段不足导致失败

**Step 3: 最小实现**

实现规则：

- 保留 [`DefaultAgentPreflightCoordinator`](penmate-backend/src/main/java/com/penmate/backend/application/agent/orchestration/preflight/DefaultAgentPreflightCoordinator.java:23) 作为入口
- `AgentPreflightDecision` 可保留，但内部要能映射到 `TaskProfile`
- preflight prompt 输出 contract 明确 JSON 字段，不再只返回简单 profile
- `TaskProfileMapper` 把 preflight decision 转成稳定结构，供 workflow / prompt / context 统一消费

**Step 4: 复跑测试**

Run: [`mvn -Dtest=DefaultAgentPreflightCoordinatorTest,TaskProfileMapperTest test`](penmate-backend/pom.xml)

Expected:

- 通过
- 旧测试无破坏

**Step 5: 反架子检查**

- 不能只多加 JSON 字段却没人消费
- 不能只做 `reasoningSummary` 文本增强，必须有可序列化标签集合
- 不能把 Task Profiler 做成第二个 workflow/service 入口

**Step 6: Commit**

Run: [`git add penmate-backend/src/main/java/com/penmate/backend/application/agent/orchestration/preflight penmate-backend/src/main/java/com/penmate/backend/application/agent/orchestration/profile/TaskProfileMapper.java penmate-backend/src/main/resources/prompts/agent/system/preflight/default/20-output-contract.md penmate-backend/src/test/java/com/penmate/backend/application/agent/orchestration/preflight penmate-backend/src/test/java/com/penmate/backend/application/agent/orchestration/profile/TaskProfileMapperTest.java && git commit -m "feat(agent): evolve preflight into task profiler"`](.gitignore)

---

## Task 4: 将现有 prompt 装配演进为 Prompt Composer

**Files:**
- Modify: [`penmate-backend/src/main/java/com/penmate/backend/application/agent/orchestration/AgentPromptAssembler.java`](penmate-backend/src/main/java/com/penmate/backend/application/agent/orchestration/AgentPromptAssembler.java:20)
- Create: [`penmate-backend/src/main/java/com/penmate/backend/application/agent/prompt/PromptComposer.java`](penmate-backend/src/main/java/com/penmate/backend/application/agent/prompt/PromptComposer.java)
- Create: [`penmate-backend/src/main/java/com/penmate/backend/application/agent/prompt/SkillPromptRegistry.java`](penmate-backend/src/main/java/com/penmate/backend/application/agent/prompt/SkillPromptRegistry.java)
- Create: [`penmate-backend/src/main/resources/prompts/agent/system/skills/planner/00-base-role.md`](penmate-backend/src/main/resources/prompts/agent/system/skills/planner/00-base-role.md)
- Create: [`penmate-backend/src/main/resources/prompts/agent/system/skills/writer/00-base-role.md`](penmate-backend/src/main/resources/prompts/agent/system/skills/writer/00-base-role.md)
- Create: [`penmate-backend/src/main/resources/prompts/agent/system/skills/editor/00-base-role.md`](penmate-backend/src/main/resources/prompts/agent/system/skills/editor/00-base-role.md)
- Create: [`penmate-backend/src/main/resources/prompts/agent/system/skills/checker/00-base-role.md`](penmate-backend/src/main/resources/prompts/agent/system/skills/checker/00-base-role.md)
- Create: [`penmate-backend/src/main/resources/prompts/agent/system/skills/story-bible/00-base-role.md`](penmate-backend/src/main/resources/prompts/agent/system/skills/story-bible/00-base-role.md)
- Test: [`penmate-backend/src/test/java/com/penmate/backend/application/agent/prompt/PromptComposerTest.java`](penmate-backend/src/test/java/com/penmate/backend/application/agent/prompt/PromptComposerTest.java)

**Step 1: 先写失败测试锁定组合规则**

断言：

- 用户明确要求优先于 skill prompt
- 不同 task profile 激活不同 skill prompt
- world-build / rewrite / default profile 与现有 execution bundle 对齐
- story bible 与 context package 不能在 composer 中拼装查询逻辑，只能消费已构建结果
- 组合结果包含模块来源，便于日志与快照

**Step 2: 跑失败测试**

Run: [`mvn -Dtest=PromptComposerTest test`](penmate-backend/pom.xml)

Expected:

- 旧 [`AgentPromptAssembler`](penmate-backend/src/main/java/com/penmate/backend/application/agent/orchestration/AgentPromptAssembler.java:20) 无法满足模块化断言

**Step 3: 最小实现**

- 新增 `PromptComposer`
- `AgentPromptAssembler` 退化为把 `PromptPlan + ContextPackage + user_request` 拼成消息，而不是自行决定所有逻辑
- 技能 prompt 文件模块化存放，禁止大段硬编码字符串

**Step 4: 复跑测试**

Run: [`mvn -Dtest=PromptComposerTest test`](penmate-backend/pom.xml)

Expected:

- 通过

**Step 5: 反架子检查**

- 不能只增加 skill prompt markdown 文件，没有 Java 消费端
- 不能继续在 assembler 里手写 profile 判断分支而绕开 composer
- 不能让 Prompt Composer 直接查 Story Bible 仓储

**Step 6: Commit**

Run: [`git add penmate-backend/src/main/java/com/penmate/backend/application/agent/prompt penmate-backend/src/main/java/com/penmate/backend/application/agent/orchestration/AgentPromptAssembler.java penmate-backend/src/main/resources/prompts/agent/system/skills penmate-backend/src/test/java/com/penmate/backend/application/agent/prompt/PromptComposerTest.java && git commit -m "feat(agent): add prompt composer and skill prompt registry"`](.gitignore)

---

## Task 5: 实现 Story Bible 初始化、查询与更新建议应用服务

**Files:**
- Create: [`penmate-backend/src/main/java/com/penmate/backend/application/storybible/StoryBibleApplicationService.java`](penmate-backend/src/main/java/com/penmate/backend/application/storybible/StoryBibleApplicationService.java)
- Create: [`penmate-backend/src/main/java/com/penmate/backend/application/storybible/StoryBibleInitializationService.java`](penmate-backend/src/main/java/com/penmate/backend/application/storybible/StoryBibleInitializationService.java)
- Create: [`penmate-backend/src/main/java/com/penmate/backend/application/storybible/StoryBibleUpdateProposalService.java`](penmate-backend/src/main/java/com/penmate/backend/application/storybible/StoryBibleUpdateProposalService.java)
- Create: [`penmate-backend/src/main/java/com/penmate/backend/application/storybible/StoryBibleVersionSelector.java`](penmate-backend/src/main/java/com/penmate/backend/application/storybible/StoryBibleVersionSelector.java)
- Test: [`penmate-backend/src/test/java/com/penmate/backend/application/storybible/StoryBibleApplicationServiceTest.java`](penmate-backend/src/test/java/com/penmate/backend/application/storybible/StoryBibleApplicationServiceTest.java)
- Test: [`penmate-backend/src/test/java/com/penmate/backend/application/storybible/StoryBibleVersionSelectorTest.java`](penmate-backend/src/test/java/com/penmate/backend/application/storybible/StoryBibleVersionSelectorTest.java)

**Step 1: 写失败测试，覆盖来源保留与版本边界**

用例至少包含：

- 一句话创意 → 生成 proposed story bible 草案
- 已有章节正文 → 抽取角色/地点/事件/信息边界为 proposal
- 高风险更新不会自动转 canon
- 第 42 章与第 45 章针对同一角色秘密知道状态返回不同版本

**Step 2: 跑失败测试**

Run: [`mvn -Dtest=StoryBibleApplicationServiceTest,StoryBibleVersionSelectorTest test`](penmate-backend/pom.xml)

Expected:

- 失败

**Step 3: 最小实现**

- 初始化只产出 proposal，不直接写 canon
- proposal 中保存 source text / source chapter / inference level
- 版本选择逻辑集中在 `StoryBibleVersionSelector`
- 更新建议服务仅返回结构化 proposal 列表，不直接落库为已确认事实

**Step 4: 复跑测试**

Run: [`mvn -Dtest=StoryBibleApplicationServiceTest,StoryBibleVersionSelectorTest test`](penmate-backend/pom.xml)

Expected:

- 通过

**Step 5: 反架子检查**

- 不能只把整段 chapter 内容塞进 story bible.content
- 不能没有版本过滤逻辑
- 不能把 proposed 与 canon 混成一个状态

**Step 6: Commit**

Run: [`git add penmate-backend/src/main/java/com/penmate/backend/application/storybible penmate-backend/src/test/java/com/penmate/backend/application/storybible && git commit -m "feat(agent): add story bible initialization and proposal services"`](.gitignore)

---

## Task 6: 将现有 context routing 演进为 Context Builder

**Files:**
- Modify: [`penmate-backend/src/main/java/com/penmate/backend/application/agent/context/DefaultAgentContextRoutingFacade.java`](penmate-backend/src/main/java/com/penmate/backend/application/agent/context/DefaultAgentContextRoutingFacade.java:14)
- Modify: [`penmate-backend/src/main/java/com/penmate/backend/application/agent/context/StoryBibleContextProvider.java`](penmate-backend/src/main/java/com/penmate/backend/application/agent/context/StoryBibleContextProvider.java:1)
- Create: [`penmate-backend/src/main/java/com/penmate/backend/application/agent/context/DefaultContextBuilder.java`](penmate-backend/src/main/java/com/penmate/backend/application/agent/context/DefaultContextBuilder.java)
- Create: [`penmate-backend/src/main/java/com/penmate/backend/application/agent/context/ContextBudgetPolicy.java`](penmate-backend/src/main/java/com/penmate/backend/application/agent/context/ContextBudgetPolicy.java)
- Create: [`penmate-backend/src/main/java/com/penmate/backend/application/agent/context/StoryBibleContextEntryView.java`](penmate-backend/src/main/java/com/penmate/backend/application/agent/context/StoryBibleContextEntryView.java)
- Test: [`penmate-backend/src/test/java/com/penmate/backend/application/agent/context/DefaultAgentContextRoutingFacadeTest.java`](penmate-backend/src/test/java/com/penmate/backend/application/agent/context/DefaultAgentContextRoutingFacadeTest.java)
- Test: [`penmate-backend/src/test/java/com/penmate/backend/application/agent/context/DefaultContextBuilderTest.java`](penmate-backend/src/test/java/com/penmate/backend/application/agent/context/DefaultContextBuilderTest.java)

**Step 1: 先写失败测试**

断言：

- 按 task profile / skills / chapter version 选取上下文
- 无 Story Bible 时返回 noop + missing flags，不直接失败
- 不全量加载 story bible
- 对冲突信息进行标记
- 输出进入 `ContextPackage`

**Step 2: 跑失败测试**

Run: [`mvn -Dtest=DefaultAgentContextRoutingFacadeTest,DefaultContextBuilderTest test`](penmate-backend/pom.xml)

Expected:

- 失败

**Step 3: 最小实现**

- `DefaultAgentContextRoutingFacade` 保留 façade 角色，但内部委托给 `DefaultContextBuilder`
- `StoryBibleContextProvider` 改为返回结构化 entry view，而不仅是一个字符串块
- context builder 负责排序、裁剪、去重、missing/conflict 标注

**Step 4: 复跑测试**

Run: [`mvn -Dtest=DefaultAgentContextRoutingFacadeTest,DefaultContextBuilderTest test`](penmate-backend/pom.xml)

Expected:

- 通过

**Step 5: 反架子检查**

- 不能只是把 `storyBibleContext.content()` 换个类名
- 不能继续只传字符串，必须有结构化来源与缺失标记
- 不能在 builder 中做 prompt 冲突处理

**Step 6: Commit**

Run: [`git add penmate-backend/src/main/java/com/penmate/backend/application/agent/context penmate-backend/src/test/java/com/penmate/backend/application/agent/context && git commit -m "feat(agent): evolve context routing into context builder"`](.gitignore)

---

## Task 7: 将 Task Profiler / Prompt Composer / Context Builder 接入 Main Orchestrator

**Files:**
- Modify: [`penmate-backend/src/main/java/com/penmate/backend/application/agent/orchestration/AgentGenerationWorkflow.java`](penmate-backend/src/main/java/com/penmate/backend/application/agent/orchestration/AgentGenerationWorkflow.java:33)
- Modify: [`penmate-backend/src/main/java/com/penmate/backend/application/agent/orchestration/AgentGenerationWorkflowDispatcher.java`](penmate-backend/src/main/java/com/penmate/backend/application/agent/orchestration/AgentGenerationWorkflowDispatcher.java:16)
- Modify: [`penmate-backend/src/main/java/com/penmate/backend/application/agent/orchestration/AgentTaskRuntimeUpdater.java`](penmate-backend/src/main/java/com/penmate/backend/application/agent/orchestration/AgentTaskRuntimeUpdater.java:15)
- Modify: [`penmate-backend/src/main/java/com/penmate/backend/domain/agent/model/AgentTaskContext.java`](penmate-backend/src/main/java/com/penmate/backend/domain/agent/model/AgentTaskContext.java:1)
- Modify: [`penmate-backend/src/main/java/com/penmate/backend/domain/agent/repository/AgentRepository.java`](penmate-backend/src/main/java/com/penmate/backend/domain/agent/repository/AgentRepository.java:9)
- Test: [`penmate-backend/src/test/java/com/penmate/backend/application/agent/orchestration/AgentGenerationWorkflowTest.java`](penmate-backend/src/test/java/com/penmate/backend/application/agent/orchestration/AgentGenerationWorkflowTest.java)

**Step 1: 先写失败测试，约束顺序与快照写入**

新增断言：

- workflow 顺序必须是 Task Profiler → Prompt Composer → Context Builder → Tool Loop
- `TaskProfile` / `PromptPlan` / `ContextPackage` 写入 task context 或 task result snapshot
- 日志与状态节点存在 `preflight done / prompt composed / context routed / execution started`
- 无 Story Bible 时允许 fallback，但 `missingContextFlags` 被写入 snapshot

**Step 2: 跑失败测试**

Run: [`mvn -Dtest=AgentGenerationWorkflowTest test`](penmate-backend/pom.xml)

Expected:

- 旧链路缺少 composer/context package/snapshot 导致失败

**Step 3: 最小实现**

- workflow 内显式创建 `TaskProfile`
- 调用 `PromptComposer` 生成 `PromptPlan`
- 调用 `DefaultContextBuilder` 生成 `ContextPackage`
- `AgentPromptAssembler` 只负责把 `PromptPlan + ContextPackage + user request` 装成消息
- `AgentTaskRuntimeUpdater` 扩展记录 profile / prompt / context 摘要

**Step 4: 复跑测试**

Run: [`mvn -Dtest=AgentGenerationWorkflowTest test`](penmate-backend/pom.xml)

Expected:

- 通过
- 原有 WAITING_APPROVAL / preflight fail / blank prompt fail 用例仍通过

**Step 5: 反架子检查**

- 不能只在 workflow 内 new 出对象却不落快照
- 不能只新增日志，不改变执行顺序
- 不能把主编排拆成第二个 orchestrator

**Step 6: Commit**

Run: [`git add penmate-backend/src/test/java/com/penmate/backend/application/agent/orchestration/AgentGenerationWorkflowTest.java penmate-backend/src/main/java/com/penmate/backend/application/agent/orchestration/AgentGenerationWorkflow.java penmate-backend/src/main/java/com/penmate/backend/application/agent/orchestration/AgentPromptAssembler.java penmate-backend/src/main/java/com/penmate/backend/application/agent/orchestration/AgentTaskRuntimeUpdater.java penmate-backend/src/main/java/com/penmate/backend/domain/agent/model/AgentTaskContext.java && git commit -m "feat(agent): wire profile prompt and context into workflow"`](.gitignore)

Expected:

- commit 中包含主编排调用链同步改动
- 无第二 orchestrator、无兼容 fallback、无仅测试可见的 helper

---

## Batch Acceptance Checklist

- [ ] `AgentGenerationWorkflow` 仍是唯一主编排入口，负责串起 profile、prompt、context、tool、approval、runtime snapshot
- [ ] `TaskProfiler` 能基于用户请求、会话状态和任务类型输出稳定 `TaskProfile`
- [ ] `PromptComposer` 输出结构化 `PromptPlan`，并区分系统规则、任务目标、Story Bible 摘要、缺失上下文标记
- [ ] `StoryBible` 聚合、版本选择和更新提案遵守 DDD 分层：domain 表达规则，application 编排用例，infrastructure 只做持久化
- [ ] `ContextBuilder` 使用预算策略裁剪 Story Bible / RAG / 会话上下文，不把全量资料塞进 prompt
- [ ] `AgentPromptAssembler` 只做最终消息装配，不承担业务决策、不直接读库
- [ ] `AgentTaskRuntimeUpdater` 记录 profile、prompt、context、missing flags 和关键节点状态，便于前端恢复与排障
- [ ] 所有新增字段如需持久化，直接修改 SQL migration，不通过代码兼容旧 schema
- [ ] 调用链全链路同步：测试、application service、domain model、mapper、runtime snapshot、日志字段保持一致
- [ ] 关键日志包含 taskId、sessionId、profileType、contextEntryCount、missingContextFlags，不输出正文全文或敏感密钥
- [ ] 关键类和复杂分支有简短注释说明设计约束，避免后续新增平行链路

---

## Anti Over-Engineering / Anti Compatibility Checklist

- [ ] 不新增第二主编排中心、Supervisor workflow、独立 workflow engine 或常驻多 Agent 主控
- [ ] 不新增独立规则中心、平台化 prompt DSL、动态插件注册框架等额外工程化设施
- [ ] 不为旧 prompt assembler、旧 context blob、旧 story bible 字段保留兼容接口、兼容 DTO 或双写代码
- [ ] 不通过 `if oldField != null`、反射兜底、默认空对象等方式掩盖迁移遗漏
- [ ] 不把 Story Bible 全量内容、RAG 全量结果或历史会话全文塞进 prompt
- [ ] 不让 infrastructure mapper 承担版本选择、上下文预算、prompt 结构决策
- [ ] 不让 tool handler 绕过 application service 直接写 Story Bible 或 Agent runtime
- [ ] 不新增只服务测试的 public 方法、test-only constructor 或生产不可达分支
- [ ] 不只新增 DTO / prompt 模板 / 日志而没有真实调用方和失败测试
- [ ] 不保留新旧并行链路；若字段和接口变更，调用方、测试、SQL、文档一次性同步

---

## Final Verification Commands

后端单测：

Run: [`mvn test`](penmate-backend/pom.xml)

Expected:

- 全部后端测试通过
- 无 migration 校验失败
- 无旧兼容路径测试残留

重点回归：

Run: [`mvn -Dtest=StoryBibleVersionSelectorTest,StoryBibleApplicationServiceTest,StoryBibleUpdateProposalServiceTest,RepositoryStoryBibleContextProviderTest,DefaultAgentContextRoutingFacadeTest,DefaultContextBuilderTest,PromptComposerTest,AgentPromptAssemblerTest,AgentTaskRuntimeUpdaterTest,AgentGenerationWorkflowTest test`](penmate-backend/pom.xml)

Expected:

- Story Bible 初始化、版本选择、更新提案、上下文检索、prompt 拼装、runtime snapshot 和 workflow 串联全部通过
- WAITING_APPROVAL / preflight fail / blank prompt fail 等既有分支仍通过

人工验收：

1. 启动后端和前端。
2. 进入 Workbench，发送“根据当前人设续写本章”。
3. 观察日志出现 `preflight done`、`task profiled`、`prompt composed`、`context routed`、`execution started`。
4. 检查 task runtime snapshot 中包含 `TaskProfile`、`PromptPlan` 摘要、`ContextPackage` 摘要和 `missingContextFlags`。
5. 在无 Story Bible 的新书会话中重复请求，确认允许生成但明确记录缺失上下文标记。
6. 触发高风险 Story Bible 更新，确认只生成 proposal，不自动覆盖 canon。

---

## Batch Boundary Notes

本批完成后边界如下：

- 第 1 批负责单主编排基础、Task Profiler、Prompt Composer、Story Bible、Context Builder、runtime snapshot 基础字段。
- 第 2 批负责 tools、Todo、RAG 混合检索、Memory Store、Runtime Status 和 Workbench 用户感知。
- 本批不实现 Todo planner、不实现 draft / review tool handler、不实现前端状态卡片细节。
- 本批不新增多 Agent 主控、不新增独立 workflow engine、不新增平台化规则中心。
- Story Bible 是长期事实与 canon 管理边界；Memory Store 只能在后续批次作为短期偏好或运行记忆补充，不能替代 Story Bible。
- 后续批次必须沿用本批 `TaskProfile -> PromptPlan -> ContextPackage -> AgentPromptAssembler -> AgentGenerationWorkflow` 调用链，不重新开平行链路。
- 任何表结构变化都直接进入 SQL migration，并同步 mapper、repository、测试数据和清理脚本，不保留兼容字段。

---

## Final Commit

Run: [`git add docs/plans/2026-05-12-prd-v1-batch-1-orchestrator-story-bible-context.md && git commit -m "docs(plan): complete prd v1 batch 1 implementation plan"`](.gitignore)

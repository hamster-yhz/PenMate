# 后端业务 ID 全量 string 化与前端同步改造 Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use [executing-plans] mode to implement this plan task-by-task.

**Goal:** 将当前项目全部对外业务 ID 统一重构为 `string`，覆盖后端接口层所有入参/出参与前端 API、类型、状态、composable、视图，不保留任何历史兼容代码。

**Architecture:** 由于长文档在当前工具链中容易被截断，本计划采用“主计划 + 分卷清单”结构：主计划负责定义目标、原则、非兼容策略、总任务拆分、验证与回归要求；分卷文件分别承载数据库业务 ID 清单、后端逐 Controller/DTO/响应对象逐接口改造表、前端逐 API 模块/类型/状态/composable/视图改造表。实施时先按本主计划建立 string-only 契约，再逐分卷执行并在每个分卷中勾选完成项。

**Tech Stack:** Java 17, Spring Boot, Jackson, MyBatis, JUnit 5, MockMvc, TypeScript, Vue 3, Vitest

---

## 1. 目标与原则

### 1.1 目标

- 后端所有对外业务 ID 改为 `String`：`@PathVariable`、`@RequestParam`、DTO 字段、响应 DTO / `Map`。
- 前端所有业务 ID 改为 `string`：API 签名、state、composable 参数、视图选中值、组件 key、测试 fixture。
- 删除全部历史兼容：禁止 `number | string`、禁止 `Number(id)`、禁止 `fooId ?? id` / `fooId || id`、禁止同时暴露 `id` 与 `xxxId`。

### 1.2 原则表

| 可勾选 | 原则 | 说明 |
|---|---|---|
| [ ] | 对外 only string | 所有 HTTP 边界业务 ID 只允许 string |
| [ ] | 不保留兼容代码 | 不设灰度字段、不设 fallback |
| [ ] | 响应只保留业务语义 | 不向前端暴露物理主键或重复字段 |
| [ ] | 前后端同批收口 | 避免单边改造导致联调断裂 |
| [ ] | 先契约测试后实现 | 先固定 string contract，再逐层收敛 |

### 1.3 完成定义

| 可勾选 | 完成标准 | 验证方式 |
|---|---|---|
| [ ] | 后端 `Long` 形式的对外业务 ID 参数归零 | 搜索 `@PathVariable Long .*Id`、`@RequestParam(".*Id") Long` |
| [ ] | 接口 DTO 中 `private Long .*Id` 归零 | 搜索 [`interfaces/api`](penmate-backend/src/main/java/com/penmate/backend/interfaces/api) |
| [ ] | 前端 [`IdLike`](penmate-frontend/src/api/types.ts:31) 不再用于业务 ID | 搜索 `IdLike` |
| [ ] | 前端业务路径不再 `Number(...)` | 搜索 `Number(`、`parseInt(` |
| [ ] | 无回退兼容逻辑 | 搜索 `?? id`、`|| id`、`conversationId` fallback |
| [ ] | MockMvc/Vitest 契约通过 | controller/api/composable/view tests |

---

## 2. 非兼容策略

| 可勾选 | 非兼容项 | 旧行为 | 新行为 |
|---|---|---|---|
| [ ] | 前端传 number 型 ID | `number|string` 混传 | 只接受 `string` |
| [ ] | 后端 path/query 用 `Long` | 自动数值绑定 | `String` 入参 + 显式校验/转换 |
| [ ] | 回退读取 `id` | `roleId || id`、`sessionId ?? conversationId` | 只允许唯一业务字段 |
| [ ] | 响应双字段并存 | `id` 与 `xxxId` 同时存在 | 只保留单一业务语义字段 |
| [ ] | `Number(id) > 0` 判定有效 | 数值化判断 | 纯字符串非空判断 |

---

## 3. 业务 ID 与范围总览

### 3.1 必覆盖业务 ID

- `projectId`
- `operatorId`
- `ownerUserId`
- `userId`
- `roleId`
- `permissionId`
- `menuId`
- `volumeId`
- `chapterId`
- `outlineNodeId` / `nodeId`
- `cardId`
- `cardRelationId` / `relationId`
- `styleId` / `toStyleId`
- `documentId`
- `approvalId`
- `sessionId` / `conversationId`
- `taskId`
- `requestContextId`
- `modelConfigId`
- `providerId`
- `keyId`
- `migrationId`
- `jobId`

### 3.2 必覆盖后端 Controller 范围

- [`AgentController`](penmate-backend/src/main/java/com/penmate/backend/interfaces/api/agent/AgentController.java:31)
- [`ApprovalController`](penmate-backend/src/main/java/com/penmate/backend/interfaces/api/approval/ApprovalController.java:27)
- [`NovelController`](penmate-backend/src/main/java/com/penmate/backend/interfaces/api/novel/NovelController.java:50)
- [`StyleController`](penmate-backend/src/main/java/com/penmate/backend/interfaces/api/style/StyleController.java:32)
- [`RagController`](penmate-backend/src/main/java/com/penmate/backend/interfaces/api/rag/RagController.java:28)
- [`PluginController`](penmate-backend/src/main/java/com/penmate/backend/interfaces/api/plugin/PluginController.java:30)
- [`OpsController`](penmate-backend/src/main/java/com/penmate/backend/interfaces/api/ops/OpsController.java:22)
- [`RbacQueryController`](penmate-backend/src/main/java/com/penmate/backend/interfaces/api/rbac/RbacQueryController.java:34)
- [`ModelController`](penmate-backend/src/main/java/com/penmate/backend/interfaces/api/model/ModelController.java:39)
- [`AuthController`](penmate-backend/src/main/java/com/penmate/backend/interfaces/api/auth/AuthController.java:25)

### 3.3 必覆盖前端模块范围

- API modules：[`agent.api.ts`](penmate-frontend/src/api/modules/agent.api.ts), [`novel.api.ts`](penmate-frontend/src/api/modules/novel.api.ts), [`outline.api.ts`](penmate-frontend/src/api/modules/outline.api.ts), [`chapter.api.ts`](penmate-frontend/src/api/modules/chapter.api.ts), [`card.api.ts`](penmate-frontend/src/api/modules/card.api.ts), [`style.api.ts`](penmate-frontend/src/api/modules/style.api.ts), [`approval.api.ts`](penmate-frontend/src/api/modules/approval.api.ts), [`rag.api.ts`](penmate-frontend/src/api/modules/rag.api.ts), [`plugin.api.ts`](penmate-frontend/src/api/modules/plugin.api.ts), [`model.api.ts`](penmate-frontend/src/api/modules/model.api.ts), [`rbac.api.ts`](penmate-frontend/src/api/modules/rbac.api.ts), [`ops.api.ts`](penmate-frontend/src/api/modules/ops.api.ts), [`profile.api.ts`](penmate-frontend/src/api/modules/profile.api.ts)
- types / stores：[`types.ts`](penmate-frontend/src/api/types.ts:31), [`workbenchSession.ts`](penmate-frontend/src/stores/workbenchSession.ts:3)
- composables：[`useWorkbenchChat.ts`](penmate-frontend/src/composables/workbench/useWorkbenchChat.ts:15), [`useWorkbenchApprovals.ts`](penmate-frontend/src/composables/workbench/useWorkbenchApprovals.ts:4), [`useWorkbenchCards.ts`](penmate-frontend/src/composables/workbench/useWorkbenchCards.ts:4), [`useWorkbenchOutline.ts`](penmate-frontend/src/composables/workbench/useWorkbenchOutline.ts:9), [`useWorkbenchSessionRecovery.ts`](penmate-frontend/src/composables/workbench/useWorkbenchSessionRecovery.ts:1)
- views：[`Workbench/index.vue`](penmate-frontend/src/views/Workbench/index.vue:137), [`AdminRbac/index.vue`](penmate-frontend/src/views/AdminRbac/index.vue:1)

---

## 4. 分卷文件结构

> 下面 3 个分卷文件是本主计划的必需组成部分。每个分卷内部都应使用“可打勾 markdown 表格”细化到用户要求的粒度。

### 4.1 分卷 A：数据库业务 ID 清单与接口映射

**目标文件：** [`docs/plans/2026-05-08-business-id-string-full-refactor-part-a-id-inventory.md`](docs/plans/2026-05-08-business-id-string-full-refactor-part-a-id-inventory.md)

**必须包含：**

- 每一个业务 ID 一行
- 对应数据库/领域语义
- 对应后端接口入口
- 对应前端 API / composable / 视图消费点
- 当前风险 / 当前是否已有部分 string 化

**最少列：**

| 可勾选 | 业务 ID | 领域/表语义 | 后端接口入口 | 前端消费点 | 当前问题 | 改造后状态 |
|---|---|---|---|---|---|---|

### 4.2 分卷 B/C 压缩终稿：后端逐接口 + 前端逐模块改造表

**执行入口文件：** [`docs/plans/2026-05-08-business-id-string-full-refactor-part-bc-compact.md`](docs/plans/2026-05-08-business-id-string-full-refactor-part-bc-compact.md)

**保留说明：**

- [`docs/plans/2026-05-08-business-id-string-full-refactor-part-b-backend.md`](docs/plans/2026-05-08-business-id-string-full-refactor-part-b-backend.md) 保留为工具截断草稿，不再作为执行入口
- [`docs/plans/2026-05-08-business-id-string-full-refactor-part-c-frontend.md`](docs/plans/2026-05-08-business-id-string-full-refactor-part-c-frontend.md) 保留为工具截断草稿，不再作为执行入口
- 实际执行、评审、验收统一以 [`2026-05-08-business-id-string-full-refactor-part-bc-compact.md`](docs/plans/2026-05-08-business-id-string-full-refactor-part-bc-compact.md) 为准

**压缩终稿必须包含：**

- 后端：每个 Controller 一行，接口集合、Path/Query/Body/Response、DTO、测试、禁用兼容策略
- 前端：每个模块一行，文件、业务 ID、当前问题、改造动作、目标 string-only 状态、验证文件
- 明确 string-only、无历史兼容代码、无双字段、无 `Number(...)`

**最少列：**

| 可勾选 | 模块 | 文件/接口集合 | 涉及业务 ID | 当前问题 | 改造动作 | 测试文件 |
|---|---|---|---|---|---|---|

---

## 5. 实施任务拆分

### Task 1: 建立 string-only 契约基线

**Files:**
- Modify: [`penmate-backend/src/test/java`](penmate-backend/src/test/java)
- Modify: [`penmate-frontend/src/**/*.spec.ts`](penmate-frontend/src)
- Modify: [`docs/plans/business-id-audit-notes.md`](docs/plans/business-id-audit-notes.md)

**Step 1: 写失败测试**
- 后端：所有关键 controller 的 path/query/body/response 断言改为 string 业务 ID。
- 前端：所有 api/composable/view 测试 fixture 改为 string，移除 number 输入。

**Step 2: 运行测试确认失败**
- Run: [`mvn -q test`](penmate-backend/pom.xml)
- Run: [`npm run test`](penmate-frontend/package.json)

**Step 3: 记录失败基线**
- 将搜索结果与失败点补入 [`business-id-audit-notes.md`](docs/plans/business-id-audit-notes.md:1)。

**Step 4: Commit**
- `git add .`
- `git commit -m "test: lock string-only business id contract"`

### Task 2: 后端接口层全量 string 化

**Files:**
- Modify: [`penmate-backend/src/main/java/com/penmate/backend/interfaces/api`](penmate-backend/src/main/java/com/penmate/backend/interfaces/api)
- Modify: [`penmate-backend/src/test/java`](penmate-backend/src/test/java)

**Step 1: 逐 Controller 改 path/query 类型为 String**
**Step 2: 逐 DTO 改业务 ID 字段为 String**
**Step 3: 逐响应对象/Map 投影统一 string 业务 ID**
**Step 4: 跑后端测试**
- Run: [`mvn -q test`](penmate-backend/pom.xml)
**Step 5: Commit**
- `git add .`
- `git commit -m "refactor: unify backend business ids as string"`

### Task 3: 前端 API 与状态层收口

**Files:**
- Modify: [`penmate-frontend/src/api/modules`](penmate-frontend/src/api/modules)
- Modify: [`penmate-frontend/src/api/types.ts`](penmate-frontend/src/api/types.ts:31)
- Modify: [`penmate-frontend/src/stores/workbenchSession.ts`](penmate-frontend/src/stores/workbenchSession.ts:3)

**Step 1: 移除 `IdLike` 混合签名**
**Step 2: 所有 API 入参改 string**
**Step 3: store/state 统一 string**
**Step 4: 跑前端 API/unit tests**
- Run: [`npm run test -- src/api/modules`](penmate-frontend/package.json)
**Step 5: Commit**
- `git add .`
- `git commit -m "refactor: unify frontend api and state ids as string"`

### Task 4: 前端 composable / view 去 number 与 fallback

**Files:**
- Modify: [`useWorkbenchChat.ts`](penmate-frontend/src/composables/workbench/useWorkbenchChat.ts:15)
- Modify: [`useWorkbenchApprovals.ts`](penmate-frontend/src/composables/workbench/useWorkbenchApprovals.ts:4)
- Modify: [`useWorkbenchCards.ts`](penmate-frontend/src/composables/workbench/useWorkbenchCards.ts:29)
- Modify: [`useWorkbenchOutline.ts`](penmate-frontend/src/composables/workbench/useWorkbenchOutline.ts:51)
- Modify: [`useWorkbenchSessionRecovery.ts`](penmate-frontend/src/composables/workbench/useWorkbenchSessionRecovery.ts:1)
- Modify: [`Workbench/index.vue`](penmate-frontend/src/views/Workbench/index.vue:137)
- Modify: [`AdminRbac/index.vue`](penmate-frontend/src/views/AdminRbac/index.vue:1)

**Step 1: 删除 `Number(...)` 业务 ID 逻辑**
**Step 2: 删除 `sessionId/conversationId` fallback**
**Step 3: 删除 `roleId || id` 等兼容代码**
**Step 4: 跑 composable/view tests**
- Run: [`npm run test -- src/composables/workbench src/views`](penmate-frontend/package.json)
**Step 5: Commit**
- `git add .`
- `git commit -m "refactor: remove frontend id fallback and numeric coercion"`

### Task 5: 联调、搜索归零、回归

**Files:**
- Modify: [`docs/plans/business-id-audit-notes.md`](docs/plans/business-id-audit-notes.md:1)

**Step 1: 全文搜索归零**
- 搜索 `@PathVariable Long .*Id`
- 搜索 `private Long .*Id`
- 搜索 `IdLike`
- 搜索 `Number(`
- 搜索 `?? id`
- 搜索 `|| id`

**Step 2: 全量回归**
- Run: [`mvn -q test`](penmate-backend/pom.xml)
- Run: [`npm run test`](penmate-frontend/package.json)

**Step 3: 更新审计记录**
- 在 [`business-id-audit-notes.md`](docs/plans/business-id-audit-notes.md:1) 记录归零结果与例外项（理论上应为 0 例外）。

**Step 4: Commit**
- `git add .`
- `git commit -m "test: verify business id string-only refactor"`

---

## 6. 联调与验证步骤

| 可勾选 | 验证项 | 操作 |
|---|---|---|
| [ ] | 后端 path/query/body 全 string | 用 MockMvc / integration test 断言字符串入参与响应 |
| [ ] | 模型配置链路 | 验证 `providerId/modelConfigId/keyId/userId/operatorId` 全 string |
| [ ] | RBAC 管理链路 | 验证 `userId/roleId/permissionId/menuId` 全 string |
| [ ] | 工作台会话链路 | 验证 `projectId/sessionId/taskId/chapterId/styleId/modelConfigId` 全 string |
| [ ] | 大纲/章节/卡片链路 | 验证 `nodeId/chapterId/cardId/relationId` 全 string |
| [ ] | 审批链路 | 验证 `approvalId/taskId` 全 string |
| [ ] | RAG/插件/运维链路 | 验证 `documentId/jobId/migrationId` 全 string |
| [ ] | 搜索归零 | 关键反模式无结果 |

---

## 7. 风险与回归清单

| 可勾选 | 风险 | 说明 | 回归重点 |
|---|---|---|---|
| [ ] | 后端内部服务仍依赖 `Long` | Controller → Application 转换边界可能遗漏 | 所有 controller contract tests |
| [ ] | 前端 UI 组件默认把 value 作为 number | `<select>` / 列表 key / local state 可能隐式转型 | RBAC、工作台、大纲、资料卡 |
| [ ] | 响应仍混入旧字段 | `id` / `xxxId` 双字段可能残留 | response snapshot tests |
| [ ] | `sessionId/conversationId` 语义冲突 | Agent 会话恢复最敏感 | workbench chat/recovery tests |
| [ ] | `versionNo` 与业务 ID 混淆 | 版本号应可保持 number | chapter version tests |
| [ ] | Model 模块存在半改造状态 | 已部分 string 化，残余最难发现 | model api/controller tests |

---

## 8. 交付物清单

| 可勾选 | 文件 | 作用 |
|---|---|---|
| [ ] | [`2026-05-08-business-id-string-full-refactor-plan.md`](docs/plans/2026-05-08-business-id-string-full-refactor-plan.md) | 主计划 |
| [ ] | [`2026-05-08-business-id-string-full-refactor-part-a-id-inventory.md`](docs/plans/2026-05-08-business-id-string-full-refactor-part-a-id-inventory.md) | 业务 ID 清单与接口映射分卷 |
| [ ] | [`2026-05-08-business-id-string-full-refactor-part-bc-compact.md`](docs/plans/2026-05-08-business-id-string-full-refactor-part-bc-compact.md) | 后端分卷 B + 前端分卷 C 的压缩终稿与执行入口 |
| [ ] | [`2026-05-08-business-id-string-full-refactor-part-b-backend.md`](docs/plans/2026-05-08-business-id-string-full-refactor-part-b-backend.md) | 被工具截断的后端草稿，仅作历史保留 |
| [ ] | [`2026-05-08-business-id-string-full-refactor-part-c-frontend.md`](docs/plans/2026-05-08-business-id-string-full-refactor-part-c-frontend.md) | 被工具截断的前端草稿，仅作历史保留 |
| [ ] | [`business-id-audit-notes.md`](docs/plans/business-id-audit-notes.md:1) | 审计基线与归零记录 |

---

## 9. 执行选项

1. 在当前会话基于 [`2026-05-08-business-id-string-full-refactor-part-bc-compact.md`](docs/plans/2026-05-08-business-id-string-full-refactor-part-bc-compact.md) 直接进入 [executing-plans]。
2. 用户稍后基于压缩终稿手动实施，再执行 [`/execute-plan`](docs/plans/2026-05-08-business-id-string-full-refactor-plan.md)。
3. 切换到 [subagent-driven-development]，按压缩终稿中的后端/前端模块并行落地。
# Business ID 全链路统一改造 Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use [executing-plans] mode to implement this plan task-by-task.

**Goal:** 将当前项目中仍误用数据库物理主键作为对外 `id`、路径参数、服务入参、前端绑定键值的地方，统一替换为业务 ID，并保证后端 Java 与前端 TypeScript/Vue 全链路语义一致。

**Architecture:** 该改造以“领域对象同时拥有物理主键 `id` 与业务主键 `xxxId`”为基础，遵循“数据库内部允许保留物理主键、领域/应用/接口/前端对外只传播业务 ID”的边界原则。实施时先收敛识别规则与搜索范围，再按 repository → application → interfaces → frontend 的顺序逐层切换，最后通过接口契约测试、前端单测、端到端冒烟与全文搜索确认不存在对外暴露物理主键的残留点。

**Tech Stack:** Java 17, Spring Boot, MyBatis, Flyway, JUnit 5, MockMvc, TypeScript, Vue 3, Vitest

---

## 一、判定标准：如何识别数据库主键 vs 业务 ID

### 1.1 判定规则（必须先统一认知）

**数据库物理主键（禁止继续向上泄漏）** 的典型特征：

1. 字段名直接为 `id`。
2. SQL DDL 中为 `BIGINT ... PRIMARY KEY AUTO_INCREMENT`。
3. MyBatis `@Options(useGeneratedKeys = true, keyProperty = "id")` 写回的字段。
4. 仅用于库表内部排序、索引、插入回填，不应作为接口路径参数、响应 DTO 的业务标识。

**业务 ID（必须作为跨层传递主标识）** 的典型特征：

1. 领域对象同时存在 `id` 与 `xxxId`，其中 `xxxId` 带业务语义。
2. SQL 表中与物理主键并存，例如：
   - [`project_id`](penmate-backend/src/main/resources/db/migration/V2__init_novel_and_approval_minimal.sql:3)
   - [`user_id`](penmate-backend/src/main/resources/db/migration/V1__init_iam_and_rbac.sql:3)
   - [`role_id`](penmate-backend/src/main/resources/db/migration/V1__init_iam_and_rbac.sql:22)
   - [`permission_id`](penmate-backend/src/main/resources/db/migration/V1__init_iam_and_rbac.sql:36)
   - [`model_config_id`](penmate-backend/src/main/resources/db/migration/V10__init_plugin_and_model_domains.sql:87)
3. 领域模型注释已明确声明业务语义，例如：
   - [`private Long projectId;`](penmate-backend/src/main/java/com/penmate/backend/domain/novel/model/NovelProject.java:14)
   - [`private Long userId;`](penmate-backend/src/main/java/com/penmate/backend/domain/iam/model/IamUser.java:14)
4. OpenAPI 字段说明已经按业务 ID 解释，例如 [`describeField()`](penmate-backend/src/main/java/com/penmate/backend/interfaces/config/OpenApiConfig.java:357) 中对 `projectId`、`userId`、`approvalId`、`keyId`、`modelConfigId` 的解释。

### 1.2 识别异常模式（本次要重点排查）

出现以下任一模式，默认视为“可能仍在使用物理主键”：

1. 控制器或接口返回整个领域对象，响应 JSON 自动暴露了领域对象的 `id` 字段。
2. 前端组件使用 `item.id` 作为业务提交参数，同时同一对象还存在 `item.userId` / `item.roleId` / `item.projectId` / `item.modelConfigId`。
3. 前端出现 `fooId || id`、`fooId ?? id`、`profile.id ?? profile.userId` 之类兼容逻辑。
4. Application Service 对外返回 `Map.of("id", domain.getId())`，但语义上该字段应该是业务对象 ID。
5. Repository / Gateway / Controller 方法命名为 `findById(Long id)`，但 SQL 实际按 `user_id` / `role_id` / `approval_request_id` 等业务列查询，导致跨层语义混乱。
6. 测试断言只验证 `$.data.id`，却没有验证该值来自业务 ID 字段而非物理主键。

### 1.3 当前项目已确认的领域规律

从现有代码可确认，本项目大量领域对象都采用“双 ID”结构：

- [`NovelProject.id + projectId`](penmate-backend/src/main/java/com/penmate/backend/domain/novel/model/NovelProject.java:12)
- [`IamUser.id + userId`](penmate-backend/src/main/java/com/penmate/backend/domain/iam/model/IamUser.java:12)
- [`ApprovalRequest.id + approvalRequestId`](penmate-backend/src/main/java/com/penmate/backend/domain/approval/model/ApprovalRequest.java:12)
- [`AgentConversation.id + conversationId`](penmate-backend/src/main/java/com/penmate/backend/domain/agent/model/AgentConversation.java:12)
- [`ModelUserApiKey.id + userApiKeyId`](penmate-backend/src/main/java/com/penmate/backend/domain/model/model/ModelUserApiKey.java:12)
- [`ModelOfficialApiKey.id + officialApiKeyId`](penmate-backend/src/main/java/com/penmate/backend/domain/model/model/ModelOfficialApiKey.java:13)
- [`ModelProvider.id` 注释声明为物理主键，另有业务语义提供商 ID 需要统一明确](penmate-backend/src/main/java/com/penmate/backend/domain/model/model/ModelProvider.java:11)

因此本次改造不是“新增业务 ID 体系”，而是“把已存在但仍未完全贯彻的业务 ID 体系落实到全链路”。

---

## 二、代码与接口搜索范围

### 2.1 后端搜索范围

**目录范围：**

- [`penmate-backend/src/main/java/com/penmate/backend/domain`](penmate-backend/src/main/java/com/penmate/backend/domain)
- [`penmate-backend/src/main/java/com/penmate/backend/application`](penmate-backend/src/main/java/com/penmate/backend/application)
- [`penmate-backend/src/main/java/com/penmate/backend/interfaces`](penmate-backend/src/main/java/com/penmate/backend/interfaces)
- [`penmate-backend/src/main/java/com/penmate/backend/infrastructure/persistence`](penmate-backend/src/main/java/com/penmate/backend/infrastructure/persistence)
- [`penmate-backend/src/main/resources/db/migration`](penmate-backend/src/main/resources/db/migration)
- [`penmate-backend/src/test/java`](penmate-backend/src/test/java)

**全文搜索关键词：**

1. 领域双 ID 模式识别：
   - `private Long id;`
   - `private Long .*Id;`
2. 仓储/网关命名歧义：
   - `findById\(`
   - `delete.*\(Long id\)`
   - `update.*\(Long id\)`
   - `approve\(Long id`
3. SQL/Mapper 物理主键泄漏：
   - `SELECT id,`
   - `ORDER BY id`
   - `WHERE .*_id = #{id}`
   - `keyProperty = "id"`
4. 接口响应/Map 投影：
   - `Map.of\("id"`
   - `put\("id"`
   - `jsonPath\("\$\.data\.id"`
   - `jsonPath\("\$\.data\[0\]\.id"`
5. DTO / Controller 路径参数：
   - `@PathVariable Long .*Id`
   - `@RequestParam\(".*Id"\)`
   - `ApiResponse<List<.*>>`
   - 直接返回领域对象而非 DTO

### 2.2 前端搜索范围

**目录范围：**

- [`penmate-frontend/src/api/modules`](penmate-frontend/src/api/modules)
- [`penmate-frontend/src/views`](penmate-frontend/src/views)
- [`penmate-frontend/src/components`](penmate-frontend/src/components)
- [`penmate-frontend/src/composables`](penmate-frontend/src/composables)
- [`penmate-frontend/src/stores`](penmate-frontend/src/stores)
- [`penmate-frontend/src/api/types.ts`](penmate-frontend/src/api/types.ts)
- [`penmate-frontend/src/**/*.spec.ts`](penmate-frontend/src)

**全文搜索关键词：**

1. 兼容式回退（高风险）：
   - `\?\? id`
   - `\|\| id`
   - `\.id \?\?`
   - `\.id \|\|`
2. 提交接口参数：
   - `update.*\(`
   - `delete.*\(`
   - `approve.*\(`
   - `reject.*\(`
3. 列表绑定与选择器：
   - `:key=".*id`
   - `v-model=.*Id`
   - `selected.*Id`
4. 本地类型定义：
   - `id\?:`
   - `id:`
   - `roleId\?:`
   - `permissionId\?:`
   - `menuId\?:`
   - `userId\?:`
5. 路由和 query 参数：
   - `query: \{`
   - `projectId:`
   - `userId:`
   - `operatorId:`

### 2.3 当前已暴露的重点疑点文件

以下文件已从搜索中确认，必须进入计划首批排查：

**后端接口/应用层：**

- [`AuthApplicationService.java`](penmate-backend/src/main/java/com/penmate/backend/application/auth/AuthApplicationService.java:156)
- [`IamQueryApplicationService.java`](penmate-backend/src/main/java/com/penmate/backend/application/iam/IamQueryApplicationService.java:48)
- [`ApprovalRequestRepository.java`](penmate-backend/src/main/java/com/penmate/backend/domain/approval/repository/ApprovalRequestRepository.java:13)
- [`ApprovalRequestRepositoryImpl.java`](penmate-backend/src/main/java/com/penmate/backend/infrastructure/persistence/approval/ApprovalRequestRepositoryImpl.java:51)
- [`ModelController.java`](penmate-backend/src/main/java/com/penmate/backend/interfaces/api/model/ModelController.java:74)
- [`OpenApiConfig.java`](penmate-backend/src/main/java/com/penmate/backend/interfaces/config/OpenApiConfig.java:357)

**后端测试：**

- [`NovelControllerTest.java`](penmate-backend/src/test/java/com/penmate/backend/interfaces/api/novel/NovelControllerTest.java:69)
- [`ApprovalControllerTest.java`](penmate-backend/src/test/java/com/penmate/backend/interfaces/api/approval/ApprovalControllerTest.java:68)
- [`AuthControllerTest.java`](penmate-backend/src/test/java/com/penmate/backend/interfaces/api/auth/AuthControllerTest.java:106)
- [`RbacQueryControllerTest.java`](penmate-backend/src/test/java/com/penmate/backend/interfaces/api/rbac/RbacQueryControllerTest.java:212)
- [`OpsControllerTest.java`](penmate-backend/src/test/java/com/penmate/backend/interfaces/api/ops/OpsControllerTest.java:52)
- [`RagControllerTest.java`](penmate-backend/src/test/java/com/penmate/backend/interfaces/api/rag/RagControllerTest.java:66)

**前端：**

- [`AdminRbac/index.vue`](penmate-frontend/src/views/AdminRbac/index.vue:568)
- [`model.api.ts`](penmate-frontend/src/api/modules/model.api.ts:52)
- [`useLoginSubmit.ts`](penmate-frontend/src/composables/auth/useLoginSubmit.ts:37)
- [`useBookshelf.ts`](penmate-frontend/src/composables/bookshelf/useBookshelf.ts:64)
- [`ModelSettings.vue`](penmate-frontend/src/components/workbench/ModelSettings.vue:333)
- [`PluginWorkshop.vue`](penmate-frontend/src/components/workbench/PluginWorkshop.vue:124)
- [`useWorkbenchChat.ts`](penmate-frontend/src/composables/workbench/useWorkbenchChat.ts:125)
- [`useWorkbenchApprovals.ts`](penmate-frontend/src/composables/workbench/useWorkbenchApprovals.ts:39)

---

## 三、分层改造顺序

原则：**先定义语义，再收敛仓储命名，再修应用服务投影，再修接口契约，最后修前端绑定。**

### Task 1: 建立 ID 语义基线与首批失败测试

Use [test-driven-development] mode for this task.

**Files:**
- Modify: [`penmate-backend/src/test/java/com/penmate/backend/interfaces/api/auth/AuthControllerTest.java`](penmate-backend/src/test/java/com/penmate/backend/interfaces/api/auth/AuthControllerTest.java)
- Modify: [`penmate-backend/src/test/java/com/penmate/backend/interfaces/api/rbac/RbacQueryControllerTest.java`](penmate-backend/src/test/java/com/penmate/backend/interfaces/api/rbac/RbacQueryControllerTest.java)
- Modify: [`penmate-backend/src/test/java/com/penmate/backend/interfaces/api/model/ModelControllerTest.java`](penmate-backend/src/test/java/com/penmate/backend/interfaces/api/model/ModelControllerTest.java)
- Modify: [`penmate-frontend/src/views/AdminRbac/index.spec.ts`](penmate-frontend/src/views/AdminRbac/index.spec.ts)
- Modify: [`penmate-frontend/src/api/modules/model.api.spec.ts`](penmate-frontend/src/api/modules/model.api.spec.ts)
- Create: [`docs/plans/business-id-audit-notes.md`](docs/plans/business-id-audit-notes.md)

**Step 1: Write the failing test**

为以下契约先补“失败测试”：

1. 接口层返回 `id` 时必须等于业务 ID，而不是物理主键。
2. 前端提交路径参数、query 参数、视图选择值必须取 `xxxId`，不允许 `xxxId || id` 回退。
3. 登录态解析优先使用 `userId`，`id` 仅作为兼容过渡输入，且要在测试中单独标识为 legacy case。

后端测试示例断言模板：

```java
.andExpect(jsonPath("$.data.id").value(1001))
.andExpect(jsonPath("$.data.userId").doesNotExist())
```

对于 mock 返回对象，必须显式构造“物理主键 ≠ 业务 ID”的数据，例如：

```java
when(authApplicationService.me(anyString())).thenReturn(Map.of(
        "id", 900001L,
        "userId", 1001L,
        "email", "author@penmate.ai"
));
```

然后把测试目标改成：最终返回给前端的 `$.data.id` 必须是 `1001`，从而迫使实现层修正投影逻辑。

前端测试示例模板：

```ts
listRolesMock.mockResolvedValue([
  { id: 9, roleId: 2001, code: 'ADMIN', name: '管理员' },
])
```

然后断言：

```ts
await wrapper.get('[data-testid="rbac-assign-role-submit"]').trigger('click')
expect(assignRoleMock).toHaveBeenCalledWith(expect.any(Number), 2001)
```

**Step 2: Run test to verify it fails**

Run: [`mvn -q -Dtest=AuthControllerTest,RbacQueryControllerTest,ModelControllerTest test`](penmate-backend/pom.xml)

Expected: 至少出现 1 个失败断言，表明当前 `id` 仍可能映射到物理主键或前端存在回退使用。

Run: [`npm run test -- src/views/AdminRbac/index.spec.ts src/api/modules/model.api.spec.ts`](penmate-frontend/package.json)

Expected: 至少出现 1 个失败断言，表明前端仍接受或回退到物理主键。

**Step 3: Write minimal implementation**

本任务不立即改实现，只记录失败截图/输出到 [`business-id-audit-notes.md`](docs/plans/business-id-audit-notes.md) 作为审计基线。

**Step 4: Run test to verify it passes**

本任务目标是建立失败基线，因此保持失败，进入下一任务。

**Step 5: Commit**

```bash
git add penmate-backend/src/test/java/com/penmate/backend/interfaces/api/auth/AuthControllerTest.java penmate-backend/src/test/java/com/penmate/backend/interfaces/api/rbac/RbacQueryControllerTest.java penmate-backend/src/test/java/com/penmate/backend/interfaces/api/model/ModelControllerTest.java penmate-frontend/src/views/AdminRbac/index.spec.ts penmate-frontend/src/api/modules/model.api.spec.ts docs/plans/business-id-audit-notes.md
git commit -m "test: add failing business-id contract coverage"
```

### Task 2: 统一 repository / gateway / mapper 的“按业务 ID 查询”语义

Use [test-driven-development] mode for this task.

**Files:**
- Modify: [`penmate-backend/src/main/java/com/penmate/backend/domain/approval/repository/ApprovalRequestRepository.java`](penmate-backend/src/main/java/com/penmate/backend/domain/approval/repository/ApprovalRequestRepository.java)
- Modify: [`penmate-backend/src/main/java/com/penmate/backend/infrastructure/persistence/approval/ApprovalRequestRepositoryImpl.java`](penmate-backend/src/main/java/com/penmate/backend/infrastructure/persistence/approval/ApprovalRequestRepositoryImpl.java)
- Modify: [`penmate-backend/src/main/java/com/penmate/backend/infrastructure/persistence/approval/ApprovalRequestMapper.java`](penmate-backend/src/main/java/com/penmate/backend/infrastructure/persistence/approval/ApprovalRequestMapper.java)
- Modify: [`penmate-backend/src/main/java/com/penmate/backend/domain/iam/repository/IamGateway.java`](penmate-backend/src/main/java/com/penmate/backend/domain/iam/repository/IamGateway.java)
- Modify: [`penmate-backend/src/main/java/com/penmate/backend/infrastructure/persistence/iam/IamGatewayImpl.java`](penmate-backend/src/main/java/com/penmate/backend/infrastructure/persistence/iam/IamGatewayImpl.java)
- Modify: [`penmate-backend/src/main/java/com/penmate/backend/infrastructure/persistence/iam/IamUserMapper.java`](penmate-backend/src/main/java/com/penmate/backend/infrastructure/persistence/iam/IamUserMapper.java)
- Modify: [`penmate-backend/src/main/java/com/penmate/backend/infrastructure/persistence/iam/IamRoleMapper.java`](penmate-backend/src/main/java/com/penmate/backend/infrastructure/persistence/iam/IamRoleMapper.java)
- Modify: [`penmate-backend/src/main/java/com/penmate/backend/infrastructure/persistence/iam/IamPermissionMapper.java`](penmate-backend/src/main/java/com/penmate/backend/infrastructure/persistence/iam/IamPermissionMapper.java)
- Test: [`penmate-backend/src/test/java/com/penmate/backend/interfaces/api/approval/ApprovalControllerTest.java`](penmate-backend/src/test/java/com/penmate/backend/interfaces/api/approval/ApprovalControllerTest.java)

**Step 1: Write the failing test**

为仓储接口命名一致性增加测试或编译约束，目标是把 `findById(Long id)` 改成显式业务语义：

- `findByApprovalRequestId(Long approvalRequestId)`
- `findUserByUserId(Long userId)`
- `findRoleByRoleId(Long roleId)`
- `findPermissionByPermissionId(Long permissionId)`

如果现有单测不足，新增针对 service/gateway 的 mock 交互测试，断言调用的是业务 ID 参数而非领域对象物理主键。

**Step 2: Run test to verify it fails**

Run: [`mvn -q -Dtest=ApprovalControllerTest,Iam*Test test`](penmate-backend/pom.xml)

Expected: 编译失败或 mock 交互失败，因为旧方法名/旧语义尚未替换。

**Step 3: Write minimal implementation**

实施要点：

1. **仅重命名跨层方法语义，不改数据库列定义。**
2. Mapper 中保留 `SELECT id, user_id, ...` 这样的查询是允许的，因为这是持久化映射所需；但 `WHERE` 条件参数命名与上层方法命名必须明确为业务 ID。
3. 例如将：
   - [`IamUserMapper.findById(@Param("id") Long id)`](penmate-backend/src/main/java/com/penmate/backend/infrastructure/persistence/iam/IamUserMapper.java:36)
   改为：
   - `IamUser findByUserId(@Param("userId") Long userId)`
   - SQL 改为 `WHERE user_id = #{userId}`
4. 对 `softDelete`、`touchLastLogin`、`approve`、`reject` 同步改成业务语义参数名。

**Step 4: Run test to verify it passes**

Run: [`mvn -q -Dtest=ApprovalControllerTest,AuthControllerTest,RbacQueryControllerTest test`](penmate-backend/pom.xml)

Expected: 所有改名影响链路的单测通过，且无因参数名混乱导致的空数据问题。

**Step 5: Commit**

```bash
git add penmate-backend/src/main/java/com/penmate/backend/domain/approval/repository/ApprovalRequestRepository.java penmate-backend/src/main/java/com/penmate/backend/infrastructure/persistence/approval/ApprovalRequestRepositoryImpl.java penmate-backend/src/main/java/com/penmate/backend/infrastructure/persistence/approval/ApprovalRequestMapper.java penmate-backend/src/main/java/com/penmate/backend/domain/iam/repository/IamGateway.java penmate-backend/src/main/java/com/penmate/backend/infrastructure/persistence/iam/IamGatewayImpl.java penmate-backend/src/main/java/com/penmate/backend/infrastructure/persistence/iam/IamUserMapper.java penmate-backend/src/main/java/com/penmate/backend/infrastructure/persistence/iam/IamRoleMapper.java penmate-backend/src/main/java/com/penmate/backend/infrastructure/persistence/iam/IamPermissionMapper.java
git commit -m "refactor: clarify repository business-id semantics"
```

### Task 3: 修正 application service 对外投影，禁止返回物理主键

Use [test-driven-development] mode for this task.

**Files:**
- Modify: [`penmate-backend/src/main/java/com/penmate/backend/application/auth/AuthApplicationService.java`](penmate-backend/src/main/java/com/penmate/backend/application/auth/AuthApplicationService.java)
- Modify: [`penmate-backend/src/main/java/com/penmate/backend/application/iam/IamQueryApplicationService.java`](penmate-backend/src/main/java/com/penmate/backend/application/iam/IamQueryApplicationService.java)
- Modify: [`penmate-backend/src/main/java/com/penmate/backend/application/rag/RagApplicationService.java`](penmate-backend/src/main/java/com/penmate/backend/application/rag/RagApplicationService.java)
- Modify: [`penmate-backend/src/main/java/com/penmate/backend/application/model/ModelApplicationService.java`](penmate-backend/src/main/java/com/penmate/backend/application/model/ModelApplicationService.java)
- Modify: 相关返回 Map/DTO 的 application tests

**Step 1: Write the failing test**

将所有 `Map.of("id", ...)` 的场景改成“物理主键与业务 ID 不相等”的测试样例。重点覆盖：

- 登录态 [`AuthApplicationService.me()`](penmate-backend/src/main/java/com/penmate/backend/application/auth/AuthApplicationService.java:156)
- RAG 检索日志返回 [`RagApplicationService`](penmate-backend/src/main/java/com/penmate/backend/application/rag/RagApplicationService.java:231)
- 模型配置列表/详情/偏好返回 [`ModelApplicationService`](penmate-backend/src/main/java/com/penmate/backend/application/model/ModelApplicationService.java)

**Step 2: Run test to verify it fails**

Run: [`mvn -q -Dtest=AuthApplicationServiceTest,ModelApplicationServiceTest,RagApplicationServiceTest test`](penmate-backend/pom.xml)

Expected: 至少一项断言显示当前 `id` 取错来源。

**Step 3: Write minimal implementation**

实施规则：

1. 对外响应中字段名仍可保持 `id`，但值必须来自业务 ID 字段。
2. 内部领域对象允许继续保留 `id` 物理主键，不在应用层向上直接透传整个领域对象。
3. 优先引入显式 DTO / view model，避免控制器直接返回领域对象：
   - `AuthMeView`
   - `RbacUserView`
   - `ModelConfigView`
   - `NovelProjectView`
   - `ApprovalView`
4. 若短期不引入 DTO，至少在 `Map<String, Object>` 构造时显式映射：

```java
result.put("id", user.getUserId());
```

而不是：

```java
result.put("id", user.getId());
```

**Step 4: Run test to verify it passes**

Run: [`mvn -q -Dtest=AuthApplicationServiceTest,ModelApplicationServiceTest,RagApplicationServiceTest test`](penmate-backend/pom.xml)

Expected: 全部通过，且 `id` 断言值稳定等于业务 ID。

**Step 5: Commit**

```bash
git add penmate-backend/src/main/java/com/penmate/backend/application/auth/AuthApplicationService.java penmate-backend/src/main/java/com/penmate/backend/application/iam/IamQueryApplicationService.java penmate-backend/src/main/java/com/penmate/backend/application/rag/RagApplicationService.java penmate-backend/src/main/java/com/penmate/backend/application/model/ModelApplicationService.java penmate-backend/src/test/java/com/penmate/backend/application
git commit -m "fix: project application responses to business ids"
```

### Task 4: 修正 interfaces 层接口契约、路径变量与 OpenAPI 说明

Use [test-driven-development] mode for this task.

**Files:**
- Modify: [`penmate-backend/src/main/java/com/penmate/backend/interfaces/api/model/ModelController.java`](penmate-backend/src/main/java/com/penmate/backend/interfaces/api/model/ModelController.java)
- Modify: [`penmate-backend/src/main/java/com/penmate/backend/interfaces/config/OpenApiConfig.java`](penmate-backend/src/main/java/com/penmate/backend/interfaces/config/OpenApiConfig.java)
- Modify: 其他直接返回领域对象的 controller
- Modify: 对应 controller tests

**Step 1: Write the failing test**

重点为以下问题建立失败测试：

1. `PathVariable` 名称虽为 `keyId` / `modelConfigId`，但服务层如果实参传递的是物理主键，需要被拦截。
2. `GET /api/v1/model/providers` 当前可能直接返回 [`ModelProvider`](penmate-backend/src/main/java/com/penmate/backend/interfaces/api/model/ModelController.java:41)，需要验证 `$.data[0].id` 是否为业务服务商 ID。
3. OpenAPI 文档中所有 `id` 描述必须统一解释为“业务语义 ID”，且示例值要与接口测试一致。

**Step 2: Run test to verify it fails**

Run: [`mvn -q -Dtest=ModelControllerTest,OpenApiConfigTest,AuthControllerTest,NovelControllerTest test`](penmate-backend/pom.xml)

Expected: 若控制器仍透传领域对象，将有 JSON 断言失败。

**Step 3: Write minimal implementation**

实施要点：

1. 对所有 Controller 做一次契约审计：
   - 不直接返回含物理主键 `id` 的领域对象。
   - 返回 DTO 时统一把 DTO 的 `id` 绑定到业务 ID。
2. 对 [`ModelController`](penmate-backend/src/main/java/com/penmate/backend/interfaces/api/model/ModelController.java:40) 这类直接返回领域对象的场景，优先改为返回 `List<Map<String, Object>>` 或专用 DTO。
3. 在 [`OpenApiConfig.describeField()`](penmate-backend/src/main/java/com/penmate/backend/interfaces/config/OpenApiConfig.java:357) 中补充更精确的模型域说明，确保 `providerId`、`keyId`、`modelConfigId`、`approvalId` 全部明示为业务 ID。
4. 若发现 `/auth/me`、`/users`、`/roles`、`/projects` 等接口响应同时存在 `id` 与 `xxxId`，统一收敛为仅保留对外 `id`，不再重复暴露 `xxxId`，除非前端仍依赖迁移期兼容字段。

**Step 4: Run test to verify it passes**

Run: [`mvn -q -Dtest=ModelControllerTest,OpenApiConfigTest,AuthControllerTest,RbacQueryControllerTest,NovelControllerTest,ApprovalControllerTest test`](penmate-backend/pom.xml)

Expected: 所有接口层契约测试通过，OpenAPI 说明与真实接口一致。

**Step 5: Commit**

```bash
git add penmate-backend/src/main/java/com/penmate/backend/interfaces/api penmate-backend/src/main/java/com/penmate/backend/interfaces/config/OpenApiConfig.java penmate-backend/src/test/java/com/penmate/backend/interfaces
git commit -m "refactor: expose only business ids at interface layer"
```

### Task 5: 修正前端 API 模块，消除对物理主键的回退依赖

Use [test-driven-development] mode for this task.

**Files:**
- Modify: [`penmate-frontend/src/api/modules/model.api.ts`](penmate-frontend/src/api/modules/model.api.ts)
- Modify: [`penmate-frontend/src/api/modules/auth.api.ts`](penmate-frontend/src/api/modules/auth.api.ts)
- Modify: [`penmate-frontend/src/api/modules/rbac.api.ts`](penmate-frontend/src/api/modules/rbac.api.ts)
- Modify: [`penmate-frontend/src/api/modules/novel.api.ts`](penmate-frontend/src/api/modules/novel.api.ts)
- Modify: 对应 `.spec.ts`

**Step 1: Write the failing test**

补充/修改 API 层测试，要求：

1. 请求参数的命名与语义始终是业务 ID。
2. 响应归一化必须输出统一的前端业务主键 `id`，且来自 `xxxId` 或服务端契约中的业务值。
3. 不再允许 API 模块对物理主键做兜底合并。

以模型模块为例：

- [`updateUserModelConfig()`](penmate-frontend/src/api/modules/model.api.ts:79)
- [`deleteUserModelConfig()`](penmate-frontend/src/api/modules/model.api.ts:85)

要验证传递的是 `modelConfigId` 对应业务值，而不是原始记录物理主键。

**Step 2: Run test to verify it fails**

Run: [`npm run test -- src/api/modules/model.api.spec.ts src/api/modules/auth.api.spec.ts src/api/modules/rbac.api.spec.ts src/api/modules/novel.api.spec.ts`](penmate-frontend/package.json)

Expected: 至少有一处测试失败，提示响应归一化或参数传递仍混用物理主键。

**Step 3: Write minimal implementation**

实施要点：

1. 在 API 模块层新增统一归一化函数，例如：

```ts
const pickBusinessId = (item: Record<string, unknown>, ...keys: string[]) => {
  for (const key of keys) {
    const value = item[key]
    if (typeof value === 'number' && value > 0) return value
    if (typeof value === 'string' && value.trim()) return value.trim()
  }
  return null
}
```

2. 对各领域分别显式指定业务 ID 键优先级，只允许业务语义字段，不允许 `id` 作为兜底，除非服务端契约已明确 `id` 本身就是业务 ID。
3. 对迁移期兼容场景，在 API 模块中集中处理一次，不允许继续分散在组件层 `fooId || id`。

**Step 4: Run test to verify it passes**

Run: [`npm run test -- src/api/modules/model.api.spec.ts src/api/modules/auth.api.spec.ts src/api/modules/rbac.api.spec.ts src/api/modules/novel.api.spec.ts`](penmate-frontend/package.json)

Expected: API 模块测试全部通过，业务 ID 归一化逻辑固定在单点。

**Step 5: Commit**

```bash
git add penmate-frontend/src/api/modules
git commit -m "refactor: normalize frontend api ids to business ids"
```

### Task 6: 修正前端视图、状态与组件绑定

Use [test-driven-development] mode for this task.

**Files:**
- Modify: [`penmate-frontend/src/views/AdminRbac/index.vue`](penmate-frontend/src/views/AdminRbac/index.vue)
- Modify: [`penmate-frontend/src/composables/auth/useLoginSubmit.ts`](penmate-frontend/src/composables/auth/useLoginSubmit.ts)
- Modify: [`penmate-frontend/src/composables/bookshelf/useBookshelf.ts`](penmate-frontend/src/composables/bookshelf/useBookshelf.ts)
- Modify: [`penmate-frontend/src/components/workbench/ModelSettings.vue`](penmate-frontend/src/components/workbench/ModelSettings.vue)
- Modify: [`penmate-frontend/src/components/workbench/PluginWorkshop.vue`](penmate-frontend/src/components/workbench/PluginWorkshop.vue)
- Modify: [`penmate-frontend/src/views/MyBooks/index.vue`](penmate-frontend/src/views/MyBooks/index.vue)
- Modify: [`penmate-frontend/src/components/workbench/ApprovalCard.vue`](penmate-frontend/src/components/workbench/ApprovalCard.vue)
- Modify: [`penmate-frontend/src/composables/workbench/useWorkbenchApprovals.ts`](penmate-frontend/src/composables/workbench/useWorkbenchApprovals.ts)
- Modify: 对应 `.spec.ts`

**Step 1: Write the failing test**

重点覆盖以下现有高风险点：

1. [`AdminRbac/index.vue`](penmate-frontend/src/views/AdminRbac/index.vue:650) 当前大量使用 `(item.roleId || item.id)`、`(item.permissionId || item.id)`、`(menu.menuId || menu.id)`。
2. [`useLoginSubmit.ts`](penmate-frontend/src/composables/auth/useLoginSubmit.ts:37) 当前优先级是 `profile.userId ?? profile.id ?? profile.uid`，需改成“只接受业务语义字段或 API 层已归一化后的 `id`”。
3. [`useBookshelf.ts`](penmate-frontend/src/composables/bookshelf/useBookshelf.ts:64) 将作品 `id` 绑定到 `projectId`，这是正确方向，但需补测试确保永不回退到物理主键。
4. [`ModelSettings.vue`](penmate-frontend/src/components/workbench/ModelSettings.vue:333) 当前 `providerId: Number(item.providerId ?? item.id ?? 0)`，需要收敛为只吃 API 归一化后的业务 ID。
5. [`PluginWorkshop.vue`](penmate-frontend/src/components/workbench/PluginWorkshop.vue:124) 需要验证插件 `id` 的来源是否为 `pluginId` 而非物理主键。
6. [`useWorkbenchApprovals.ts`](penmate-frontend/src/composables/workbench/useWorkbenchApprovals.ts:39) 对审批卡片 ID 的 `Number(id)` 转换必须明确来源于审批业务 ID。

**Step 2: Run test to verify it fails**

Run: [`npm run test -- src/views/AdminRbac/index.spec.ts src/components/workbench/ModelSettings.spec.ts src/composables/auth/useLoginSubmit.spec.ts src/composables/bookshelf/useBookshelf.spec.ts src/composables/workbench/__tests__/useWorkbenchApprovals.spec.ts`](penmate-frontend/package.json)

Expected: 至少出现 1 个失败断言，表明视图层仍存在回退到物理主键的绑定逻辑。

**Step 3: Write minimal implementation**

实施要点：

1. 组件层默认只消费 API 层已经归一化后的统一 `id`。
2. 若迁移期必须保留 `roleId` / `permissionId` / `menuId`，则只允许在单一适配函数中兼容，不允许在模板中散落 `fooId || id`。
3. 将选择框 `value`、列表 `:key`、点击事件参数、路由 query 参数全部固定为业务 ID。
4. 对工作台审批流，保持 [`ApprovalCardData.id`](penmate-frontend/src/components/workbench/ApprovalCard.vue:35) 为审批业务 ID 字符串，不允许以聊天消息物理自增 ID 替代。

**Step 4: Run test to verify it passes**

Run: [`npm run test -- src/views/AdminRbac/index.spec.ts src/components/workbench/ModelSettings.spec.ts src/composables/auth/useLoginSubmit.spec.ts src/composables/bookshelf/useBookshelf.spec.ts src/composables/workbench/__tests__/useWorkbenchApprovals.spec.ts`](penmate-frontend/package.json)

Expected: 所有视图/状态相关测试通过，UI 选择、删除、更新、审批动作均使用业务 ID。

**Step 5: Commit**

```bash
git add penmate-frontend/src/views/AdminRbac/index.vue penmate-frontend/src/composables/auth/useLoginSubmit.ts penmate-frontend/src/composables/bookshelf/useBookshelf.ts penmate-frontend/src/components/workbench/ModelSettings.vue penmate-frontend/src/components/workbench/PluginWorkshop.vue penmate-frontend/src/views/MyBooks/index.vue penmate-frontend/src/components/workbench/ApprovalCard.vue penmate-frontend/src/composables/workbench/useWorkbenchApprovals.ts penmate-frontend/src/**/*.spec.ts
git commit -m "fix: bind frontend state and views to business ids"
```

### Task 7: 全量回归、残留搜索与人工冒烟

Use [verification-before-completion] mode for this task.

**Files:**
- Verify only: [`penmate-backend/src/main/java`](penmate-backend/src/main/java)
- Verify only: [`penmate-frontend/src`](penmate-frontend/src)
- Verify only: [`penmate-backend/src/test/java`](penmate-backend/src/test/java)
- Verify only: [`penmate-frontend/src/**/*.spec.ts`](penmate-frontend/src)

**Step 1: Write the failing test**

此任务以“残留搜索”和“回归验证”替代新增功能测试；先定义通过标准：

1. 后端接口对外 `id` 全部指向业务 ID。
2. 前端组件模板与 composable 不再出现 `fooId || id` / `fooId ?? id`。
3. Repository / Gateway 中对业务对象定位的方法名都带明确业务语义。

**Step 2: Run verification searches**

Run: [`mvn -q test`](penmate-backend/pom.xml)

Expected: 后端全量测试通过。

Run: [`npm run test`](penmate-frontend/package.json)

Expected: 前端全量测试通过。

Run: [`rg "\|\| id|\?\? id|Map\.of\(\"id\"|put\(\"id\"|findById\(" penmate-backend/src/main/java penmate-frontend/src`](.gitignore)

Expected: 结果仅剩以下几类合法场景：

1. LLM/tool-call 协议中的第三方字段 `id`，例如 [`AgentLlmToolCall.id`](penmate-backend/src/main/java/com/penmate/backend/application/agent/llm/AgentLlmToolCall.java:7)。
2. 前端聊天消息本地渲染自增 ID，例如 [`ChatMessage.id`](penmate-frontend/src/components/workbench/workbenchTypes.ts:39)，其语义明确不是业务对象主键。
3. 数据库持久化层实体内部的物理主键字段，但不能被接口层直接外露。

**Step 3: Manual regression checklist**

手工冒烟至少覆盖以下路径：

1. 登录后获取当前用户资料，确认返回 `id` 与会话用户 ID 一致。
2. 管理后台：
   - 选中用户
   - 分配角色
   - 移除角色
   - 查看角色权限
   - 验证所有网络请求参数均为业务 ID
3. 我的作品：
   - 进入作品工作台
   - 删除作品
   - 验证路由 query 中 `projectId` 为业务 ID
4. 模型设置：
   - 查询 provider 列表
   - 新增/编辑/删除 key
   - 新增/编辑/删除 model config
   - 保存模型偏好
5. 审批流：
   - 展示审批卡
   - 点击通过/拒绝
   - 验证提交的是审批业务 ID，而非消息局部 ID

**Step 4: Completion criteria**

满足以下条件才可宣告完成：

1. 后端全量测试通过。
2. 前端全量测试通过。
3. 全文搜索无新增物理主键外露点。
4. OpenAPI 描述、Controller 契约、前端 API 类型、视图绑定语义一致。
5. 审计文档 [`business-id-audit-notes.md`](docs/plans/business-id-audit-notes.md) 更新为“问题列表 + 处理结果 + 残留例外”。

**Step 5: Commit**

```bash
git add docs/plans/business-id-audit-notes.md
git commit -m "test: verify business-id refactor end to end"
```

---

## 四、测试与回归验证策略

### 4.1 测试分层策略

1. **Repository / Gateway 层**
   - 重点验证“方法语义与 SQL 条件列一致”。
   - 不要求改变物理表结构。
2. **Application 层**
   - 重点验证 `id` 投影值来源于业务 ID。
   - 对 `Map` 或 DTO 响应全部制造“物理主键 ≠ 业务 ID”的测试样本。
3. **Controller / 接口层**
   - 使用 MockMvc 验证 `$.data.id`。
   - 验证不再额外暴露 `userId` / `roleId` / `projectId` 等冗余字段，除非兼容期必需。
4. **前端 API 层**
   - 验证请求 URL、query、payload 使用业务 ID。
   - 验证响应归一化后给 UI 的 `id` 确实是业务 ID。
5. **前端视图/组件层**
   - 验证列表 `:key`、选中值、提交参数、事件参数、路由参数均使用业务 ID。

### 4.2 回归风险与应对

1. **风险：旧测试数据里 `id == xxxId`，导致问题被掩盖。**
   - 应对：所有关键测试样本都强制设置二者不同。
2. **风险：前端模板中残留回退表达式。**
   - 应对：统一通过全文搜索 `|| id` / `?? id` 清零。
3. **风险：接口改造后前端与后端短暂不兼容。**
   - 应对：先改后端契约测试，再改前端 API 归一化，最后删兼容分支。
4. **风险：持久化层排序、审计、日志仍需物理主键。**
   - 应对：允许内部保留，但禁止跨越接口边界外露。

### 4.3 允许保留的合法 `id` 场景

以下场景不属于本次“误用数据库主键”范畴：

1. 第三方协议字段 `id`：
   - [`AgentLlmToolCall.id`](penmate-backend/src/main/java/com/penmate/backend/application/agent/llm/AgentLlmToolCall.java:7)
   - [`toolCallPayload.get("id")`](penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/runtime/ToolCallResumeService.java:76)
2. 前端纯展示型局部消息 ID：
   - [`messages.value.push({ id: msgIdCounter++, ... })`](penmate-frontend/src/composables/workbench/useWorkbenchChat.ts:374)
3. SQL `ORDER BY id` 仅用于库内排序，不属于对外语义泄漏。

---

## 五、推荐执行顺序（半天到一天）

1. Task 1：建立失败基线，1~1.5 小时。
2. Task 2：仓储/网关命名收敛，1~2 小时。
3. Task 3：应用层投影修正，1~2 小时。
4. Task 4：接口层契约与 OpenAPI 修正，1~1.5 小时。
5. Task 5：前端 API 层归一化，1~1.5 小时。
6. Task 6：前端视图与状态绑定修正，1~2 小时。
7. Task 7：全量回归与清场，1 小时。

**Estimated effort:** 7~11 小时

---

## 六、执行交接

Plan file: [`docs/plans/2026-05-06-business-id-refactor-plan.md`](docs/plans/2026-05-06-business-id-refactor-plan.md)

执行选项：

1. **Subagent-Driven（当前会话）**：使用 [subagent-driven-development] 按任务逐个执行，并在每个任务后做 review gate。
2. **Parallel Session（独立会话）**：使用 [executing-plans] 从该计划文件启动实现。
3. **Manual Implementation**：开发者按本计划手工执行。

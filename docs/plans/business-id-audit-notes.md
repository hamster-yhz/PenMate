# Business ID 审计基线

## 范围

- 后端控制器契约测试：[`AuthControllerTest.java`](penmate-backend/src/test/java/com/penmate/backend/interfaces/api/auth/AuthControllerTest.java)
- 后端控制器契约测试：[`RbacQueryControllerTest.java`](penmate-backend/src/test/java/com/penmate/backend/interfaces/api/rbac/RbacQueryControllerTest.java)
- 后端控制器契约测试：[`ModelControllerTest.java`](penmate-backend/src/test/java/com/penmate/backend/interfaces/api/model/ModelControllerTest.java)
- 前端视图测试：[`index.spec.ts`](penmate-frontend/src/views/AdminRbac/index.spec.ts)
- 前端 API 测试：[`model.api.spec.ts`](penmate-frontend/src/api/modules/model.api.spec.ts)

## 已确认问题基线

1. [`/api/v1/auth/me`](penmate-backend/src/test/java/com/penmate/backend/interfaces/api/auth/AuthControllerTest.java) 需要确保对外仅暴露业务语义 [`id`](penmate-backend/src/test/java/com/penmate/backend/interfaces/api/auth/AuthControllerTest.java)，不重复暴露 [`userId`](penmate-backend/src/test/java/com/penmate/backend/interfaces/api/auth/AuthControllerTest.java)。
2. [`/api/v1/users`](penmate-backend/src/test/java/com/penmate/backend/interfaces/api/rbac/RbacQueryControllerTest.java)、[`/api/v1/users/{userId}/roles`](penmate-backend/src/test/java/com/penmate/backend/interfaces/api/rbac/RbacQueryControllerTest.java)、[`/api/v1/roles/{roleId}/permissions`](penmate-backend/src/test/java/com/penmate/backend/interfaces/api/rbac/RbacQueryControllerTest.java) 目前测试样本已强制设置“物理主键 ≠ 业务 ID”，用于拦截接口层透传领域物理主键。
3. [`/api/v1/model/providers`](penmate-backend/src/test/java/com/penmate/backend/interfaces/api/model/ModelControllerTest.java)、[`/api/v1/model/keys`](penmate-backend/src/test/java/com/penmate/backend/interfaces/api/model/ModelControllerTest.java)、[`/api/v1/model/configs`](penmate-backend/src/test/java/com/penmate/backend/interfaces/api/model/ModelControllerTest.java) 需要统一向前端暴露业务 ID 形式的 [`id`](penmate-backend/src/test/java/com/penmate/backend/interfaces/api/model/ModelControllerTest.java)。
4. [`AdminRbac/index.vue`](penmate-frontend/src/views/AdminRbac/index.vue) 当前仍存在 [`roleId || id`](penmate-frontend/src/views/AdminRbac/index.vue:278) 与同类回退，测试已通过制造不同 ID 值来暴露问题。
5. [`model.api.ts`](penmate-frontend/src/api/modules/model.api.ts) 当前对更新 / 删除接口主要验证 URL 组装，新增测试用于固定业务 [`modelConfigId`](penmate-frontend/src/api/modules/model.api.ts:79) 路径参数语义。
6. [`AgentControllerTest.java`](penmate-backend/src/test/java/com/penmate/backend/interfaces/api/agent/AgentControllerTest.java) 已进入 string-only RED 基线：把 [`/api/v1/novels/{projectId}/agent/sessions`](penmate-backend/src/test/java/com/penmate/backend/interfaces/api/agent/AgentControllerTest.java)、[`/api/v1/novels/{projectId}/agent/sessions/{sessionId}/recovery`](penmate-backend/src/test/java/com/penmate/backend/interfaces/api/agent/AgentControllerTest.java) 改为非数值字符串业务 ID（如 `project-10001`、`session-90001`）后，执行 [`mvn -q -Dtest=AgentControllerTest test`](penmate-backend/pom.xml) 出现 3 个 `422` 失败；当前根因已定位到 [`AgentController.java`](penmate-backend/src/main/java/com/penmate/backend/interfaces/api/agent/AgentController.java:150) 仍通过 `parseBusinessId()` / `parseOptionalBusinessId()` 强制将业务 ID 转为 `Long`，且旧响应字段 [`conversationId`](penmate-backend/src/test/java/com/penmate/backend/interfaces/api/agent/AgentControllerTest.java:69) 仍在旧测试契约中残留。

## 处理结果

1. [`LegacyRouteExposureMvcTest.java`](penmate-backend/src/test/java/com/penmate/backend/interfaces/api/model/LegacyRouteExposureMvcTest.java) 已补齐 [`AgentConversationAppService`](penmate-backend/src/main/java/com/penmate/backend/interfaces/api/agent/AgentController.java:36) 的测试 mock，`ApplicationContext` 可正常启动；同时把旧路由 [`/api/v1/novels/{projectId}/model-policies`](penmate-backend/src/test/java/com/penmate/backend/interfaces/api/model/LegacyRouteExposureMvcTest.java:115) 的 `GET` / `POST` / `PUT` / `DELETE` 全部固定为 `404`，避免遗留入口通过其他 HTTP 方法重新暴露。
2. [`ModelSettings.spec.ts`](penmate-frontend/src/components/workbench/ModelSettings.spec.ts) 已对齐 [`ModelSettings.vue`](penmate-frontend/src/components/workbench/ModelSettings.vue:192) 的 string-only session 契约：测试会话改为字符串 [`userId`](penmate-frontend/src/stores/session.ts:4)，并将 [`createUserModelConfig()`](penmate-frontend/src/components/workbench/ModelSettings.spec.ts) / [`updateUserModelConfig()`](penmate-frontend/src/components/workbench/ModelSettings.spec.ts) / [`deleteUserModelConfig()`](penmate-frontend/src/components/workbench/ModelSettings.spec.ts) / [`saveUserModelPreferences()`](penmate-frontend/src/components/workbench/ModelSettings.spec.ts) 的调用断言同步改为字符串业务 ID。
3. [`useLoginSubmit.spec.ts`](penmate-frontend/src/composables/auth/useLoginSubmit.spec.ts) 已对齐 [`useLoginSubmit.ts`](penmate-frontend/src/composables/auth/useLoginSubmit.ts:37) 的用户会话归一化行为，登录成功后断言 [`getSession().userId`](penmate-frontend/src/composables/auth/useLoginSubmit.spec.ts:80) 为字符串值 `"8"`。

## 回归证据

- 定向后端验证：执行 [`mvn -q -Dtest=LegacyRouteExposureMvcTest test`](penmate-backend/pom.xml)，实际输出显示 [`LegacyRouteExposureMvcTest`](penmate-backend/src/test/java/com/penmate/backend/interfaces/api/model/LegacyRouteExposureMvcTest.java) 启动成功，并对遗留路径 [`/api/v1/novels/920001/model-policies`](penmate-backend/src/test/java/com/penmate/backend/interfaces/api/model/LegacyRouteExposureMvcTest.java:116) 记录 `No static resource ... model-policies`，测试进程以 `Exit code: 0` 结束。
- 定向前端验证：执行 [`npm run test -- src/components/workbench/ModelSettings.spec.ts`](penmate-frontend/package.json)，实际输出为 `src/components/workbench/ModelSettings.spec.ts (14 tests)` 且 `14 passed`。
- 定向前端验证：执行 [`npm run test -- src/composables/auth/useLoginSubmit.spec.ts`](penmate-frontend/package.json)，实际输出为 `src/composables/auth/useLoginSubmit.spec.ts (3 tests)` 且 `3 passed`。
- 后端全量回归：执行 [`mvn -q test`](penmate-backend/pom.xml)，命令结果为 `Exit code: 0`。
- 前端全量回归：执行 [`npm run test && npm run build`](penmate-frontend/package.json)，实际输出显示 [`vitest`](penmate-frontend/package.json) 汇总为 `Test Files 65 passed`、`Tests 320 passed`，随后 [`vite build`](penmate-frontend/package.json) 输出 `✓ built in 1.84s`。

## 当前阶段说明

本轮阻塞已解除；当前基线继续保持 business ID string-only 方案，不回退 [`ModelSettings.vue`](penmate-frontend/src/components/workbench/ModelSettings.vue:176) 与 [`session.ts`](penmate-frontend/src/stores/session.ts:1) 的现有实现。允许保留的例外场景待后续专项审计任务继续补充。

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

## 当前阶段说明

本文件先记录失败基线与排查范围；待后续任务完成后，再补充“处理结果”与“允许保留的例外场景”。

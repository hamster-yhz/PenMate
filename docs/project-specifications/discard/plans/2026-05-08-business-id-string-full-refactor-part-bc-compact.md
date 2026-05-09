# 业务 ID string 化分卷 BC 压缩终稿

> 关联主计划：[`2026-05-08-business-id-string-full-refactor-plan.md`](docs/plans/2026-05-08-business-id-string-full-refactor-plan.md)
>
> 关联分卷 A：[`2026-05-08-business-id-string-full-refactor-part-a-id-inventory.md`](docs/plans/2026-05-08-business-id-string-full-refactor-part-a-id-inventory.md)
>
> 本文件替代被工具截断的 [`2026-05-08-business-id-string-full-refactor-part-b-backend.md`](docs/plans/2026-05-08-business-id-string-full-refactor-part-b-backend.md) 与 [`2026-05-08-business-id-string-full-refactor-part-c-frontend.md`](docs/plans/2026-05-08-business-id-string-full-refactor-part-c-frontend.md)，作为稳定可执行终稿。

## 目标

- 后端所有对外业务 ID：Path / Query / Body / Response 全部改为 `string`
- 前端所有业务 ID：API / types / stores / composables / views / tests 全部改为 `string`
- 不保留任何历史兼容代码：
  - 禁止 `number | string`
  - 禁止 `Number(...)` / `parseInt(...)`
  - 禁止 `id` / `xxxId` 双字段
  - 禁止 `conversationId` / `sessionId` fallback
  - 禁止 `ownerId -> ownerUserId`、`roleId || id` 等 alias / fallback

---

## A. 后端压缩终稿

### A1. 全局规则

| 可勾选 | 规则 | 说明 |
|---|---|---|
| [ ] | Path/Query string-only | 禁止 `@PathVariable Long ...Id`、`@RequestParam Long ...Id` |
| [ ] | Body DTO string-only | 接口 DTO 内 `Long ...Id` 归零 |
| [ ] | Response string-only | 所有业务 ID 响应字段统一 `String` |
| [ ] | 无 controller parse 兼容 | 删除 [`parseBusinessId()`](penmate-backend/src/main/java/com/penmate/backend/interfaces/api/agent/AgentController.java:150)、[`parseRequiredPositiveId()`](penmate-backend/src/main/java/com/penmate/backend/interfaces/api/model/ModelController.java:342) |
| [ ] | 无双字段响应 | 禁止 `id` 与 `xxxId` 并存 |
| [ ] | 无会话 fallback | 禁止 `conversationId` / `sessionId` 双出口 |

### A2. Controller × 接口压缩表

| 可勾选 | Controller | 接口清单 | Path/Query ID | Body DTO ID | Response ID | DTO / 响应文件 | 测试文件 | 不保留兼容 |
|---|---|---|---|---|---|---|---|---|
| [ ] | [`AgentController`](penmate-backend/src/main/java/com/penmate/backend/interfaces/api/agent/AgentController.java:31) | `GET/POST sessions`、`GET recovery`、`POST resume`、`POST turns` | `projectId` / `sessionId` string | `userId` / `operatorId` / `chapterId` / `modelConfigId` string | `sessionId` / `taskId` / `requestContextId` / `styleId` / `chapterId` / `modelConfigId` string | [`CreateAgentConversationDto.java`](penmate-backend/src/main/java/com/penmate/backend/interfaces/api/agent/dto/CreateAgentConversationDto.java), [`ResumeAgentSessionDto.java`](penmate-backend/src/main/java/com/penmate/backend/interfaces/api/agent/dto/ResumeAgentSessionDto.java), [`CreateAgentTurnDto.java`](penmate-backend/src/main/java/com/penmate/backend/interfaces/api/agent/dto/CreateAgentTurnDto.java), [`AgentRecoverySnapshotDto.java`](penmate-backend/src/main/java/com/penmate/backend/interfaces/api/agent/dto/AgentRecoverySnapshotDto.java), [`AgentTaskDto.java`](penmate-backend/src/main/java/com/penmate/backend/interfaces/api/agent/dto/AgentTaskDto.java) | [`AgentControllerTest.java`](penmate-backend/src/test/java/com/penmate/backend/interfaces/api/agent/AgentControllerTest.java), [`AgentControllerRecoveryContractTest.java`](penmate-backend/src/test/java/com/penmate/backend/interfaces/api/agent/AgentControllerRecoveryContractTest.java) | 删除 parse；删除 `conversationId` |
| [ ] | [`ApprovalController`](penmate-backend/src/main/java/com/penmate/backend/interfaces/api/approval/ApprovalController.java:27) | `POST list detail approve reject` | `projectId` / `approvalId` string | `taskId` / `requestedBy` / `reviewedBy` string | `approvalId` / `projectId` / `taskId` / `requestedBy` / `reviewedBy` string | [`CreateApprovalRequestDto.java`](penmate-backend/src/main/java/com/penmate/backend/interfaces/api/approval/dto/CreateApprovalRequestDto.java), [`ReviewApprovalRequestDto.java`](penmate-backend/src/main/java/com/penmate/backend/interfaces/api/approval/dto/ReviewApprovalRequestDto.java), [`ApprovalRequest.java`](penmate-backend/src/main/java/com/penmate/backend/domain/approval/model/ApprovalRequest.java) | [`ApprovalControllerTest.java`](penmate-backend/src/test/java/com/penmate/backend/interfaces/api/approval/ApprovalControllerTest.java) | 不保留 number body |
| [ ] | [`NovelController`](penmate-backend/src/main/java/com/penmate/backend/interfaces/api/novel/NovelController.java:50) | 30 个接口：`projects` / `volumes` / `chapters` / `members` / `versions` / `content-*` / `outlines` / `cards` / `card-relations` | `projectId` / `operatorId` / `volumeId` / `chapterId` / `userId` / `nodeId` / `relationId` string | `ownerUserId` / `volumeId` / `outlineNodeId` / `userId` / `parentId` / `fromCardId` / `toCardId` string | `projectId` / `ownerUserId` / `volumeId` / `chapterId` / `userId` / `outlineNodeId` / `nodeId` / `cardId` / `relationId` string；仅 `versionNo` 保持数值 | [`CreateNovelProjectDto.java`](penmate-backend/src/main/java/com/penmate/backend/interfaces/api/novel/dto/CreateNovelProjectDto.java), [`CreateNovelChapterDto.java`](penmate-backend/src/main/java/com/penmate/backend/interfaces/api/novel/dto/CreateNovelChapterDto.java), [`CreateNovelOutlineNodeDto.java`](penmate-backend/src/main/java/com/penmate/backend/interfaces/api/novel/dto/CreateNovelOutlineNodeDto.java), [`CreateNovelCardRelationDto.java`](penmate-backend/src/main/java/com/penmate/backend/interfaces/api/novel/dto/CreateNovelCardRelationDto.java), [`NovelProject.java`](penmate-backend/src/main/java/com/penmate/backend/domain/novel/model/NovelProject.java), [`NovelChapter.java`](penmate-backend/src/main/java/com/penmate/backend/domain/novel/model/NovelChapter.java), [`NovelOutlineNode.java`](penmate-backend/src/main/java/com/penmate/backend/domain/novel/model/NovelOutlineNode.java), [`NovelCard.java`](penmate-backend/src/main/java/com/penmate/backend/domain/novel/model/NovelCard.java), [`NovelCardRelation.java`](penmate-backend/src/main/java/com/penmate/backend/domain/novel/model/NovelCardRelation.java) | [`NovelControllerTest.java`](penmate-backend/src/test/java/com/penmate/backend/interfaces/api/novel/NovelControllerTest.java) | 不保留 `ownerId` alias；不把 `versionNo` 当 business ID |
| [ ] | [`StyleController`](penmate-backend/src/main/java/com/penmate/backend/interfaces/api/style/StyleController.java:32) | `list/create/get/update/delete/switch/analyze-sample` | `projectId` / `styleId` / `operatorId` / `sessionId` string | `toStyleId` string | `styleId` / `projectId` / `sessionId` string | [`SwitchStyleDto.java`](penmate-backend/src/main/java/com/penmate/backend/interfaces/api/style/dto/SwitchStyleDto.java), [`StyleProfile.java`](penmate-backend/src/main/java/com/penmate/backend/domain/style/model/StyleProfile.java) | [`StyleControllerTest.java`](penmate-backend/src/test/java/com/penmate/backend/interfaces/api/style/StyleControllerTest.java) | 删除 `Long sessionId` 绑定兼容 |
| [ ] | [`RagController`](penmate-backend/src/main/java/com/penmate/backend/interfaces/api/rag/RagController.java:28) | `documents/list/create/get/delete/upload-url/parse/embed/index-status/retrieval-logs` | `projectId` / `documentId` / `operatorId` string | 无业务 ID 或 `operatorId` string | `documentId` / `projectId` string | [`CreateRagDocumentDto.java`](penmate-backend/src/main/java/com/penmate/backend/interfaces/api/rag/dto/CreateRagDocumentDto.java), [`RagDocument.java`](penmate-backend/src/main/java/com/penmate/backend/domain/rag/model/RagDocument.java) | [`RagControllerTest.java`](penmate-backend/src/test/java/com/penmate/backend/interfaces/api/rag/RagControllerTest.java) | 日志/状态 Map 不得混入数值 ID |
| [ ] | [`PluginController`](penmate-backend/src/main/java/com/penmate/backend/interfaces/api/plugin/PluginController.java:30) | `catalog/item/projectPlugins/install/update/delete/callLogs` | `projectId` / `operatorId` string | 无业务 ID | `projectId` string | [`InstallPluginDto.java`](penmate-backend/src/main/java/com/penmate/backend/interfaces/api/plugin/dto/InstallPluginDto.java), [`PluginProjectInstall.java`](penmate-backend/src/main/java/com/penmate/backend/domain/plugin/model/PluginProjectInstall.java), [`PluginCallLog.java`](penmate-backend/src/main/java/com/penmate/backend/domain/plugin/model/PluginCallLog.java) | [`PluginControllerTest.java`](penmate-backend/src/test/java/com/penmate/backend/interfaces/api/plugin/PluginControllerTest.java) | `pluginCode` 非本次 ID，但不允许混入数值兼容 |
| [ ] | [`OpsController`](penmate-backend/src/main/java/com/penmate/backend/interfaces/api/ops/OpsController.java:22) | `getJob/listJobs/retryJob/runMigration/getMigration` | `jobId` / `migrationId` / `operatorId` string | `operatorId` string | `jobId` / `migrationId` / `operatorId` string | [`OpsAsyncJob.java`](penmate-backend/src/main/java/com/penmate/backend/domain/ops/model/OpsAsyncJob.java), [`OpsMigrationTask.java`](penmate-backend/src/main/java/com/penmate/backend/domain/ops/model/OpsMigrationTask.java) | [`OpsControllerTest.java`](penmate-backend/src/test/java/com/penmate/backend/interfaces/api/ops/OpsControllerTest.java) | path/query 不再 `Long` |
| [ ] | [`RbacQueryController`](penmate-backend/src/main/java/com/penmate/backend/interfaces/api/rbac/RbacQueryController.java:34) | `users/user/roles/userRoles/permissions/rolePermissions/createUser/updateUser/deleteUser/createRole/updateRole/deleteRole/assignRole/removeRole/assignPermission/removePermission/menus/profileMenus` | `userId` / `roleId` / `permissionId` / `menuId` string | 基本无业务 ID DTO；绑定动作用 query string | 响应只保留 `userId` / `roleId` / `permissionId` / `menuId` / `parentId`，禁止 `id` fallback | [`CreateUserDto.java`](penmate-backend/src/main/java/com/penmate/backend/interfaces/api/rbac/dto/CreateUserDto.java), [`CreateRoleDto.java`](penmate-backend/src/main/java/com/penmate/backend/interfaces/api/rbac/dto/CreateRoleDto.java), [`RbacQueryController.java`](penmate-backend/src/main/java/com/penmate/backend/interfaces/api/rbac/RbacQueryController.java:276) | [`RbacQueryControllerTest.java`](penmate-backend/src/test/java/com/penmate/backend/interfaces/api/rbac/RbacQueryControllerTest.java) | 删除 [`toSafeUser()`](penmate-backend/src/main/java/com/penmate/backend/interfaces/api/rbac/RbacQueryController.java:276) / [`toRoleView()`](penmate-backend/src/main/java/com/penmate/backend/interfaces/api/rbac/RbacQueryController.java:287) / [`toPermissionView()`](penmate-backend/src/main/java/com/penmate/backend/interfaces/api/rbac/RbacQueryController.java:297) / [`toMenuView()`](penmate-backend/src/main/java/com/penmate/backend/interfaces/api/rbac/RbacQueryController.java:307) 的 `id` 输出 |
| [ ] | [`ModelController`](penmate-backend/src/main/java/com/penmate/backend/interfaces/api/model/ModelController.java:39) | `providers/keys/official-keys/configs/preferences` 共 12 个接口 | `userId` / `operatorId` / `providerId` / `keyId` / `modelConfigId` string | `providerId` / `mainAgentModelConfigId` / `dirtyWorkAgentModelConfigId` string | 响应只保留 `providerId` / `keyId` / `userId` / `modelConfigId` 等语义字段，不再输出 `id` | [`CreateModelKeyDto.java`](penmate-backend/src/main/java/com/penmate/backend/interfaces/api/model/dto/CreateModelKeyDto.java), [`CreateOfficialModelKeyDto.java`](penmate-backend/src/main/java/com/penmate/backend/interfaces/api/model/dto/CreateOfficialModelKeyDto.java), [`CreateUserModelConfigDto.java`](penmate-backend/src/main/java/com/penmate/backend/interfaces/api/model/dto/CreateUserModelConfigDto.java), [`SaveUserModelPreferencesDto.java`](penmate-backend/src/main/java/com/penmate/backend/interfaces/api/model/dto/SaveUserModelPreferencesDto.java), [`ModelController.java`](penmate-backend/src/main/java/com/penmate/backend/interfaces/api/model/ModelController.java:42) | [`ModelControllerTest.java`](penmate-backend/src/test/java/com/penmate/backend/interfaces/api/model/ModelControllerTest.java), [`LegacyRouteExposureMvcTest.java`](penmate-backend/src/test/java/com/penmate/backend/interfaces/api/model/LegacyRouteExposureMvcTest.java) | 删除 parse-to-long，禁止 `BUSINESS_ID_KEYS` 继续作为兼容工具 |
| [ ] | [`AuthController`](penmate-backend/src/main/java/com/penmate/backend/interfaces/api/auth/AuthController.java:25) | `login/logout/refresh/me` | 无 path/query 业务 ID；`me` 响应含 `userId` / `menuId` | 无业务 ID DTO | `me` 响应只保留 `userId` / `menuId` 等语义字段，不再回写 `id` | [`AuthController.java`](penmate-backend/src/main/java/com/penmate/backend/interfaces/api/auth/AuthController.java:109) | [`AuthControllerTest.java`](penmate-backend/src/test/java/com/penmate/backend/interfaces/api/auth/AuthControllerTest.java) | 删除 `userId -> id` 回写 |

### A3. 搜索归零项

| 可勾选 | 搜索项 | 预期 |
|---|---|---|
| [ ] | `@PathVariable Long .*Id` | 0 结果 |
| [ ] | `@RequestParam\(".*Id"\) Long` | 0 结果 |
| [ ] | `private Long .*Id` | 接口 DTO 范围 0 结果 |
| [ ] | `parseBusinessId\(` | Controller 范围 0 结果 |
| [ ] | `parseRequiredPositiveId\(` | Controller 范围 0 结果 |
| [ ] | `data.put("id"` | 对外视图语境 0 结果 |

---

## B. 前端压缩终稿

### B1. 全局规则

| 可勾选 | 规则 | 说明 |
|---|---|---|
| [ ] | API 参数 string-only | 删除业务 ID 语境下的 [`IdLike`](penmate-frontend/src/api/types.ts:31) |
| [ ] | state string-only | store/composable/view 中业务 ID 不再使用 number |
| [ ] | 无数值化 | 禁止 `Number(...)` / `parseInt(...)` / `> 0` 检查业务 ID |
| [ ] | 无 alias/fallback | 禁止 `ownerId -> ownerUserId`、`roleId || id`、`conversationId ?? sessionId` |
| [ ] | 响应语义唯一 | 前端类型不再接受 `id` 与 `xxxId` 双字段 |

### B2. 前端逐文件压缩表

| 可勾选 | 模块类型 | 文件 | 涉及业务 ID | 当前问题 | 目标 string-only 状态 | 验证文件 |
|---|---|---|---|---|---|---|
| [ ] | type | [`types.ts`](penmate-frontend/src/api/types.ts:31) | 全部 | `IdLike = number | string` 扩散 | 删除或禁用于 business ID | API module specs |
| [ ] | api | [`agent.api.ts`](penmate-frontend/src/api/modules/agent.api.ts:8) | `projectId` / `sessionId` / `taskId` | 仍保留 `conversationId?` | 只保留 `sessionId` / `taskId` string | [`agent.api.spec.ts`](penmate-frontend/src/api/modules/agent.api.spec.ts), [`useWorkbenchChat.spec.ts`](penmate-frontend/src/composables/workbench/__tests__/useWorkbenchChat.spec.ts) |
| [ ] | api | [`approval.api.ts`](penmate-frontend/src/api/modules/approval.api.ts:7) | `projectId` / `approvalId` | `IdLike` 混合 | 全 string | approval API tests |
| [ ] | api | [`novel.api.ts`](penmate-frontend/src/api/modules/novel.api.ts:6) | `projectId` / `ownerUserId` / `operatorId` / `volumeId` / `chapterId` / `nodeId` / `cardId` / `relationId` | 全量 `IdLike` + `ownerId` fallback | 全 string；删除 `ownerId` alias | [`novel.api.spec.ts`](penmate-frontend/src/api/modules/novel.api.spec.ts) |
| [ ] | api | [`chapter.api.ts`](penmate-frontend/src/api/modules/chapter.api.ts:7) | `projectId` / `chapterId` / `operatorId` | `IdLike` 混合 | 业务 ID 改 string；`versionNo` 独立保留 | chapter API tests |
| [ ] | api | [`outline.api.ts`](penmate-frontend/src/api/modules/outline.api.ts:7) | `projectId` / `operatorId` / `nodeId` | `IdLike` 混合 | 全 string | outline API tests |
| [ ] | api | [`card.api.ts`](penmate-frontend/src/api/modules/card.api.ts:7) | `projectId` / `operatorId` / `cardId` / `relationId` | `IdLike` 混合 | 全 string | card API tests |
| [ ] | api | [`style.api.ts`](penmate-frontend/src/api/modules/style.api.ts:7) | `projectId` / `operatorId` / `styleId` / `sessionId` | `IdLike` 混合 | 全 string | style API tests |
| [ ] | api | [`rag.api.ts`](penmate-frontend/src/api/modules/rag.api.ts:7) | `projectId` / `documentId` | `IdLike` 混合 | 全 string | rag API tests |
| [ ] | api | [`plugin.api.ts`](penmate-frontend/src/api/modules/plugin.api.ts:13) | `projectId` / `operatorId` | `IdLike` 混合 | 全 string | plugin API tests |
| [ ] | api | [`ops.api.ts`](penmate-frontend/src/api/modules/ops.api.ts:7) | `migrationId` / `jobId` / `operatorId` | `IdLike` 混合 | 全 string | ops API tests |
| [ ] | api | [`rbac.api.ts`](penmate-frontend/src/api/modules/rbac.api.ts:10) | `userId` / `roleId` / `permissionId` / `menuId` | `IdLike` 混合 | 全 string；不再兼容 `id` 字段 | [`rbac.api.spec.ts`](penmate-frontend/src/api/modules/rbac.api.spec.ts) |
| [ ] | api | [`model.api.ts`](penmate-frontend/src/api/modules/model.api.ts:6) | `userId` / `operatorId` / `providerId` / `keyId` / `modelConfigId` / `mainAgentModelConfigId` / `dirtyWorkAgentModelConfigId` | 参数仍用 `IdLike`，仅局部 normalize | 参数 / payload / response business IDs 全 string | [`model.api.spec.ts`](penmate-frontend/src/api/modules/model.api.spec.ts) |
| [ ] | api | [`profile.api.ts`](penmate-frontend/src/api/modules/profile.api.ts:7) | `userId` / `menuId` | `IdLike` 混合 | 全 string | profile API tests |
| [ ] | type | [`workbenchTypes.ts`](penmate-frontend/src/components/workbench/workbenchTypes.ts:4) | `cardId` / `cardRelationId` / `fromCardId` / `toCardId` / `conversationId` / `ChatMessage.id` | 仍为 number / union | 全 string；`conversationId` 收敛为 `sessionId` 语义 | workbench specs |
| [ ] | store | [`workbenchSession.ts`](penmate-frontend/src/stores/workbenchSession.ts:3) | `sessionId` / `styleId` / `taskId` / `chapterId` / `modelConfigId` | 已部分 string 化 | 维持唯一 string 事实源 | [`useWorkbenchSessionRecovery.spec.ts`](penmate-frontend/src/composables/workbench/__tests__/useWorkbenchSessionRecovery.spec.ts) |
| [ ] | composable | [`useWorkbenchContext.ts`](penmate-frontend/src/composables/workbench/useWorkbenchContext.ts:23) | `projectId` / `operatorId` / `userId` | 用 `Number()` 解析 query/session/storage | 全部改 trim/string 解析；localStorage 存 string | [`useWorkbenchContext.spec.ts`](penmate-frontend/src/composables/workbench/__tests__/useWorkbenchContext.spec.ts) |
| [ ] | composable | [`useWorkbenchCards.ts`](penmate-frontend/src/composables/workbench/useWorkbenchCards.ts:29) | `cardId` / `relationId` / `fromCardId` / `toCardId` | `pickCardId()` / `pickRelationId()` / `toWorkbenchCard()` / `toCardRelation()` 使用 `Number(...)` | 纯字符串规范化函数；删除数值真假判断 | [`useWorkbenchCards.spec.ts`](penmate-frontend/src/composables/workbench/__tests__/useWorkbenchCards.spec.ts) |
| [ ] | composable | [`useWorkbenchOutline.ts`](penmate-frontend/src/composables/workbench/useWorkbenchOutline.ts:51) | `nodeId` / `chapterId` / `outlineNodeId` / `projectId` / `operatorId` | `Number(volume.key)` / `Number(nodeKey)` / `Number(chapter.chapterId)` | key 与业务 ID 全 string | [`useWorkbenchOutline.spec.ts`](penmate-frontend/src/composables/workbench/__tests__/useWorkbenchOutline.spec.ts) |
| [ ] | composable | [`useWorkbenchChat.ts`](penmate-frontend/src/composables/workbench/useWorkbenchChat.ts:47) | `projectId` / `operatorId` / `sessionId` / `taskId` / `approvalId` / `chapterId` / `modelConfigId` | 会话语义仍可能含 `conversationId` | 全链路 string-only；只保留 `sessionId` | [`useWorkbenchChat.spec.ts`](penmate-frontend/src/composables/workbench/__tests__/useWorkbenchChat.spec.ts), [`index.chat-binding.spec.ts`](penmate-frontend/src/views/Workbench/index.chat-binding.spec.ts) |
| [ ] | composable | [`useWorkbenchApprovals.ts`](penmate-frontend/src/composables/workbench/useWorkbenchApprovals.ts:17) | `projectId` / `operatorId` / `approvalId` | 上下文可能为 number | 改为 string-only | [`useWorkbenchApprovals.spec.ts`](penmate-frontend/src/composables/workbench/__tests__/useWorkbenchApprovals.spec.ts) |
| [ ] | composable | [`useWorkbenchSessionRecovery.ts`](penmate-frontend/src/composables/workbench/useWorkbenchSessionRecovery.ts:30) | `projectId` / `sessionId` / `operatorId` / `chapterId` / `styleId` / `taskId` / `modelConfigId` | 依赖上游仍可能喂入 number | 强制 recovery 相关 ID 只接受 string | [`useWorkbenchSessionRecovery.spec.ts`](penmate-frontend/src/composables/workbench/__tests__/useWorkbenchSessionRecovery.spec.ts) |
| [ ] | composable | [`useWorkbenchVersions.ts`](penmate-frontend/src/composables/workbench/useWorkbenchVersions.ts:61) | `projectId` / `chapterId` | tests 仍传 number | 改 string context；`versionNo` 不纳入 business ID | [`useWorkbenchVersions.spec.ts`](penmate-frontend/src/composables/workbench/__tests__/useWorkbenchVersions.spec.ts) |
| [ ] | composable | [`useWorkbenchDraft.ts`](penmate-frontend/src/composables/workbench/useWorkbenchDraft.ts:27) | `projectId` / `chapterId` | `projectId:number` / `chapterId:string|number` | local draft key 只接受 string business ID | [`useWorkbenchDraft.spec.ts`](penmate-frontend/src/composables/workbench/__tests__/useWorkbenchDraft.spec.ts) |
| [ ] | view | [`Workbench/index.vue`](penmate-frontend/src/views
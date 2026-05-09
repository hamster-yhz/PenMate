# 后端业务 ID 全量 string 化分卷 C：前端逐模块改造表

> 关联主计划：[`2026-05-08-business-id-string-full-refactor-plan.md`](docs/plans/2026-05-08-business-id-string-full-refactor-plan.md)
>
> 关联分卷：[`2026-05-08-business-id-string-full-refactor-part-a-id-inventory.md`](docs/plans/2026-05-08-business-id-string-full-refactor-part-a-id-inventory.md)
>
> 后端对应分卷：[`2026-05-08-business-id-string-full-refactor-part-b-backend.md`](docs/plans/2026-05-08-business-id-string-full-refactor-part-b-backend.md)

## 说明

本分卷覆盖前端所有与业务 ID 相关的 API modules、types、stores、composables、views、tests。
目标是把 [`penmate-frontend/src`](penmate-frontend/src) 内所有业务 ID 统一收敛为 `string`，并删除全部历史兼容代码：

- 禁止 [`IdLike`](penmate-frontend/src/api/types.ts:31) 继续用于业务 ID
- 禁止 `number | string` 混合 state / props / payload
- 禁止 `Number(...)`、`parseInt(...)`、`> 0` 这类数值化业务 ID 判断
- 禁止 `ownerId -> ownerUserId`、`roleId || id`、`conversationId ?? sessionId` 之类 fallback / alias
- 禁止响应类型继续接受 `id` 与 `xxxId` 双字段

## 当前问题总览

| 可勾选 | 问题 | 当前位置 | 目标状态 | 验证文件 |
|---|---|---|---|---|
| [ ] | `IdLike = number | string` 扩散到几乎全部 API | [`types.ts`](penmate-frontend/src/api/types.ts:31), [`novel.api.ts`](penmate-frontend/src/api/modules/novel.api.ts:29), [`model.api.ts`](penmate-frontend/src/api/modules/model.api.ts:73), [`rbac.api.ts`](penmate-frontend/src/api/modules/rbac.api.ts:10) | 业务 ID 统一 `string` | API module specs + TS 编译 |
| [ ] | 工作台上下文仍以 number 解析 query/session/localStorage | [`useWorkbenchContext.ts`](penmate-frontend/src/composables/workbench/useWorkbenchContext.ts:23) | `projectId` / `operatorId` 全 string | [`useWorkbenchContext.spec.ts`](penmate-frontend/src/composables/workbench/__tests__/useWorkbenchContext.spec.ts) |
| [ ] | 资料卡/关系大量 `Number(...)` 强制转型 | [`useWorkbenchCards.ts`](penmate-frontend/src/composables/workbench/useWorkbenchCards.ts:29) | `cardId` / `relationId` / `fromCardId` / `toCardId` 全 string | [`useWorkbenchCards.spec.ts`](penmate-frontend/src/composables/workbench/__tests__/useWorkbenchCards.spec.ts) |
| [ ] | 大纲/章节节点大量 `Number(nodeKey)` | [`useWorkbenchOutline.ts`](penmate-frontend/src/composables/workbench/useWorkbenchOutline.ts:51) | `nodeId` / `outlineNodeId` / `chapterId` / `projectId` 全 string | [`useWorkbenchOutline.spec.ts`](penmate-frontend/src/composables/workbench/__tests__/useWorkbenchOutline.spec.ts) |
| [ ] | 工作台类型仍有 number 业务 ID | [`workbenchTypes.ts`](penmate-frontend/src/components/workbench/workbenchTypes.ts:4) | 业务 ID 类型全 string | 组件 + composable tests |
| [ ] | Agent 模块仍保留 `conversationId` | [`agent.api.ts`](penmate-frontend/src/api/modules/agent.api.ts:8) | 只保留 `sessionId` | [`agent.api.spec.ts`](penmate-frontend/src/api/modules/agent.api.spec.ts), [`useWorkbenchChat.spec.ts`](penmate-frontend/src/composables/workbench/__tests__/useWorkbenchChat.spec.ts) |
| [ ] | Novel API 仍保留 `ownerId -> ownerUserId` alias | [`novel.api.ts`](penmate-frontend/src/api/modules/novel.api.ts:6) | 只接受 `ownerUserId:string` | [`novel.api.spec.ts`](penmate-frontend/src/api/modules/novel.api.spec.ts) |
| [ ] | RBAC / Model 仍依赖 `IdLike` | [`rbac.api.ts`](penmate-frontend/src/api/modules/rbac.api.ts:10), [`model.api.ts`](penmate-frontend/src/api/modules/model.api.ts:73) | 所有业务 ID 参数和返回类型改 string | [`rbac.api.spec.ts`](penmate-frontend/src/api/modules/rbac.api.spec.ts), [`model.api.spec.ts`](penmate-frontend/src/api/modules/model.api.spec.ts) |

## 改造总表

| 可勾选 | 模块类型 | 文件 | 涉及业务 ID | 当前问题 | 目标 string-only 状态 | 测试文件 |
|---|---|---|---|---|---|---|
| [ ] | type | [`types.ts`](penmate-frontend/src/api/types.ts:31) | 全部 | `IdLike = number | string` | 删除 `IdLike`，按域定义 `string` 业务 ID 类型 | API module specs |
| [ ] | api | [`agent.api.ts`](penmate-frontend/src/api/modules/agent.api.ts:6) | `projectId` / `sessionId` / `taskId` | 保留 `conversationId?` | 仅保留 `sessionId` / `taskId` string | [`agent.api.spec.ts`](penmate-frontend/src/api/modules/agent.api.spec.ts) |
| [ ] | api | [`approval.api.ts`](penmate-frontend/src/api/modules/approval.api.ts:7) | `projectId` / `approvalId` | `IdLike` 混合 | 所有参数改 `string` | 新增或补齐 approval API spec |
| [ ] | api | [`novel.api.ts`](penmate-frontend/src/api/modules/novel.api.ts:6) | `projectId` / `ownerUserId` / `operatorId` / `volumeId` / `chapterId` / `nodeId` / `cardId` / `relationId` | 全量 `IdLike` + `ownerId` fallback | 所有业务 ID 参数改 `string`；移除 `ownerId` alias | [`novel.api.spec.ts`](penmate-frontend/src/api/modules/novel.api.spec.ts) |
| [ ] | api | [`chapter.api.ts`](penmate-frontend/src/api/modules/chapter.api.ts:7) | `projectId` / `chapterId` / `operatorId` | `IdLike` 混合；`versionNo` 误用 `IdLike` | 业务 ID 改 string；`versionNo` 单独保留 number/string text 语义 | 章节 API tests |
| [ ] | api | [`outline.api.ts`](penmate-frontend/src/api/modules/outline.api.ts:7) | `projectId` / `operatorId` / `nodeId` | `IdLike` 混合 | 全 string | 大纲 API tests |
| [ ] | api | [`card.api.ts`](penmate-frontend/src/api/modules/card.api.ts:7) | `projectId` / `operatorId` / `cardId` / `relationId` | `IdLike` 混合 | 全 string | 卡片 API tests |
| [ ] | api | [`style.api.ts`](penmate-frontend/src/api/modules/style.api.ts:7) | `projectId` / `operatorId` / `styleId` / `sessionId` | `IdLike` 混合 | 全 string | style API tests |
| [ ] | api | [`rag.api.ts`](penmate-frontend/src/api/modules/rag.api.ts:7) | `projectId` / `documentId` | `IdLike` 混合 | 全 string | rag API tests |
| [ ] | api | [`plugin.api.ts`](penmate-frontend/src/api/modules/plugin.api.ts:13) | `projectId` / `operatorId` | `IdLike` 混合 | 全 string | plugin API tests |
| [ ] | api | [`ops.api.ts`](penmate-frontend/src/api/modules/ops.api.ts:7) | `migrationId` / `jobId` / `operatorId` | `IdLike` 混合 | 全 string | ops API tests |
| [ ] | api | [`rbac.api.ts`](penmate-frontend/src/api/modules/rbac.api.ts:10) | `userId` / `roleId` / `permissionId` / `menuId` | `IdLike` 混合 | 全 string；不再兼容 `id` 字段 | [`rbac.api.spec.ts`](penmate-frontend/src/api/modules/rbac.api.spec.ts) |
| [ ] | api | [`model.api.ts`](penmate-frontend/src/api/modules/model.api.ts:6) | `userId` / `operatorId` / `providerId` / `keyId` / `modelConfigId` / `mainAgentModelConfigId` / `dirtyWorkAgentModelConfigId` | `IdLike` 混合；局部 normalize 但未全收口 | 所有业务 ID 参数、响应类型、payload 全 string | [`model.api.spec.ts`](penmate-frontend/src/api/modules/model.api.spec.ts) |
| [ ] | api | [`profile.api.ts`](penmate-frontend/src/api/modules/profile.api.ts:7) | `userId` / `menuId` | `IdLike` 混合 | 全 string | profile API tests |
| [ ] | store | [`workbenchSession.ts`](penmate-frontend/src/stores/workbenchSession.ts:3) | `sessionId` / `styleId` / `taskId` / `chapterId` / `modelConfigId` | 已部分 string 化 | 维持并扩展为唯一事实源；禁止 number 回写 | [`useWorkbenchSessionRecovery.spec.ts`](penmate-frontend/src/composables/workbench/__tests__/useWorkbenchSessionRecovery.spec.ts) |
| [ ] | type | [`workbenchTypes.ts`](penmate-frontend/src/components/workbench/workbenchTypes.ts:4) | `cardId` / `cardRelationId` / `fromCardId` / `toCardId` / `conversationId` / `ChatMessage.id` | 仍为 `number` 或 `number | string` | 全部 string；`conversationId` 重命名/收敛到 `sessionId` | workbench component/composable specs |
| [ ] | composable | [`useWorkbenchContext.ts`](penmate-frontend/src/composables/workbench/useWorkbenchContext.ts:23) | `projectId` / `operatorId` / `userId` | 用 `Number()` 解析 query/session/storage | 全部通过 trim/string 解析；localStorage 也存 string | [`useWorkbenchContext.spec.ts`](penmate-frontend/src/composables/workbench/__tests__/useWorkbenchContext.spec.ts) |
| [ ] | composable | [`useWorkbenchCards.ts`](penmate-frontend/src/composables/workbench/useWorkbenchCards.ts:29) | `projectId` / `operatorId` / `cardId` / `relationId` / `fromCardId` / `toCardId` | `pickCardId()` / `pickRelationId()` / `Number(...)` 链式扩散 | 所有卡片与关系 ID 保持 string，不做数值比较 | [`useWorkbenchCards.spec.ts`](penmate-frontend/src/composables/workbench/__tests__/useWorkbenchCards.spec.ts) |
| [ ] | composable | [`useWorkbenchOutline.ts`](penmate-frontend/src/composables/workbench/useWorkbenchOutline.ts:51) | `projectId` / `operatorId` / `nodeId` / `chapterId` / `outlineNodeId` | `pickOutlineNodeId()`、`Number(volume.key)`、`Number(nodeKey)`、`Number(chapter.chapterId)` | 大纲节点和章节 ID 全 string；空值用 `''/null` 语义表达 | [`useWorkbenchOutline.spec.ts`](penmate-frontend/src/composables/workbench/__tests__/useWorkbenchOutline.spec.ts) |
| [ ] | composable | [`useWorkbenchChat.ts`](penmate-frontend/src/composables/workbench/useWorkbenchChat.ts:47) | `projectId` / `operatorId` / `sessionId` / `taskId` / `approvalId` / `chapterId` / `modelConfigId` | Agent 会话与任务链路仍需清理 `conversationId` 语义 | 全链路 string-only；只用 `sessionId` | [`useWorkbenchChat.spec.ts`](penmate-frontend/src/composables/workbench/__tests__/useWorkbenchChat.spec.ts), [`index.chat-binding.spec.ts`](penmate-frontend/src/views/Workbench/index.chat-binding.spec.ts) |
| [ ] | composable | [`useWorkbenchApprovals.ts`](penmate-frontend/src/composables/workbench/useWorkbenchApprovals.ts:17) | `projectId` / `operatorId` / `approvalId` | 上下文常由 number 提供 | 审批链路全 string | [`useWorkbenchApprovals.spec.ts`](penmate-frontend/src/composables/workbench/__tests__/useWorkbenchApprovals.spec.ts) |
| [ ] | composable | [`useWorkbenchSessionRecovery.ts`](penmate-frontend/src/composables/workbench/useWorkbenchSessionRecovery.ts:30) | `projectId` / `sessionId` / `operatorId` / `chapterId` / `styleId` / `taskId` / `modelConfigId` | 当前已偏 string，但依赖上游上下文仍可能喂入 number | 维持严格 string-only 输入输出 | [`useWorkbenchSessionRecovery.spec.ts`](penmate-frontend/src/composables/workbench/__tests__/useWorkbenchSessionRecovery.spec.ts) |
| [ ] | composable | [`useWorkbenchVersions.ts`](penmate-frontend/src/composables/workbench/useWorkbenchVersions.ts:61) | `projectId` / `chapterId` | tests 仍传 number `projectId` | 改为 `projectId:string` / `chapterId:string`；`versionNo` 不纳入业务 ID string 化 | [`useWorkbenchVersions.spec.ts`](penmate-frontend/src/composables/workbench/__tests__/useWorkbenchVersions.spec.ts) |
| [ ] | composable | [`useWorkbenchDraft.ts`](penmate-frontend/src/composables/workbench/useWorkbenchDraft.ts:27) | `projectId` / `chapterId` | `readDraft(projectId:number, chapterId:string|number)` | local draft key 只接受 string business ID | [`useWorkbenchDraft.spec.ts`](penmate-frontend/src/composables/workbench/__tests__/useWorkbenchDraft.spec.ts) |
| [ ] | view | [`Workbench/index.vue`](penmate-frontend/src/views/Workbench/index.vue:194) | `projectId` / `operatorId` / `sessionId` / `chapterId` | `getCurrentProjectId()` / `resolveOperatorId()` / `getAgentProjectId()` 仍围绕 number 兼容构造 | 全部改为 string 源；删除 `String(projectId)` 补救式转换 | [`index.chat-binding.spec.ts`](penmate-frontend/src/views/Workbench/index.chat-binding.spec.ts), [`index.refactor.spec.ts`](penmate-frontend/src/views/Workbench/index.refactor.spec.ts) |
| [ ] | view | [`AdminRbac/index.vue`](penmate-frontend/src/views/AdminRbac/index.vue:155) | `userId` / `roleId` / `permissionId` / `menuId` | 页面层仍兼容 `id` / `roleId` 混合与 number fallback | 所有 getter / key / select value / action param 改 string | [`AdminRbac/index.spec.ts`](penmate-frontend/src/views/AdminRbac/index.spec.ts), [`admin-entry-rbac.spec.ts`](penmate-frontend/src/views/admin-entry-rbac.spec.ts) |

## API Modules 逐文件改造清单

### 1. Agent API

| 可勾选 | 文件 | 当前问题 | 改造动作 | 目标状态 | 验证文件 |
|---|---|---|---|---|---|
| [ ] | [`agent.api.ts`](penmate-frontend/src/api/modules/agent.api.ts:8) | `AgentSessionRecord` 同时允许 `sessionId?` 与 `conversationId?` | 删除 `conversationId` 字段；把会话响应类型固定为 `sessionId:string` | Agent API 和 workbench chat 只认 `sessionId` | [`agent.api.spec.ts`](penmate-frontend/src/api/modules/agent.api.spec.ts), [`useWorkbenchChat.spec.ts`](penmate-frontend/src/composables/workbench/__tests__/useWorkbenchChat.spec.ts) |
| [ ] | [`agent.api.ts`](penmate-frontend/src/api/modules/agent.api.ts:13) | `AgentTaskRecord` 仍是宽松 `AnyRecord` | 固定 `taskId:string` / `taskStatus:string` / 相关业务 ID string | 任务流保持 string 精度 | [`agent.api.spec.ts`](penmate-frontend/src/api/modules/agent.api.spec.ts) |

### 2. Novel / Chapter / Outline / Card API

| 可勾选 | 文件 | 当前问题 | 改造动作 | 目标状态 | 验证文件 |
|---|---|---|---|---|---|
| [ ] | [`novel.api.ts`](penmate-frontend/src/api/modules/novel.api.ts:29) | 全面使用 `IdLike` | 所有业务 ID 参数改 `string` | project / volume / chapter / member / outline / card / relation 全 string | [`novel.api.spec.ts`](penmate-frontend/src/api/modules/novel.api.spec.ts) |
| [ ] | [`novel.api.ts`](penmate-frontend/src/api/modules/novel.api.ts:6) | `ownerId -> ownerUserId` alias 兼容 | 删除 alias；不再读写 `ownerId` | 只接受 `ownerUserId:string` | [`novel.api.spec.ts`](penmate-frontend/src/api/modules/novel.api.spec.ts) |
| [ ] | [`chapter.api.ts`](penmate-frontend/src/api/modules/chapter.api.ts:7) | `projectId/chapterId/operatorId` 用 `IdLike` | 业务 ID 改 `string`；版本号单独保留 | 章节 API string-only | 章节 API tests |
| [ ] | [`outline.api.ts`](penmate-frontend/src/api/modules/outline.api.ts:7) | `nodeId` 仍用 `IdLike` | 节点 ID 改 `string` | outline API string-only | 大纲 API tests |
| [ ] | [`card.api.ts`](penmate-frontend/src/api/modules/card.api.ts:7) | `cardId/relationId` 仍用 `IdLike` | 卡片和关系 ID 改 `string` | card API string-only | 卡片 API tests |

### 3. Style / Rag / Plugin / Ops / RBAC / Model / Profile API

| 可勾选 | 文件 | 当前问题 | 改造动作 | 目标状态 | 验证文件 |
|---|---|---|---|---|---|
| [ ] | [`style.api.ts`](penmate-frontend/src/api/modules/style.api.ts:7) | `projectId/styleId/operatorId/sessionId` 用 `IdLike` | 全部改 `string` | 风格链路 string-only | style API tests |
| [ ] | [`rag.api.ts`](penmate-frontend/src/api/modules/rag.api.ts:7) | `projectId/docId` 用 `IdLike` | 改 `string`，命名统一 `documentId` | RAG string-only | rag API tests |
| [ ] | [`plugin.api.ts`](penmate-frontend/src/api/modules/plugin.api.ts:13) | `projectId/operatorId` 用 `IdLike` | 改 `string` | 插件链路 string-only | plugin API tests |
| [ ] | [`ops.api.ts`](penmate-frontend/src/api/modules/ops.api.ts:7) | `migrationId/jobId` 用 `IdLike` | 改 `string` | Ops string-only | ops API tests |
| [ ] | [`rbac.api.ts`](penmate-frontend/src/api/modules/rbac.api.ts:10) | 全面 `IdLike` | user / role / permission / menu 全 string | RBAC string-only | [`rbac.api.spec.ts`](penmate-frontend/src/api/modules/rbac.api.spec.ts) |
| [ ] | [`model.api.ts`](penmate-frontend/src/api/modules/model.api.ts:73) | 参数仍用 `IdLike`，局部 normalize 但未彻底收口 | 参数、payload、response business IDs 全 string；不再删除/依赖 `id` fallback | model string-only | [`model.api.spec.ts`](penmate-frontend/src/api/modules/model.api.spec.ts) |
| [ ] | [`profile.api.ts`](penmate-frontend/src/api/modules/profile.api.ts:7) | `userId` 用 `IdLike` | 改 `string` | profile 菜单链路 string-only | profile API tests |

## Types / Stores 改造清单

| 可勾选 | 文件 | 当前问题 | 改造动作 | 目标状态 | 验证文件 |
|---|---|---|---|---|---|
| [ ] | [`types.ts`](penmate-frontend/src/api/types.ts:31) | `IdLike` 扩散 | 删除或限制为非业务场景；新增域内 string type aliases | 不再作为业务 ID 公共类型 | API module specs |
| [ ] | [`workbenchTypes.ts`](penmate-frontend/src/components/workbench/workbenchTypes.ts:4) | `cardId/cardRelationId/fromCardId/toCardId` 为 number；`ChatMessage.id` 为 `number | string`；`conversationId` 命名过时 | 全部业务 ID 改 string；`conversationId` 收敛为 `sessionId` 语义 | 工作台类型不再有 number 业务 ID | workbench specs |
| [ ] | [`workbenchSession.ts`](penmate-frontend/src/stores/workbenchSession.ts:3) | 已大部分 string，但需强制上游只写 string | 保持 string-only，并在恢复/聊天链路中禁止 number 回写 | store 成为唯一 string 事实源 | [`useWorkbenchSessionRecovery.spec.ts`](penmate-frontend/src/composables/workbench/__tests__/useWorkbenchSessionRecovery.spec.ts) |

## Composables 改造清单

| 可勾选 | 文件 | 当前问题 | 改造动作 | 目标状态 | 验证文件 |
|---|---|---|---|---|---|
| [ ] | [`useWorkbenchContext.ts`](penmate-frontend/src/composables/workbench/useWorkbenchContext.ts:23) | `Number()` 解析 query / session / storage；`session.userId:number` | 改为 string trim 解析；session/store 也改 string | project/operator 上下文全 string | [`useWorkbenchContext.spec.ts`](penmate-frontend/src/composables/workbench/__tests__/useWorkbenchContext.spec.ts) |
| [ ] | [`useWorkbenchCards.ts`](penmate-frontend/src/composables/workbench/useWorkbenchCards.ts:29) | `pickCardId()` / `pickRelationId()` / `toWorkbenchCard()` / `toCardRelation()` 使用 `Number(...)` | 改为纯字符串规范化函数，删除数值真假判断 | 卡片与关系链路全 string | [`useWorkbenchCards.spec.ts`](penmate-frontend/src/composables/workbench/__tests__/useWorkbenchCards.spec.ts) |
| [ ] | [`useWorkbenchOutline.ts`](penmate-frontend/src/composables/workbench/useWorkbenchOutline.ts:51) | `pickOutlineNodeId()` / `Number(volume.key)` / `Number(nodeKey)` / `Number(chapter.chapterId)` | 改为 string key / string chapterId / string outlineNodeId | 大纲树与章节链路全 string | [`useWorkbenchOutline.spec.ts`](penmate-frontend/src/composables/workbench/__tests__/useWorkbenchOutline.spec.ts) |
| [ ] | [`useWorkbenchChat.ts`](penmate-frontend/src/composables/workbench/useWorkbenchChat.ts:47) | 依赖上下文来源可能为 number；会话语义仍可能包含 conversationId | 全部入参、恢复、流式任务、审批卡片关联字段改 string；只保留 sessionId | agent/workbench string-only | [`useWorkbenchChat.spec.ts`](penmate-frontend/src/composables/workbench/__tests__/useWorkbenchChat.spec.ts), [`index.chat-binding.spec.ts`](penmate-frontend/src/views/Workbench/index.chat-binding.spec.ts) |
| [ ] | [`useWorkbenchApprovals.ts`](penmate-frontend/src/composables/workbench/useWorkbenchApprovals.ts:17) | 上下文与 approvalId 可能从 number 进入 | 改为 string-only | 审批链路全 string | [`useWorkbenchApprovals.spec.ts`](penmate-frontend/src/composables/workbench/__tests__/useWorkbenchApprovals.spec.ts) |
| [ ] | [`useWorkbenchSessionRecovery.ts`](penmate-frontend/src/composables/workbench/useWorkbenchSessionRecovery.ts:30) | 已偏 string，但测试与调用者需完全去 number | 强制所有 recovery 相关业务 ID 为 string | 恢复链路无数值化 | [`useWorkbenchSessionRecovery.spec.ts`](penmate-frontend/src/composables/workbench/__tests__/useWorkbenchSessionRecovery.spec.ts) |
| [ ] | [`useWorkbenchVersions.ts`](penmate-frontend/src/composables/workbench/useWorkbenchVersions.ts:61) | tests 仍传 number `projectId` | 统一改 string context，保留 `versionNo` 非业务 ID 语义 | 版本链路 string-only | [`useWorkbenchVersions.spec.ts`](penmate-frontend/src/composables/workbench/__tests__/useWorkbenchVersions.spec.ts) |
| [ ] | [`useWorkbenchDraft.ts`](penmate-frontend/src/composables/workbench/useWorkbenchDraft.ts:27) | `projectId:number` / `
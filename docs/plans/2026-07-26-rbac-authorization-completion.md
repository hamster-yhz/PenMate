# PenMate RBAC 与授权体系完善实施设计

> 日期：2026-07-26
> 状态：决策已确认，待实施
> 范围：后端 IAM/RBAC、统一授权策略层、官方模型授权、会话失效、Flyway 基线重排、RBAC 管理前端
> 前置：本文件是本次改造的唯一实施基准，取代此前口头结论

## 1. 背景与问题

当前 RBAC 只完成了“骨架”，没有形成闭环。审计到的事实如下：

- 种子数据只创建了 `ROLE_ADMIN` 一个角色，没有普通用户角色，见 `penmate-backend/src/main/resources/db/migration/V7__seed_system_data.sql:3`。
- 权限目录只有 14 条，集中在 RBAC、系统模型和运维，作品、章节、Agent、Story Bible、RAG、插件、个人模型完全没有纳入权限模型。
- 普通业务接口只要求“已登录”，见 `penmate-backend/src/main/java/com/penmate/backend/interfaces/config/SecurityConfig.java:72` 的 `anyRequest().authenticated()`，因此“无角色用户”并不是什么都不能做，而是几乎可以使用全部创作功能。
- 官方模型（`scope_type = 'SYSTEM'`）对所有登录用户可见可用：`ModelMapper.listAccessibleConfigurations` 与 `findAccessibleConfiguration` 都直接放行 `SYSTEM` 作用域，平台成本不可控。

另外发现三个必须修复的缺陷：

1. **业务 ID 混用**：登录时用数据库物理主键 `IamUser.id` 查询角色和权限（`AuthApplicationService.login`），而 `iam_user_roles.user_id` 存的是业务 `user_id`。除了 id 与 user_id 恰好相等的 bootstrap 管理员，普通用户即使分配了角色也加载不到。
2. **角色变更不生效**：`IamRbacAssignmentApplicationService` 只改数据库，不撤销 Redis 会话；旧 access token 与 refresh token 继续携带旧权限载荷（权限快照写在 `AuthUserSessionPayload`，由 `BearerAuthenticationFilter` 直接转成 authorities）。
3. **创建用户无有效密码**：`IamQueryApplicationService.createUser` 写入 `{noop}changeme`，但系统使用 `BCryptPasswordEncoder`，管理台创建的账号根本无法登录。

补充发现（同批修复）：

4. **运维接口鉴权路径错配**：`SecurityConfig` 保护的是 `/api/v1/ops/**`，而 `OpsController` 实际映射在 `/api/v1/jobs` 与 `/api/v1/migrations`，`ops:job:read` / `ops:job:write` 从未生效。
5. **认证方式是假的**：`auth_method` 无数据库约束，前端提供 OAuth / SSO 选项，但没有任何 OAuth2/OIDC/SAML 实现，登录链路只有本地邮箱密码。

## 2. 已确认的设计决策

以下决策已在 `$grilling` 阶段逐条确认，实施时不得擅自更改。

| 编号 | 决策 |
| --- | --- |
| D1 | 普通用户默认**不能**使用官方模型额度，需要管理员显式授予 |
| D2 | 鉴权分四层：接口层、业务执行层、资源归属层、前端体验层；前端不是安全边界 |
| D3 | 不引入远程鉴权网关，在单体内建统一授权策略层（Policy + Port/Adapter） |
| D4 | 权限粒度为“业务能力”级，不做 CRUD 碎片权限 |
| D5 | 无权使用官方模型时**明确拒绝**，不静默回退到其他模型 |
| D6 | 角色/权限变更后立即撤销受影响会话，重新登录后加载新授权 |
| D7 | 当前阶段只支持本地账户，`auth_method` 收敛为 `LOCAL`，删除 OAuth/SSO 假入口 |
| D8 | 管理员创建账号时直接设置初始密码，不做一次性临时密码，不强制首次修改 |
| D9 | 授权变更数据库强一致提交；Redis 会话清理通过 `ops_async_jobs` 异步兜底 |
| D10 | 复用现有 `ops_async_jobs` 作为 transactional outbox，不引入 MQ 依赖 |
| D11 | `ROLE_ADMIN`、`ROLE_USER`、`ROLE_OFFICIAL_MODEL_USER` 为不可改、不可删的系统角色 |
| D12 | 无角色账号“受限登录”：只能管理本人账号，不能进入业务区 |
| D13 | 新建账号自动绑定 `ROLE_USER`；官方模型权限必须额外授予 |
| D14 | Agent Run 使用版本化授权快照，每次模型调用只比对 `authorization_version` |
| D15 | `authorization_version` 以 PostgreSQL 为权威；Redis 只做会话与快照缓存，故障时失败关闭 |
| D16 | 纯允许模型（pure-allow），多角色取并集，不引入 DENY |
| D17 | 官方模型对无权用户**仍然返回**，但前端显式不可选，直接提交模型 ID 返回 403 |
| D18 | 保护应急管理员、禁止自我降权、保证至少一个启用管理员 |
| D19 | Flyway 保持按领域拆分，把 V8-V15 补丁折回所属领域基线，本地库删除重建 |
| D20 | RBAC 前端不展示内部权限码，改为中文能力名称、说明与生效结果 |

## 3. 角色模型

### 3.1 系统角色

| 角色码 | 中文名 | 说明 | 是否系统角色 |
| --- | --- | --- | --- |
| `ROLE_USER` | 普通用户 | 全部基础创作能力，使用自有 API Key 模型 | 是 |
| `ROLE_OFFICIAL_MODEL_USER` | 官方模型用户 | 仅追加官方模型使用权限 | 是 |
| `ROLE_ADMIN` | 系统管理员 | 全部权限，含管理与运维 | 是 |

系统角色由 Flyway 种子维护，`is_system = TRUE`。后端拒绝对系统角色执行改名、改权限、删除操作；前端相应入口禁用并说明原因。

自定义角色由管理员创建，可任意组合非管理类权限；需要“比普通用户更少的权限”时，移除 `ROLE_USER` 后分配自定义角色，而不是引入 DENY。

### 3.2 默认分配

- 管理员新建账号自动绑定 `ROLE_USER`，创建接口不允许省略基础角色。
- `ROLE_ADMIN` 自身已含全部普通业务权限，不重复绑定 `ROLE_USER`。
- `ROLE_OFFICIAL_MODEL_USER` 必须显式授予，永不自动绑定。
- 项目未上线，不做历史账号兼容迁移，默认分配逻辑直接体现在新基线与创建流程里。

### 3.3 无角色账号（受限登录）

允许：登录、登出、查看本人信息、查看与撤销自己的会话、修改邮箱、修改密码、注销账号。

禁止：作品、工作台、Story Bible、Agent、RAG、插件、模型配置等全部业务能力。

前端登录后直接进入“账户暂无使用权限”页，不渲染业务导航。账号状态为停用时连登录都不允许。

## 4. 权限目录

权限码为内部标识，前端只展示中文名称与说明（D20）。`module` 字段沿用现有分组能力，用于 RBAC 页面的业务域矩阵。

### 4.1 基础与账户

| 权限码 | 中文名 | 说明 |
| --- | --- | --- |
| `app:access` | 进入应用 | 访问业务工作区的前置权限 |
| `profile:read` | 查看作者资料 | 查看个人资料与偏好 |
| `profile:write` | 编辑作者资料 | 修改个人资料与编辑器偏好 |

账号自身的凭据管理（改邮箱、改密码、会话管理、注销）不纳入 RBAC，属于“本人账户操作”，只要登录即可，无角色也保留。

### 4.2 创作业务

| 权限码 | 中文名 | 说明 |
| --- | --- | --- |
| `novel:read` | 查看作品 | 查看作品、卷、章节、目录、回收站 |
| `novel:write` | 编辑作品 | 创建与修改作品、卷、章节、正文、封面 |
| `novel:delete` | 删除作品 | 删除作品与章节、清空回收站 |
| `novel:import` | 导入作品 | 导入外部书稿并落库 |
| `novel:export` | 导出作品 | 导出 DOCX/EPUB/TXT 等格式 |
| `storybible:read` | 查看故事圣经 | 查看节点、关系、进程、变更集 |
| `storybible:write` | 编辑故事圣经 | 维护节点类型、节点、分类、标签、关系 |
| `agent:use` | 使用 AI 创作 | 发起 Agent 会话与轮次、消费流式结果 |
| `agent:session:manage` | 管理 AI 会话 | 重命名、删除、恢复会话，管理 Todo 与审批 |
| `rag:read` | 查看知识库 | 查看 RAG 配置、文档与检索日志 |
| `rag:write` | 管理知识库 | 上传文档、解析、向量化、重建索引 |
| `plugin:read` | 查看插件 | 浏览插件目录与已安装插件 |
| `plugin:write` | 管理插件 | 安装、配置、卸载项目插件 |

### 4.3 模型

| 权限码 | 中文名 | 说明 |
| --- | --- | --- |
| `model:user:read` | 查看个人模型 | 查看自己的模型配置与偏好 |
| `model:user:write` | 管理个人模型 | 新增、修改、删除自己的 API Key 与模型配置 |
| `model:official:use` | 使用官方模型 | 消费平台提供的官方 Chat/Embedding 模型 |
| `model:system:write` | 管理官方模型 | 创建、测试、修改、删除系统模型配置（已存在） |

### 4.4 管理与运维

| 权限码 | 中文名 | 说明 |
| --- | --- | --- |
| `rbac:admin:access` | 进入权限管理 | 访问管理后台 |
| `rbac:user:read` | 查看用户 | 查看用户目录与有效访问结果 |
| `rbac:user:write` | 管理用户 | 创建与编辑用户 |
| `rbac:user:delete` | 删除用户 | 删除或恢复用户 |
| `rbac:user:bind-role` | 分配用户角色 | 替换用户角色集合 |
| `rbac:role:read` | 查看角色 | 查看角色定义 |
| `rbac:role:write` | 管理角色 | 创建与编辑角色 |
| `rbac:role:delete` | 删除角色 | 删除自定义角色 |
| `rbac:role:bind-permission` | 分配角色权限 | 替换角色权限集合 |
| `rbac:permission:read` | 查看权限 | 查看权限目录 |
| `rbac:menu:read` | 查看菜单 | 查看菜单树 |
| `ops:job:read` | 查看运维任务 | 查看异步任务与迁移任务 |
| `ops:job:write` | 管理运维任务 | 重试、取消任务，执行数据迁移 |

### 4.5 角色权限矩阵

| 权限组 | ROLE_USER | ROLE_OFFICIAL_MODEL_USER | ROLE_ADMIN |
| --- | --- | --- | --- |
| 基础与账户（`app:access`、`profile:*`） | 是 | 否 | 是 |
| 创作业务（`novel:*`、`storybible:*`、`agent:*`、`rag:*`、`plugin:*`） | 是 | 否 | 是 |
| 个人模型（`model:user:*`） | 是 | 否 | 是 |
| 官方模型使用（`model:official:use`） | 否 | 是 | 是 |
| 官方模型管理（`model:system:write`） | 否 | 否 | 是 |
| RBAC 管理（`rbac:*`） | 否 | 否 | 是 |
| 运维（`ops:*`） | 否 | 否 | 是 |

`ROLE_OFFICIAL_MODEL_USER` 是纯附加角色，单独持有它没有意义，必须与 `ROLE_USER` 并存。

## 5. 授权架构

### 5.1 分层

```text
HTTP 请求
  |
  v
BearerAuthenticationFilter        会话有效性 + authorization_version 校验
  |
  v
SecurityConfig / @PreAuthorize    接口层：粗粒度业务权限
  |
  v
应用服务                          资源归属校验（作品、会话、文档、模型配置）
  |
  +--> CapabilityAuthorizationService   require(userId, permissionCode)
  |
  +--> ModelAccessPolicy                USER 模型 -> 归属校验
                                        SYSTEM 模型 -> require(model:official:use)
```

分工约束：

- `SecurityConfig` 与 `@PreAuthorize` 只处理 HTTP 入口。
- `CapabilityAuthorizationService` 是唯一的权限判定入口，同步接口与异步任务共用，不依赖 `SecurityContext`。
- `ModelAccessPolicy` 只负责模型领域规则，被 `AgentModelRoutingService`、`EmbeddingModelRoutingService` 和模型查询接口共同使用。
- 资源归属校验保留在各业务应用服务中，RBAC 不能替代数据归属。
- 前端只负责隐藏或禁用，不承担鉴权。

### 5.2 端口与适配器

- 端口：`AuthorizationQueryPort`（按 `userId` 读取有效权限码集合与当前 `authorization_version`）。
- 适配器：基于现有 `IamUserMapper` 的 JOIN 查询，允许短 TTL 缓存，角色变化时按版本失效。
- 失败关闭：底层存储不可用时抛出鉴权失败，绝不默认放行。

## 6. 官方模型授权

### 6.1 后端

在模型解析的唯一收口处判定，而不是只保护配置接口：

- `AgentModelRoutingService.resolveExecutionConfig`：解析出 `scopeType = SYSTEM` 时要求 `model:official:use`。
- `EmbeddingModelRoutingService.resolve`：同上。
- `ModelApplicationService` 中的连接测试、维度探测、模型发现等涉及官方模型的路径同样校验。

无权时抛出 403 业务异常，错误码语义为“未授权使用官方模型”，并提示改选个人模型。**不做静默回退**（D5）。

### 6.2 列表可见性（D17）

`GET /api/v1/model/configurations` 仍返回官方模型，但每项附带结构化可用性字段：

```json
{
  "modelConfigId": 1,
  "scopeType": "SYSTEM",
  "displayName": "官方 Chat 模型",
  "usable": false,
  "unavailableReason": "OFFICIAL_MODEL_NOT_AUTHORIZED"
}
```

前端据此渲染锁定态：条目可见、不可选、带锁标与简短说明，样式统一，不使用弹窗打断。直接提交该 `modelConfigId` 时后端仍返回 403，前端禁用只是体验层。

### 6.3 Agent Run 授权快照（D14/D15）

1. Run 启动时加载一次完整授权：`userId + permissions + authorizationVersion`，写入 Run 授权上下文。
2. 每次真正调用外部模型前，只按主键读取 `iam_users.authorization_version`（单行、无 JOIN）。
3. 版本一致：直接复用内存快照。
4. 版本变化：重新加载完整权限并重新判定；已失去权限则终止 Run 并记录原因。
5. 同一次业务调用内部传递同一个授权上下文，不重复查询。

撤权时的边界：

- 尚未开始的 Agent/RAG 任务在开始时重新鉴权并拒绝。
- 已经发出、正在等待供应商响应的单次请求允许自然结束，不伪装成已撤回。
- Agent 循环进入下一次模型调用前再次鉴权，因此不会继续产生新的官方模型费用。
- 账号停用或删除时，取消该用户全部可取消的运行中任务，并立即撤销会话。

## 7. 会话失效与授权版本

### 7.1 数据模型

`iam_users` 增加 `authorization_version BIGINT NOT NULL DEFAULT 0`。

- 用户角色变更：该用户版本 +1。
- 角色权限变更：该角色下全部用户版本 +1（单条 UPDATE ... FROM 完成，不逐个循环）。
- 账号停用、删除：版本 +1 并撤销全部会话。

会话载荷 `AuthUserSessionPayload` 增加 `authorizationVersion`，登录与刷新时写入。

### 7.2 校验点

`BearerAuthenticationFilter` 在取到会话后比对版本：不一致直接 401，要求重新登录。Redis 故障时返回 503，失败关闭（D15）。

### 7.3 事务边界（D9/D10）

同一个 PostgreSQL 事务内完成：

1. 角色 / 权限关系替换（沿用现有 `rbac_revision` 乐观并发）。
2. 写入 `iam_rbac_assignment_audits` 审计。
3. 递增受影响用户的 `authorization_version`。
4. 通过 `AsyncJobQueueService.enqueue` 写入 `IAM_REVOKE_AUTH_SESSIONS` 任务（同事务，充当 outbox）。

任一数据库步骤失败则整体回滚，权限不会半更新。

事务提交后：

- 立即尝试同步清理 Redis 会话缓存与 `auth_sessions` 撤销标记（尽力而为）。
- 由 `AsyncJobWorker` 领取 `IAM_REVOKE_AUTH_SESSIONS` 兜底重试；PostgreSQL 的 `FOR UPDATE SKIP LOCKED` 保证多实例安全。
- Redis 清理失败**不回滚**已提交的权限变更；版本校验保证残留旧会话无法继续使用旧权限。

新增 `AsyncJobHandler` 实现，`jobType()` 返回 `IAM_REVOKE_AUTH_SESSIONS`，payload 为受影响的 `userId` 列表或 `roleId`。

## 8. 账号与凭据

### 8.1 只支持本地账户（D7）

- 后端认证方式收敛为枚举 `LOCAL`，数据库对 `iam_users.auth_method` 增加 CHECK 约束。
- `CreateUserDto` 移除 `authMethod` 字段，创建时固定为 `LOCAL`。
- 前端删除 OAuth / SSO 下拉选项与相关文案（`RbacUsersWorkspace.vue`、`useRbacConsole.ts`、`rbacModel.ts`），用户列表不再展示“认证方式”列或固定显示“本地账户”。
- 将来接入 OAuth/OIDC 时再新增外部身份表与完整回调链路，不保留假入口。

### 8.2 初始密码（D8）

- 管理员创建账号时提交“初始密码 + 确认密码”，只提交一次，不回显、不落盘明文。
- 后端做强度校验（沿用 `PasswordChangeDto` 的 8-128 位下限约束，统一到一个密码策略校验器）。
- 使用 `BCryptPasswordEncoder` 存储，彻底移除 `{noop}changeme`。
- 账号创建后可立即登录，不强制首次修改密码。
- 用户后续在个人设置中自行修改密码。

### 8.3 管理员防锁死（D18）

四层保护，最终约束都在数据库事务内：

1. `ROLE_ADMIN` 是系统角色，不可修改、不可删除。
2. Bootstrap 创建的应急管理员（`user_id = 1`）不能被停用、删除，也不能移除其 `ROLE_ADMIN`。
3. 管理员不能移除自己的 `ROLE_ADMIN`，必须由另一名管理员操作。
4. 任何降权、停用、删除操作提交前，在同一事务内校验“系统仍至少保留一个启用状态的管理员”，否则拒绝。

前端对应操作禁用并说明原因，但不依赖前端约束。

## 9. 接口权限映射

原则：每个 Controller 接口至少映射一个权限；组合操作要求全部相关权限；资源归属校验保留。

| 接口前缀 | 方法 | 所需权限 |
| --- | --- | --- |
| `/api/v1/auth/login`、`/refresh` | 全部 | 匿名放行 |
| `/api/v1/auth/**`（me、password、email、sessions、ui-preferences、account） | 全部 | 仅需登录，无角色亦可 |
| `/api/v1/author-profile` | GET / PUT | `profile:read` / `profile:write` |
| `/api/v1/novels`、`/{projectId}`、`/trash`、`/volumes`、`/chapters`、`/directory` | GET | `novel:read` |
| 同上 | POST / PUT / PATCH | `novel:write` |
| `/api/v1/novels/{projectId}`、`/chapters/{id}`、`/volumes/{id}`、`/trash/{id}` | DELETE | `novel:delete` |
| `/api/v1/novels/{projectId}/cover/**` | 全部 | `novel:write` |
| `/api/v1/novels/imports/**` | 全部 | `novel:import` |
| `/api/v1/novels/{projectId}/exports/{format}` | GET | `novel:export` |
| `/api/v1/novels/{projectId}/story-bible/**` | GET / POST(search) | `storybible:read` |
| 同上 | POST / PATCH / DELETE | `storybible:write` |
| `/api/v1/novels/{projectId}/agent/sessions`、`/turns`、`/runs/**` | POST | `agent:use` |
| `/api/v1/novels/{projectId}/agent/sessions/**`（改名、删除、恢复、Todo） | PATCH / DELETE / POST | `agent:session:manage` |
| `/api/v1/novels/{projectId}/approvals/**` | 全部 | `agent:session:manage` |
| `/api/v1/novels/{projectId}/rag/**` | GET | `rag:read` |
| 同上 | POST / PUT / DELETE | `rag:write` |
| `/api/v1/plugins/catalog/**`、`/novels/{projectId}/plugins` | GET | `plugin:read` |
| `/api/v1/novels/{projectId}/plugins/**` | POST / PATCH / DELETE | `plugin:write` |
| `/api/v1/novels/{projectId}/styles/**` | GET | `novel:read` |
| 同上 | POST / PUT / DELETE | `novel:write` |
| `/api/v1/novels/{projectId}/styles/analyze-sample` | POST | `novel:write` + `agent:use` + 模型访问策略 |
| `/api/v1/model/providers`、`/configurations`、`/preferences` | GET | `model:user:read` |
| `/api/v1/model/configurations/**`、`/preferences`、探测与发现 | POST / PUT / DELETE | `model:user:write` |
| `/api/v1/model/system-**` | 全部 | `model:system:write`（已有） |
| 任意路径解析到 `scope_type = SYSTEM` 的模型 | — | `model:official:use`（执行层） |
| `/api/v1/users/**` | GET | `rbac:user:read` |
| `/api/v1/users`、`/users/{id}` | POST / PUT | `rbac:user:write` |
| `/api/v1/users/{id}`、`/restore-deletion` | DELETE / POST | `rbac:user:delete` |
| `/api/v1/users/{id}/roles` | PUT | `rbac:user:bind-role` |
| `/api/v1/roles`、`/roles/{id}` | GET / POST / PUT / DELETE | `rbac:role:read` / `write` / `delete` |
| `/api/v1/roles/{id}/permissions` | PUT | `rbac:role:bind-permission` |
| `/api/v1/permissions` | GET | `rbac:permission:read` |
| `/api/v1/menus` | GET | `rbac:menu:read` |
| `/api/v1/profile/menus` | GET | 仅需登录（返回本人可见菜单） |
| `/api/v1/jobs`、`/jobs/{id}` | GET | `ops:job:read` |
| `/api/v1/jobs/{id}/retry`、`/migrations/**` | POST | `ops:job:write` |

存储不单独暴露权限：封面上传归 `novel:write`，知识文档上传归 `rag:write`。

**必须同时修正**：`SecurityConfig` 中 `/api/v1/ops/**` 的匹配路径与 `OpsController` 的 `/api/v1/jobs`、`/api/v1/migrations` 不一致，当前运维权限完全没有生效。

进入业务工作区的统一前置是 `app:access`，在 `SecurityConfig` 中对全部业务前缀统一要求，配合各接口的细粒度权限。

## 10. 数据库与 Flyway 重排（D19）

现有迁移本就按领域组织，保留这个结构，只把后续补丁折回所属领域文件：

| 基线文件 | 领域 | 折回内容 |
| --- | --- | --- |
| `V1__create_iam_and_rbac.sql` | IAM / RBAC / Auth | 新增 `authorization_version`、`auth_method` CHECK 约束 |
| `V2__create_novel_and_story_domain.sql` | 小说、审批、故事圣经 | V10 故事核心扩展、V15 章节租约 AI-only |
| `V3__create_storage_and_rag.sql` | 存储与 RAG | 无 |
| `V4__create_plugin_and_model.sql` | 插件与模型 | V12 项目模型默认值、V14 Chat 协议调整 |
| `V5__create_agent_domain.sql` | Agent | V8 技能绑定、V11 会话技能名 |
| `V6__create_agent_execution_extensions.sql` | Agent 执行与异步任务 | V13 导入会话 |
| `V7__seed_system_data.sql` | 系统种子 | 新角色、新权限目录、角色权限绑定、菜单权限码 |
| 新增 `V8__add_author_profiles.sql` 或折回 | 作者资料 | 原 V9 内容 |

删除已折回的 V10-V15 增量文件。项目未上线，不做兼容迁移；本地开发库直接删除重建，实施阶段给出明确的重建命令与影响范围，不自动执行破坏性操作。

Bootstrap（`SystemDataBootstrap`）职责不变：只创建本地管理员账号、密码与环境相关模型配置，密钥不写入 Flyway。需要补充：管理员账号同时确保绑定 `ROLE_ADMIN`，并标记为受保护的应急管理员。

种子数据需要新增的内容：

- `iam_roles`：`ROLE_USER`(role_id=2)、`ROLE_OFFICIAL_MODEL_USER`(role_id=3)，`is_system = TRUE`。
- `iam_permissions`：第 4 节全部权限，保留现有 1-14 的 ID 不变，新增权限从 15 起。
- `iam_role_permissions`：按 4.5 矩阵绑定。
- `iam_menus`：为业务菜单补上 `permission_code`（`/mybooks`、`/workbench` 用 `app:access`，`/profile` 用 `profile:read`），管理菜单沿用现有权限码。

## 11. 前端改造（D20）

- **不展示权限码**：角色权限矩阵按业务域分组，展示中文能力名称、说明与风险标识。
- **有效访问结果**：用户详情展示“该用户实际能做什么”，包括是否具备官方模型能力。
- **系统角色保护**：`ROLE_ADMIN`/`ROLE_USER`/`ROLE_OFFICIAL_MODEL_USER` 的编辑、删除入口禁用并说明原因。
- **管理员防锁死提示**：自我降权、停用/删除最后一个管理员的按钮禁用并给出原因。
- **创建用户表单**：删除认证方式选择，新增“初始密码 + 确认密码”，前端做基本强度提示。
- **官方模型锁定态**：模型选择器与默认模型设置中，官方模型可见但不可选，带锁标与统一样式的简短说明，不使用打断式弹窗。
- **无角色受限页**：登录后若缺少 `app:access`，进入“账户暂无使用权限”页，隐藏业务导航。
- **路由守卫**：当前 `router/index.ts` 只在 `/admin` 前缀检查菜单，需要扩展为按 `app:access` 与页面所需权限判定，但仍以后端为准。
- **403 处理**：统一拦截官方模型未授权错误码，提示改选个人模型，并引导到模型设置。

## 12. 安全不变量

实施与后续修改都不得破坏以下不变量：

1. 权限判定以后端为唯一权威，前端任何禁用都不构成安全边界。
2. 纯允许模型：不存在 DENY，多角色权限取并集。
3. 官方模型的授权判定发生在模型解析层，不是仅在配置接口。
4. RBAC 不替代资源归属校验，两者必须同时通过。
5. 授权变更在 PostgreSQL 事务内强一致提交，Redis 只是缓存。
6. 鉴权依赖的存储不可用时失败关闭。
7. 系统中始终至少存在一个启用状态的管理员。
8. 系统角色不可被修改或删除。
9. 长任务在每次消费外部付费资源前重新确认授权版本。

## 13. 实施顺序

1. Flyway 基线重排与种子数据（角色、权限、绑定、菜单权限码）。
2. IAM 领域与 `authorization_version` 字段、Gateway/Mapper 调整。
3. 三个必修缺陷：业务 ID 混用、初始密码、角色变更后会话失效；外加运维路径错配。
4. `CapabilityAuthorizationService` 与 `AuthorizationQueryPort` 落地。
5. `ModelAccessPolicy` 与官方模型执行层鉴权、列表可用性字段。
6. `SecurityConfig` 与 `@PreAuthorize` 全量接口映射。
7. `IAM_REVOKE_AUTH_SESSIONS` 异步任务处理器与事务内入队。
8. 管理员防锁死约束。
9. 认证方式收敛为 `LOCAL`，前后端删除 OAuth/SSO 假入口。
10. 前端 RBAC 界面、官方模型锁定态、无角色受限页、路由守卫。

## 14. 测试与验收

### 14.1 后端单元与集成

- 无角色用户访问业务接口返回 403，访问本人账户接口返回 200。
- `ROLE_USER` 可完成作品/章节/Agent 全链路，使用个人模型成功。
- `ROLE_USER` 解析官方模型时返回 403，且**不发生**模型回退。
- 授予 `ROLE_OFFICIAL_MODEL_USER` 后官方模型可用。
- 角色变更后旧 access token 与旧 refresh token 均立即失效。
- 角色权限变更导致该角色下所有用户版本递增，会话全部失效。
- 数据库操作失败时整体回滚，`authorization_version` 与审计记录都不变更。
- Redis 不可用时鉴权返回 503，不放行。
- Agent Run 中途撤权：当前进行中的单次模型请求可完成，下一次调用被拒绝。
- 管理员不能移除自己的 `ROLE_ADMIN`；不能停用/删除最后一个启用管理员；应急管理员不可被降权、停用、删除。
- 系统角色的改名、改权限、删除均被拒绝。
- 创建用户写入 BCrypt 密码，创建后可直接登录。
- 登录按业务 `user_id` 加载角色与权限，普通用户角色生效。
- `/api/v1/jobs` 与 `/api/v1/migrations` 正确要求 `ops:job:read` / `ops:job:write`。

### 14.2 前端

- RBAC 页面不出现任何内部权限码。
- 官方模型在无权时可见、不可选、有锁定说明。
- 无角色账号登录后进入受限页，不显示业务导航。
- 创建用户表单无认证方式选项，包含初始密码与确认密码校验。
- 系统角色与危险管理操作的禁用状态与原因提示正确。

### 14.3 回归

- 后端全量测试通过。
- 前端单测、类型检查、ESLint、生产构建通过。
- E2E 覆盖：管理台用户与角色管理、官方模型锁定态、无角色受限登录。

## 15. 未纳入本次范围

- OAuth2 / OIDC / SAML 等外部身份接入。
- 一次性临时密码与首次登录强制改密。
- 邮件邀请与密码重置链路。
- 基于属性的访问控制（ABAC）与显式 DENY。
- 多租户与组织层级模型。
- 官方模型的额度、配额与计费统计（本次只做“能不能用”的授权判定）。

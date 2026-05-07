# PenMate 数据库迁移脚本表设计分析

本文面向“读懂业务模型”而不是逐列翻译 SQL，重点解释以下 migration 文件中每张表承载什么业务、字段大致表达什么含义、表之间的关系，以及它们分别属于哪些领域边界：

- [`V1__init_iam_and_rbac.sql`](../penmate-backend/src/main/resources/db/migration/V1__init_iam_and_rbac.sql)
- [`V2__init_novel_and_approval_minimal.sql`](../penmate-backend/src/main/resources/db/migration/V2__init_novel_and_approval_minimal.sql)
- [`V3__init_storage_and_rag_minimal.sql`](../penmate-backend/src/main/resources/db/migration/V3__init_storage_and_rag_minimal.sql)
- [`V4__init_novel_volume_and_chapter.sql`](../penmate-backend/src/main/resources/db/migration/V4__init_novel_volume_and_chapter.sql)
- [`V5__init_novel_members_and_chapter_versions.sql`](../penmate-backend/src/main/resources/db/migration/V5__init_novel_members_and_chapter_versions.sql)
- [`V7__init_iam_menus.sql`](../penmate-backend/src/main/resources/db/migration/V7__init_iam_menus.sql)
- [`V8__init_novel_outlines_and_cards.sql`](../penmate-backend/src/main/resources/db/migration/V8__init_novel_outlines_and_cards.sql)
- [`V9__init_style_profiles.sql`](../penmate-backend/src/main/resources/db/migration/V9__init_style_profiles.sql)
- [`V10__init_plugin_and_model_domains.sql`](../penmate-backend/src/main/resources/db/migration/V10__init_plugin_and_model_domains.sql)
- [`V11__init_agent_and_ops_domains.sql`](../penmate-backend/src/main/resources/db/migration/V11__init_agent_and_ops_domains.sql)
- [`V12__init_pending_tool_invocations.sql`](../penmate-backend/src/main/resources/db/migration/V12__init_pending_tool_invocations.sql)

同时参考了测试造数 [`seed_all_domain_base.sql`](../penmate-backend/src/test/resources/db/cases/seed_all_domain_base.sql) 和若干应用服务命名，以校准表的真实业务语义，例如 [`AuthApplicationService.login()`](../penmate-backend/src/main/java/com/penmate/backend/application/auth/AuthApplicationService.java:44)、[`IamQueryApplicationService.listUsers()`](../penmate-backend/src/main/java/com/penmate/backend/application/iam/IamQueryApplicationService.java:36)、[`ApprovalApplicationService.create()`](../penmate-backend/src/main/java/com/penmate/backend/application/approval/ApprovalApplicationService.java:48)、[`ModelApplicationService.getUserModelPreferencesDetail()`](../penmate-backend/src/main/java/com/penmate/backend/application/model/ModelApplicationService.java:78)、[`AgentMessageAppService.createMessage()`](../penmate-backend/src/main/java/com/penmate/backend/application/agent/usecase/AgentMessageAppService.java:32)。

---

## 1. 整体领域地图

从这些 migration 看，PenMate 的库表大致被拆成以下几个业务域：

1. **IAM / RBAC 域**：用户、角色、权限、菜单。
2. **Novel 写作域**：小说项目、成员、卷、章、章节版本、大纲、卡片、卡片关系。
3. **Approval 审批域**：Agent 触发的人工审批请求与审批动作。
4. **Storage / RAG 域**：对象存储元数据、知识文档、切片、检索日志。
5. **Style 文风域**：文风配置及切换记录。
6. **Plugin / Model 域**：插件目录、项目安装、插件调用日志；模型密钥、模型配置、项目级模型策略。
7. **Agent 域**：会话、消息、生成任务。
8. **Ops 运维域**：异步任务、迁移任务。
9. **Pending Tool Invocation 桥接域**：审批通过后待恢复执行的工具调用快照。

一个重要特征是：

- **`project_id` 是多数业务表的核心租户/上下文边界**，说明 PenMate 的很多能力都是围绕“小说项目”组织的。
- 表里同时存在 `id` 和 `xxx_id` 两套 ID：
  - `id` 通常是数据库自增主键，偏基础设施层。
  - `user_id`、`project_id`、`chapter_id`、`approval_request_id` 这类业务 ID 更像领域对象对外暴露的标识。
- 大量表带有 `created_at`、`updated_at`、`deleted_at`，说明系统偏向**审计友好 + 软删除**。
- SQL 中很少直接写外键约束，更多依赖应用层和约定维持一致性，这让系统迁移灵活，但也意味着数据治理更依赖代码正确性。

---

## 2. [`V1__init_iam_and_rbac.sql`](../penmate-backend/src/main/resources/db/migration/V1__init_iam_and_rbac.sql)：身份与权限基础域

### 2.1 [`iam_users`](../penmate-backend/src/main/resources/db/migration/V1__init_iam_and_rbac.sql)

**承载业务**：系统用户主档。

**核心含义**：

- `user_id`：业务用户 ID，对外识别用户主要靠它。
- `email`：登录账号。
- `password_hash`：密码摘要。结合 [`AuthApplicationService.login()`](../penmate-backend/src/main/java/com/penmate/backend/application/auth/AuthApplicationService.java:44) 看，这是本地认证入口的核心字段。
- `display_name`：展示名。
- `status`：用户状态，测试数据里出现 `0/1/2`，大致可理解为禁用、启用、冻结等。
- `auth_method`：认证方式，如 `local`、`sso`。
- `main_agent_model_config_id`、`dirty_work_agent_model_config_id`：用户对 Agent 模型的偏好配置引用，说明“用户身份域”与“模型配置域”有轻度耦合。
- `last_login_at`：最后登录时间。
- `deleted_at`：软删除标记。

**业务定位**：
这不是单纯账户表，而是“账号 + 基础偏好入口”的用户主记录。

### 2.2 [`iam_roles`](../penmate-backend/src/main/resources/db/migration/V1__init_iam_and_rbac.sql)

**承载业务**：角色定义。

**核心字段**：

- `role_id`：业务角色 ID。
- `name` / `code`：角色名与机器码。
- `description`：角色说明。
- `is_system`：是否系统内置角色。

测试数据里的 `platform_admin`、`project_owner`、`approval_reviewer` 很明确说明：角色是平台治理与写作协作的权限抽象载体。

### 2.3 [`iam_permissions`](../penmate-backend/src/main/resources/db/migration/V1__init_iam_and_rbac.sql)

**承载业务**：权限点定义。

**核心字段**：

- `permission_id`：业务权限 ID。
- `name`：权限名称。
- `code`：权限码，如 `novel.project.manage`、`approval.request.review`。
- `module`：权限所属模块，例如 `novel`、`approval`、`plugin`、`model`、`rbac`。

这张表说明 PenMate 的权限模型已经不是页面级，而是**模块/动作级能力授权**。

### 2.4 [`iam_user_roles`](../penmate-backend/src/main/resources/db/migration/V1__init_iam_and_rbac.sql)

**承载业务**：用户与角色的多对多绑定。

**字段语义**：

- `user_id`：对应 [`iam_users`](../penmate-backend/src/main/resources/db/migration/V1__init_iam_and_rbac.sql) 的业务 ID。
- `role_id`：对应 [`iam_roles`](../penmate-backend/src/main/resources/db/migration/V1__init_iam_and_rbac.sql) 的业务 ID。
- 联合主键 `(user_id, role_id)`：一个用户不能重复绑定同一角色。

### 2.5 [`iam_role_permissions`](../penmate-backend/src/main/resources/db/migration/V1__init_iam_and_rbac.sql)

**承载业务**：角色与权限的多对多绑定。

**意义**：角色是权限集合的容器；用户不直接绑定权限，而是通过角色继承权限。

### 2.6 领域边界总结

IAM/RBAC 域负责回答三个问题：

1. 你是谁？
2. 你拥有什么能力？
3. 你在前端应该看见哪些入口？（菜单见 V7）

它原则上不关心小说项目内部结构，但用户表里挂了模型偏好字段，说明系统把“当前用户默认 AI 能力配置”也收敛在身份域入口中。

---

## 3. [`V2__init_novel_and_approval_minimal.sql`](../penmate-backend/src/main/resources/db/migration/V2__init_novel_and_approval_minimal.sql)：小说项目根对象与审批最小闭环

### 3.1 [`novel_projects`](../penmate-backend/src/main/resources/db/migration/V2__init_novel_and_approval_minimal.sql)

**承载业务**：小说项目主表，是多数创作域能力的根聚合。

**核心字段**：

- `project_id`：业务项目 ID，全域高频引用。
- `owner_user_id`：项目所有者。
- `title`：项目标题。
- `summary`：项目简介。
- `status`：项目状态。测试数据里有草稿、连载、完结、归档等语义。
- `deleted_at`：软删除。

**业务意义**：
PenMate 的“书/作品/创作空间”核心就是这张表。后续卷、章、文风、插件安装、会话、RAG 文档几乎都围绕它展开。

### 3.2 [`agent_approval_requests`](../penmate-backend/src/main/resources/db/migration/V2__init_novel_and_approval_minimal.sql)

**承载业务**：Agent 发起的人工审批请求。

**核心字段**：

- `approval_request_id`：业务审批 ID。
- `project_id`：审批属于哪个项目。
- `task_id`：关联哪个 Agent 生成任务，可空，说明审批不一定总挂在任务上。
- `approval_type`：审批类型，如发布、重写、扩写、工具执行等。
- `payload_json`：审批上下文载荷，保存待审对象详细信息。
- `risk_level`：风险等级。
- `status`：审批状态，至少有 `pending`、`approved`、`rejected`。
- `requested_by`：申请人。
- `reviewed_by` / `reviewed_at` / `review_comment`：审核人、审核时间、审核意见。

结合 [`ApprovalApplicationService.create()`](../penmate-backend/src/main/java/com/penmate/backend/application/approval/ApprovalApplicationService.java:48) 和测试数据，这张表是“人工介入点”的主记录。

### 3.3 [`agent_approval_actions`](../penmate-backend/src/main/resources/db/migration/V2__init_novel_and_approval_minimal.sql)

**承载业务**：审批动作流水。

**核心字段**：

- `approval_action_id`：动作业务 ID。
- `request_id`：关联审批请求 ID。
- `action`：执行动作，如 `approve`、`reject`。
- `operator_id`：操作人。
- `comment`：动作备注。

**业务意义**：
`agent_approval_requests` 记录审批当前状态，`agent_approval_actions` 记录审批过程事件。前者偏“主状态”，后者偏“操作日志”。

### 3.4 关系与边界

- 一个 [`novel_projects`](../penmate-backend/src/main/resources/db/migration/V2__init_novel_and_approval_minimal.sql) 可拥有多个 [`agent_approval_requests`](../penmate-backend/src/main/resources/db/migration/V2__init_novel_and_approval_minimal.sql)。
- 一个 [`agent_approval_requests`](../penmate-backend/src/main/resources/db/migration/V2__init_novel_and_approval_minimal.sql) 可拥有多个 [`agent_approval_actions`](../penmate-backend/src/main/resources/db/migration/V2__init_novel_and_approval_minimal.sql)。
- 审批域不负责真正执行生成或工具调用，它只负责“让动作合法地被人确认”，后续与 Agent、待执行工具快照在 V11/V12 联动。

---

## 4. [`V3__init_storage_and_rag_minimal.sql`](../penmate-backend/src/main/resources/db/migration/V3__init_storage_and_rag_minimal.sql)：对象存储与知识检索域

### 4.1 [`storage_objects`](../penmate-backend/src/main/resources/db/migration/V3__init_storage_and_rag_minimal.sql)

**承载业务**：对象存储中文件/对象的元数据登记表。

**核心字段**：

- `storage_object_id`：业务对象 ID。
- `object_key`：对象存储中的路径键。
- `bucket` / `provider` / `region` / `storage_class`：存储位置与存储策略。
- `etag` / `size`：对象版本与大小信息。
- `ref_type` / `ref_id`：这份对象元数据“归属于谁”的多态引用。

测试数据里出现：

- `ref_type = novel_chapter`
- `ref_type = novel_chapter_version`
- `ref_type = rag_document`

这说明它是全局文件资产登记簿，而不是只服务某一个领域。

### 4.2 [`rag_documents`](../penmate-backend/src/main/resources/db/migration/V3__init_storage_and_rag_minimal.sql)

**承载业务**：进入 RAG 流程的知识文档主表。

**核心字段**：

- `document_id`：业务文档 ID。
- `project_id`：文档属于哪个小说项目。
- `doc_type`：文档类型，如设定、FAQ、手册。
- `title`：文档标题。
- `source_ref`：来源引用，如上传来源、外部 wiki 来源。
- `origin_object_key` / `origin_etag`：原始文件对象信息。
- `mime_type`：媒体类型。
- `parse_status`：解析状态。
- `index_status`：向量索引状态。

**业务意义**：
这张表代表“项目知识库中的一份原始文档”，还没到向量粒度。

### 4.3 [`rag_chunks`](../penmate-backend/src/main/resources/db/migration/V3__init_storage_and_rag_minimal.sql)

**承载业务**：RAG 文档切片及其向量索引元数据。

**核心字段**：

- `chunk_id`：业务切片 ID。
- `project_id` / `document_id`：归属项目和文档。
- `chunk_no`：切片顺序号。
- `content_text`：切片文本。
- `token_count`：切片 token 数。
- `vector_id` / `vector_store`：向量库中的索引标识。
- `embedding_provider` / `embedding_model` / `embedding_dim` / `embedding_version`：嵌入模型信息。
- `metadata_json`：切片附加元数据，如章节、分区、标签。

**业务意义**：
这张表是“文本切分 + 向量化”的结果索引层，用于支撑 Agent 检索增强生成。

### 4.4 [`rag_retrieval_logs`](../penmate-backend/src/main/resources/db/migration/V3__init_storage_and_rag_minimal.sql)

**承载业务**：RAG 检索行为日志。

**核心字段**：

- `retrieval_log_id`：业务日志 ID。
- `project_id` / `task_id`：在哪个项目、哪次任务里发生检索。
- `query_text`：查询语句。
- `hit_count`：命中文档/切片数。
- `sources_json`：引用来源列表。
- `latency_ms`：耗时。
- `adopted`：检索结果是否被采纳。
- `trace_id`：链路跟踪号。

**业务意义**：
这是 RAG 效果与调用链可观测性的关键表，用来分析“查了什么、命中了什么、有没有真正用上”。

### 4.5 边界总结

Storage/RAG 域做两件事：

1. 统一登记底层文件对象。
2. 把项目知识沉淀成可检索的增强上下文。

它与 Novel 域、Agent 域高度协作，但领域职责不同：

- Novel 域关心“章节/大纲/卡片本身”。
- Storage/RAG 域关心“这些内容怎么被存储、解析、切片、检索”。

---

## 5. [`V4__init_novel_volume_and_chapter.sql`](../penmate-backend/src/main/resources/db/migration/V4__init_novel_volume_and_chapter.sql)：卷章结构主干

### 5.1 [`novel_volumes`](../penmate-backend/src/main/resources/db/migration/V4__init_novel_volume_and_chapter.sql)

**承载业务**：项目内的“卷”层级。

**核心字段**：

- `volume_id`：业务卷 ID。
- `project_id`：所属项目。
- `title`：卷名。
- `sort_order`：排序。
- `description`：卷简介。

**业务意义**：
这是比章节更高一级的目录编排单位。

### 5.2 [`novel_chapters`](../penmate-backend/src/main/resources/db/migration/V4__init_novel_volume_and_chapter.sql)

**承载业务**：章节主表，是写作内容生产的核心对象之一。

**核心字段**：

- `chapter_id`：业务章节 ID。
- `project_id`：所属项目。
- `volume_id`：所属卷，可空，说明允许未归卷章节。
- `outline_node_id`：关联大纲节点，可空，说明大纲与章节是“可绑定但不强绑定”的关系。
- `title`：章节标题。
- `chapter_no`：章节号。
- `status`：章节状态。
- `word_count`：字数。
- `excerpt`：摘要。
- `content_object_key` / `content_etag` / `content_size` / `content_checksum`：章节正文文件在对象存储中的定位与校验信息。
- `storage_provider`：正文内容所在存储提供商。
- `last_generated_at`：最近一次 AI 生成时间。

**业务意义**：
这张表没有直接存正文，而是把正文放在对象存储里，数据库只放元信息。这是一种典型的“结构化元数据入库，正文对象外置”的设计。

### 5.3 关系与边界

- 一个项目有多个卷。
- 一个项目有多个章节。
- 一个卷有多个章节，但章节可以暂时不属于任何卷。
- 一个章节可以映射到一个大纲节点，但这不是硬约束。

这说明 Novel 域在设计上支持比较灵活的创作流程：先写章、后归卷；先有大纲、后绑定章节；甚至可以平行演进。

---

## 6. [`V5__init_novel_members_and_chapter_versions.sql`](../penmate-backend/src/main/resources/db/migration/V5__init_novel_members_and_chapter_versions.sql)：项目协作与版本化

### 6.1 [`novel_members`](../penmate-backend/src/main/resources/db/migration/V5__init_novel_members_and_chapter_versions.sql)

**承载业务**：项目成员表。

**核心字段**：

- `project_id`：项目。
- `user_id`：成员用户。
- `member_role`：项目内角色，如 `owner`、`admin`、`editor`。
- `joined_at`：加入时间。

**业务意义**：
这张表定义的是**项目内协作角色**，与 [`iam_roles`](../penmate-backend/src/main/resources/db/migration/V1__init_iam_and_rbac.sql) 的**平台级系统角色**不同。

也就是说，PenMate 同时存在两层权限语义：

- 平台层：你是不是管理员、审批员。
- 项目层：你在这本书里是 owner、editor 还是协作者。

### 6.2 [`novel_chapter_versions`](../penmate-backend/src/main/resources/db/migration/V5__init_novel_members_and_chapter_versions.sql)

**承载业务**：章节版本快照表。

**核心字段**：

- `chapter_version_id`：业务版本 ID。
- `chapter_id`：所属章节。
- `version_no`：版本序号。
- `change_type`：变更类型，如创建、重写、扩写。
- `change_reason`：变更原因。
- `snapshot_object_key` / `snapshot_etag` / `snapshot_size` / `snapshot_checksum`：版本快照对象信息。
- `created_by`：由谁产生该版本。
- `created_at`：版本创建时间。

**业务意义**：
章节当前态保存在 [`novel_chapters`](../penmate-backend/src/main/resources/db/migration/V4__init_novel_volume_and_chapter.sql)，历史态保存在 [`novel_chapter_versions`](../penmate-backend/src/main/resources/db/migration/V5__init_novel_members_and_chapter_versions.sql)。

这体现出很清晰的设计分工：

- 当前工作副本：便于读写和展示。
- 历史版本快照：便于回溯、预览、恢复、对比。

---

## 7. [`V7__init_iam_menus.sql`](../penmate-backend/src/main/resources/db/migration/V7__init_iam_menus.sql)：菜单导航域

### 7.1 [`iam_menus`](../penmate-backend/src/main/resources/db/migration/V7__init_iam_menus.sql)

**承载业务**：前端导航菜单定义。

**核心字段**：

- `menu_id`：业务菜单 ID。
- `parent_id`：父菜单 ID，支持树形菜单。
- `title`：标题。
- `path`：路由路径。
- `sort_order`：排序。
- `permission_code`：访问所需权限码。
- `visible`：是否可见。

结合测试数据可见：

- `工作台` -> `/workbench`
- `小说管理` -> `/workbench/novel`
- `审批中心` -> `/workbench/approval`
- `RBAC 管理` -> `/admin/rbac`

**业务意义**：
这张表不是权限定义本身，而是“权限驱动的 UI 导航配置”。

### 7.2 边界说明

- [`iam_permissions`](../penmate-backend/src/main/resources/db/migration/V1__init_iam_and_rbac.sql) 决定“能不能做”。
- [`iam_menus`](../penmate-backend/src/main/resources/db/migration/V7__init_iam_menus.sql) 决定“看不看得见入口”。

菜单域本质是 IAM/RBAC 的表现层延伸。

---

## 8. [`V8__init_novel_outlines_and_cards.sql`](../penmate-backend/src/main/resources/db/migration/V8__init_novel_outlines_and_cards.sql)：大纲与设定卡片域

### 8.1 [`novel_outline_nodes`](../penmate-backend/src/main/resources/db/migration/V8__init_novel_outlines_and_cards.sql)

**承载业务**：项目大纲树节点。

**核心字段**：

- `outline_node_id`：业务节点 ID。
- `project_id`：所属项目。
- `parent_id`：父节点。
- `title`：节点标题。
- `node_type`：节点类型，如 `root`、`arc`、`chapter`。
- `sort_order`：排序。
- `content`：节点内容说明。

**业务意义**：
这是“创作规划层”的核心数据结构，用树形节点承载总纲、幕、章等抽象。

### 8.2 [`novel_cards`](../penmate-backend/src/main/resources/db/migration/V8__init_novel_outlines_and_cards.sql)

**承载业务**：小说设定卡片。

**核心字段**：

- `card_id`：业务卡片 ID。
- `project_id`：所属项目。
- `card_type`：卡片类型，如角色、世界、派系、道具。
- `name`：名称。
- `summary`：简述。
- `detail_json`：结构化扩展详情。

**业务意义**：
这张表是“半结构化设定库”。公共字段保持统一，不同卡片类型的个性化属性下沉到 `detail_json`。

### 8.3 [`novel_card_relations`](../penmate-backend/src/main/resources/db/migration/V8__init_novel_outlines_and_cards.sql)

**承载业务**：卡片之间的关系边。

**核心字段**：

- `card_relation_id`：业务关系 ID。
- `project_id`：所属项目。
- `from_card_id` / `to_card_id`：起点卡片与终点卡片。
- `relation_type`：关系类型，如 `belongs_to`、`member_of`、`owns`。
- `description`：关系说明。

**业务意义**：
Novel 卡片域不是简单列表，而是一个轻量知识图谱。人物与地点、派系、道具之间的边关系可以被建模出来。

### 8.4 与章节/大纲的边界

- 大纲描述“故事如何展开”。
- 章节是“实际正文成果”。
- 卡片描述“设定世界里有什么对象”。
- 卡片关系描述“这些对象之间怎么连接”。

这四者都属于 Novel 域，但职责明显不同：

- **章节**偏内容产出。
- **大纲**偏结构规划。
- **卡片**偏知识资产。
- **卡片关系**偏设定网络。

---

## 9. [`V9__init_style_profiles.sql`](../penmate-backend/src/main/resources/db/migration/V9__init_style_profiles.sql)：文风策略域

### 9.1 [`style_profiles`](../penmate-backend/src/main/resources/db/migration/V9__init_style_profiles.sql)

**承载业务**：项目级文风配置。

**核心字段**：

- `style_id`：业务文风 ID。
- `project_id`：所属项目。
- `name`：文风名称。
- `is_default`：是否默认文风。
- `pace`：节奏。
- `tone`：语气/色调。
- `narrative_focus`：叙事重心。
- `prompt_template`：喂给模型的提示模板。
- `sample_text`：示例文本。

**业务意义**：
它不是纯 UI 偏好，而是**真正参与生成过程的创作策略配置**。在 [`agent_generation_tasks`](../penmate-backend/src/main/resources/db/migration/V11__init_agent_and_ops_domains.sql) 里还有 `style_profile_snapshot`，说明任务执行时会固化文风快照。

### 9.2 [`style_switch_logs`](../penmate-backend/src/main/resources/db/migration/V9__init_style_profiles.sql)

**承载业务**：文风切换历史。

**核心字段**：

- `style_switch_log_id`：业务日志 ID。
- `project_id`：所属项目。
- `from_style_id` / `to_style_id`：从哪个文风切到哪个文风。
- `switched_by`：操作人。
- `warning_confirmed`：是否确认过风险提示。
- `reason`：切换原因。

**业务意义**：
文风切换被认为是一个重要操作，因为它可能影响整书一致性，所以系统专门记录切换轨迹。

### 9.3 边界说明

文风域是 Novel 域的配套策略域：

- Novel 决定“写什么”。
- Style 决定“怎么写”。

---

## 10. [`V10__init_plugin_and_model_domains.sql`](../penmate-backend/src/main/resources/db/migration/V10__init_plugin_and_model_domains.sql)：插件域与模型域

这一版 migration 实际包含两个并列子域：**插件** 和 **模型配置**。

### 10.1 插件子域

#### 10.1.1 [`plugin_catalog`](../penmate-backend/src/main/resources/db/migration/V10__init_plugin_and_model_domains.sql)

**承载业务**：插件总目录。

**核心字段**：

- `plugin_id`：业务插件 ID。
- `code`：插件唯一编码。
- `name`：插件名称。
- `category`：分类。
- `provider`：提供方。
- `status`：状态。
- `latest_version`：最新版本。

**业务意义**：
这是“平台可提供什么插件”的注册表。

#### 10.1.2 [`plugin_project_installs`](../penmate-backend/src/main/resources/db/migration/V10__init_plugin_and_model_domains.sql)

**承载业务**：项目级插件安装记录。

**核心字段**：

- `plugin_install_id`：业务安装 ID。
- `project_id`：安装到哪个项目。
- `plugin_id`：装的是哪个插件。
- `version`：安装版本。
- `config_json`：项目级插件配置。
- `enabled`：是否启用。
- `installed_by` / `installed_at`：安装人和时间。

**业务意义**：
平台目录中的插件，只有被项目安装后，才真正成为项目可用能力。

#### 10.1.3 [`plugin_call_logs`](../penmate-backend/src/main/resources/db/migration/V10__init_plugin_and_model_domains.sql)

**承载业务**：插件/工具调用日志。

**核心字段**：

- `plugin_call_log_id`：业务日志 ID。
- `project_id` / `task_id`：在哪个项目/任务里调用。
- `plugin_code` / `tool_name`：调用了哪个插件、哪个工具。
- `request_json` / `response_json`：请求与响应快照。
- `latency_ms`：耗时。
- `status` / `error_msg`：结果状态与错误。

**业务意义**：
这张表是插件执行可观测性的关键。它和审批域、待执行工具域一起，构成了“Agent 调工具”的审计链路。

### 10.2 模型子域

#### 10.2.1 [`model_user_api_keys`](../penmate-backend/src/main/resources/db/migration/V10__init_plugin_and_model_domains.sql)

**承载业务**：用户自有模型 API Key。

**核心字段**：

- `user_api_key_id`：业务密钥 ID。
- `user_id`：归属用户。
- `provider_id`：模型厂商 ID。
- `key_name`：密钥名称。
- `encrypted_api_key`：加密后的密钥正文。
- `masked_api_key`：脱敏显示值。
- `is_default`：是否默认。
- `last_used_at` / `status`：最近使用时间与状态。

#### 10.2.2 [`model_official_api_keys`](../penmate-backend/src/main/resources/db/migration/V10__init_plugin_and_model_domains.sql)

**承载业务**：平台官方托管的模型 API Key。

与用户 Key 的差别在于归属主体不是用户，而是平台/提供商维度。

#### 10.2.3 [`model_user_configurations`](../penmate-backend/src/main/resources/db/migration/V10__init_plugin_and_model_domains.sql)

**承载业务**：用户可选的模型配置项。

**核心字段**：

- `model_config_id`：业务模型配置 ID。
- `user_id`：配置归属用户。
- `provider_id`：厂商。
- `model_name`：模型名。
- `base_url`：自定义服务地址。
- `key_source_type`：密钥来源类型，结合 [`ModelApplicationService`](../penmate-backend/src/main/java/com/penmate/backend/application/model/ModelApplicationService.java:36) 可知至少有 `USER_KEY`、`OFFICIAL_KEY`。
- `user_key_id` / `official_key_id`：根据来源指向对应密钥。
- `status`：配置状态。

**业务意义**：
Key 是“凭证”，Configuration 是“可运行的模型接入配置”。一个配置把模型名、厂商、地址、凭证来源绑定在一起。

**承载业务**：项目级模型调用策略。

**核心字段**：

- `project_policy_id`：业务策略 ID。
- `project_id`：所属项目。
- `policy_name`：策略名。
- `scene`：使用场景，如写作、审阅、摘要等。
- `provider_model_id`：预留的厂商模型标识。
- `model_name` / `base_url`：实际模型接入信息。
- `user_key_id` / `official_key_id`：策略使用的密钥。
- `temperature` / `top_p` / `max_tokens`：生成参数。
- `fallback_policy_json`：降级策略。
- `is_default`：项目默认策略。

**业务意义**：
用户模型配置解决“我有哪些模型可选”，项目模型策略解决“在这个项目、这个场景里应该选哪套模型参数”。

### 10.3 模型域与身份域的连接

[`iam_users`](../penmate-backend/src/main/resources/db/migration/V1__init_iam_and_rbac.sql) 里的 `main_agent_model_config_id`、`dirty_work_agent_model_config_id` 直接引用模型配置，见测试数据对用户偏好的更新，以及 [`ModelApplicationService.getUserModelPreferencesDetail()`](../penmate-backend/src/main/java/com/penmate/backend/application/model/ModelApplicationService.java:78)。

所以模型域有三层：

1. **密钥层**：user/official key。
2. **个人配置层**：user model configurations。
3. **项目策略层**：project policies。

---

## 11. [`V11__init_agent_and_ops_domains.sql`](../penmate-backend/src/main/resources/db/migration/V11__init_agent_and_ops_domains.sql)：Agent 执行域与运维域

### 11.1 Agent 子域

#### 11.1.1 [`agent_conversations`](../penmate-backend/src/main/resources/db/migration/V11__init_agent_and_ops_domains.sql)

**承载业务**：Agent 会话主表。

**核心字段**：

- `conversation_id`：业务会话 ID。
- `project_id`：所属项目。
- `user_id`：发起或归属用户。
- `title`：会话标题。
- `context_scope_json`：会话上下文范围，例如关联哪些章节。
- `last_message_at`：最近消息时间。
- `status`：会话状态。

**业务意义**：
这是“工作台聊天/创作会话”的容器。

#### 11.1.2 [`agent_messages`](../penmate-backend/src/main/resources/db/migration/V11__init_agent_and_ops_domains.sql)

**承载业务**：会话消息。

**核心字段**：

- `message_id`：业务消息 ID。
- `conversation_id`：所属会话。
- `role`：消息角色，如 `system`、`user`、`assistant`、`tool`。
- `user_message_type`：用户消息类型。
- `content_md`：Markdown 内容正文。
- `attachments_json`：附件。
- `tool_calls_json`：工具调用信息。
- `seq_no`：会话内顺序号。

结合 [`AgentMessageAppService.createMessage()`](../penmate-backend/src/main/java/com/penmate/backend/application/agent/usecase/AgentMessageAppService.java:32)，这张表是完整对话历史的持久化载体。

#### 11.1.3 [`agent_generation_tasks`](../penmate-backend/src/main/resources/db/migration/V11__init_agent_and_ops_domains.sql)

**承载业务**：一次 Agent 生成/改写/扩写等任务的执行记录。

**核心字段**：

- `task_id`：业务任务 ID。
- `project_id`：所属项目。
- `conversation_id`：源会话。
- `chapter_id`：作用到哪个章节，可空。
- `model_config_id`：使用的用户模型配置，可空。
- `task_type`：任务类型，如 draft、rewrite、expand、summarize。
- `prompt_snapshot`：任务执行时的提示快照。
- `style_profile_snapshot`：执行时文风快照。
- `plugin_snapshot`：执行时插件快照。
- `token_usage_json` / `cost_json`：消耗与成本。
- `trace_id`：链路跟踪。
- `status`：任务状态。
- `started_at` / `finished_at` / `error_msg`：执行生命周期。

**业务意义**：
这是 Agent 域的“执行实例表”。对话是上下文，任务是动作，任务真正承载一次模型调用/工作流运行的状态机。

### 11.2 Ops 子域

#### 11.2.1 [`ops_async_jobs`](../penmate-backend/src/main/resources/db/migration/V11__init_agent_and_ops_domains.sql)

**承载业务**：通用异步作业表。

**核心字段**：

- `job_id`：业务作业 ID。
- `job_type`：作业类型，如 rag-embed、plugin-sync、migration。
- `biz_key`：业务键。
- `status`：状态。
- `error_msg`：错误信息。
- `started_at` / `finished_at`：作业起止时间。

**业务意义**：
这是一个跨域通用“后台任务运行记录”。

#### 11.2.2 [`ops_migrations`](../penmate-backend/src/main/resources/db/migration/V11__init_agent_and_ops_domains.sql)

**承载业务**：业务数据迁移任务记录。

**核心字段**：

- `migration_id`：业务迁移 ID。
- `migration_type`：迁移类型。
- `status`：迁移状态。
- `progress_pct`：进度百分比。
- `summary_json`：迁移统计摘要。
- `error_msg`：异常。
- `started_at` / `finished_at`：迁移生命周期。

**业务意义**：
这不是 Flyway schema migration，而是业务层大批量数据迁移/重建任务记录，比如测试里出现的内容迁移、向量重建。

### 11.3 边界说明

- Agent 域负责“和模型交互并产出结果”。
- Ops 域负责“后台作业与系统级运行可观测性”。

两者经常协作，但不应混为一谈。

---

## 12. [`V12__init_pending_tool_invocations.sql`](../penmate-backend/src/main/resources/db/migration/V12__init_pending_tool_invocations.sql)：审批挂起的工具调用桥接表

### 12.1 [`pending_tool_invocations`](../penmate-backend/src/main/resources/db/migration/V12__init_pending_tool_invocations.sql)

**承载业务**：因为审批而暂挂、等待恢复执行的工具调用快照。

**核心字段**：

- `approval_id`：关联审批 ID，并且唯一，说明一个审批请求对应一个待恢复调用快照。
- `project_id` / `task_id` / `conversation_id`：调用所属项目、任务、会话上下文。
- `tool_code`：待执行工具编码。
- `tool_args_json`：工具参数。
- `context_json`：恢复执行所需上下文快照。
- `operator_id`：操作者或关联用户。
- `trace_id`：链路跟踪。
- `idempotency_key`：幂等键，避免重复恢复执行。
- `status`：状态，如 `pending`、`executing`、`failed`。

### 12.2 它为什么单独成域

从 [`ApprovalApplicationService.approve()`](../penmate-backend/src/main/java/com/penmate/backend/application/approval/ApprovalApplicationService.java:92) 及其内部的 [`resumeToolInvocationAfterApproved()`](../penmate-backend/src/main/java/com/penmate/backend/application/approval/ApprovalApplicationService.java:130) 可见：

1. 审批通过后，系统会根据 `approval_id` 去找待恢复的工具调用快照。
2. 把状态从 `pending` 抢占到 `executing`。
3. 异步恢复工具执行。

而在 [`ApprovalApplicationService.reject()`](../penmate-backend/src/main/java/com/penmate/backend/application/approval/ApprovalApplicationService.java:111) 中，如果审批驳回：

- 待执行工具调用会被标记为 `failed`。
- 相关 Agent 任务也可能从 `WAITING_APPROVAL` 进入 `FAILED`。

**因此这张表本质上是 Approval 域与 Agent/Tool Runtime 域之间的桥表**，负责把“审批决策”衔接成“执行恢复/终止”。

---

## 13. 关键表关系总览

下面用“业务理解”的视角串一下最重要的关系：

### 13.1 用户、权限、菜单

- 一个 [`iam_users`](../penmate-backend/src/main/resources/db/migration/V1__init_iam_and_rbac.sql) 通过 [`iam_user_roles`](../penmate-backend/src/main/resources/db/migration/V1__init_iam_and_rbac.sql) 绑定多个 [`iam_roles`](../penmate-backend/src/main/resources/db/migration/V1__init_iam_and_rbac.sql)。
- 一个 [`iam_roles`](../penmate-backend/src/main/resources/db/migration/V1__init_iam_and_rbac.sql) 通过 [`iam_role_permissions`](../penmate-backend/src/main/resources/db/migration/V1__init_iam_and_rbac.sql) 拥有多个 [`iam_permissions`](../penmate-backend/src/main/resources/db/migration/V1__init_iam_and_rbac.sql)。
- [`iam_menus`](../penmate-backend/src/main/resources/db/migration/V7__init_iam_menus.sql) 使用 `permission_code` 决定入口显隐。

### 13.2 小说项目主线

- 一个 [`novel_projects`](../penmate-backend/src/main/resources/db/migration/V2__init_novel_and_approval_minimal.sql) 有多个 [`novel_members`](../penmate-backend/src/main/resources/db/migration/V5__init_novel_members_and_chapter_versions.sql)。
- 一个项目有多个 [`novel_volumes`](../penmate-backend/src/main/resources/db/migration/V4__init_novel_volume_and_chapter.sql)、[`novel_chapters`](../penmate-backend/src/main/resources/db/migration/V4__init_novel_volume_and_chapter.sql)、[`novel_outline_nodes`](../penmate-backend/src/main/resources/db/migration/V8__init_novel_outlines_and_cards.sql)、[`novel_cards`](../penmate-backend/src/main/resources/db/migration/V8__init_novel_outlines_and_cards.sql)、[`style_profiles`](../penmate-backend/src/main/resources/db/migration/V9__init_style_profiles.sql)、[`plugin_project_installs`](../penmate-backend/src/main/resources/db/migration/V10__init_plugin_and_model_domains.sql)、[`rag_documents`](../penmate-backend/src/main/resources/db/migration/V3__init_storage_and_rag_minimal.sql)、[`agent_conversations`](../penmate-backend/src/main/resources/db/migration/V11__init_agent_and_ops_domains.sql)。
- 一个 [`novel_chapters`](../penmate-backend/src/main/resources/db/migration/V4__init_novel_volume_and_chapter.sql) 有多个 [`novel_chapter_versions`](../penmate-backend/src/main/resources/db/migration/V5__init_novel_members_and_chapter_versions.sql)。

### 13.3 Agent 生成主线

- 一个 [`agent_conversations`](../penmate-backend/src/main/resources/db/migration/V11__init_agent_and_ops_domains.sql) 有多个 [`agent_messages`](../penmate-backend/src/main/resources/db/migration/V11__init_agent_and_ops_domains.sql)。
- 一个会话可派生多个 [`agent_generation_tasks`](../penmate-backend/src/main/resources/db/migration/V11__init_agent_and_ops_domains.sql)。
- 一个生成任务可能触发 [`agent_approval_requests`](../penmate-backend/src/main/resources/db/migration/V2__init_novel_and_approval_minimal.sql)。
- 一个审批请求可能挂住一个 [`pending_tool_invocations`](../penmate-backend/src/main/resources/db/migration/V12__init_pending_tool_invocations.sql)。
- 工具恢复执行后，可能留下 [`plugin_call_logs`](../penmate-backend/src/main/resources/db/migration/V10__init_plugin_and_model_domains.sql) 或任务状态更新。

### 13.4 内容存储与知识增强主线

- [`novel_chapters`](../penmate-backend/src/main/resources/db/migration/V4__init_novel_volume_and_chapter.sql) 和 [`novel_chapter_versions`](../penmate-backend/src/main/resources/db/migration/V5__init_novel_members_and_chapter_versions.sql) 的内容实体通常会在 [`storage_objects`](../penmate-backend/src/main/resources/db/migration/V3__init_storage_and_rag_minimal.sql) 留档。
- [`rag_documents`](../penmate-backend/src/main/resources/db/migration/V3__init_storage_and_rag_minimal.sql) 指向原始对象，再拆成 [`rag_chunks`](../penmate-backend/src/main/resources/db/migration/V3__init_storage_and_rag_minimal.sql)。
- Agent 执行中如果做检索，会留下 [`rag_retrieval_logs`](../penmate-backend/src/main/resources/db/migration/V3__init_storage_and_rag_minimal.sql)。

---

## 14. 领域边界如何理解

### 14.1 平台级身份权限 vs 项目级协作权限

- [`iam_roles`](../penmate-backend/src/main/resources/db/migration/V1__init_iam_and_rbac.sql) / [`iam_permissions`](../penmate-backend/src/main/resources/db/migration/V1__init_iam_and_rbac.sql) 是**平台治理权限**。
- [`novel_members`](../penmate-backend/src/main/resources/db/migration/V5__init_novel_members_and_chapter_versions.sql) 是**项目内部协作角色**。

这两个概念不能混用。一个人可以不是平台管理员，但仍然是某本书的 owner。

### 14.2 小说内容本体 vs 内容外部存储

- [`novel_chapters`](../penmate-backend/src/main/resources/db/migration/V4__init_novel_volume_and_chapter.sql) 是章节业务对象。
- [`storage_objects`](../penmate-backend/src/main/resources/db/migration/V3__init_storage_and_rag_minimal.sql) 是底层对象元数据。

前者是“业务实体”，后者是“基础设施记录”。

### 14.3 知识资产 vs 生成上下文

- [`novel_cards`](../penmate-backend/src/main/resources/db/migration/V8__init_novel_outlines_and_cards.sql) 与 [`novel_outline_nodes`](../penmate-backend/src/main/resources/db/migration/V8__init_novel_outlines_and_cards.sql) 是创作知识资产。
- [`rag_documents`](../penmate-backend/src/main/resources/db/migration/V3__init_storage_and_rag_minimal.sql) / [`rag_chunks`](../penmate-backend/src/main/resources/db/migration/V3__init_storage_and_rag_minimal.sql) 是可检索的增强语料。

资产不一定全部进入 RAG，RAG 也不只服务设定卡片。

### 14.4 模型偏好 vs 项目策略

- [`iam_users`](../penmate-backend/src/main/resources/db/migration/V1__init_iam_and_rbac.sql) 上的模型偏好字段，是**用户层默认选项**。
- [`model_user_configurations`](../penmate-backend/src/main/resources/db/migration/V10__init_plugin_and_model_domains.sql) 是**用户可用配置池**。

这三层抽象是合理的：用户先有能力，再把能力映射到项目策略。

### 14.5 审批域不是执行域

- [`agent_approval_requests`](../penmate-backend/src/main/resources/db/migration/V2__init_novel_and_approval_minimal.sql) 只决定“批不批”。
- [`pending_tool_invocations`](../penmate-backend/src/main/resources/db/migration/V12__init_pending_tool_invocations.sql) 负责“批完之后怎么恢复执行”。
- [`agent_generation_tasks`](../penmate-backend/src/main/resources/db/migration/V11__init_agent_and_ops_domains.sql) 负责“任务状态流转”。

审批是治理关口，不是执行引擎。

---

## 15. 从设计上看，这套表结构透露出的系统形态

综合这些 migration，可以看出 PenMate 并不是一个简单的“在线写小说”系统，而是一个带有以下特征的创作平台：

1. **项目化创作空间**：所有创作资产围绕 `project_id` 组织。
2. **人机协作写作**：章节、文风、卡片、大纲与 Agent 会话/任务彼此联动。
3. **可治理的 AI 执行链**：模型、插件、审批、待恢复工具调用、调用日志形成闭环。
4. **内容对象外置**：长文本和版本快照不直接堆数据库，而是落对象存储。
5. **知识增强能力**：RAG 文档、切片、检索日志说明系统已准备好让 Agent 基于项目知识库进行生成。
6. **双层权限模型**：既有平台 RBAC，也有项目内协作身份。
7. **较强可观测性**：任务日志、检索日志、插件调用日志、审批动作、异步任务、迁移任务都在留痕。

---

## 16. 一句话总结每个 migration 的职责

- [`V1__init_iam_and_rbac.sql`](../penmate-backend/src/main/resources/db/migration/V1__init_iam_and_rbac.sql)：建立用户、角色、权限三层 RBAC 骨架。
- [`V2__init_novel_and_approval_minimal.sql`](../penmate-backend/src/main/resources/db/migration/V2__init_novel_and_approval_minimal.sql)：建立小说项目根对象与 Agent 审批主流程。
- [`V3__init_storage_and_rag_minimal.sql`](../penmate-backend/src/main/resources/db/migration/V3__init_storage_and_rag_minimal.sql)：建立对象存储登记与 RAG 知识处理链。
- [`V4__init_novel_volume_and_chapter.sql`](../penmate-backend/src/main/resources/db/migration/V4__init_novel_volume_and_chapter.sql)：建立卷章结构与章节内容元数据。
- [`V5__init_novel_members_and_chapter_versions.sql`](../penmate-backend/src/main/resources/db/migration/V5__init_novel_members_and_chapter_versions.sql)：建立项目协作成员和章节历史版本。
- [`V7__init_iam_menus.sql`](../penmate-backend/src/main/resources/db/migration/V7__init_iam_menus.sql)：建立权限驱动的导航菜单模型。
- [`V8__init_novel_outlines_and_cards.sql`](../penmate-backend/src/main/resources/db/migration/V8__init_novel_outlines_and_cards.sql)：建立大纲树与设定卡片/关系图谱。
- [`V9__init_style_profiles.sql`](../penmate-backend/src/main/resources/db/migration/V9__init_style_profiles.sql)：建立项目文风配置及切换留痕。
- [`V10__init_plugin_and_model_domains.sql`](../penmate-backend/src/main/resources/db/migration/V10__init_plugin_and_model_domains.sql)：建立插件能力层与模型接入/策略层。
- [`V11__init_agent_and_ops_domains.sql`](../penmate-backend/src/main/resources/db/migration/V11__init_agent_and_ops_domains.sql)：建立 Agent 会话、消息、任务及运维作业记录。
- [`V12__init_pending_tool_invocations.sql`](../penmate-backend/src/main/resources/db/migration/V12__init_pending_tool_invocations.sql)：建立审批挂起工具调用的恢复桥接机制。

---

## 17. 结论

如果把 PenMate 看成一个领域模型，这批 migration 的核心主线可以概括为：

> **用户在项目空间中进行协作式小说创作，创作过程由大纲、卡片、章节、文风、知识库共同支撑；Agent 基于模型与插件执行生成任务，必要时进入人工审批，最终通过对象存储、日志与运维表形成完整可追踪闭环。**

从建模角度讲，这套表结构已经具备比较明确的领域分层，尤其是：

- **项目是聚合根上下文**；
- **Agent/Approval/Plugin/Model 是 AI 执行链**；
- **Storage/RAG 是基础设施增强链**；
- **IAM/RBAC 与 Novel Members 是双层权限体系**。

这也是理解后续代码组织和接口边界的最好入口。
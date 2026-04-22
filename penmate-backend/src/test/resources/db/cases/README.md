# PenMate DB Case 造数说明

## 1. 文件清单

- [`seed_all_domain_base.sql`](penmate-backend/src/test/resources/db/cases/seed_all_domain_base.sql)
  - 全域主路径基础数据（覆盖当前 Flyway 实际业务表）
- [`seed_all_domain_conflict_boundary.sql`](penmate-backend/src/test/resources/db/cases/seed_all_domain_conflict_boundary.sql)
  - 边界/异常可执行样本 + 冲突/非法模板（默认注释）
- [`seed_all_domain_concurrency_rollback.sql`](penmate-backend/src/test/resources/db/cases/seed_all_domain_concurrency_rollback.sql)
  - 并发、幂等、回滚模板数据
- [`cleanup_all_domain_cases.sql`](penmate-backend/src/test/resources/db/cases/cleanup_all_domain_cases.sql)
  - 统一清理脚本（ID段 920001~922999）

## 2. 推荐执行顺序

1. 执行 [`seed_all_domain_base.sql`](penmate-backend/src/test/resources/db/cases/seed_all_domain_base.sql)
2. 执行 [`seed_all_domain_conflict_boundary.sql`](penmate-backend/src/test/resources/db/cases/seed_all_domain_conflict_boundary.sql)
3. 执行 [`seed_all_domain_concurrency_rollback.sql`](penmate-backend/src/test/resources/db/cases/seed_all_domain_concurrency_rollback.sql)
4. 测试完成后执行 [`cleanup_all_domain_cases.sql`](penmate-backend/src/test/resources/db/cases/cleanup_all_domain_cases.sql)

## 3. 覆盖矩阵（按当前迁移表）

| 领域 | 表 | 覆盖脚本 |
|---|---|---|
| IAM | iam_users / iam_roles / iam_permissions / iam_user_roles / iam_role_permissions / iam_menus | base + conflict_boundary |
| 小说 | novel_projects / novel_members / novel_volumes / novel_chapters / novel_chapter_versions / novel_outline_nodes / novel_cards / novel_card_relations | base + conflict_boundary + concurrency_rollback |
| 文风 | style_profiles / style_switch_logs | base + conflict_boundary |
| 插件 | plugin_catalog / plugin_project_installs / plugin_call_logs | base + conflict_boundary |
| Agent审批 | agent_conversations / agent_messages / agent_generation_tasks / agent_approval_requests / agent_approval_actions | base + conflict_boundary + concurrency_rollback |
| RAG/存储 | rag_documents / rag_chunks / storage_objects | base + conflict_boundary |
| 运维 | ops_async_jobs / ops_migrations | base + conflict_boundary + concurrency_rollback |

## 4. 约束与异常覆盖说明

- 唯一键冲突模板：见 [`seed_all_domain_conflict_boundary.sql`](penmate-backend/src/test/resources/db/cases/seed_all_domain_conflict_boundary.sql)
  - 如 `iam_users.email`、`novel_chapter_versions(chapter_id,version_no)`、`plugin_project_installs(project_id,plugin_id)`、`novel_members(project_id,user_id)`、`rag_chunks(vector_id,vector_store)`
- 并发/乐观锁模板：见 [`seed_all_domain_concurrency_rollback.sql`](penmate-backend/src/test/resources/db/cases/seed_all_domain_concurrency_rollback.sql)
- 事务回滚模板：见 [`seed_all_domain_concurrency_rollback.sql`](penmate-backend/src/test/resources/db/cases/seed_all_domain_concurrency_rollback.sql)
- 按约定不包含模型与密钥造数：`model_providers` / `model_provider_models` / `model_user_api_keys` / `model_project_policies`

## 5. 注意事项

- 以上脚本对齐当前 Flyway 表结构（V1~V11），优先保证可执行。
- 文档 v1.1 中新增但尚未落地迁移的表（如 `plugin_versions`、`model_invocation_logs`、`rag_retrieval_logs` 等）未写入可执行 SQL。
- 若后续迁移补齐这些表，请在对应脚本中新增样本并同步更新本说明。


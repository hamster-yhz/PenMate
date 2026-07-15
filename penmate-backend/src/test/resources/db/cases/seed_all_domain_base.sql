-- PenMate 数据库造数：全域主路径基础集（对齐当前Flyway表）
-- Case: DBCASE_ALL_001_MAIN

SET NAMES utf8mb4;

-- cleanup: child -> parent
DELETE FROM agent_pending_approvals  WHERE pending_approval_id BETWEEN 920001 AND 920999;
DELETE FROM agent_approval_actions  WHERE approval_action_id BETWEEN 920001 AND 920999;
DELETE FROM agent_approval_requests WHERE approval_request_id BETWEEN 920001 AND 920999;
DELETE FROM agent_task_results      WHERE result_id BETWEEN 920001 AND 920999;
DELETE FROM agent_task_contexts     WHERE context_id BETWEEN 920001 AND 920999;
DELETE FROM agent_tasks             WHERE task_id BETWEEN 920001 AND 920999;
DELETE FROM agent_messages          WHERE message_id BETWEEN 920001 AND 920999;
DELETE FROM agent_turns             WHERE turn_id BETWEEN 920001 AND 920999;
DELETE FROM agent_session_working_set WHERE session_id BETWEEN 920001 AND 920999;
DELETE FROM agent_context_epochs    WHERE epoch_id BETWEEN 920001 AND 920999;
DELETE FROM agent_session_style_bindings WHERE binding_id BETWEEN 920001 AND 920999;
DELETE FROM agent_sessions          WHERE session_id BETWEEN 920001 AND 920999;
DELETE FROM agent_user_preferences  WHERE user_id BETWEEN 920001 AND 920999;

DELETE FROM rag_chunks              WHERE chunk_id BETWEEN 920001 AND 920999;
DELETE FROM rag_documents           WHERE document_id BETWEEN 920001 AND 920999;
DELETE FROM storage_objects         WHERE storage_object_id BETWEEN 920001 AND 920999;

DELETE FROM plugin_call_logs        WHERE plugin_call_log_id BETWEEN 920001 AND 920999;
DELETE FROM plugin_project_installs WHERE plugin_install_id BETWEEN 920001 AND 920999;
DELETE FROM plugin_catalog          WHERE plugin_id BETWEEN 920001 AND 920999;

DELETE FROM style_switch_logs       WHERE style_switch_log_id BETWEEN 920001 AND 920999;
DELETE FROM style_profiles          WHERE style_id BETWEEN 920001 AND 920999;

DELETE FROM story_bible_change_items WHERE change_item_id BETWEEN 920001 AND 920999;
DELETE FROM story_bible_changesets  WHERE changeset_id BETWEEN 920001 AND 920999;
DELETE FROM story_bible_view_preferences WHERE story_bible_id BETWEEN 920001 AND 920999;
DELETE FROM story_bible_progressions WHERE progression_id BETWEEN 920001 AND 920999;
DELETE FROM story_bible_relations   WHERE relation_id BETWEEN 920001 AND 920999;
DELETE FROM story_bible_node_tags   WHERE story_bible_id BETWEEN 920001 AND 920999;
DELETE FROM story_bible_tags        WHERE tag_id BETWEEN 920001 AND 920999;
DELETE FROM story_bible_node_categories WHERE story_bible_id BETWEEN 920001 AND 920999;
DELETE FROM story_bible_categories  WHERE category_id BETWEEN 920001 AND 920999;
DELETE FROM story_bible_aliases     WHERE alias_id BETWEEN 920001 AND 920999;
DELETE FROM story_bible_nodes       WHERE node_id BETWEEN 920001 AND 920999;
DELETE FROM story_bible_node_types  WHERE type_id BETWEEN 920001 AND 920999;
DELETE FROM story_bibles            WHERE story_bible_id BETWEEN 920001 AND 920999;
DELETE FROM novel_outline_nodes     WHERE outline_node_id BETWEEN 920001 AND 920999;
DELETE FROM novel_chapter_versions  WHERE chapter_version_id BETWEEN 920001 AND 920999;
DELETE FROM novel_chapters          WHERE chapter_id BETWEEN 920001 AND 920999;
DELETE FROM novel_volumes           WHERE volume_id BETWEEN 920001 AND 920999;
DELETE FROM novel_members           WHERE project_id BETWEEN 920001 AND 920999;
DELETE FROM novel_projects          WHERE project_id BETWEEN 920001 AND 920999;

DELETE FROM iam_menus               WHERE menu_id BETWEEN 920001 AND 920999;
DELETE FROM iam_role_permissions    WHERE role_id BETWEEN 920001 AND 920999;
DELETE FROM iam_user_roles          WHERE user_id BETWEEN 920001 AND 920999;
DELETE FROM iam_permissions         WHERE permission_id BETWEEN 920001 AND 920999;
DELETE FROM iam_roles               WHERE role_id BETWEEN 920001 AND 920999;
DELETE FROM iam_users               WHERE user_id BETWEEN 920001 AND 920999;

DELETE FROM ops_async_jobs          WHERE job_id BETWEEN 920001 AND 920999;
DELETE FROM ops_migrations          WHERE migration_id BETWEEN 920001 AND 920999;

-- IAM
INSERT INTO iam_users (id, user_id, email, password_hash, display_name, status, auth_method, last_login_at, created_at, updated_at, deleted_at) VALUES
(920001, 920001, 'dbcase_admin@penmate.local', '$2a$10$admin',  'DBCASE Admin', 1, 'local', NOW(3), NOW(3), NOW(3), NULL),
(920002, 920002, 'dbcase_owner@penmate.local', '$2a$10$owner',  'DBCASE Owner', 1, 'local', NOW(3), NOW(3), NOW(3), NULL),
(920003, 920003, 'dbcase_editor@penmate.local','$2a$10$editor', 'DBCASE Editor',0, 'local', NULL,   NOW(3), NOW(3), NULL),
(920004, 920004, 'dbcase_frozen@penmate.local','$2a$10$frozen', 'DBCASE Frozen',2, 'sso',   NULL,   NOW(3), NOW(3), NULL);

INSERT INTO iam_roles (id, role_id, name, code, description, is_system, created_at, updated_at, deleted_at) VALUES
(920001, 920001, '平台管理员', 'platform_admin', '全权限', 1, NOW(3), NOW(3), NULL),
(920002, 920002, '项目作者',   'project_owner',  '项目写作', 0, NOW(3), NOW(3), NULL),
(920003, 920003, '审批审阅者', 'approval_reviewer', '审批处理', 0, NOW(3), NOW(3), NULL);

INSERT INTO iam_permissions (id, permission_id, name, code, module, description, created_at) VALUES
(920001, 920001, '项目管理', 'novel.project.manage', 'novel', '项目与卷章管理', NOW(3)),
(920002, 920002, '审批处理', 'approval.request.review', 'approval', '审批请求处理', NOW(3)),
(920003, 920003, '插件调用', 'plugin.call.execute', 'plugin', '插件工具调用', NOW(3)),
(920004, 920004, '模型策略', 'model.policy.manage', 'model', '模型策略维护', NOW(3)),
(920005, 920005, 'RBAC 管理', 'rbac.manage', 'rbac', 'RBAC 管理后台入口', NOW(3));

INSERT INTO iam_user_roles (user_id, role_id, created_at) VALUES
(920001, 920001, NOW(3)),
(920002, 920002, NOW(3)),
(920003, 920003, NOW(3));

INSERT INTO iam_role_permissions (role_id, permission_id, created_at) VALUES
(920001, 920001, NOW(3)), (920001, 920002, NOW(3)), (920001, 920003, NOW(3)), (920001, 920004, NOW(3)), (920001, 920005, NOW(3)),
(920002, 920001, NOW(3)),
(920003, 920002, NOW(3));

INSERT INTO iam_menus (id, menu_id, parent_id, title, path, sort_order, permission_code, visible, created_at, updated_at, deleted_at) VALUES
(920001, 920001, NULL,   '工作台',   '/workbench',                  1,  NULL,                      1, NOW(3), NOW(3), NULL),
(920002, 920002, 920001, '小说管理', '/workbench/novel',           10, 'novel.project.manage',    1, NOW(3), NOW(3), NULL),
(920003, 920003, 920001, '审批中心', '/workbench/approval',        20, 'approval.request.review', 1, NOW(3), NOW(3), NULL),
(920004, 920004, 920002, '章节编辑', '/workbench/novel/chapters', 11, 'novel.project.manage',    1, NOW(3), NOW(3), NULL),
(920005, 920005, NULL,   'RBAC 管理', '/admin/rbac',              90, 'rbac.manage',             1, NOW(3), NOW(3), NULL);

-- 小说核心
INSERT INTO novel_projects (id, project_id, owner_user_id, title, summary, status, structure_revision, created_at, updated_at, deleted_at) VALUES
(920001, 920001, 920002, 'DBCASE_长夜行_草稿', '草稿态项目', 0, 1, NOW(3), NOW(3), NULL),
(920002, 920002, 920002, 'DBCASE_长夜行_连载', '连载态项目', 1, 1, NOW(3), NOW(3), NULL),
(920003, 920003, 920002, 'DBCASE_长夜行_完结', '完结态项目', 2, 1, NOW(3), NOW(3), NULL),
(920004, 920004, 920002, 'DBCASE_长夜行_归档', '归档态项目', 3, 1, NOW(3), NOW(3), NULL);

INSERT INTO novel_members (project_id, user_id, member_role, joined_at) VALUES
(920001, 920002, 'owner', NOW(3)),
(920001, 920001, 'admin', NOW(3)),
(920001, 920003, 'editor', NOW(3));

INSERT INTO novel_volumes (id, volume_id, project_id, title, sort_order, description, created_at, updated_at, deleted_at) VALUES
(920001, 920001, 920001, '第一卷 灰烬城', 1,  '主卷',     NOW(3), NOW(3), NULL),
(920002, 920002, 920001, '第二卷 星港',   2,  '扩展卷',   NOW(3), NOW(3), NULL),
(920003, 920003, 920001, '空卷样本',      99, '空卷测试', NOW(3), NOW(3), NULL);

INSERT INTO novel_outline_nodes (id, outline_node_id, project_id, parent_id, title, node_type, sort_order, content, created_at, updated_at, deleted_at) VALUES
(920001, 920001, 920001, NULL,   '主线大纲',  'root',    1,  '总纲',   NOW(3), NOW(3), NULL),
(920002, 920002, 920001, 920001, '第一幕',    'arc',    10, '幕内容', NOW(3), NOW(3), NULL),
(920003, 920003, 920001, 920002, '第一章节点', 'chapter', 11, '章节点', NOW(3), NOW(3), NULL),
(920004, 920004, 920001, 920002, '第二章节点', 'chapter', 12, '章节点', NOW(3), NOW(3), NULL),
(920005, 920005, 920001, 920002, '孤立章节节点', 'chapter', 99, '章节点', NOW(3), NOW(3), NULL);

INSERT INTO novel_chapters (id, chapter_id, project_id, volume_id, outline_node_id, title, sort_order, status, word_count, excerpt, content_object_key, content_etag, content_size, content_checksum, storage_provider, last_generated_at, created_at, updated_at, deleted_at) VALUES
(920001, 920001, 920001, 920003, 920003, '第1章 雾墙外',   1, 1, 3200, '章节一摘要', 'dbcase/projects/920001/chapters/920001/content.md', 'etag-ch-920001', 4096, 'sha256-ch-920001', 's3', NOW(3), NOW(3), NOW(3), NULL),
(920002, 920002, 920001, NULL,   920004, '第2章 失落哨站', 2, 2, 4100, '章节二摘要', 'dbcase/projects/920001/chapters/920002/content.md', 'etag-ch-920002', 5120, 'sha256-ch-920002', 's3', NOW(3), NOW(3), NOW(3), NULL),
(920003, 920003, 920001, NULL,   920005, '孤立章节样本',   99, 0, 0, NULL, 'dbcase/projects/920001/chapters/920003/content.md', 'etag-ch-920003', 1024, 'sha256-ch-920003', 's3', NULL, NOW(3), NOW(3), NULL);

INSERT INTO novel_chapter_versions (id, chapter_version_id, chapter_id, version_no, change_type, change_reason, snapshot_object_key, snapshot_etag, snapshot_size, snapshot_checksum, created_by, created_at) VALUES
(920001, 920001, 920001, 1, 'create',  '初稿',     'dbcase/projects/920001/chapters/920001/v1.json', 'etag-v-920001-1', 2048, 'sha256-v-920001-1', 920002, NOW(3)),
(920002, 920002, 920001, 2, 'rewrite', '重写优化', 'dbcase/projects/920001/chapters/920001/v2.json', 'etag-v-920001-2', 3072, 'sha256-v-920001-2', 920002, NOW(3)),
(920003, 920003, 920002, 1, 'create',  '初稿',     'dbcase/projects/920001/chapters/920002/v1.json', 'etag-v-920002-1', 2048, 'sha256-v-920002-1', 920002, NOW(3));

INSERT INTO story_bibles (id, story_bible_id, project_id, title, description, content_revision, created_at, updated_at, deleted_at) VALUES
(920001, 920001, 920001, '长夜行 Story Bible', 'Current canonical story context', 1, NOW(3), NOW(3), NULL);

INSERT INTO story_bible_node_types (id, type_id, story_bible_id, type_code, semantic_family, display_name, icon_code, field_schema_json, is_system, sort_order, created_at, updated_at, archived_at) VALUES
(920101, 920101, 920001, 'CHARACTER', 'CHARACTER', 'Character', 'user', JSON_OBJECT('type', 'object'), 1, 10, NOW(3), NOW(3), NULL),
(920102, 920102, 920001, 'LOCATION', 'WORLD', 'Location', 'map-pin', JSON_OBJECT('type', 'object'), 1, 20, NOW(3), NOW(3), NULL),
(920103, 920103, 920001, 'FACTION', 'WORLD', 'Faction', 'shield', JSON_OBJECT('type', 'object'), 1, 30, NOW(3), NOW(3), NULL),
(920104, 920104, 920001, 'ITEM', 'THING', 'Item', 'gem', JSON_OBJECT('type', 'object'), 1, 40, NOW(3), NOW(3), NULL);

INSERT INTO story_bible_nodes (id, node_id, story_bible_id, type_id, title, summary, body_markdown, attributes_json, inclusion_policy, canon_status, revision, created_by, updated_by, created_at, updated_at, archived_at, deleted_at) VALUES
(920201, 920201, 920001, 920101, 'Lin Jin', 'Protagonist', 'A survivor from Ash City.', JSON_OBJECT('age', 23), 'AUTO_RETRIEVE', 'CANON', 1, 920002, 920002, NOW(3), NOW(3), NULL, NULL),
(920202, 920202, 920001, 920102, 'Ash City', 'Primary location', 'A ruined northern city.', JSON_OBJECT('terrain', 'ruins'), 'ALWAYS_INCLUDE', 'CANON', 1, 920002, 920002, NOW(3), NOW(3), NULL, NULL),
(920203, 920203, 920001, 920103, 'Night Watch', 'Primary faction', NULL, JSON_OBJECT('rank', 'A'), 'AUTO_RETRIEVE', 'CANON', 1, 920002, 920002, NOW(3), NOW(3), NULL, NULL),
(920204, 920204, 920001, 920104, 'Rift Compass', 'Key item', NULL, JSON_OBJECT('rarity', 'legendary'), 'MANUAL_ONLY', 'DRAFT', 1, 920002, 920002, NOW(3), NOW(3), NULL, NULL);

INSERT INTO story_bible_aliases (id, alias_id, story_bible_id, node_id, alias, normalized_alias, created_at, deleted_at) VALUES
(920301, 920301, 920001, 920201, 'Lin', 'lin', NOW(3), NULL),
(920302, 920302, 920001, 920202, 'Ash', 'ash', NOW(3), NULL);

INSERT INTO story_bible_categories (id, category_id, story_bible_id, parent_category_id, name, sort_order, created_at, updated_at, deleted_at) VALUES
(920401, 920401, 920001, NULL, 'Main Cast', 10, NOW(3), NOW(3), NULL);

INSERT INTO story_bible_node_categories (id, story_bible_id, node_id, category_id, created_at) VALUES
(920401, 920001, 920201, 920401, NOW(3));

INSERT INTO story_bible_tags (id, tag_id, story_bible_id, name, normalized_name, color, created_at, updated_at, deleted_at) VALUES
(920501, 920501, 920001, 'core', 'core', '#2563EB', NOW(3), NOW(3), NULL);

INSERT INTO story_bible_node_tags (id, story_bible_id, node_id, tag_id, created_at) VALUES
(920501, 920001, 920201, 920501, NOW(3));

INSERT INTO story_bible_relations (id, relation_id, story_bible_id, source_node_id, relation_type, target_node_id, description, attributes_json, revision, created_by, updated_by, created_at, updated_at, deleted_at) VALUES
(920601, 920601, 920001, 920201, 'MEMBER_OF', 920203, 'Lin Jin belongs to the Night Watch.', JSON_OBJECT(), 1, 920002, 920002, NOW(3), NOW(3), NULL);

INSERT INTO story_bible_progressions (id, progression_id, story_bible_id, node_id, anchor_chapter_id, end_chapter_id, story_event_node_id, patch_json, summary, revision, created_by, updated_by, created_at, updated_at, deleted_at) VALUES
(920701, 920701, 920001, 920201, 920002, NULL, NULL, JSON_ARRAY(JSON_OBJECT('op', 'replace', 'path', '/attributes/age', 'value', 24)), 'Age after chapter two', 1, 920002, 920002, NOW(3), NOW(3), NULL);

INSERT INTO story_bible_view_preferences (id, story_bible_id, view_code, display_name, hidden, sort_order, updated_by, updated_at) VALUES
(920801, 920001, 'CORE', 'Story Core', 0, 10, 920002, NOW(3)),
(920802, 920001, 'CHARACTER', 'Characters', 0, 20, 920002, NOW(3)),
(920803, 920001, 'WORLD', 'World', 0, 30, 920002, NOW(3)),
(920804, 920001, 'THING', 'Things', 0, 40, 920002, NOW(3)),
(920805, 920001, 'NARRATIVE', 'Narrative', 0, 50, 920002, NOW(3)),
(920806, 920001, 'TIMELINE', 'Timeline', 0, 60, 920002, NOW(3));

INSERT INTO story_bible_changesets (id, changeset_id, story_bible_id, content_revision, actor_type, actor_id, source_run_id, change_summary, created_at) VALUES
(920901, 920901, 920001, 1, 'USER', 920002, NULL, 'Initial Story Bible baseline', NOW(3));

INSERT INTO story_bible_change_items (id, change_item_id, changeset_id, entity_type, entity_id, operation, field_path, before_json, after_json, created_at) VALUES
(920901, 920901, 920901, 'STORY_BIBLE', 920001, 'CREATE', '/', NULL, JSON_OBJECT('contentRevision', 1), NOW(3));

-- 文风
-- 基础 seed 仅保留项目级稳定文风档案；切换日志属于行为历史，由具体测试自行构造。
INSERT INTO style_profiles (id, style_id, project_id, name, is_default, pace, tone, narrative_focus, prompt_template, sample_text, created_at, updated_at, deleted_at) VALUES
(920001, 920001, 920001, '平稳叙事',    1, 'medium', 'neutral', 'character', '请保持平稳叙事', '默认文风样本',   NOW(3), NOW(3), NULL),
(920002, 920002, 920001, '高张力快节奏', 0, 'fast',   'dark',    'plot',      '请提升冲突密度', '快节奏文风样本', NOW(3), NOW(3), NULL);

-- 插件
INSERT INTO plugin_catalog (id, plugin_id, code, name, category, provider, status, latest_version, created_at, updated_at) VALUES
(920001, 920001, 'plot-expander',      '剧情扩写器', 'writing', 'penmate', 'active',   '1.3.0', NOW(3), NOW(3)),
(920002, 920002, 'character-polisher', '角色润色器', 'writing', 'penmate', 'inactive', '2.1.0', NOW(3), NOW(3));

INSERT INTO plugin_project_installs (id, plugin_install_id, project_id, plugin_id, version, config_json, enabled, installed_by, installed_at, updated_at) VALUES
(920001, 920001, 920001, 920001, '1.3.0', JSON_OBJECT('temperature', 0.7), 1, 920002, NOW(3), NOW(3)),
(920002, 920002, 920001, 920002, '2.1.0', JSON_OBJECT('strict_mode', TRUE), 0, 920001, NOW(3), NOW(3));

INSERT INTO plugin_call_logs (id, plugin_call_log_id, project_id, plugin_code, tool_name, request_json, response_json, latency_ms, status, error_msg, created_at) VALUES
(920001, 920001, 920001, 'plot-expander',      'expand_plot',      JSON_OBJECT('chapterId', 920001), JSON_OBJECT('ok', TRUE), 220, 'success', NULL, NOW(3)),
(920002, 920002, 920001, 'character-polisher', 'polish_character', JSON_OBJECT('nodeId', 920201),    NULL,                    560, 'failed',  'tool timeout', NOW(3));

-- Agent + 审批
-- 基础 seed 仅保留“可被恢复/可被继续创建 turn”的稳定主数据：session 与当前生效 style 绑定。
-- agent_turns / agent_messages / agent_tasks / agent_task_contexts / agent_task_results /
-- agent_pending_approvals / agent_approval_requests / agent_approval_actions 只在 cleanup 中参与，
-- 用于保证脚本可重复执行；这些运行时/断点/审批数据不在 baseline insert 中预置，必须由具体测试按场景单独造数。
INSERT INTO agent_user_preferences (id, user_id, story_bible_routing_mode, router_model_config_id, created_at, updated_at) VALUES
(920001, 920002, 'RETRIEVAL_THEN_LLM', 920021, NOW(3), NOW(3));

INSERT INTO agent_sessions (id, session_id, project_id, owner_user_id, title, session_status, bound_style_id, story_bible_routing_mode, router_model_config_id, active_context_epoch_id, last_turn_id, last_run_id, last_message_at, resumed_at, created_at, updated_at, deleted_at) VALUES
(920001, 920001, 920001, 920002, '第一卷创作会话', 'ACTIVE',   920001, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NOW(3), NOW(3), NULL),
(920002, 920002, 920001, 920001, '审批复核会话',   'ARCHIVED', 920002, 'RETRIEVAL', NULL, NULL, NULL, NULL, NULL, NULL, NOW(3), NOW(3), NULL);

INSERT INTO agent_session_style_bindings (id, binding_id, session_id, style_id, source, activated_at, deactivated_at, created_at) VALUES
(920001, 920001, 920001, 920001, 'PROJECT_DEFAULT', NOW(3), NULL, NOW(3)),
(920002, 920002, 920002, 920002, 'MANUAL_SWITCH',   NOW(3), NULL, NOW(3));

-- RAG + 对象存储
INSERT INTO rag_documents (id, document_id, project_id, doc_type, title, source_ref, origin_object_key, origin_etag, mime_type, parse_status, index_status, created_at, updated_at, deleted_at) VALUES
(920001, 920001, 920001, 'note',   '世界观设定集', 'wiki://world/ash-city', 'dbcase/rag/920001/origin.md', 'etag-rag-920001', 'text/markdown', 'done',   'indexed',  NOW(3), NOW(3), NULL),
(920002, 920002, 920001, 'manual', '战斗规则草案', 'upload://rulebook-v1',  'dbcase/rag/920002/origin.md', 'etag-rag-920002', 'text/plain',    'failed', 'pending',  NOW(3), NOW(3), NULL),
(920003, 920003, 920001, 'faq',    '角色FAQ',      'upload://faq-v1',       'dbcase/rag/920003/origin.md', 'etag-rag-920003', 'text/plain',    'done',   'indexing', NOW(3), NOW(3), NULL);

INSERT INTO rag_chunks (id, chunk_id, project_id, document_id, chunk_no, content_text, token_count, vector_id, vector_store, embedding_provider, embedding_model, embedding_dim, embedding_version, metadata_json, created_at) VALUES
(920001, 920001, 920001, 920001, 1, '灰烬城位于北境裂谷边缘。', 32, 'vec-920001-1', 'milvus', 'openai', 'text-embedding-3-small', 1536, 'v1', JSON_OBJECT('section', 'geo'), NOW(3)),
(920002, 920002, 920001, 920001, 2, '守夜人分为三阶。',         28, 'vec-920001-2', 'milvus', 'openai', 'text-embedding-3-small', 1536, 'v1', JSON_OBJECT('section', 'faction'), NOW(3)),
(920003, 920003, 920001, 920003, 1, '林烬常见问题。',            36, 'vec-920003-1', 'milvus', 'openai', 'text-embedding-3-small', 1536, 'v1', JSON_OBJECT('section', 'faq'), NOW(3));

INSERT INTO storage_objects (id, storage_object_id, object_key, bucket, provider, region, etag, size, storage_class, ref_type, ref_id, created_at) VALUES
(920001, 920001, 'dbcase/projects/920001/chapters/920001/content.md', 'penmate-test', 's3', 'ap-southeast-1', 'etag-ch-920001', 4096, 'STANDARD', 'novel_chapter', 920001, NOW(3)),
(920002, 920002, 'dbcase/projects/920001/chapters/920001/v2.json',    'penmate-test', 's3', 'ap-southeast-1', 'etag-v-920001-2', 3072, 'STANDARD', 'novel_chapter_version', 920002, NOW(3)),
(920003, 920003, 'dbcase/rag/920001/origin.md',                        'penmate-test', 's3', 'ap-southeast-1', 'etag-rag-920001', 10240, 'STANDARD_IA', 'rag_document', 920001, NOW(3));

-- 运维
INSERT INTO ops_async_jobs (id, job_id, job_type, biz_key, status, error_msg, started_at, finished_at, created_at, updated_at) VALUES
(920001, 920001, 'rag-embed',   'rag:doc:920001',         'done',    NULL,              NOW(3), NOW(3), NOW(3), NOW(3)),
(920002, 920002, 'plugin-sync', 'plugin:project:920001', 'failed',  'network timeout', NOW(3), NOW(3), NOW(3), NOW(3)),
(920003, 920003, 'migration',   'novel:920001',          'running', NULL,              NOW(3), NULL,   NOW(3), NOW(3));

INSERT INTO ops_migrations (id, migration_id, migration_type, status, progress_pct, summary_json, error_msg, started_at, finished_at, created_at, updated_at) VALUES
(920001, 920001, 'content_to_object_storage', 'running', 45,  JSON_OBJECT('migrated', 12, 'failed', 1), NULL,              NOW(3), NULL,   NOW(3), NOW(3)),
(920002, 920002, 'content_to_object_storage', 'done',    100, JSON_OBJECT('migrated', 23, 'failed', 0), NULL,              NOW(3), NOW(3), NOW(3), NOW(3)),
(920003, 920003, 'vector_rebuild',            'failed',  73,  JSON_OBJECT('processed', 88),             'index corruption', NOW(3), NOW(3), NOW(3), NOW(3));

-- 模型域
INSERT INTO model_official_api_keys (id, official_api_key_id, provider_id, key_name, encrypted_api_key, masked_api_key, is_default, last_used_at, status, created_at, updated_at, deleted_at) VALUES
(920001, 920001, 1, 'DBCASE 官方 OpenAI Key',   'cipher-official-openai-920001',   '****openai',   1, NOW(3), 'active',   NOW(3), NOW(3), NULL),
(920002, 920002, 2, 'DBCASE 官方 DeepSeek Key', 'cipher-official-deepseek-920002', '****deepseek', 1, NOW(3), 'active',   NOW(3), NOW(3), NULL),
(920003, 920003, 3, 'DBCASE 官方 Anthropic Key','cipher-official-claude-920003',   '****claude',   0, NULL,   'disabled', NOW(3), NOW(3), NULL);

INSERT INTO model_user_api_keys (id, user_api_key_id, user_id, provider_id, key_name, encrypted_api_key, masked_api_key, is_default, last_used_at, status, created_at, updated_at, deleted_at) VALUES
(920001, 920011, 920002, 1, 'DBCASE Owner OpenAI 主 Key',  'cipher-user-openai-920011',  '****92011', 1, NOW(3), 'active',   NOW(3), NOW(3), NULL),
(920002, 920012, 920002, 2, 'DBCASE Owner DeepSeek Key',   'cipher-user-deepseek-920012','****92012', 0, NOW(3), 'active',   NOW(3), NOW(3), NULL),
(920003, 920013, 920001, 1, 'DBCASE Admin OpenAI Key',     'cipher-user-openai-920013',  '****92013', 0, NULL,   'disabled', NOW(3), NOW(3), NULL),
(920004, 920014, 920002, 7, 'DBCASE Owner OpenAI-Compatible Key', 'cipher-user-openai-compatible-920014', '****92014', 0, NOW(3), 'active', NOW(3), NOW(3), NULL);

INSERT INTO model_user_configurations (
    id, model_config_id, user_id, provider_id, model_name, base_url,
    key_source_type, user_key_id, official_key_id, context_window_turns,
    status, created_at, updated_at, deleted_at
) VALUES
(920001, 920021, 920002, 1, 'gpt-4o-mini',              NULL,                             'USER_KEY',     920011, NULL,   6, 'active',   NOW(3), NOW(3), NULL),
(920002, 920022, 920002, 2, 'deepseek-chat',            'https://api.deepseek.com',      'USER_KEY',     920012, NULL,   4, 'active',   NOW(3), NOW(3), NULL),
(920003, 920023, 920002, 1, 'gpt-4.1',                  NULL,                             'OFFICIAL_KEY', NULL,   920001, 8, 'active',   NOW(3), NOW(3), NULL),
(920004, 920024, 920001, 1, 'gpt-4o-mini',              NULL,                             'USER_KEY',     920013, NULL,   0, 'disabled', NOW(3), NOW(3), NULL),
(920005, 920025, 920002, 7, 'openai-compatible-chat',   'https://example.internal/openai', 'USER_KEY',   920014, NULL,   6, 'active',   NOW(3), NOW(3), NULL);

UPDATE iam_users
SET main_agent_model_config_id = CASE user_id
        WHEN 920001 THEN 920024
        WHEN 920002 THEN 920021
        ELSE main_agent_model_config_id
    END,
    dirty_work_agent_model_config_id = CASE user_id
        WHEN 920001 THEN NULL
        WHEN 920002 THEN 920023
        ELSE dirty_work_agent_model_config_id
    END,
    updated_at = NOW(3)
WHERE user_id IN (920001, 920002);


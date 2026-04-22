-- PenMate 数据库造数：全域主路径基础集（对齐当前Flyway表）
-- Case: DBCASE_ALL_001_MAIN

SET NAMES utf8mb4;

-- cleanup: child -> parent
DELETE FROM agent_approval_actions  WHERE id BETWEEN 920001 AND 920999;
DELETE FROM agent_approval_requests WHERE id BETWEEN 920001 AND 920999;
DELETE FROM agent_generation_tasks  WHERE id BETWEEN 920001 AND 920999;
DELETE FROM agent_messages          WHERE id BETWEEN 920001 AND 920999;
DELETE FROM agent_conversations     WHERE id BETWEEN 920001 AND 920999;

DELETE FROM rag_chunks              WHERE id BETWEEN 920001 AND 920999;
DELETE FROM rag_documents           WHERE id BETWEEN 920001 AND 920999;
DELETE FROM storage_objects         WHERE id BETWEEN 920001 AND 920999;

DELETE FROM plugin_call_logs        WHERE id BETWEEN 920001 AND 920999;
DELETE FROM plugin_project_installs WHERE id BETWEEN 920001 AND 920999;
DELETE FROM plugin_catalog          WHERE id BETWEEN 920001 AND 920999;

DELETE FROM style_switch_logs       WHERE id BETWEEN 920001 AND 920999;
DELETE FROM style_profiles          WHERE id BETWEEN 920001 AND 920999;

DELETE FROM novel_card_relations    WHERE id BETWEEN 920001 AND 920999;
DELETE FROM novel_cards             WHERE id BETWEEN 920001 AND 920999;
DELETE FROM novel_outline_nodes     WHERE id BETWEEN 920001 AND 920999;
DELETE FROM novel_chapter_versions  WHERE id BETWEEN 920001 AND 920999;
DELETE FROM novel_chapters          WHERE id BETWEEN 920001 AND 920999;
DELETE FROM novel_volumes           WHERE id BETWEEN 920001 AND 920999;
DELETE FROM novel_members           WHERE project_id BETWEEN 920001 AND 920999;
DELETE FROM novel_projects          WHERE id BETWEEN 920001 AND 920999;

DELETE FROM iam_menus               WHERE id BETWEEN 920001 AND 920999;
DELETE FROM iam_role_permissions    WHERE role_id BETWEEN 920001 AND 920999;
DELETE FROM iam_user_roles          WHERE user_id BETWEEN 920001 AND 920999;
DELETE FROM iam_permissions         WHERE id BETWEEN 920001 AND 920999;
DELETE FROM iam_roles               WHERE id BETWEEN 920001 AND 920999;
DELETE FROM iam_users               WHERE id BETWEEN 920001 AND 920999;

DELETE FROM ops_async_jobs          WHERE id BETWEEN 920001 AND 920999;
DELETE FROM ops_migrations          WHERE id BETWEEN 920001 AND 920999;

-- IAM
INSERT INTO iam_users (id, email, password_hash, display_name, status, auth_method, last_login_at, created_at, updated_at, deleted_at) VALUES
(920001, 'dbcase_admin@penmate.local', '$2a$10$admin',  'DBCASE Admin', 1, 'local', NOW(3), NOW(3), NOW(3), NULL),
(920002, 'dbcase_owner@penmate.local', '$2a$10$owner',  'DBCASE Owner', 1, 'local', NOW(3), NOW(3), NOW(3), NULL),
(920003, 'dbcase_editor@penmate.local','$2a$10$editor', 'DBCASE Editor',0, 'local', NULL,   NOW(3), NOW(3), NULL),
(920004, 'dbcase_frozen@penmate.local','$2a$10$frozen', 'DBCASE Frozen',2, 'sso',   NULL,   NOW(3), NOW(3), NULL);

INSERT INTO iam_roles (id, name, code, description, is_system, created_at, updated_at, deleted_at) VALUES
(920001, '平台管理员', 'platform_admin', '全权限', 1, NOW(3), NOW(3), NULL),
(920002, '项目作者',   'project_owner',  '项目写作', 0, NOW(3), NOW(3), NULL),
(920003, '审批审阅者', 'approval_reviewer', '审批处理', 0, NOW(3), NOW(3), NULL);

INSERT INTO iam_permissions (id, name, code, module, description, created_at) VALUES
(920001, '项目管理', 'novel.project.manage', 'novel', '项目与卷章管理', NOW(3)),
(920002, '审批处理', 'approval.request.review', 'approval', '审批请求处理', NOW(3)),
(920003, '插件调用', 'plugin.call.execute', 'plugin', '插件工具调用', NOW(3)),
(920004, '模型策略', 'model.policy.manage', 'model', '模型策略维护', NOW(3));

INSERT INTO iam_user_roles (user_id, role_id, created_at) VALUES
(920001, 920001, NOW(3)),
(920002, 920002, NOW(3)),
(920003, 920003, NOW(3));

INSERT INTO iam_role_permissions (role_id, permission_id, created_at) VALUES
(920001, 920001, NOW(3)), (920001, 920002, NOW(3)), (920001, 920003, NOW(3)), (920001, 920004, NOW(3)),
(920002, 920001, NOW(3)),
(920003, 920002, NOW(3));

INSERT INTO iam_menus (id, parent_id, title, path, sort_order, permission_code, visible, created_at, updated_at, deleted_at) VALUES
(920001, NULL,   '工作台',   '/workbench',                  1,  NULL,                      1, NOW(3), NOW(3), NULL),
(920002, 920001, '小说管理', '/workbench/novel',           10, 'novel.project.manage',    1, NOW(3), NOW(3), NULL),
(920003, 920001, '审批中心', '/workbench/approval',        20, 'approval.request.review', 1, NOW(3), NOW(3), NULL),
(920004, 920002, '章节编辑', '/workbench/novel/chapters', 11, 'novel.project.manage',    1, NOW(3), NOW(3), NULL);

-- 小说核心
INSERT INTO novel_projects (id, owner_user_id, title, summary, status, created_at, updated_at, deleted_at) VALUES
(920001, 920002, 'DBCASE_长夜行_草稿', '草稿态项目', 0, NOW(3), NOW(3), NULL),
(920002, 920002, 'DBCASE_长夜行_连载', '连载态项目', 1, NOW(3), NOW(3), NULL),
(920003, 920002, 'DBCASE_长夜行_完结', '完结态项目', 2, NOW(3), NOW(3), NULL),
(920004, 920002, 'DBCASE_长夜行_归档', '归档态项目', 3, NOW(3), NOW(3), NULL);

INSERT INTO novel_members (project_id, user_id, member_role, joined_at) VALUES
(920001, 920002, 'owner', NOW(3)),
(920001, 920001, 'admin', NOW(3)),
(920001, 920003, 'editor', NOW(3));

INSERT INTO novel_volumes (id, project_id, title, sort_order, description, created_at, updated_at, deleted_at) VALUES
(920001, 920001, '第一卷 灰烬城', 1,  '主卷',     NOW(3), NOW(3), NULL),
(920002, 920001, '第二卷 星港',   2,  '扩展卷',   NOW(3), NOW(3), NULL),
(920003, 920001, '空卷样本',      99, '空卷测试', NOW(3), NOW(3), NULL);

INSERT INTO novel_outline_nodes (id, project_id, parent_id, title, node_type, sort_order, content, created_at, updated_at, deleted_at) VALUES
(920001, 920001, NULL,   '主线大纲',  'root',    1,  '总纲',   NOW(3), NOW(3), NULL),
(920002, 920001, 920001, '第一幕',    'arc',    10, '幕内容', NOW(3), NOW(3), NULL),
(920003, 920001, 920002, '第一章节点', 'chapter', 11, '章节点', NOW(3), NOW(3), NULL);

INSERT INTO novel_chapters (id, project_id, volume_id, outline_node_id, title, chapter_no, status, word_count, excerpt, content_object_key, content_etag, content_size, content_checksum, storage_provider, last_generated_at, created_at, updated_at, deleted_at) VALUES
(920001, 920001, 920001, 920003, '第1章 雾墙外',   1, 1, 3200, '章节一摘要', 'dbcase/projects/920001/chapters/920001/content.md', 'etag-ch-920001', 4096, 'sha256-ch-920001', 's3', NOW(3), NOW(3), NOW(3), NULL),
(920002, 920001, 920001, NULL,   '第2章 失落哨站', 2, 2, 4100, '章节二摘要', 'dbcase/projects/920001/chapters/920002/content.md', 'etag-ch-920002', 5120, 'sha256-ch-920002', 's3', NOW(3), NOW(3), NOW(3), NULL),
(920003, 920001, NULL,   NULL,   '孤立章节样本',   99, 0, 0, NULL, 'dbcase/projects/920001/chapters/920003/content.md', 'etag-ch-920003', 1024, 'sha256-ch-920003', 's3', NULL, NOW(3), NOW(3), NULL);

INSERT INTO novel_chapter_versions (id, chapter_id, version_no, change_type, change_reason, snapshot_object_key, snapshot_etag, snapshot_size, snapshot_checksum, created_by, created_at) VALUES
(920001, 920001, 1, 'create',  '初稿',     'dbcase/projects/920001/chapters/920001/v1.json', 'etag-v-920001-1', 2048, 'sha256-v-920001-1', 920002, NOW(3)),
(920002, 920001, 2, 'rewrite', '重写优化', 'dbcase/projects/920001/chapters/920001/v2.json', 'etag-v-920001-2', 3072, 'sha256-v-920001-2', 920002, NOW(3)),
(920003, 920002, 1, 'create',  '初稿',     'dbcase/projects/920001/chapters/920002/v1.json', 'etag-v-920002-1', 2048, 'sha256-v-920002-1', 920002, NOW(3));

INSERT INTO novel_cards (id, project_id, card_type, name, summary, detail_json, created_at, updated_at, deleted_at) VALUES
(920001, 920001, 'character', '林烬',     '主角',     JSON_OBJECT('age', 23, 'camp', '灰烬城'), NOW(3), NOW(3), NULL),
(920002, 920001, 'world',     '灰烬城',   '核心地点', JSON_OBJECT('terrain', '废都'),             NOW(3), NOW(3), NULL),
(920003, 920001, 'faction',   '守夜人',   '组织',     JSON_OBJECT('rank', 'A'),                  NOW(3), NOW(3), NULL),
(920004, 920001, 'item',      '裂隙罗盘', '关键道具', JSON_OBJECT('rarity', 'legendary'),        NOW(3), NOW(3), NULL);

INSERT INTO novel_card_relations (id, project_id, from_card_id, to_card_id, relation_type, description, created_at, deleted_at) VALUES
(920001, 920001, 920001, 920002, 'belongs_to', '角色属于地点', NOW(3), NULL),
(920002, 920001, 920001, 920003, 'member_of',  '角色属于派系', NOW(3), NULL),
(920003, 920001, 920001, 920004, 'owns',       '角色持有道具', NOW(3), NULL);

-- 文风
INSERT INTO style_profiles (id, project_id, name, is_default, pace, tone, narrative_focus, prompt_template, sample_text, created_at, updated_at, deleted_at) VALUES
(920001, 920001, '平稳叙事',    1, 'medium', 'neutral', 'character', '请保持平稳叙事', '默认文风样本',   NOW(3), NOW(3), NULL),
(920002, 920001, '高张力快节奏', 0, 'fast',   'dark',    'plot',      '请提升冲突密度', '快节奏文风样本', NOW(3), NOW(3), NULL);

INSERT INTO style_switch_logs (id, project_id, from_style_id, to_style_id, switched_by, warning_confirmed, reason, created_at) VALUES
(920001, 920001, 920001, 920002, 920002, 1, '切换到战斗场景文风', NOW(3)),
(920002, 920001, 920002, 920001, 920002, 0, '恢复默认文风', NOW(3));

-- 插件
INSERT INTO plugin_catalog (id, code, name, category, provider, status, latest_version, created_at, updated_at) VALUES
(920001, 'plot-expander',      '剧情扩写器', 'writing', 'penmate', 'active',   '1.3.0', NOW(3), NOW(3)),
(920002, 'character-polisher', '角色润色器', 'writing', 'penmate', 'inactive', '2.1.0', NOW(3), NOW(3));

INSERT INTO plugin_project_installs (id, project_id, plugin_id, version, config_json, enabled, installed_by, installed_at, updated_at) VALUES
(920001, 920001, 920001, '1.3.0', JSON_OBJECT('temperature', 0.7), 1, 920002, NOW(3), NOW(3)),
(920002, 920001, 920002, '2.1.0', JSON_OBJECT('strict_mode', TRUE), 0, 920001, NOW(3), NOW(3));

INSERT INTO plugin_call_logs (id, project_id, plugin_code, tool_name, request_json, response_json, latency_ms, status, error_msg, created_at) VALUES
(920001, 920001, 'plot-expander',      'expand_plot',      JSON_OBJECT('chapterId', 920001), JSON_OBJECT('ok', TRUE), 220, 'success', NULL, NOW(3)),
(920002, 920001, 'character-polisher', 'polish_character', JSON_OBJECT('cardId', 920001),    NULL,                    560, 'failed',  'tool timeout', NOW(3));

-- Agent + 审批
INSERT INTO agent_conversations (id, project_id, user_id, title, context_scope_json, last_message_at, status, created_at, updated_at, deleted_at) VALUES
(920001, 920001, 920002, '第一卷创作会话', JSON_OBJECT('chapterIds', JSON_ARRAY(920001, 920002)), NOW(3), 'active',   NOW(3), NOW(3), NULL),
(920002, 920001, 920001, '审批复核会话',   JSON_OBJECT('mode', 'approval'), NOW(3), 'archived', NOW(3), NOW(3), NULL);

INSERT INTO agent_messages (id, conversation_id, role, user_message_type, content_md, attachments_json, tool_calls_json, seq_no, created_at) VALUES
(920001, 920001, 'system', NULL,     '你是创作助手。',     NULL, NULL,                                             1, NOW(3)),
(920002, 920001, 'user',   'prompt', '请扩写第1章冲突。',   JSON_ARRAY(), NULL,                                    2, NOW(3)),
(920003, 920001, 'assistant', NULL,  '已生成扩写草稿。',   NULL, JSON_ARRAY(JSON_OBJECT('tool', 'expand_plot')), 3, NOW(3)),
(920004, 920001, 'tool',   NULL,     '插件调用完成。',     NULL, NULL,                                             4, NOW(3));

INSERT INTO agent_generation_tasks (id, project_id, conversation_id, chapter_id, task_type, prompt_snapshot, style_profile_snapshot, plugin_snapshot, status, started_at, finished_at, error_msg, created_at) VALUES
(920001, 920001, 920001, 920001, 'draft',     JSON_OBJECT('prompt', 'draft chapter 1'),     JSON_OBJECT('styleId', 920001), JSON_ARRAY('plot-expander'),      'done',    NOW(3), NOW(3), NULL,               NOW(3)),
(920002, 920001, 920001, 920001, 'rewrite',   JSON_OBJECT('prompt', 'rewrite chapter 1'),   JSON_OBJECT('styleId', 920002), JSON_ARRAY('character-polisher'), 'failed',  NOW(3), NOW(3), 'provider timeout', NOW(3)),
(920003, 920001, 920001, 920002, 'expand',    JSON_OBJECT('prompt', 'expand chapter 2'),    JSON_OBJECT('styleId', 920001), JSON_ARRAY('plot-expander'),      'running', NOW(3), NULL,    NULL,              NOW(3)),
(920004, 920001, 920001, 920002, 'summarize', JSON_OBJECT('prompt', 'summarize chapter 2'), JSON_OBJECT('styleId', 920001), JSON_ARRAY(),                      'pending', NULL,   NULL,    NULL,              NOW(3));

INSERT INTO agent_approval_requests (id, project_id, task_id, approval_type, payload_json, risk_level, status, requested_by, reviewed_by, reviewed_at, review_comment, created_at, updated_at) VALUES
(920001, 920001, 920001, 'publish', JSON_OBJECT('chapterId', 920001), 2, 'approved', 920002, 920001, NOW(3), '可发布', NOW(3), NOW(3)),
(920002, 920001, 920002, 'rewrite', JSON_OBJECT('chapterId', 920001), 3, 'rejected', 920002, 920001, NOW(3), '需补充动机描写', NOW(3), NOW(3)),
(920003, 920001, 920003, 'expand',  JSON_OBJECT('chapterId', 920002), 1, 'pending',  920002, NULL, NULL, NULL, NOW(3), NOW(3));

INSERT INTO agent_approval_actions (id, request_id, action, operator_id, comment, created_at) VALUES
(920001, 920001, 'approve', 920001, '通过', NOW(3)),
(920002, 920002, 'reject',  920001, '驳回', NOW(3));

-- RAG + 对象存储
INSERT INTO rag_documents (id, project_id, doc_type, title, source_ref, origin_object_key, origin_etag, mime_type, parse_status, index_status, created_at, updated_at, deleted_at) VALUES
(920001, 920001, 'note',   '世界观设定集', 'wiki://world/ash-city', 'dbcase/rag/920001/origin.md', 'etag-rag-920001', 'text/markdown', 'done',   'indexed',  NOW(3), NOW(3), NULL),
(920002, 920001, 'manual', '战斗规则草案', 'upload://rulebook-v1',  'dbcase/rag/920002/origin.md', 'etag-rag-920002', 'text/plain',    'failed', 'pending',  NOW(3), NOW(3), NULL),
(920003, 920001, 'faq',    '角色FAQ',      'upload://faq-v1',       'dbcase/rag/920003/origin.md', 'etag-rag-920003', 'text/plain',    'done',   'indexing', NOW(3), NOW(3), NULL);

INSERT INTO rag_chunks (id, project_id, document_id, chunk_no, content_text, token_count, vector_id, vector_store, embedding_provider, embedding_model, embedding_dim, embedding_version, metadata_json, created_at) VALUES
(920001, 920001, 920001, 1, '灰烬城位于北境裂谷边缘。', 32, 'vec-920001-1', 'milvus', 'openai', 'text-embedding-3-small', 1536, 'v1', JSON_OBJECT('section', 'geo'), NOW(3)),
(920002, 920001, 920001, 2, '守夜人分为三阶。',         28, 'vec-920001-2', 'milvus', 'openai', 'text-embedding-3-small', 1536, 'v1', JSON_OBJECT('section', 'faction'), NOW(3)),
(920003, 920001, 920003, 1, '林烬常见问题。',            36, 'vec-920003-1', 'milvus', 'openai', 'text-embedding-3-small', 1536, 'v1', JSON_OBJECT('section', 'faq'), NOW(3));

INSERT INTO storage_objects (id, object_key, bucket, provider, region, etag, size, storage_class, ref_type, ref_id, created_at) VALUES
(920001, 'dbcase/projects/920001/chapters/920001/content.md', 'penmate-test', 's3', 'ap-southeast-1', 'etag-ch-920001', 4096, 'STANDARD', 'novel_chapter', 920001, NOW(3)),
(920002, 'dbcase/projects/920001/chapters/920001/v2.json',    'penmate-test', 's3', 'ap-southeast-1', 'etag-v-920001-2', 3072, 'STANDARD', 'novel_chapter_version', 920002, NOW(3)),
(920003, 'dbcase/rag/920001/origin.md',                        'penmate-test', 's3', 'ap-southeast-1', 'etag-rag-920001', 10240, 'STANDARD_IA', 'rag_document', 920001, NOW(3));

-- 运维
INSERT INTO ops_async_jobs (id, job_type, biz_key, status, error_msg, started_at, finished_at, created_at, updated_at) VALUES
(920001, 'rag-embed',   'rag:doc:920001',         'done',    NULL,              NOW(3), NOW(3), NOW(3), NOW(3)),
(920002, 'plugin-sync', 'plugin:project:920001', 'failed',  'network timeout', NOW(3), NOW(3), NOW(3), NOW(3)),
(920003, 'migration',   'novel:920001',          'running', NULL,              NOW(3), NULL,   NOW(3), NOW(3));

INSERT INTO ops_migrations (id, migration_type, status, progress_pct, summary_json, error_msg, started_at, finished_at, created_at, updated_at) VALUES
(920001, 'content_to_object_storage', 'running', 45,  JSON_OBJECT('migrated', 12, 'failed', 1), NULL,              NOW(3), NULL,   NOW(3), NOW(3)),
(920002, 'content_to_object_storage', 'done',    100, JSON_OBJECT('migrated', 23, 'failed', 0), NULL,              NOW(3), NOW(3), NOW(3), NOW(3)),
(920003, 'vector_rebuild',            'failed',  73,  JSON_OBJECT('processed', 88),             'index corruption', NOW(3), NOW(3), NOW(3), NOW(3));


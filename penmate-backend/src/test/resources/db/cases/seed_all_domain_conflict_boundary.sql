-- PenMate 数据库造数：冲突/边界/异常集
-- Case: DBCASE_ALL_002_CONFLICT_BOUNDARY
-- 说明：
-- 1) 本文件先写入“可执行的边界样本”
-- 2) 再给出“应失败SQL模板”（默认注释，按单测按需解开）

SET NAMES utf8mb4;

-- =========================================================
-- A. 可执行边界样本（不会破坏执行）
-- =========================================================

-- IAM：极限长度、禁用/冻结状态
INSERT INTO iam_users (id, email, password_hash, display_name, status, auth_method, last_login_at, created_at, updated_at, deleted_at) VALUES
(921001, 'dbcase_boundary_user1@penmate.local', '$2a$10$boundary', RPAD('U', 80, 'U'), 0, 'local', NULL, NOW(3), NOW(3), NULL),
(921002, 'dbcase_boundary_user2@penmate.local', '$2a$10$boundary', '冻结用户', 2, 'sso', NULL, NOW(3), NOW(3), NULL)
ON DUPLICATE KEY UPDATE updated_at = VALUES(updated_at);

-- Novel：标题长度上限、excerpt为空、孤立章节
INSERT INTO novel_projects (id, owner_user_id, title, summary, status, created_at, updated_at, deleted_at) VALUES
(921001, 920002, RPAD('边界项目', 200, '测'), NULL, 0, NOW(3), NOW(3), NULL)
ON DUPLICATE KEY UPDATE updated_at = VALUES(updated_at);

INSERT INTO novel_chapters (id, project_id, volume_id, outline_node_id, title, chapter_no, status, word_count, excerpt, content_object_key, content_etag, content_size, content_checksum, storage_provider, last_generated_at, created_at, updated_at, deleted_at) VALUES
(921001, 921001, NULL, NULL, RPAD('边界章节', 200, '章'), 1, 0, 0, NULL, 'dbcase/boundary/chapter/921001.md', NULL, 0, NULL, 's3', NULL, NOW(3), NOW(3), NULL)
ON DUPLICATE KEY UPDATE updated_at = VALUES(updated_at);

-- Style：默认与非默认并存
INSERT INTO style_profiles (id, project_id, name, is_default, pace, tone, narrative_focus, prompt_template, sample_text, created_at, updated_at, deleted_at) VALUES
(921001, 921001, '边界默认文风', 1, 'slow', 'cold', 'world', '保持克制叙述', '', NOW(3), NOW(3), NULL),
(921002, 921001, '边界实验文风', 0, 'fast', 'chaotic', 'plot', '允许高密度短句', NULL, NOW(3), NOW(3), NULL)
ON DUPLICATE KEY UPDATE updated_at = VALUES(updated_at);

-- Plugin：禁用安装与失败调用
INSERT INTO plugin_catalog (id, code, name, category, provider, status, latest_version, created_at, updated_at) VALUES
(921001, 'boundary-plugin', '边界插件', 'test', 'penmate', 'inactive', '0.0.1', NOW(3), NOW(3))
ON DUPLICATE KEY UPDATE updated_at = VALUES(updated_at);

INSERT INTO plugin_project_installs (id, project_id, plugin_id, version, config_json, enabled, installed_by, installed_at, updated_at) VALUES
(921001, 921001, 921001, '0.0.1', JSON_OBJECT('mode', 'safe'), 0, 920001, NOW(3), NOW(3))
ON DUPLICATE KEY UPDATE updated_at = VALUES(updated_at);

INSERT INTO plugin_call_logs (id, project_id, plugin_code, tool_name, request_json, response_json, latency_ms, status, error_msg, created_at) VALUES
(921001, 921001, 'boundary-plugin', 'dry_run', JSON_OBJECT('input', 'x'), NULL, 9999, 'failed', RPAD('timeout', 120, 'x'), NOW(3))
ON DUPLICATE KEY UPDATE created_at = VALUES(created_at);

-- Agent/Approval：pending + 重复动作候选
INSERT INTO agent_conversations (id, project_id, user_id, title, context_scope_json, last_message_at, status, created_at, updated_at, deleted_at) VALUES
(921001, 921001, 920002, '边界会话', JSON_OBJECT('scope', 'minimal'), NOW(3), 'active', NOW(3), NOW(3), NULL)
ON DUPLICATE KEY UPDATE updated_at = VALUES(updated_at);

INSERT INTO agent_generation_tasks (id, project_id, conversation_id, chapter_id, task_type, prompt_snapshot, style_profile_snapshot, plugin_snapshot, status, started_at, finished_at, error_msg, created_at) VALUES
(921001, 921001, 921001, 921001, 'rewrite', JSON_OBJECT('prompt', ''), JSON_OBJECT('styleId', 921001), JSON_ARRAY('boundary-plugin'), 'failed', NOW(3), NOW(3), 'empty prompt', NOW(3))
ON DUPLICATE KEY UPDATE created_at = VALUES(created_at);

INSERT INTO agent_approval_requests (id, project_id, task_id, approval_type, payload_json, risk_level, status, requested_by, reviewed_by, reviewed_at, review_comment, created_at, updated_at) VALUES
(921001, 921001, 921001, 'rewrite', JSON_OBJECT('chapterId', 921001), 3, 'pending', 920002, NULL, NULL, NULL, NOW(3), NOW(3))
ON DUPLICATE KEY UPDATE updated_at = VALUES(updated_at);

-- RAG：parse/index失败态
INSERT INTO rag_documents (id, project_id, doc_type, title, source_ref, origin_object_key, origin_etag, mime_type, parse_status, index_status, created_at, updated_at, deleted_at) VALUES
(921001, 921001, 'manual', '边界文档', 'upload://boundary', 'dbcase/rag/921001/origin.txt', NULL, 'text/plain', 'failed', 'pending', NOW(3), NOW(3), NULL)
ON DUPLICATE KEY UPDATE updated_at = VALUES(updated_at);

INSERT INTO rag_chunks (id, project_id, document_id, chunk_no, content_text, token_count, vector_id, vector_store, embedding_provider, embedding_model, embedding_dim, embedding_version, metadata_json, created_at) VALUES
(921001, 921001, 921001, 1, 'x', 1, 'vec-921001-1', 'milvus', 'openai', 'text-embedding-3-small', 1536, 'v1', JSON_OBJECT('boundary', true), NOW(3))
ON DUPLICATE KEY UPDATE created_at = VALUES(created_at);

-- Ops：失败与重试前态
INSERT INTO ops_async_jobs (id, job_type, biz_key, status, error_msg, started_at, finished_at, created_at, updated_at) VALUES
(921001, 'migration', 'novel:921001', 'failed', 'conflict detected', NOW(3), NOW(3), NOW(3), NOW(3))
ON DUPLICATE KEY UPDATE updated_at = VALUES(updated_at);

INSERT INTO ops_migrations (id, migration_type, status, progress_pct, summary_json, error_msg, started_at, finished_at, created_at, updated_at) VALUES
(921001, 'content_to_object_storage', 'running', 1, JSON_OBJECT('migrated', 0, 'failed', 0), NULL, NOW(3), NULL, NOW(3), NOW(3))
ON DUPLICATE KEY UPDATE updated_at = VALUES(updated_at);

-- =========================================================
-- B. 冲突/非法样本模板（默认注释，按单测逐条启用）
-- =========================================================

-- CASE-CONFLICT-001: 邮箱唯一键冲突（iam_users.uk_iam_users_email）
-- INSERT INTO iam_users (id,email,password_hash,display_name,status,auth_method,created_at,updated_at)
-- VALUES (921101,'dbcase_admin@penmate.local','$2a$10$x','dup-email',1,'local',NOW(3),NOW(3));

-- CASE-CONFLICT-002: 章节版本唯一键冲突（novel_chapter_versions.uk_chapter_version_no）
-- INSERT INTO novel_chapter_versions (id,chapter_id,version_no,change_type,snapshot_object_key,created_by,created_at)
-- VALUES (921102,920001,2,'rewrite','dup-version',920002,NOW(3));

-- CASE-CONFLICT-003: 插件安装唯一键冲突（plugin_project_installs.uk_project_plugin）
-- INSERT INTO plugin_project_installs (id,project_id,plugin_id,version,enabled,installed_by,installed_at,updated_at)
-- VALUES (921103,920001,920001,'1.3.0',1,920002,NOW(3),NOW(3));

-- CASE-CONFLICT-004: 项目成员重复添加冲突（novel_members PK(project_id,user_id)）
-- INSERT INTO novel_members (project_id,user_id,member_role,joined_at)
-- VALUES (920001,920002,'owner',NOW(3));

-- CASE-CONFLICT-005: Rag向量唯一冲突（rag_chunks.uk_chunks_vector）
-- INSERT INTO rag_chunks (id,project_id,document_id,chunk_no,content_text,token_count,vector_id,vector_store,embedding_provider,embedding_model,embedding_dim,embedding_version,created_at)
-- VALUES (921104,921001,921001,2,'dup vector',2,'vec-921001-1','milvus','openai','text-embedding-3-small',1536,'v1',NOW(3));

-- CASE-BOUNDARY-ILLEGAL-001: 非法状态注入（应用层应拒绝）
-- INSERT INTO agent_approval_requests (id,project_id,task_id,approval_type,payload_json,risk_level,status,requested_by,created_at,updated_at)
-- VALUES (921105,921001,921001,'rewrite',JSON_OBJECT('chapterId',921001),2,'UNKNOWN_STATUS',920002,NOW(3),NOW(3));


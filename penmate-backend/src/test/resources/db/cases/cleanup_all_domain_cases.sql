-- PenMate 数据库造数：统一清理脚本
-- 清理范围：DBCASE_ALL_001 / 002 / 003（ID段 920001~922999）

SET NAMES utf8mb4;

DELETE FROM agent_approval_actions  WHERE approval_action_id BETWEEN 920001 AND 922999;
DELETE FROM agent_approval_requests WHERE approval_request_id BETWEEN 920001 AND 922999;
DELETE FROM agent_generation_tasks  WHERE task_id BETWEEN 920001 AND 922999;
DELETE FROM agent_messages          WHERE message_id BETWEEN 920001 AND 922999;
DELETE FROM agent_conversations     WHERE conversation_id BETWEEN 920001 AND 922999;
DELETE FROM agent_session_working_set WHERE session_id BETWEEN 920001 AND 922999;
DELETE FROM agent_context_epochs    WHERE epoch_id BETWEEN 920001 AND 922999;
DELETE FROM agent_user_preferences  WHERE user_id BETWEEN 920001 AND 922999;

DELETE FROM rag_chunks              WHERE chunk_id BETWEEN 920001 AND 922999;
DELETE FROM rag_documents           WHERE document_id BETWEEN 920001 AND 922999;
DELETE FROM storage_objects         WHERE storage_object_id BETWEEN 920001 AND 922999;

DELETE FROM plugin_call_logs        WHERE plugin_call_log_id BETWEEN 920001 AND 922999;
DELETE FROM plugin_project_installs WHERE plugin_install_id BETWEEN 920001 AND 922999;
DELETE FROM plugin_catalog          WHERE plugin_id BETWEEN 920001 AND 922999;

DELETE FROM style_switch_logs       WHERE style_switch_log_id BETWEEN 920001 AND 922999;
DELETE FROM style_profiles          WHERE style_id BETWEEN 920001 AND 922999;

DELETE FROM story_bible_change_items WHERE change_item_id BETWEEN 920001 AND 922999;
DELETE FROM story_bible_changesets  WHERE changeset_id BETWEEN 920001 AND 922999;
DELETE FROM story_bible_view_preferences WHERE story_bible_id BETWEEN 920001 AND 922999;
DELETE FROM story_bible_progressions WHERE progression_id BETWEEN 920001 AND 922999;
DELETE FROM story_bible_relations   WHERE relation_id BETWEEN 920001 AND 922999;
DELETE FROM story_bible_node_tags   WHERE story_bible_id BETWEEN 920001 AND 922999;
DELETE FROM story_bible_tags        WHERE tag_id BETWEEN 920001 AND 922999;
DELETE FROM story_bible_node_categories WHERE story_bible_id BETWEEN 920001 AND 922999;
DELETE FROM story_bible_categories  WHERE category_id BETWEEN 920001 AND 922999;
DELETE FROM story_bible_aliases     WHERE alias_id BETWEEN 920001 AND 922999;
DELETE FROM story_bible_nodes       WHERE node_id BETWEEN 920001 AND 922999;
DELETE FROM story_bible_node_types  WHERE type_id BETWEEN 920001 AND 922999;
DELETE FROM story_bibles            WHERE story_bible_id BETWEEN 920001 AND 922999;
DELETE FROM novel_outline_nodes     WHERE outline_node_id BETWEEN 920001 AND 922999;
DELETE FROM novel_chapter_versions  WHERE chapter_version_id BETWEEN 920001 AND 922999;
DELETE FROM novel_chapters          WHERE chapter_id BETWEEN 920001 AND 922999;
DELETE FROM novel_volumes           WHERE volume_id BETWEEN 920001 AND 922999;
DELETE FROM novel_members           WHERE project_id BETWEEN 920001 AND 922999;
DELETE FROM novel_projects          WHERE project_id BETWEEN 920001 AND 922999;

DELETE FROM iam_menus               WHERE menu_id BETWEEN 920001 AND 922999;
DELETE FROM iam_role_permissions    WHERE role_id BETWEEN 920001 AND 922999;
DELETE FROM iam_user_roles          WHERE user_id BETWEEN 920001 AND 922999;
DELETE FROM iam_permissions         WHERE permission_id BETWEEN 920001 AND 922999;
DELETE FROM iam_roles               WHERE role_id BETWEEN 920001 AND 922999;
UPDATE iam_users
SET main_agent_model_config_id = NULL,
    dirty_work_agent_model_config_id = NULL
WHERE user_id BETWEEN 920001 AND 922999;

DELETE FROM iam_users               WHERE user_id BETWEEN 920001 AND 922999;

DELETE FROM model_user_configurations WHERE model_config_id BETWEEN 920001 AND 922999;
DELETE FROM model_official_api_keys WHERE official_api_key_id BETWEEN 920001 AND 922999;
DELETE FROM model_user_api_keys     WHERE user_api_key_id BETWEEN 920001 AND 922999;

DELETE FROM ops_async_jobs          WHERE job_id BETWEEN 920001 AND 922999;
DELETE FROM ops_migrations          WHERE migration_id BETWEEN 920001 AND 922999;


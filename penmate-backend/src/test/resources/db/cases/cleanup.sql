-- Remove only explicit demo/case rows. System metadata and bootstrap data are untouched.
DELETE FROM agent_tool_call_executions WHERE run_id BETWEEN 920000 AND 922999;
DELETE FROM agent_event_archives WHERE run_id BETWEEN 920000 AND 922999;
DELETE FROM agent_checkpoints WHERE run_id BETWEEN 920000 AND 922999;
DELETE FROM agent_events WHERE run_id BETWEEN 920000 AND 922999;
DELETE FROM agent_run_inputs WHERE run_id BETWEEN 920000 AND 922999;
DELETE FROM agent_run_pending_approvals WHERE run_id BETWEEN 920000 AND 922999;
DELETE FROM agent_artifacts WHERE run_id BETWEEN 920000 AND 922999;
DELETE FROM agent_todo_projections WHERE run_id BETWEEN 920000 AND 922999;
DELETE FROM agent_tool_call_projections WHERE run_id BETWEEN 920000 AND 922999;
DELETE FROM agent_run_projections WHERE run_id BETWEEN 920000 AND 922999;
DELETE FROM agent_runs WHERE run_id BETWEEN 920000 AND 922999;
DELETE FROM agent_turns WHERE turn_id BETWEEN 920000 AND 922999;
DELETE FROM agent_messages WHERE message_id BETWEEN 920000 AND 922999;
DELETE FROM agent_session_todos WHERE session_id BETWEEN 920000 AND 922999;
DELETE FROM agent_session_style_bindings WHERE session_id BETWEEN 920000 AND 922999;
DELETE FROM agent_session_working_set WHERE session_id BETWEEN 920000 AND 922999;
DELETE FROM agent_context_epochs WHERE session_id BETWEEN 920000 AND 922999;
DELETE FROM agent_sessions WHERE session_id BETWEEN 920000 AND 922999;

DELETE FROM plugin_call_logs WHERE project_id BETWEEN 920000 AND 922999;
DELETE FROM plugin_project_installs WHERE project_id BETWEEN 920000 AND 922999;
DELETE FROM plugin_catalog WHERE plugin_id BETWEEN 920000 AND 922999;

DELETE FROM rag_chunks WHERE project_id BETWEEN 920000 AND 922999;
DELETE FROM rag_retrieval_logs WHERE project_id BETWEEN 920000 AND 922999;
DELETE FROM rag_documents WHERE project_id BETWEEN 920000 AND 922999;
DELETE FROM storage_objects WHERE storage_object_id BETWEEN 920000 AND 922999;

DELETE FROM story_bible_change_items WHERE change_item_id BETWEEN 920000 AND 922999;
DELETE FROM story_bible_changesets WHERE story_bible_id BETWEEN 920000 AND 922999;
DELETE FROM story_bible_view_preferences WHERE story_bible_id BETWEEN 920000 AND 922999;
DELETE FROM story_bible_progressions WHERE story_bible_id BETWEEN 920000 AND 922999;
DELETE FROM story_bible_relations WHERE story_bible_id BETWEEN 920000 AND 922999;
DELETE FROM story_bible_node_tags WHERE story_bible_id BETWEEN 920000 AND 922999;
DELETE FROM story_bible_tags WHERE story_bible_id BETWEEN 920000 AND 922999;
DELETE FROM story_bible_node_categories WHERE story_bible_id BETWEEN 920000 AND 922999;
DELETE FROM story_bible_categories WHERE story_bible_id BETWEEN 920000 AND 922999;
DELETE FROM story_bible_aliases WHERE story_bible_id BETWEEN 920000 AND 922999;
DELETE FROM story_bible_nodes WHERE story_bible_id BETWEEN 920000 AND 922999;
DELETE FROM story_bible_node_types WHERE story_bible_id BETWEEN 920000 AND 922999;
DELETE FROM story_bibles WHERE story_bible_id BETWEEN 920000 AND 922999;

DELETE FROM style_switch_logs WHERE project_id BETWEEN 920000 AND 922999;
DELETE FROM style_profiles WHERE project_id BETWEEN 920000 AND 922999;
DELETE FROM novel_chapter_versions WHERE chapter_id BETWEEN 920000 AND 922999;
DELETE FROM novel_chapters WHERE project_id BETWEEN 920000 AND 922999;
DELETE FROM novel_outline_nodes WHERE project_id BETWEEN 920000 AND 922999;
DELETE FROM novel_volumes WHERE project_id BETWEEN 920000 AND 922999;
DELETE FROM novel_members WHERE project_id BETWEEN 920000 AND 922999;
DELETE FROM novel_projects WHERE project_id BETWEEN 920000 AND 922999;

DELETE FROM ops_async_jobs WHERE job_id BETWEEN 920000 AND 922999;
DELETE FROM ops_migrations WHERE migration_id BETWEEN 920000 AND 922999;

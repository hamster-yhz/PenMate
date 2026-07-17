ALTER TABLE novel_chapters ADD COLUMN content_revision BIGINT UNSIGNED NOT NULL DEFAULT 1;
ALTER TABLE agent_context_epochs ADD COLUMN active_chapter_content_revision BIGINT UNSIGNED NOT NULL DEFAULT 0;
ALTER TABLE agent_runs ADD COLUMN predecessor_run_id BIGINT UNSIGNED NULL;
ALTER TABLE agent_runs DROP INDEX uk_agent_runs_turn_id;
CREATE INDEX idx_agent_runs_turn_id ON agent_runs(turn_id);
CREATE INDEX idx_agent_runs_predecessor ON agent_runs(predecessor_run_id);

-- Compatibility upgrade for databases that applied V11 before context epochs were added.
CREATE TABLE IF NOT EXISTS agent_user_preferences (
    id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT UNSIGNED NOT NULL,
    story_bible_routing_mode VARCHAR(32) NOT NULL DEFAULT 'RETRIEVAL_THEN_LLM',
    router_model_config_id BIGINT UNSIGNED NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_agent_user_preferences_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='User-level Agent routing preferences';

CREATE TABLE IF NOT EXISTS agent_context_epochs (
    id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
    epoch_id BIGINT UNSIGNED NOT NULL,
    session_id BIGINT UNSIGNED NOT NULL,
    epoch_no INT UNSIGNED NOT NULL,
    fingerprint CHAR(64) NOT NULL,
    story_bible_revision BIGINT UNSIGNED NOT NULL,
    manuscript_revision BIGINT UNSIGNED NOT NULL,
    active_chapter_id BIGINT UNSIGNED NULL,
    style_binding_revision BIGINT UNSIGNED NOT NULL DEFAULT 0,
    routing_mode VARCHAR(32) NOT NULL,
    router_model_config_id BIGINT UNSIGNED NULL,
    router_model_config_revision BIGINT UNSIGNED NOT NULL DEFAULT 0,
    prompt_bundle_hash CHAR(64) NOT NULL,
    skill_catalog_hash CHAR(64) NOT NULL,
    tool_catalog_hash CHAR(64) NOT NULL,
    snapshot_object_key VARCHAR(500) NOT NULL,
    snapshot_hash CHAR(64) NOT NULL,
    snapshot_size_bytes BIGINT UNSIGNED NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    superseded_at DATETIME(3) NULL,
    UNIQUE KEY uk_agent_context_epochs_epoch_id (epoch_id),
    UNIQUE KEY uk_agent_context_epochs_session_no (session_id, epoch_no),
    KEY idx_agent_context_epochs_session_fingerprint (session_id, fingerprint, superseded_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Immutable session context epochs';

CREATE TABLE IF NOT EXISTS agent_session_working_set (
    id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
    session_id BIGINT UNSIGNED NOT NULL,
    node_id BIGINT UNSIGNED NOT NULL,
    activation_score DECIMAL(12,6) NOT NULL DEFAULT 0,
    last_used_turn_id BIGINT UNSIGNED NULL,
    use_count INT UNSIGNED NOT NULL DEFAULT 0,
    pinned TINYINT(1) NOT NULL DEFAULT 0,
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_agent_session_working_set_node (session_id, node_id),
    KEY idx_agent_session_working_set_eviction (session_id, pinned, last_used_turn_id, activation_score)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Mutable Story Bible working set';

SET @ddl = IF(
    (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema = DATABASE() AND table_name = 'agent_sessions'
       AND column_name = 'story_bible_routing_mode') = 0,
    'ALTER TABLE agent_sessions ADD COLUMN story_bible_routing_mode VARCHAR(32) NULL',
    'SELECT 1'
);
PREPARE migration_statement FROM @ddl;
EXECUTE migration_statement;
DEALLOCATE PREPARE migration_statement;

SET @ddl = IF(
    (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema = DATABASE() AND table_name = 'agent_sessions'
       AND column_name = 'router_model_config_id') = 0,
    'ALTER TABLE agent_sessions ADD COLUMN router_model_config_id BIGINT UNSIGNED NULL',
    'SELECT 1'
);
PREPARE migration_statement FROM @ddl;
EXECUTE migration_statement;
DEALLOCATE PREPARE migration_statement;

SET @ddl = IF(
    (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema = DATABASE() AND table_name = 'agent_sessions'
       AND column_name = 'active_context_epoch_id') = 0,
    'ALTER TABLE agent_sessions ADD COLUMN active_context_epoch_id BIGINT UNSIGNED NULL',
    'SELECT 1'
);
PREPARE migration_statement FROM @ddl;
EXECUTE migration_statement;
DEALLOCATE PREPARE migration_statement;

SET @ddl = IF(
    (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema = DATABASE() AND table_name = 'agent_runs'
       AND column_name = 'context_epoch_id') = 0,
    'ALTER TABLE agent_runs ADD COLUMN context_epoch_id BIGINT UNSIGNED NULL',
    'SELECT 1'
);
PREPARE migration_statement FROM @ddl;
EXECUTE migration_statement;
DEALLOCATE PREPARE migration_statement;

SET @ddl = IF(
    (SELECT COUNT(*) FROM information_schema.statistics
     WHERE table_schema = DATABASE() AND table_name = 'agent_runs'
       AND index_name = 'idx_agent_runs_context_epoch') = 0,
    'CREATE INDEX idx_agent_runs_context_epoch ON agent_runs(context_epoch_id)',
    'SELECT 1'
);
PREPARE migration_statement FROM @ddl;
EXECUTE migration_statement;
DEALLOCATE PREPARE migration_statement;

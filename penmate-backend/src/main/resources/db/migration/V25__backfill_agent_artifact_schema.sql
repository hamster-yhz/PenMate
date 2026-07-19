-- Compatibility upgrade for databases created before Agent artifacts stored
-- durable JSON payloads and optional source event references.
SET @ddl = IF(
    (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema = DATABASE() AND table_name = 'agent_artifacts'
       AND column_name = 'event_id') = 0,
    'ALTER TABLE agent_artifacts ADD COLUMN event_id BIGINT UNSIGNED NULL AFTER run_id',
    'SELECT 1'
);
PREPARE migration_statement FROM @ddl;
EXECUTE migration_statement;
DEALLOCATE PREPARE migration_statement;

SET @ddl = IF(
    (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema = DATABASE() AND table_name = 'agent_artifacts'
       AND column_name = 'payload_json') = 0,
    'ALTER TABLE agent_artifacts ADD COLUMN payload_json LONGTEXT NULL AFTER artifact_type',
    'SELECT 1'
);
PREPARE migration_statement FROM @ddl;
EXECUTE migration_statement;
DEALLOCATE PREPARE migration_statement;

UPDATE agent_artifacts
SET payload_json = COALESCE(payload_json, metadata_json, content_text, '{}')
WHERE payload_json IS NULL;

ALTER TABLE agent_artifacts
    MODIFY COLUMN payload_json LONGTEXT NOT NULL,
    MODIFY COLUMN content_type VARCHAR(100) NOT NULL DEFAULT 'application/json';

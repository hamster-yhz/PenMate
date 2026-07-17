ALTER TABLE agent_checkpoints ADD COLUMN state_schema_version INT UNSIGNED NOT NULL DEFAULT 1;
ALTER TABLE agent_checkpoints ADD COLUMN state_sha256 CHAR(64) NULL;
ALTER TABLE agent_checkpoints ADD COLUMN state_object_key VARCHAR(500) NULL;

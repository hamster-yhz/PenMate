ALTER TABLE agent_checkpoints ADD COLUMN storage_tier VARCHAR(16) NOT NULL DEFAULT 'HOT';
ALTER TABLE agent_checkpoints ADD COLUMN cold_archived_at DATETIME(3) NULL;
ALTER TABLE agent_checkpoints ADD COLUMN expires_at DATETIME(3) NULL;
ALTER TABLE agent_checkpoints ADD KEY idx_agent_checkpoints_storage_expiry (storage_tier, expires_at);

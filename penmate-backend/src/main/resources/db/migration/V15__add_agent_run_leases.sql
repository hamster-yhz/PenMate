ALTER TABLE agent_runs ADD COLUMN lease_owner VARCHAR(128) NULL;
ALTER TABLE agent_runs ADD COLUMN lease_until DATETIME(3) NULL;
ALTER TABLE agent_runs ADD COLUMN execution_token BIGINT UNSIGNED NOT NULL DEFAULT 0;
ALTER TABLE agent_runs ADD COLUMN attempt_count INT UNSIGNED NOT NULL DEFAULT 0;
ALTER TABLE agent_runs ADD COLUMN next_retry_at DATETIME(3) NULL;
ALTER TABLE agent_runs ADD COLUMN last_error_code VARCHAR(96) NULL;
ALTER TABLE agent_runs ADD COLUMN last_error_message VARCHAR(500) NULL;
CREATE INDEX idx_agent_runs_recovery ON agent_runs(run_status, next_retry_at, lease_until);

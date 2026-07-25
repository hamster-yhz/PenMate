-- Durable Agent Skill activation and version snapshots.
CREATE TABLE agent_skill_snapshots (
    content_hash CHAR(64) PRIMARY KEY,
    content_text TEXT NOT NULL,
    created_at TIMESTAMPTZ(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
);

CREATE TABLE agent_session_skill_bindings (
    session_id BIGINT NOT NULL,
    skill_name VARCHAR(64) NOT NULL,
    activated_at TIMESTAMPTZ(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT uk_agent_session_skill_binding UNIQUE (session_id, skill_name)
);

CREATE INDEX idx_agent_session_skill_bindings_session
    ON agent_session_skill_bindings (session_id, skill_name);

CREATE TABLE agent_run_skill_bindings (
    run_id BIGINT NOT NULL,
    skill_name VARCHAR(64) NOT NULL,
    content_hash CHAR(64) NOT NULL,
    activation_source VARCHAR(16) NOT NULL,
    tool_call_id VARCHAR(128) NULL,
    activated_at TIMESTAMPTZ(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT uk_agent_run_skill_binding UNIQUE (run_id, skill_name),
    CONSTRAINT ck_agent_run_skill_binding_source CHECK (activation_source IN ('EXPLICIT', 'AUTO'))
);

CREATE INDEX idx_agent_run_skill_bindings_run
    ON agent_run_skill_bindings (run_id, skill_name);
CREATE INDEX idx_agent_run_skill_bindings_snapshot
    ON agent_run_skill_bindings (content_hash);

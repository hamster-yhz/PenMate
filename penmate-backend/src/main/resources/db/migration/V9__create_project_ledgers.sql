CREATE TABLE project_ledgers (
    ledger_id BIGINT PRIMARY KEY,
    project_id BIGINT NOT NULL REFERENCES novel_projects(project_id) ON DELETE CASCADE,
    title VARCHAR(120) NOT NULL,
    content TEXT NOT NULL DEFAULT '',
    content_revision BIGINT NOT NULL DEFAULT 1,
    lease_owner_type VARCHAR(16) NULL,
    lease_owner_id BIGINT NULL,
    lease_token VARCHAR(96) NULL,
    lease_expires_at TIMESTAMPTZ(3) NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT ck_project_ledgers_revision CHECK (content_revision >= 1),
    CONSTRAINT ck_project_ledgers_title CHECK (char_length(btrim(title)) BETWEEN 1 AND 120),
    CONSTRAINT ck_project_ledgers_content CHECK (char_length(content) <= 200000),
    CONSTRAINT ck_project_ledgers_ai_lease CHECK (
        (lease_owner_type IS NULL AND lease_owner_id IS NULL AND lease_token IS NULL AND lease_expires_at IS NULL)
        OR (lease_owner_type = 'AI' AND lease_owner_id IS NOT NULL AND lease_token IS NOT NULL AND lease_expires_at IS NOT NULL)
    )
);

CREATE INDEX idx_project_ledgers_project_updated
    ON project_ledgers(project_id, updated_at DESC, ledger_id);

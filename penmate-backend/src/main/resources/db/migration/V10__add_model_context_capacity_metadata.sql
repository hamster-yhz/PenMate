ALTER TABLE model_configurations
    ADD COLUMN IF NOT EXISTS context_capacity_source VARCHAR(16) NOT NULL DEFAULT 'FALLBACK',
    ADD COLUMN IF NOT EXISTS context_capacity_source_url VARCHAR(500) NULL,
    ADD COLUMN IF NOT EXISTS context_capacity_verified_at TIMESTAMPTZ(3) NULL;

UPDATE model_configurations
SET context_capacity_source = CASE
    WHEN max_context_tokens <> 128000 THEN 'MANUAL'
    ELSE 'FALLBACK'
END
WHERE context_capacity_source IS NULL;

ALTER TABLE model_configurations
    DROP CONSTRAINT IF EXISTS ck_model_configuration_capacity_source;

ALTER TABLE model_configurations
    ADD CONSTRAINT ck_model_configuration_capacity_source
    CHECK (context_capacity_source IN ('MANUAL', 'PROVIDER', 'CATALOG', 'FALLBACK'));

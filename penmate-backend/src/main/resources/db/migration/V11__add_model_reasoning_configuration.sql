ALTER TABLE model_configurations
    ADD COLUMN reasoning_effort VARCHAR(16) NOT NULL DEFAULT 'AUTO',
    ADD COLUMN reasoning_mode VARCHAR(16) NOT NULL DEFAULT 'AUTO',
    ADD COLUMN reasoning_summary VARCHAR(16) NOT NULL DEFAULT 'AUTO';

ALTER TABLE model_configurations
    ADD CONSTRAINT ck_model_configuration_reasoning_effort
        CHECK (reasoning_effort IN ('AUTO', 'NONE', 'MINIMAL', 'LOW', 'MEDIUM', 'HIGH', 'XHIGH', 'MAX')),
    ADD CONSTRAINT ck_model_configuration_reasoning_mode
        CHECK (reasoning_mode IN ('AUTO', 'STANDARD', 'PRO', 'ADAPTIVE', 'DISABLED')),
    ADD CONSTRAINT ck_model_configuration_reasoning_summary
        CHECK (reasoning_summary IN ('AUTO', 'NONE', 'CONCISE', 'DETAILED')),
    ADD CONSTRAINT ck_model_configuration_embedding_reasoning
        CHECK (model_type = 'CHAT' OR (
            reasoning_effort = 'AUTO'
            AND reasoning_mode = 'AUTO'
            AND reasoning_summary = 'AUTO'
        ));

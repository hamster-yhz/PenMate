-- Account defaults seed a new project's model choices once. Existing nullable
-- references represented inheritance, so materialize their current values.
UPDATE project_ai_configurations configuration
SET creative_model_config_id = COALESCE(
        configuration.creative_model_config_id,
        preferences.default_creative_model_config_id
    ),
    embedding_model_config_id = COALESCE(
        configuration.embedding_model_config_id,
        preferences.default_embedding_model_config_id
    ),
    router_model_config_id = COALESCE(
        configuration.router_model_config_id,
        preferences.default_context_selector_model_config_id
    ),
    index_status = CASE
        WHEN configuration.embedding_model_config_id IS NULL
            AND preferences.default_embedding_model_config_id IS NOT NULL
            AND configuration.active_index_build_id IS NULL
            THEN 'REINDEX_REQUIRED'
        ELSE configuration.index_status
    END,
    updated_at = CURRENT_TIMESTAMP(3)
FROM novel_projects project
JOIN model_user_preferences preferences ON preferences.user_id = project.owner_user_id
WHERE configuration.project_id = project.project_id
  AND (
      configuration.creative_model_config_id IS NULL
      OR configuration.embedding_model_config_id IS NULL
      OR configuration.router_model_config_id IS NULL
  );

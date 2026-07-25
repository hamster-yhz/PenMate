UPDATE model_provider_capabilities
SET protocol_code = 'OPENAI_RESPONSES',
    updated_at = CURRENT_TIMESTAMP(3)
WHERE provider_id = 1
  AND capability_code = 'CHAT'
  AND deleted_at IS NULL;

CREATE TABLE IF NOT EXISTS plugin_catalog (
    id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
    code VARCHAR(100) NOT NULL,
    name VARCHAR(120) NOT NULL,
    category VARCHAR(60) NULL,
    provider VARCHAR(60) NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'active',
    latest_version VARCHAR(40) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_plugin_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS plugin_project_installs (
    id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
    project_id BIGINT UNSIGNED NOT NULL,
    plugin_id BIGINT UNSIGNED NOT NULL,
    version VARCHAR(40) NULL,
    config_json JSON NULL,
    enabled TINYINT(1) NOT NULL DEFAULT 1,
    installed_by BIGINT UNSIGNED NOT NULL,
    installed_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_project_plugin (project_id, plugin_id),
    KEY idx_plugin_install_project (project_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS plugin_call_logs (
    id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
    project_id BIGINT UNSIGNED NOT NULL,
    task_id BIGINT UNSIGNED NULL,
    plugin_code VARCHAR(100) NOT NULL,
    tool_name VARCHAR(100) NULL,
    request_json JSON NULL,
    response_json JSON NULL,
    latency_ms INT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'success',
    error_msg VARCHAR(255) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    KEY idx_plugin_call_project_time (project_id, created_at),
    KEY idx_plugin_call_task (task_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS model_user_api_keys (
    id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT UNSIGNED NOT NULL,
    provider_id BIGINT UNSIGNED NOT NULL,
    key_name VARCHAR(120) NOT NULL,
    encrypted_api_key TEXT NOT NULL,
    masked_api_key VARCHAR(40) NULL,
    is_default TINYINT(1) NOT NULL DEFAULT 0,
    last_used_at DATETIME(3) NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'active',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted_at DATETIME(3) NULL,
    KEY idx_model_key_user_deleted (user_id, deleted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS model_official_api_keys (
    id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
    provider_id BIGINT UNSIGNED NOT NULL,
    key_name VARCHAR(120) NOT NULL,
    encrypted_api_key TEXT NOT NULL,
    masked_api_key VARCHAR(40) NULL,
    is_default TINYINT(1) NOT NULL DEFAULT 0,
    last_used_at DATETIME(3) NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'active',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted_at DATETIME(3) NULL,
    KEY idx_model_official_key_provider_deleted (provider_id, deleted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS model_project_policies (
    id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
    project_id BIGINT UNSIGNED NOT NULL,
    policy_name VARCHAR(120) NOT NULL,
    scene VARCHAR(60) NULL,
    provider_model_id BIGINT UNSIGNED NULL,
    model_name VARCHAR(120) NULL,
    base_url VARCHAR(255) NULL,
    user_key_id BIGINT UNSIGNED NULL,
    official_key_id BIGINT UNSIGNED NULL,
    temperature DECIMAL(4,2) NULL,
    top_p DECIMAL(4,2) NULL,
    max_tokens INT NULL,
    fallback_policy_json JSON NULL,
    is_default TINYINT(1) NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted_at DATETIME(3) NULL,
    KEY idx_model_policy_project_deleted (project_id, deleted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;


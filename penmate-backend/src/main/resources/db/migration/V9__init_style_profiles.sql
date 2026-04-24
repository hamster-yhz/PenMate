CREATE TABLE IF NOT EXISTS style_profiles (
    id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
    style_id BIGINT UNSIGNED NOT NULL,
    project_id BIGINT UNSIGNED NOT NULL,
    name VARCHAR(120) NOT NULL,
    is_default TINYINT(1) NOT NULL DEFAULT 0,
    pace VARCHAR(50) NULL,
    tone VARCHAR(50) NULL,
    narrative_focus VARCHAR(100) NULL,
    prompt_template TEXT NULL,
    sample_text TEXT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted_at DATETIME(3) NULL,
    UNIQUE KEY uk_style_profiles_style_id (style_id),
    KEY idx_style_project_deleted (project_id, deleted_at),
    KEY idx_style_project_default (project_id, is_default, deleted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS style_switch_logs (
    id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
    style_switch_log_id BIGINT UNSIGNED NOT NULL,
    project_id BIGINT UNSIGNED NOT NULL,
    from_style_id BIGINT UNSIGNED NULL,
    to_style_id BIGINT UNSIGNED NOT NULL,
    switched_by BIGINT UNSIGNED NOT NULL,
    warning_confirmed TINYINT(1) NOT NULL DEFAULT 0,
    reason VARCHAR(255) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_style_switch_logs_log_id (style_switch_log_id),
    KEY idx_style_switch_project_created (project_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

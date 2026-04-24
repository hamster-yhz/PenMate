CREATE TABLE IF NOT EXISTS iam_menus (
    id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
    menu_id BIGINT UNSIGNED NOT NULL,
    parent_id BIGINT UNSIGNED NULL,
    title VARCHAR(100) NOT NULL,
    path VARCHAR(255) NOT NULL,
    sort_order INT UNSIGNED NOT NULL DEFAULT 0,
    permission_code VARCHAR(120) NULL,
    visible TINYINT(1) NOT NULL DEFAULT 1,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted_at DATETIME(3) NULL,
    UNIQUE KEY uk_iam_menus_menu_id (menu_id),
    KEY idx_menu_parent_sort (parent_id, sort_order),
    KEY idx_menu_visible (visible, deleted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;


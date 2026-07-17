CREATE TABLE IF NOT EXISTS novel_outline_nodes (
    id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
    outline_node_id BIGINT UNSIGNED NOT NULL,
    project_id BIGINT UNSIGNED NOT NULL,
    parent_id BIGINT UNSIGNED NULL,
    title VARCHAR(200) NOT NULL,
    node_type VARCHAR(40) NOT NULL DEFAULT 'chapter',
    sort_order INT NOT NULL DEFAULT 0,
    content TEXT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted_at DATETIME(3) NULL,
    UNIQUE KEY uk_novel_outline_nodes_outline_node_id (outline_node_id),
    KEY idx_outline_project_parent_sort (project_id, parent_id, sort_order, deleted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;


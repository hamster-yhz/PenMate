CREATE TABLE IF NOT EXISTS novel_volumes (
    id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
    volume_id BIGINT UNSIGNED NOT NULL,
    project_id BIGINT UNSIGNED NOT NULL,
    title VARCHAR(200) NOT NULL,
    sort_order INT UNSIGNED NOT NULL DEFAULT 0,
    description TEXT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted_at DATETIME(3) NULL,
    UNIQUE KEY uk_novel_volumes_volume_id (volume_id),
    KEY idx_volume_project_sort (project_id, sort_order, deleted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS novel_chapters (
    id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
    chapter_id BIGINT UNSIGNED NOT NULL,
    project_id BIGINT UNSIGNED NOT NULL,
    volume_id BIGINT UNSIGNED NULL,
    outline_node_id BIGINT UNSIGNED NULL,
    title VARCHAR(200) NOT NULL,
    chapter_no INT UNSIGNED NOT NULL,
    status TINYINT UNSIGNED NOT NULL DEFAULT 1,
    word_count INT UNSIGNED NOT NULL DEFAULT 0,
    excerpt TEXT NULL,
    content_object_key VARCHAR(500) NOT NULL,
    content_etag VARCHAR(128) NULL,
    content_size BIGINT UNSIGNED NULL,
    content_checksum VARCHAR(128) NULL,
    storage_provider VARCHAR(32) NOT NULL DEFAULT 's3',
    last_generated_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted_at DATETIME(3) NULL,
    UNIQUE KEY uk_novel_chapters_chapter_id (chapter_id),
    KEY idx_chapter_project_volume_no (project_id, volume_id, chapter_no),
    KEY idx_chapter_outline (outline_node_id),
    KEY idx_chapter_content_object (content_object_key(191))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;


CREATE TABLE IF NOT EXISTS novel_members (
    project_id BIGINT UNSIGNED NOT NULL,
    user_id BIGINT UNSIGNED NOT NULL,
    member_role VARCHAR(32) NOT NULL,
    joined_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (project_id, user_id),
    KEY idx_novel_members_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS novel_chapter_versions (
    id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
    chapter_id BIGINT UNSIGNED NOT NULL,
    version_no INT UNSIGNED NOT NULL,
    change_type VARCHAR(32) NOT NULL,
    change_reason VARCHAR(255) NULL,
    snapshot_object_key VARCHAR(500) NOT NULL,
    snapshot_etag VARCHAR(128) NULL,
    snapshot_size BIGINT UNSIGNED NULL,
    snapshot_checksum VARCHAR(128) NULL,
    created_by BIGINT UNSIGNED NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_chapter_version_no (chapter_id, version_no),
    KEY idx_chapter_versions_chapter_created (chapter_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;


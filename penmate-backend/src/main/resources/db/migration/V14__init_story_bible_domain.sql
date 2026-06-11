CREATE TABLE IF NOT EXISTS story_bibles (
    id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
    story_bible_id BIGINT UNSIGNED NOT NULL,
    project_id BIGINT UNSIGNED NOT NULL COMMENT '关联小说项目 business id',
    title VARCHAR(200) NOT NULL COMMENT 'Story Bible 标题；用于项目级知识库辨识',
    description VARCHAR(500) NULL COMMENT 'Story Bible 摘要；说明该知识库覆盖范围',
    active_version_no INT UNSIGNED NOT NULL DEFAULT 1 COMMENT '当前激活版本号；供上下文装配读取',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted_at DATETIME(3) NULL,
    UNIQUE KEY uk_story_bibles_story_bible_id (story_bible_id),
    UNIQUE KEY uk_story_bibles_project_id (project_id),
    KEY idx_story_bibles_project_deleted (project_id, deleted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Story Bible 聚合根；项目级长期知识库，不等于 prompt 大文本快照';

CREATE TABLE IF NOT EXISTS story_bible_versions (
    id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
    version_id BIGINT UNSIGNED NOT NULL,
    story_bible_id BIGINT UNSIGNED NOT NULL COMMENT '关联 Story Bible business id',
    project_id BIGINT UNSIGNED NOT NULL COMMENT '冗余项目 business id；便于按项目过滤版本',
    version_no INT UNSIGNED NOT NULL COMMENT 'Story Bible 版本号；按聚合单调递增',
    change_summary VARCHAR(500) NOT NULL COMMENT '版本变更摘要；供审批、恢复与前端追踪引用',
    created_by BIGINT UNSIGNED NOT NULL COMMENT '创建版本的操作者 business id',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_story_bible_versions_version_id (version_id),
    UNIQUE KEY uk_story_bible_versions_sb_version (story_bible_id, version_no),
    KEY idx_story_bible_versions_project_created (project_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Story Bible 版本头；记录项目知识库的版本化变更';

CREATE TABLE IF NOT EXISTS story_bible_entries (
    id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
    entry_id BIGINT UNSIGNED NOT NULL,
    story_bible_id BIGINT UNSIGNED NOT NULL COMMENT '关联 Story Bible business id',
    project_id BIGINT UNSIGNED NOT NULL COMMENT '所属项目 business id',
    entry_type VARCHAR(40) NOT NULL COMMENT '条目类型：character/world/plot/item/faction/rule',
    entry_key VARCHAR(120) NOT NULL COMMENT '条目稳定键；供上下文聚合和去重',
    title VARCHAR(200) NOT NULL COMMENT '条目标题',
    content TEXT NOT NULL COMMENT '条目正文；长期知识库内容，不直接等价于 prompt 片段',
    canonical_status VARCHAR(20) NOT NULL COMMENT '条目规范状态：CANON/PROPOSED/ASSUMPTION',
    risk_level TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '条目风险等级：1低/2中/3高',
    source_refs_json JSON NOT NULL COMMENT '来源引用 JSON；记录章节、卡片或外部证据',
    valid_from_chapter_id BIGINT UNSIGNED NULL COMMENT '起始生效章节 business id；NULL 表示项目开头即生效',
    valid_to_chapter_id BIGINT UNSIGNED NULL COMMENT '截止生效章节 business id；NULL 表示持续生效',
    version_no INT UNSIGNED NOT NULL COMMENT '该条目归属的 Story Bible 版本号',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted_at DATETIME(3) NULL,
    UNIQUE KEY uk_story_bible_entries_entry_id (entry_id),
    KEY idx_story_bible_entries_project_chapter_status (project_id, canonical_status, valid_from_chapter_id, valid_to_chapter_id, deleted_at),
    KEY idx_story_bible_entries_sb_version (story_bible_id, version_no, deleted_at),
    KEY idx_story_bible_entries_project_key (project_id, entry_key, deleted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Story Bible 结构化条目；支持 canon/proposed/assumption 与章节边界过滤';

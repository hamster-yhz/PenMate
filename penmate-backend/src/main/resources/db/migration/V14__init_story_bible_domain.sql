CREATE TABLE IF NOT EXISTS story_bibles (
    id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
    story_bible_id BIGINT UNSIGNED NOT NULL,
    project_id BIGINT UNSIGNED NOT NULL,
    title VARCHAR(200) NOT NULL,
    description TEXT NULL,
    content_revision BIGINT UNSIGNED NOT NULL DEFAULT 1,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted_at DATETIME(3) NULL,
    UNIQUE KEY uk_story_bibles_story_bible_id (story_bible_id),
    UNIQUE KEY uk_story_bibles_project_id (project_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='One current Story Bible per project';

CREATE TABLE IF NOT EXISTS story_bible_node_types (
    id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
    type_id BIGINT UNSIGNED NOT NULL,
    story_bible_id BIGINT UNSIGNED NULL,
    type_code VARCHAR(80) NOT NULL,
    semantic_family VARCHAR(40) NOT NULL,
    display_name VARCHAR(120) NOT NULL,
    icon_code VARCHAR(80) NULL,
    field_schema_json JSON NOT NULL,
    is_system TINYINT(1) NOT NULL DEFAULT 0,
    sort_order INT NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    archived_at DATETIME(3) NULL,
    UNIQUE KEY uk_story_bible_node_types_type_id (type_id),
    UNIQUE KEY uk_story_bible_node_types_scope_code (story_bible_id, type_code),
    KEY idx_story_bible_node_types_family (story_bible_id, semantic_family, archived_at, sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='System and project Story Bible node templates';

CREATE TABLE IF NOT EXISTS story_bible_nodes (
    id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
    node_id BIGINT UNSIGNED NOT NULL,
    story_bible_id BIGINT UNSIGNED NOT NULL,
    type_id BIGINT UNSIGNED NOT NULL,
    title VARCHAR(240) NOT NULL,
    summary TEXT NULL,
    body_markdown LONGTEXT NULL,
    attributes_json JSON NOT NULL,
    inclusion_policy VARCHAR(24) NOT NULL DEFAULT 'AUTO_RETRIEVE',
    canon_status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    revision BIGINT UNSIGNED NOT NULL DEFAULT 1,
    created_by BIGINT UNSIGNED NOT NULL,
    updated_by BIGINT UNSIGNED NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    archived_at DATETIME(3) NULL,
    deleted_at DATETIME(3) NULL,
    UNIQUE KEY uk_story_bible_nodes_node_id (node_id),
    KEY idx_story_bible_nodes_scope (story_bible_id, type_id, canon_status, deleted_at),
    KEY idx_story_bible_nodes_title (story_bible_id, title)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Current Story Bible node state';

CREATE TABLE IF NOT EXISTS story_bible_aliases (
    id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
    alias_id BIGINT UNSIGNED NOT NULL,
    story_bible_id BIGINT UNSIGNED NOT NULL,
    node_id BIGINT UNSIGNED NOT NULL,
    alias VARCHAR(240) NOT NULL,
    normalized_alias VARCHAR(240) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    deleted_at DATETIME(3) NULL,
    UNIQUE KEY uk_story_bible_aliases_alias_id (alias_id),
    UNIQUE KEY uk_story_bible_aliases_normalized (story_bible_id, normalized_alias, node_id),
    KEY idx_story_bible_aliases_node (node_id, deleted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Exact entity-resolution aliases';

CREATE TABLE IF NOT EXISTS story_bible_categories (
    id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
    category_id BIGINT UNSIGNED NOT NULL,
    story_bible_id BIGINT UNSIGNED NOT NULL,
    parent_category_id BIGINT UNSIGNED NULL,
    name VARCHAR(120) NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted_at DATETIME(3) NULL,
    UNIQUE KEY uk_story_bible_categories_category_id (category_id),
    UNIQUE KEY uk_story_bible_categories_sibling_name (story_bible_id, parent_category_id, name),
    KEY idx_story_bible_categories_tree (story_bible_id, parent_category_id, sort_order, deleted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS story_bible_node_categories (
    id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
    story_bible_id BIGINT UNSIGNED NOT NULL,
    node_id BIGINT UNSIGNED NOT NULL,
    category_id BIGINT UNSIGNED NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_story_bible_node_categories_membership (node_id, category_id),
    KEY idx_story_bible_node_categories_category (story_bible_id, category_id, node_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS story_bible_tags (
    id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
    tag_id BIGINT UNSIGNED NOT NULL,
    story_bible_id BIGINT UNSIGNED NOT NULL,
    name VARCHAR(80) NOT NULL,
    normalized_name VARCHAR(80) NOT NULL,
    color VARCHAR(20) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted_at DATETIME(3) NULL,
    UNIQUE KEY uk_story_bible_tags_tag_id (tag_id),
    UNIQUE KEY uk_story_bible_tags_name (story_bible_id, normalized_name),
    KEY idx_story_bible_tags_scope (story_bible_id, deleted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS story_bible_node_tags (
    id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
    story_bible_id BIGINT UNSIGNED NOT NULL,
    node_id BIGINT UNSIGNED NOT NULL,
    tag_id BIGINT UNSIGNED NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_story_bible_node_tags_membership (node_id, tag_id),
    KEY idx_story_bible_node_tags_tag (story_bible_id, tag_id, node_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS story_bible_relations (
    id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
    relation_id BIGINT UNSIGNED NOT NULL,
    story_bible_id BIGINT UNSIGNED NOT NULL,
    source_node_id BIGINT UNSIGNED NOT NULL,
    relation_type VARCHAR(80) NOT NULL,
    target_node_id BIGINT UNSIGNED NOT NULL,
    description TEXT NULL,
    attributes_json JSON NOT NULL,
    revision BIGINT UNSIGNED NOT NULL DEFAULT 1,
    created_by BIGINT UNSIGNED NOT NULL,
    updated_by BIGINT UNSIGNED NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted_at DATETIME(3) NULL,
    UNIQUE KEY uk_story_bible_relations_relation_id (relation_id),
    UNIQUE KEY uk_story_bible_relations_edge (story_bible_id, source_node_id, relation_type, target_node_id),
    KEY idx_story_bible_relations_target (story_bible_id, target_node_id, deleted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS story_bible_progressions (
    id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
    progression_id BIGINT UNSIGNED NOT NULL,
    story_bible_id BIGINT UNSIGNED NOT NULL,
    node_id BIGINT UNSIGNED NOT NULL,
    anchor_chapter_id BIGINT UNSIGNED NOT NULL,
    end_chapter_id BIGINT UNSIGNED NULL,
    story_event_node_id BIGINT UNSIGNED NULL,
    patch_json JSON NOT NULL,
    summary VARCHAR(500) NULL,
    revision BIGINT UNSIGNED NOT NULL DEFAULT 1,
    created_by BIGINT UNSIGNED NOT NULL,
    updated_by BIGINT UNSIGNED NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted_at DATETIME(3) NULL,
    UNIQUE KEY uk_story_bible_progressions_progression_id (progression_id),
    KEY idx_story_bible_progressions_effective (story_bible_id, node_id, anchor_chapter_id, end_chapter_id, deleted_at),
    KEY idx_story_bible_progressions_event (story_event_node_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Chapter-anchored RFC 6902 node state patches';

CREATE TABLE IF NOT EXISTS story_bible_view_preferences (
    id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
    story_bible_id BIGINT UNSIGNED NOT NULL,
    view_code VARCHAR(40) NOT NULL,
    display_name VARCHAR(120) NOT NULL,
    hidden TINYINT(1) NOT NULL DEFAULT 0,
    sort_order INT NOT NULL DEFAULT 0,
    updated_by BIGINT UNSIGNED NOT NULL,
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_story_bible_view_preferences_view (story_bible_id, view_code),
    KEY idx_story_bible_view_preferences_order (story_bible_id, sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS story_bible_changesets (
    id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
    changeset_id BIGINT UNSIGNED NOT NULL,
    story_bible_id BIGINT UNSIGNED NOT NULL,
    content_revision BIGINT UNSIGNED NOT NULL,
    actor_type VARCHAR(20) NOT NULL,
    actor_id BIGINT UNSIGNED NOT NULL,
    source_run_id BIGINT UNSIGNED NULL,
    change_summary VARCHAR(500) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_story_bible_changesets_changeset_id (changeset_id),
    UNIQUE KEY uk_story_bible_changesets_revision (story_bible_id, content_revision),
    KEY idx_story_bible_changesets_retention (story_bible_id, created_at, changeset_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Append-only Story Bible audit changesets';

CREATE TABLE IF NOT EXISTS story_bible_change_items (
    id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
    change_item_id BIGINT UNSIGNED NOT NULL,
    changeset_id BIGINT UNSIGNED NOT NULL,
    entity_type VARCHAR(40) NOT NULL,
    entity_id BIGINT UNSIGNED NOT NULL,
    operation VARCHAR(20) NOT NULL,
    field_path VARCHAR(500) NOT NULL,
    before_json JSON NULL,
    after_json JSON NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_story_bible_change_items_item_id (change_item_id),
    KEY idx_story_bible_change_items_changeset (changeset_id, change_item_id),
    KEY idx_story_bible_change_items_entity (entity_type, entity_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Field-level Story Bible changes';

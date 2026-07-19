-- Compatibility upgrade for databases that applied V2/V4 before the current
-- manuscript ordering and structure revision fields were introduced.
SET @ddl = IF(
    (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema = DATABASE() AND table_name = 'novel_projects'
       AND column_name = 'structure_revision') = 0,
    'ALTER TABLE novel_projects ADD COLUMN structure_revision BIGINT UNSIGNED NOT NULL DEFAULT 1 AFTER status',
    'SELECT 1'
);
PREPARE migration_statement FROM @ddl;
EXECUTE migration_statement;
DEALLOCATE PREPARE migration_statement;

SET @has_sort_order = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'novel_chapters'
      AND column_name = 'sort_order'
);
SET @has_chapter_no = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'novel_chapters'
      AND column_name = 'chapter_no'
);
SET @ddl = CASE
    WHEN @has_sort_order = 0 AND @has_chapter_no > 0
        THEN 'ALTER TABLE novel_chapters CHANGE COLUMN chapter_no sort_order INT UNSIGNED NOT NULL DEFAULT 0'
    WHEN @has_sort_order = 0
        THEN 'ALTER TABLE novel_chapters ADD COLUMN sort_order INT UNSIGNED NOT NULL DEFAULT 0 AFTER title'
    ELSE 'SELECT 1'
END;
PREPARE migration_statement FROM @ddl;
EXECUTE migration_statement;
DEALLOCATE PREPARE migration_statement;

SET @ddl = IF(
    (SELECT COUNT(*) FROM information_schema.statistics
     WHERE table_schema = DATABASE() AND table_name = 'novel_chapters'
       AND index_name = 'idx_chapter_project_volume_no') > 0,
    'DROP INDEX idx_chapter_project_volume_no ON novel_chapters',
    'SELECT 1'
);
PREPARE migration_statement FROM @ddl;
EXECUTE migration_statement;
DEALLOCATE PREPARE migration_statement;

SET @ddl = IF(
    (SELECT COUNT(*) FROM information_schema.statistics
     WHERE table_schema = DATABASE() AND table_name = 'novel_chapters'
       AND index_name = 'idx_chapter_project_volume_sort') = 0,
    'CREATE INDEX idx_chapter_project_volume_sort ON novel_chapters(project_id, volume_id, sort_order, deleted_at)',
    'SELECT 1'
);
PREPARE migration_statement FROM @ddl;
EXECUTE migration_statement;
DEALLOCATE PREPARE migration_statement;

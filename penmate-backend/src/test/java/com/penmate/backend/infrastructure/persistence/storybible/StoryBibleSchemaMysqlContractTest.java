package com.penmate.backend.infrastructure.persistence.storybible;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class StoryBibleSchemaMysqlContractTest {

    private static final String JDBC_URL = "jdbc:h2:mem:story_bible_schema_contract;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
    private static final String MIGRATION_DIR = "target/test-migrations/story-bible-schema";

    @BeforeAll
    static void migrateSchema() throws IOException {
        prepareStoryBibleMigrationsOnly();
        Flyway.configure()
                .dataSource(JDBC_URL, "sa", "")
                .locations("filesystem:" + MIGRATION_DIR)
                .load()
                .migrate();
    }

    @Test
    void should_define_story_bible_tables_columns_indexes_and_comments() throws Exception {
        assertThat(columnsOf("story_bibles"))
                .contains("story_bible_id", "project_id", "title", "description", "active_version_no", "created_at", "updated_at", "deleted_at");
        assertThat(columnsOf("story_bible_entries"))
                .contains(
                        "entry_id",
                        "story_bible_id",
                        "project_id",
                        "entry_type",
                        "entry_key",
                        "title",
                        "content",
                        "canonical_status",
                        "risk_level",
                        "source_refs_json",
                        "valid_from_chapter_id",
                        "valid_to_chapter_id",
                        "version_no",
                        "created_at",
                        "updated_at",
                        "deleted_at"
                );
        assertThat(columnsOf("story_bible_versions"))
                .contains("version_id", "story_bible_id", "project_id", "version_no", "change_summary", "created_by", "created_at");

        String migrationSql = Files.readString(Path.of("src/main/resources/db/migration/V12__init_story_bible_domain.sql"));
        assertThat(migrationSql)
                .contains("CREATE TABLE IF NOT EXISTS story_bibles")
                .contains("COMMENT='Story Bible 聚合根；项目级长期知识库，不等于 prompt 大文本快照'")
                .contains("COMMENT '关联小说项目 business id'")
                .contains("UNIQUE KEY uk_story_bibles_project_id (project_id)")
                .contains("CREATE TABLE IF NOT EXISTS story_bible_entries")
                .contains("source_refs_json JSON NOT NULL")
                .contains("valid_from_chapter_id BIGINT UNSIGNED NULL")
                .contains("valid_to_chapter_id BIGINT UNSIGNED NULL")
                .contains("COMMENT '条目规范状态：CANON/PROPOSED/ASSUMPTION'")
                .contains("KEY idx_story_bible_entries_project_chapter_status (project_id, canonical_status, valid_from_chapter_id, valid_to_chapter_id, deleted_at)")
                .contains("CREATE TABLE IF NOT EXISTS story_bible_versions")
                .contains("UNIQUE KEY uk_story_bible_versions_sb_version (story_bible_id, version_no)")
                .contains("COMMENT '版本变更摘要；供审批、恢复与前端追踪引用'");
    }

    private Set<String> columnsOf(String tableName) throws SQLException {
        try (Connection connection = DriverManager.getConnection(JDBC_URL, "sa", "");
             ResultSet columns = connection.getMetaData().getColumns(null, null, tableName, null)) {
            Set<String> names = new LinkedHashSet<>();
            while (columns.next()) {
                names.add(columns.getString("COLUMN_NAME"));
            }
            return names;
        }
    }

    private static void prepareStoryBibleMigrationsOnly() throws IOException {
        Path migrationDir = Path.of(MIGRATION_DIR);
        Files.createDirectories(migrationDir);
        Files.copy(
                Path.of("src/main/resources/db/migration/V2__init_novel_and_approval_minimal.sql"),
                migrationDir.resolve("V2__init_novel_and_approval_minimal.sql"),
                StandardCopyOption.REPLACE_EXISTING
        );
        Files.copy(
                Path.of("src/main/resources/db/migration/V12__init_story_bible_domain.sql"),
                migrationDir.resolve("V12__init_story_bible_domain.sql"),
                StandardCopyOption.REPLACE_EXISTING
        );
    }
}

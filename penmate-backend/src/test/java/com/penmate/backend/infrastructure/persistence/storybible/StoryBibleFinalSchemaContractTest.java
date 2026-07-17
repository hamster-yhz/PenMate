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
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class StoryBibleFinalSchemaContractTest {

    private static final String JDBC_URL = "jdbc:h2:mem:story_bible_final_schema;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
    private static final String MIGRATION_DIR = "target/test-migrations/story-bible-final-schema";
    private static final List<String> MIGRATIONS = List.of(
            "V2__init_novel_and_approval_minimal.sql",
            "V4__init_novel_volume_and_chapter.sql",
            "V8__init_novel_outlines_and_cards.sql",
            "V11__init_agent_and_ops_domains.sql",
            "V14__init_story_bible_domain.sql"
    );

    @BeforeAll
    static void migrateSchema() throws IOException {
        prepareMigrations();
        Flyway.configure()
                .dataSource(JDBC_URL, "sa", "")
                .locations("filesystem:" + MIGRATION_DIR)
                .load()
                .migrate();
    }

    @Test
    void should_define_single_current_story_bible_schema() throws Exception {
        assertThat(columnsOf("story_bibles"))
                .contains("story_bible_id", "project_id", "content_revision", "deleted_at");
        assertThat(columnsOf("story_bible_node_types"))
                .contains("type_id", "story_bible_id", "type_code", "semantic_family", "field_schema_json", "is_system");
        assertThat(columnsOf("story_bible_nodes"))
                .contains("node_id", "type_id", "attributes_json", "inclusion_policy", "canon_status", "revision");
        assertThat(columnsOf("story_bible_aliases"))
                .contains("node_id", "alias", "normalized_alias");
        assertThat(columnsOf("story_bible_categories")).contains("parent_category_id", "sort_order");
        assertThat(columnsOf("story_bible_node_categories")).contains("node_id", "category_id");
        assertThat(columnsOf("story_bible_tags")).contains("tag_id", "normalized_name");
        assertThat(columnsOf("story_bible_node_tags")).contains("node_id", "tag_id");
        assertThat(columnsOf("story_bible_relations"))
                .contains("source_node_id", "relation_type", "target_node_id", "attributes_json", "revision");
        assertThat(columnsOf("story_bible_progressions"))
                .contains("node_id", "anchor_chapter_id", "end_chapter_id", "story_event_node_id", "patch_json", "revision");
        assertThat(columnsOf("story_bible_view_preferences"))
                .contains("view_code", "display_name", "hidden", "sort_order");
        assertThat(columnsOf("story_bible_changesets"))
                .contains("content_revision", "actor_type", "source_run_id", "change_summary");
        assertThat(columnsOf("story_bible_change_items"))
                .contains("entity_type", "entity_id", "operation", "field_path", "before_json", "after_json");
    }

    @Test
    void should_define_context_epoch_working_set_and_routing_preferences() throws Exception {
        assertThat(columnsOf("agent_user_preferences"))
                .contains("user_id", "story_bible_routing_mode", "router_model_config_id");
        assertThat(columnsOf("agent_sessions"))
                .contains("story_bible_routing_mode", "router_model_config_id", "active_context_epoch_id")
                .doesNotContain("active_context_version");
        assertThat(columnsOf("agent_context_epochs"))
                .contains(
                        "epoch_id", "session_id", "epoch_no", "fingerprint", "story_bible_revision",
                        "manuscript_revision", "active_chapter_id", "style_binding_revision", "routing_mode",
                        "router_model_config_revision", "prompt_bundle_hash", "skill_catalog_hash", "tool_catalog_hash",
                        "snapshot_object_key", "snapshot_hash", "snapshot_size_bytes", "superseded_at"
                );
        assertThat(columnsOf("agent_session_working_set"))
                .contains("session_id", "node_id", "activation_score", "last_used_turn_id", "use_count", "pinned");
        assertThat(columnsOf("agent_runs")).contains("context_epoch_id");
    }

    @Test
    void should_use_final_manuscript_ordering_and_remove_legacy_card_tables() throws Exception {
        assertThat(columnsOf("novel_projects")).contains("structure_revision");
        assertThat(columnsOf("novel_chapters"))
                .contains("sort_order")
                .doesNotContain("chapter_no");
        assertThat(indexesOf("novel_chapters")).contains("idx_chapter_project_volume_sort");
        assertThat(columnsOf("novel_cards")).isEmpty();
        assertThat(columnsOf("novel_card_relations")).isEmpty();
        assertThat(columnsOf("story_bible_versions")).isEmpty();
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

    private Set<String> indexesOf(String tableName) throws SQLException {
        try (Connection connection = DriverManager.getConnection(JDBC_URL, "sa", "");
             ResultSet indexes = connection.getMetaData().getIndexInfo(null, null, tableName, false, false)) {
            Set<String> names = new LinkedHashSet<>();
            while (indexes.next()) {
                String name = indexes.getString("INDEX_NAME");
                if (name != null) {
                    names.add(name.toLowerCase());
                }
            }
            return names;
        }
    }

    private static void prepareMigrations() throws IOException {
        Path migrationDir = Path.of(MIGRATION_DIR);
        Files.createDirectories(migrationDir);
        for (String migration : MIGRATIONS) {
            Files.copy(
                    Path.of("src/main/resources/db/migration", migration),
                    migrationDir.resolve(migration),
                    StandardCopyOption.REPLACE_EXISTING
            );
        }
    }
}

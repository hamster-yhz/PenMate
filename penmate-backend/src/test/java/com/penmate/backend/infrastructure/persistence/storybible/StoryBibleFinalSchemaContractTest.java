package com.penmate.backend.infrastructure.persistence.storybible;

import com.penmate.backend.testinfra.PostgreSqlTestDatabase;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;

class StoryBibleFinalSchemaContractTest {

    private static DataSource dataSource;

    @BeforeAll
    static void migrateSchema() {
        dataSource = PostgreSqlTestDatabase.migratedDataSource("story_bible_final_schema");
    }

    @Test
    void should_define_single_current_story_bible_schema() {
        assertThat(PostgreSqlTestDatabase.columnsOf(dataSource, "story_bibles"))
                .contains("story_bible_id", "project_id", "content_revision", "deleted_at");
        assertThat(PostgreSqlTestDatabase.columnsOf(dataSource, "story_bible_node_types"))
                .contains("type_id", "story_bible_id", "type_code", "semantic_family", "field_schema_json", "is_system");
        assertThat(PostgreSqlTestDatabase.columnsOf(dataSource, "story_bible_nodes"))
                .contains("node_id", "type_id", "attributes_json", "inclusion_policy", "canon_status", "revision");
        assertThat(PostgreSqlTestDatabase.columnsOf(dataSource, "story_bible_aliases"))
                .contains("node_id", "alias", "normalized_alias");
        assertThat(PostgreSqlTestDatabase.columnsOf(dataSource, "story_bible_categories"))
                .contains("parent_category_id", "sort_order");
        assertThat(PostgreSqlTestDatabase.columnsOf(dataSource, "story_bible_node_categories"))
                .contains("node_id", "category_id");
        assertThat(PostgreSqlTestDatabase.columnsOf(dataSource, "story_bible_tags"))
                .contains("tag_id", "normalized_name");
        assertThat(PostgreSqlTestDatabase.columnsOf(dataSource, "story_bible_node_tags"))
                .contains("node_id", "tag_id");
        assertThat(PostgreSqlTestDatabase.columnsOf(dataSource, "story_bible_relations"))
                .contains("source_node_id", "relation_type", "target_node_id", "attributes_json", "revision");
        assertThat(PostgreSqlTestDatabase.columnsOf(dataSource, "story_bible_progressions"))
                .contains("node_id", "anchor_chapter_id", "end_chapter_id", "story_event_node_id", "patch_json", "revision");
        assertThat(PostgreSqlTestDatabase.columnsOf(dataSource, "story_bible_view_preferences"))
                .contains("view_code", "display_name", "hidden", "sort_order");
        assertThat(PostgreSqlTestDatabase.columnsOf(dataSource, "story_bible_changesets"))
                .contains("content_revision", "actor_type", "source_run_id", "change_summary");
        assertThat(PostgreSqlTestDatabase.columnsOf(dataSource, "story_bible_change_items"))
                .contains("entity_type", "entity_id", "operation", "field_path", "before_json", "after_json");
    }

    @Test
    void should_define_context_epoch_working_set_and_routing_preferences() {
        assertThat(PostgreSqlTestDatabase.columnsOf(dataSource, "project_ai_configurations"))
                .contains("project_id", "story_bible_routing_mode", "router_model_config_id",
                        "embedding_model_config_id", "index_status");
        assertThat(PostgreSqlTestDatabase.columnsOf(dataSource, "agent_user_preferences")).isEmpty();
        assertThat(PostgreSqlTestDatabase.columnsOf(dataSource, "agent_sessions"))
                .contains("active_context_epoch_id")
                .doesNotContain("story_bible_routing_mode", "router_model_config_id", "active_context_version");
        assertThat(PostgreSqlTestDatabase.columnsOf(dataSource, "agent_context_epochs"))
                .contains(
                        "epoch_id", "session_id", "epoch_no", "fingerprint", "story_bible_revision",
                        "manuscript_revision", "active_chapter_id", "style_binding_revision", "routing_mode",
                        "router_model_config_id", "prompt_bundle_hash", "skill_catalog_hash", "tool_catalog_hash",
                        "snapshot_object_key", "snapshot_hash", "snapshot_size_bytes", "superseded_at");
        assertThat(PostgreSqlTestDatabase.columnsOf(dataSource, "agent_session_working_set"))
                .contains("session_id", "node_id", "activation_score", "last_used_turn_id", "use_count", "pinned");
        assertThat(PostgreSqlTestDatabase.columnsOf(dataSource, "agent_runs")).contains("context_epoch_id");
    }

    @Test
    void should_use_final_manuscript_ordering_and_remove_legacy_card_tables() {
        assertThat(PostgreSqlTestDatabase.columnsOf(dataSource, "novel_projects")).contains("structure_revision");
        assertThat(PostgreSqlTestDatabase.columnsOf(dataSource, "novel_chapters"))
                .contains("sort_order")
                .doesNotContain("chapter_no");
        assertThat(PostgreSqlTestDatabase.indexesOf(dataSource, "novel_chapters"))
                .contains("idx_chapter_project_volume_sort");
        assertThat(PostgreSqlTestDatabase.columnsOf(dataSource, "novel_cards")).isEmpty();
        assertThat(PostgreSqlTestDatabase.columnsOf(dataSource, "novel_card_relations")).isEmpty();
        assertThat(PostgreSqlTestDatabase.columnsOf(dataSource, "story_bible_versions")).isEmpty();
    }
}

package com.penmate.backend.infrastructure.persistence.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import javax.sql.DataSource;

import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.StreamUtils;

class AgentBaseSeedSqlContractTest {

    private static final String H2_URL = "jdbc:h2:mem:agent_base_seed_contract;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";

    private static DataSource dataSource;

    @BeforeAll
    static void setUpDataSource() {
        dataSource = new UnpooledDataSource("org.h2.Driver", H2_URL, "sa", "");
    }

    @BeforeEach
    void resetSchema() throws Exception {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("DROP TABLE IF EXISTS story_bible_change_items");
            statement.execute("DROP TABLE IF EXISTS story_bible_changesets");
            statement.execute("DROP TABLE IF EXISTS story_bible_view_preferences");
            statement.execute("DROP TABLE IF EXISTS story_bible_progressions");
            statement.execute("DROP TABLE IF EXISTS story_bible_relations");
            statement.execute("DROP TABLE IF EXISTS story_bible_node_tags");
            statement.execute("DROP TABLE IF EXISTS story_bible_tags");
            statement.execute("DROP TABLE IF EXISTS story_bible_node_categories");
            statement.execute("DROP TABLE IF EXISTS story_bible_categories");
            statement.execute("DROP TABLE IF EXISTS story_bible_aliases");
            statement.execute("DROP TABLE IF EXISTS story_bible_nodes");
            statement.execute("DROP TABLE IF EXISTS story_bible_node_types");
            statement.execute("DROP TABLE IF EXISTS story_bibles");
            statement.execute("DROP TABLE IF EXISTS novel_chapter_versions");
            statement.execute("DROP TABLE IF EXISTS novel_chapters");
            statement.execute("DROP TABLE IF EXISTS novel_outline_nodes");
            statement.execute("DROP TABLE IF EXISTS novel_volumes");
            statement.execute("DROP TABLE IF EXISTS novel_members");
            statement.execute("DROP TABLE IF EXISTS novel_projects");
            statement.execute("DROP TABLE IF EXISTS agent_approval_actions");
            statement.execute("DROP TABLE IF EXISTS agent_approval_requests");
            statement.execute("DROP TABLE IF EXISTS agent_pending_approvals");
            statement.execute("DROP TABLE IF EXISTS agent_task_results");
            statement.execute("DROP TABLE IF EXISTS agent_task_contexts");
            statement.execute("DROP TABLE IF EXISTS agent_tasks");
            statement.execute("DROP TABLE IF EXISTS agent_messages");
            statement.execute("DROP TABLE IF EXISTS agent_turns");
            statement.execute("DROP TABLE IF EXISTS agent_session_style_bindings");
            statement.execute("DROP TABLE IF EXISTS agent_sessions");
            statement.execute("DROP TABLE IF EXISTS agent_user_preferences");
            statement.execute("DROP TABLE IF EXISTS style_switch_logs");
            statement.execute("DROP TABLE IF EXISTS style_profiles");
            createSchema(statement);
        }
    }

    @Test
    void should_execute_base_seed_with_only_session_style_baseline_and_no_runtime_preseed() throws Exception {
        String sql;
        try (var inputStream = new ClassPathResource("db/cases/seed_all_domain_base.sql").getInputStream()) {
            sql = StreamUtils.copyToString(inputStream, StandardCharsets.UTF_8);
        }

        assertThat(sql)
                .doesNotContain("agent_conversations")
                .doesNotContain("agent_generation_tasks")
                .doesNotContain("novel_cards")
                .doesNotContain("novel_card_relations")
                .doesNotContain("chapter_no")
                .doesNotContain("active_context_version")
                .doesNotContain("(920001, 920001, 920001, 920003, NULL,")
                .doesNotContain("(920002, 920002, 920001, NULL,   NULL,")
                .doesNotContain("(920003, 920003, 920001, NULL,   NULL,")
                .contains("DBCASE Owner OpenAI-Compatible Key")
                .contains("openai-compatible-chat")
                .contains(", 7,");

        executeBlock(sql, "-- 文风", "-- 插件");
        executeBlock(sql, "-- Agent + 审批", "-- RAG + 对象存储");

        assertThat(countRows("style_profiles")).isEqualTo(2);
        assertThat(countRows("agent_sessions")).isEqualTo(2);
        assertThat(countRows("agent_session_style_bindings")).isEqualTo(2);
        assertThat(countRows("agent_user_preferences")).isEqualTo(1);

        assertThat(countRows("style_switch_logs")).isZero();
        assertThat(countRows("agent_turns")).isZero();
        assertThat(countRows("agent_messages")).isZero();
        assertThat(countRows("agent_tasks")).isZero();
        assertThat(countRows("agent_task_contexts")).isZero();
        assertThat(countRows("agent_task_results")).isZero();
        assertThat(countRows("agent_pending_approvals")).isZero();
        assertThat(countRows("agent_approval_requests")).isZero();
        assertThat(countRows("agent_approval_actions")).isZero();
    }

    @Test
    void should_keep_real_outline_node_mapping_for_every_seeded_chapter() throws Exception {
        String sql;
        try (var inputStream = new ClassPathResource("db/cases/seed_all_domain_base.sql").getInputStream()) {
            sql = StreamUtils.copyToString(inputStream, StandardCharsets.UTF_8);
        }

        executeBlock(sql, "-- 小说核心", "-- 基础 seed");

        assertThat(countRows("story_bibles")).isEqualTo(4);
        assertThat(countRows("story_bible_nodes")).isEqualTo(4);
        assertThat(countRows("story_bible_relations")).isEqualTo(1);
        assertThat(countRows("story_bible_progressions")).isEqualTo(1);
        assertThat(countRows("story_bible_changesets")).isEqualTo(1);

        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("""
                     SELECT COUNT(*)
                     FROM novel_projects p
                     LEFT JOIN story_bibles b ON b.project_id = p.project_id
                     WHERE p.project_id BETWEEN 920001 AND 920999
                       AND b.story_bible_id IS NULL
                     """)) {
            resultSet.next();
            assertThat(resultSet.getLong(1)).isZero();
        }

        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("""
                     SELECT COUNT(*)
                     FROM novel_chapters c
                     LEFT JOIN novel_outline_nodes o
                       ON o.outline_node_id = c.outline_node_id
                     WHERE c.chapter_id BETWEEN 920001 AND 920999
                       AND (c.outline_node_id IS NULL OR o.outline_node_id IS NULL)
                     """)) {
            resultSet.next();
            assertThat(resultSet.getLong(1)).isZero();
        }
    }

    private static void createSchema(Statement statement) throws Exception {
        statement.execute("""
                CREATE TABLE style_profiles (
                    id BIGINT PRIMARY KEY,
                    style_id BIGINT NOT NULL,
                    project_id BIGINT NOT NULL,
                    name VARCHAR(120) NOT NULL,
                    is_default TINYINT NOT NULL,
                    pace VARCHAR(50) NULL,
                    tone VARCHAR(50) NULL,
                    narrative_focus VARCHAR(100) NULL,
                    prompt_template VARCHAR(4000) NULL,
                    sample_text VARCHAR(4000) NULL,
                    created_at TIMESTAMP NOT NULL,
                    updated_at TIMESTAMP NOT NULL,
                    deleted_at TIMESTAMP NULL
                )
                """);
        statement.execute("""
                CREATE TABLE style_switch_logs (
                    id BIGINT PRIMARY KEY,
                    style_switch_log_id BIGINT NOT NULL,
                    project_id BIGINT NOT NULL,
                    from_style_id BIGINT NULL,
                    to_style_id BIGINT NOT NULL,
                    switched_by BIGINT NOT NULL,
                    warning_confirmed TINYINT NOT NULL,
                    reason VARCHAR(255) NULL,
                    created_at TIMESTAMP NOT NULL
                )
                """);
        statement.execute("""
                CREATE TABLE agent_sessions (
                    id BIGINT PRIMARY KEY,
                    session_id BIGINT NOT NULL,
                    project_id BIGINT NOT NULL,
                    owner_user_id BIGINT NOT NULL,
                    title VARCHAR(200) NOT NULL,
                    session_status VARCHAR(20) NOT NULL,
                    bound_style_id BIGINT NULL,
                    story_bible_routing_mode VARCHAR(32) NULL,
                    router_model_config_id BIGINT NULL,
                    active_context_epoch_id BIGINT NULL,
                    last_turn_id BIGINT NULL,
                    last_run_id BIGINT NULL,
                    last_message_at TIMESTAMP NULL,
                    resumed_at TIMESTAMP NULL,
                    total_prompt_tokens INT NOT NULL DEFAULT 0,
                    total_completion_tokens INT NOT NULL DEFAULT 0,
                    total_tokens INT NOT NULL DEFAULT 0,
                    created_at TIMESTAMP NOT NULL,
                    updated_at TIMESTAMP NOT NULL,
                    deleted_at TIMESTAMP NULL
                )
                """);
        statement.execute("""
                CREATE TABLE agent_user_preferences (
                    id BIGINT PRIMARY KEY,
                    user_id BIGINT NOT NULL,
                    story_bible_routing_mode VARCHAR(32) NOT NULL,
                    router_model_config_id BIGINT NULL,
                    created_at TIMESTAMP NOT NULL,
                    updated_at TIMESTAMP NOT NULL
                )
                """);
        statement.execute("""
                CREATE TABLE agent_session_style_bindings (
                    id BIGINT PRIMARY KEY,
                    binding_id BIGINT NOT NULL,
                    session_id BIGINT NOT NULL,
                    style_id BIGINT NOT NULL,
                    source VARCHAR(24) NOT NULL,
                    activated_at TIMESTAMP NOT NULL,
                    deactivated_at TIMESTAMP NULL,
                    created_at TIMESTAMP NOT NULL
                )
                """);
        statement.execute("CREATE TABLE agent_turns (id BIGINT PRIMARY KEY, turn_id BIGINT NOT NULL)");
        statement.execute("CREATE TABLE agent_messages (id BIGINT PRIMARY KEY, message_id BIGINT NOT NULL)");
        statement.execute("CREATE TABLE agent_tasks (id BIGINT PRIMARY KEY, task_id BIGINT NOT NULL)");
        statement.execute("CREATE TABLE agent_task_contexts (id BIGINT PRIMARY KEY, context_id BIGINT NOT NULL)");
        statement.execute("CREATE TABLE agent_task_results (id BIGINT PRIMARY KEY, result_id BIGINT NOT NULL)");
        statement.execute("CREATE TABLE agent_pending_approvals (id BIGINT PRIMARY KEY, pending_approval_id BIGINT NOT NULL)");
        statement.execute("CREATE TABLE agent_approval_requests (id BIGINT PRIMARY KEY, approval_request_id BIGINT NOT NULL)");
        statement.execute("CREATE TABLE agent_approval_actions (id BIGINT PRIMARY KEY, approval_action_id BIGINT NOT NULL)");
        statement.execute("CREATE TABLE novel_projects (id BIGINT PRIMARY KEY, project_id BIGINT NOT NULL, owner_user_id BIGINT NOT NULL, title VARCHAR(200) NOT NULL, summary VARCHAR(2000) NULL, status INT NOT NULL, structure_revision BIGINT NOT NULL, created_at TIMESTAMP NOT NULL, updated_at TIMESTAMP NOT NULL, deleted_at TIMESTAMP NULL)");
        statement.execute("CREATE TABLE novel_members (project_id BIGINT NOT NULL, user_id BIGINT NOT NULL, member_role VARCHAR(50) NOT NULL, joined_at TIMESTAMP NOT NULL)");
        statement.execute("CREATE TABLE novel_volumes (id BIGINT PRIMARY KEY, volume_id BIGINT NOT NULL, project_id BIGINT NOT NULL, title VARCHAR(200) NOT NULL, sort_order INT NOT NULL, description VARCHAR(2000) NULL, created_at TIMESTAMP NOT NULL, updated_at TIMESTAMP NOT NULL, deleted_at TIMESTAMP NULL)");
        statement.execute("CREATE TABLE novel_outline_nodes (id BIGINT PRIMARY KEY, outline_node_id BIGINT NOT NULL, project_id BIGINT NOT NULL, parent_id BIGINT NULL, title VARCHAR(200) NOT NULL, node_type VARCHAR(50) NOT NULL, sort_order INT NOT NULL, content VARCHAR(4000) NULL, created_at TIMESTAMP NOT NULL, updated_at TIMESTAMP NOT NULL, deleted_at TIMESTAMP NULL)");
        statement.execute("CREATE TABLE novel_chapters (id BIGINT PRIMARY KEY, chapter_id BIGINT NOT NULL, project_id BIGINT NOT NULL, volume_id BIGINT NULL, outline_node_id BIGINT NULL, title VARCHAR(200) NOT NULL, sort_order INT NOT NULL, status INT NOT NULL, word_count INT NOT NULL, excerpt VARCHAR(2000) NULL, content_object_key VARCHAR(500) NULL, content_etag VARCHAR(255) NULL, content_size BIGINT NOT NULL, content_checksum VARCHAR(255) NULL, storage_provider VARCHAR(50) NULL, last_generated_at TIMESTAMP NULL, created_at TIMESTAMP NOT NULL, updated_at TIMESTAMP NOT NULL, deleted_at TIMESTAMP NULL)");
        statement.execute("CREATE TABLE novel_chapter_versions (id BIGINT PRIMARY KEY, chapter_version_id BIGINT NOT NULL, chapter_id BIGINT NOT NULL, version_no INT NOT NULL, change_type VARCHAR(50) NOT NULL, change_reason VARCHAR(255) NULL, snapshot_object_key VARCHAR(500) NOT NULL, snapshot_etag VARCHAR(255) NULL, snapshot_size BIGINT NOT NULL, snapshot_checksum VARCHAR(255) NULL, created_by BIGINT NOT NULL, created_at TIMESTAMP NOT NULL)");
        statement.execute("CREATE TABLE story_bibles (id BIGINT PRIMARY KEY, story_bible_id BIGINT NOT NULL, project_id BIGINT NOT NULL, title VARCHAR(200) NOT NULL, description VARCHAR(2000) NULL, content_revision BIGINT NOT NULL, created_at TIMESTAMP NOT NULL, updated_at TIMESTAMP NOT NULL, deleted_at TIMESTAMP NULL)");
        statement.execute("CREATE TABLE story_bible_node_types (id BIGINT PRIMARY KEY, type_id BIGINT NOT NULL, story_bible_id BIGINT NULL, type_code VARCHAR(80) NOT NULL, semantic_family VARCHAR(40) NOT NULL, display_name VARCHAR(120) NOT NULL, icon_code VARCHAR(80) NULL, field_schema_json JSON NOT NULL, is_system TINYINT NOT NULL, sort_order INT NOT NULL, created_at TIMESTAMP NOT NULL, updated_at TIMESTAMP NOT NULL, archived_at TIMESTAMP NULL)");
        statement.execute("CREATE TABLE story_bible_nodes (id BIGINT PRIMARY KEY, node_id BIGINT NOT NULL, story_bible_id BIGINT NOT NULL, type_id BIGINT NOT NULL, title VARCHAR(240) NOT NULL, summary VARCHAR(2000) NULL, body_markdown VARCHAR(4000) NULL, attributes_json JSON NOT NULL, inclusion_policy VARCHAR(24) NOT NULL, canon_status VARCHAR(20) NOT NULL, revision BIGINT NOT NULL, created_by BIGINT NOT NULL, updated_by BIGINT NOT NULL, created_at TIMESTAMP NOT NULL, updated_at TIMESTAMP NOT NULL, archived_at TIMESTAMP NULL, deleted_at TIMESTAMP NULL)");
        statement.execute("CREATE TABLE story_bible_aliases (id BIGINT PRIMARY KEY, alias_id BIGINT NOT NULL, story_bible_id BIGINT NOT NULL, node_id BIGINT NOT NULL, alias VARCHAR(240) NOT NULL, normalized_alias VARCHAR(240) NOT NULL, created_at TIMESTAMP NOT NULL, deleted_at TIMESTAMP NULL)");
        statement.execute("CREATE TABLE story_bible_categories (id BIGINT PRIMARY KEY, category_id BIGINT NOT NULL, story_bible_id BIGINT NOT NULL, parent_category_id BIGINT NULL, name VARCHAR(120) NOT NULL, sort_order INT NOT NULL, created_at TIMESTAMP NOT NULL, updated_at TIMESTAMP NOT NULL, deleted_at TIMESTAMP NULL)");
        statement.execute("CREATE TABLE story_bible_node_categories (id BIGINT PRIMARY KEY, story_bible_id BIGINT NOT NULL, node_id BIGINT NOT NULL, category_id BIGINT NOT NULL, created_at TIMESTAMP NOT NULL)");
        statement.execute("CREATE TABLE story_bible_tags (id BIGINT PRIMARY KEY, tag_id BIGINT NOT NULL, story_bible_id BIGINT NOT NULL, name VARCHAR(80) NOT NULL, normalized_name VARCHAR(80) NOT NULL, color VARCHAR(20) NULL, created_at TIMESTAMP NOT NULL, updated_at TIMESTAMP NOT NULL, deleted_at TIMESTAMP NULL)");
        statement.execute("CREATE TABLE story_bible_node_tags (id BIGINT PRIMARY KEY, story_bible_id BIGINT NOT NULL, node_id BIGINT NOT NULL, tag_id BIGINT NOT NULL, created_at TIMESTAMP NOT NULL)");
        statement.execute("CREATE TABLE story_bible_relations (id BIGINT PRIMARY KEY, relation_id BIGINT NOT NULL, story_bible_id BIGINT NOT NULL, source_node_id BIGINT NOT NULL, relation_type VARCHAR(80) NOT NULL, target_node_id BIGINT NOT NULL, description VARCHAR(2000) NULL, attributes_json JSON NOT NULL, revision BIGINT NOT NULL, created_by BIGINT NOT NULL, updated_by BIGINT NOT NULL, created_at TIMESTAMP NOT NULL, updated_at TIMESTAMP NOT NULL, deleted_at TIMESTAMP NULL)");
        statement.execute("CREATE TABLE story_bible_progressions (id BIGINT PRIMARY KEY, progression_id BIGINT NOT NULL, story_bible_id BIGINT NOT NULL, node_id BIGINT NOT NULL, anchor_chapter_id BIGINT NOT NULL, end_chapter_id BIGINT NULL, story_event_node_id BIGINT NULL, patch_json JSON NOT NULL, summary VARCHAR(500) NULL, revision BIGINT NOT NULL, created_by BIGINT NOT NULL, updated_by BIGINT NOT NULL, created_at TIMESTAMP NOT NULL, updated_at TIMESTAMP NOT NULL, deleted_at TIMESTAMP NULL)");
        statement.execute("CREATE TABLE story_bible_view_preferences (id BIGINT PRIMARY KEY, story_bible_id BIGINT NOT NULL, view_code VARCHAR(40) NOT NULL, display_name VARCHAR(120) NOT NULL, hidden TINYINT NOT NULL, sort_order INT NOT NULL, updated_by BIGINT NOT NULL, updated_at TIMESTAMP NOT NULL)");
        statement.execute("CREATE TABLE story_bible_changesets (id BIGINT PRIMARY KEY, changeset_id BIGINT NOT NULL, story_bible_id BIGINT NOT NULL, content_revision BIGINT NOT NULL, actor_type VARCHAR(20) NOT NULL, actor_id BIGINT NOT NULL, source_run_id BIGINT NULL, change_summary VARCHAR(500) NOT NULL, created_at TIMESTAMP NOT NULL)");
        statement.execute("CREATE TABLE story_bible_change_items (id BIGINT PRIMARY KEY, change_item_id BIGINT NOT NULL, changeset_id BIGINT NOT NULL, entity_type VARCHAR(40) NOT NULL, entity_id BIGINT NOT NULL, operation VARCHAR(20) NOT NULL, field_path VARCHAR(500) NOT NULL, before_json JSON NULL, after_json JSON NULL, created_at TIMESTAMP NOT NULL)");
    }

    private void executeBlock(String sql, String startMarker, String endMarker) throws Exception {
        int start = sql.indexOf(startMarker);
        int end = sql.indexOf(endMarker);
        String block = sql.substring(start, end)
                .replaceAll("(?m)^\\s*--.*$", "")
                .replace("SET NAMES utf8mb4;", "")
                .replace("NOW(3)", "CURRENT_TIMESTAMP");

        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            for (String statementSql : block.split(";")) {
                String trimmed = statementSql.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                statement.execute(trimmed);
            }
        }
    }

    private long countRows(String tableName) throws Exception {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT COUNT(*) FROM " + tableName)) {
            resultSet.next();
            return resultSet.getLong(1);
        }
    }
}

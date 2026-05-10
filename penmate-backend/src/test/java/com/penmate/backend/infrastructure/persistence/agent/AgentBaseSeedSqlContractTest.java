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
            statement.execute("DROP TABLE IF EXISTS novel_card_relations");
            statement.execute("DROP TABLE IF EXISTS novel_cards");
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
                    active_context_version INT NOT NULL,
                    last_turn_id BIGINT NULL,
                    last_task_id BIGINT NULL,
                    last_message_at TIMESTAMP NULL,
                    resumed_at TIMESTAMP NULL,
                    created_at TIMESTAMP NOT NULL,
                    updated_at TIMESTAMP NOT NULL,
                    deleted_at TIMESTAMP NULL
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
        statement.execute("CREATE TABLE novel_projects (id BIGINT PRIMARY KEY, project_id BIGINT NOT NULL, owner_user_id BIGINT NOT NULL, title VARCHAR(200) NOT NULL, summary VARCHAR(2000) NULL, status INT NOT NULL, created_at TIMESTAMP NOT NULL, updated_at TIMESTAMP NOT NULL, deleted_at TIMESTAMP NULL)");
        statement.execute("CREATE TABLE novel_members (project_id BIGINT NOT NULL, user_id BIGINT NOT NULL, member_role VARCHAR(50) NOT NULL, joined_at TIMESTAMP NOT NULL)");
        statement.execute("CREATE TABLE novel_volumes (id BIGINT PRIMARY KEY, volume_id BIGINT NOT NULL, project_id BIGINT NOT NULL, title VARCHAR(200) NOT NULL, sort_order INT NOT NULL, description VARCHAR(2000) NULL, created_at TIMESTAMP NOT NULL, updated_at TIMESTAMP NOT NULL, deleted_at TIMESTAMP NULL)");
        statement.execute("CREATE TABLE novel_outline_nodes (id BIGINT PRIMARY KEY, outline_node_id BIGINT NOT NULL, project_id BIGINT NOT NULL, parent_id BIGINT NULL, title VARCHAR(200) NOT NULL, node_type VARCHAR(50) NOT NULL, sort_order INT NOT NULL, content VARCHAR(4000) NULL, created_at TIMESTAMP NOT NULL, updated_at TIMESTAMP NOT NULL, deleted_at TIMESTAMP NULL)");
        statement.execute("CREATE TABLE novel_chapters (id BIGINT PRIMARY KEY, chapter_id BIGINT NOT NULL, project_id BIGINT NOT NULL, volume_id BIGINT NULL, outline_node_id BIGINT NULL, title VARCHAR(200) NOT NULL, chapter_no INT NOT NULL, status INT NOT NULL, word_count INT NOT NULL, excerpt VARCHAR(2000) NULL, content_object_key VARCHAR(500) NULL, content_etag VARCHAR(255) NULL, content_size BIGINT NOT NULL, content_checksum VARCHAR(255) NULL, storage_provider VARCHAR(50) NULL, last_generated_at TIMESTAMP NULL, created_at TIMESTAMP NOT NULL, updated_at TIMESTAMP NOT NULL, deleted_at TIMESTAMP NULL)");
        statement.execute("CREATE TABLE novel_chapter_versions (id BIGINT PRIMARY KEY, chapter_version_id BIGINT NOT NULL, chapter_id BIGINT NOT NULL, version_no INT NOT NULL, change_type VARCHAR(50) NOT NULL, change_reason VARCHAR(255) NULL, snapshot_object_key VARCHAR(500) NOT NULL, snapshot_etag VARCHAR(255) NULL, snapshot_size BIGINT NOT NULL, snapshot_checksum VARCHAR(255) NULL, created_by BIGINT NOT NULL, created_at TIMESTAMP NOT NULL)");
        statement.execute("CREATE TABLE novel_cards (id BIGINT PRIMARY KEY, card_id BIGINT NOT NULL, project_id BIGINT NOT NULL, card_type VARCHAR(50) NOT NULL, name VARCHAR(200) NOT NULL, summary VARCHAR(2000) NULL, detail_json VARCHAR(4000) NULL, created_at TIMESTAMP NOT NULL, updated_at TIMESTAMP NOT NULL, deleted_at TIMESTAMP NULL)");
        statement.execute("CREATE TABLE novel_card_relations (id BIGINT PRIMARY KEY, card_relation_id BIGINT NOT NULL, project_id BIGINT NOT NULL, from_card_id BIGINT NOT NULL, to_card_id BIGINT NOT NULL, relation_type VARCHAR(50) NOT NULL, description VARCHAR(2000) NULL, created_at TIMESTAMP NOT NULL, deleted_at TIMESTAMP NULL)");
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

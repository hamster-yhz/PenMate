package com.penmate.backend.infrastructure.persistence.agent;

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

class AgentSessionSchemaMysqlContractTest {

    private static final String JDBC_URL = "jdbc:h2:mem:agent_session_schema_contract;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
    private static final String MIGRATION_DIR = "target/test-migrations/agent-session-schema";

    @BeforeAll
    static void migrateSchema() throws IOException {
        prepareMigrationsOnly();
        Flyway.configure()
                .dataSource(JDBC_URL, "sa", "")
                .locations("filesystem:" + MIGRATION_DIR)
                .load()
                .migrate();
    }

    @Test
    void should_define_agent_session_run_recovery_tables() throws Exception {
        assertThat(columnsOf("agent_sessions"))
                .contains(
                        "session_id",
                        "bound_style_id",
                        "active_context_version",
                        "last_run_id",
                        "resumed_at",
                        "total_prompt_tokens",
                        "total_completion_tokens",
                        "total_tokens"
                );
        assertThat(columnsOf("agent_turns"))
                .contains("turn_id", "turn_seq", "run_id", "resume_token", "turn_status");
        assertThat(columnsOf("agent_runs"))
                .contains(
                        "run_id",
                        "project_id",
                        "session_id",
                        "turn_id",
                        "run_status",
                        "run_phase",
                        "active_approval_id",
                        "latest_event_seq",
                        "latest_checkpoint_id"
                );
        assertThat(columnsOf("agent_run_inputs"))
                .contains(
                        "run_id",
                        "prompt_snapshot",
                        "task_type",
                        "style_snapshot_json",
                        "model_snapshot_json",
                        "plugin_bindings_json",
                        "input_hash"
                );
        assertThat(columnsOf("agent_events"))
                .contains("run_id", "session_id", "turn_id", "sequence", "schema_version", "event_type", "payload_json");
        assertThat(columnsOf("agent_checkpoints"))
                .contains("run_id", "checkpoint_no", "last_event_seq", "state_json");
        assertThat(columnsOf("agent_run_projections"))
                .contains("run_id", "session_id", "turn_id", "run_status", "run_phase", "latest_sequence");
        assertThat(columnsOf("agent_run_pending_approvals"))
                .contains("run_id", "session_id", "turn_id", "resume_payload_json", "pending_status");

        assertThat(columnsOf("agent_conversations")).isEmpty();
        assertThat(columnsOf("pending_tool_invocations")).isEmpty();
        assertThat(columnsOf("agent_tasks")).isEmpty();
        assertThat(columnsOf("agent_task_contexts")).isEmpty();
        assertThat(columnsOf("agent_task_results")).isEmpty();
        assertThat(columnsOf("agent_pending_approvals")).isEmpty();

        String v11Sql = Files.readString(Path.of("src/main/resources/db/migration/V11__init_agent_and_ops_domains.sql"));
        String v12Sql = Files.readString(Path.of("src/main/resources/db/migration/V12__init_pending_tool_invocations.sql"));
        assertThat(v11Sql)
                .contains("UNIQUE KEY uk_agent_turns_session_seq (session_id, turn_seq)")
                .contains("UNIQUE KEY uk_agent_messages_session_seq (session_id, seq_no)")
                .contains("last_run_id BIGINT UNSIGNED NULL")
                .contains("run_id BIGINT UNSIGNED NULL")
                .contains("CREATE TABLE IF NOT EXISTS agent_runs")
                .contains("CREATE TABLE IF NOT EXISTS agent_events")
                .contains("CREATE TABLE IF NOT EXISTS agent_checkpoints")
                .contains("CREATE TABLE IF NOT EXISTS agent_run_projections");
        assertThat(v12Sql)
                .contains("CREATE TABLE IF NOT EXISTS agent_run_pending_approvals")
                .contains("UNIQUE KEY uk_agent_run_pending_approvals_approval_id (approval_id)")
                .contains("UNIQUE KEY uk_agent_run_pending_approvals_idempotency (idempotency_key)")
                .contains("KEY idx_agent_run_pending_approvals_run_status (run_id, pending_status)")
                .contains("KEY idx_agent_run_pending_approvals_session_status (session_id, pending_status)");
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

    private static void prepareMigrationsOnly() throws IOException {
        Path migrationDir = Path.of(MIGRATION_DIR);
        Files.createDirectories(migrationDir);
        Files.copy(
                Path.of("src/main/resources/db/migration/V11__init_agent_and_ops_domains.sql"),
                migrationDir.resolve("V11__init_agent_and_ops_domains.sql"),
                StandardCopyOption.REPLACE_EXISTING
        );
        Files.copy(
                Path.of("src/main/resources/db/migration/V12__init_pending_tool_invocations.sql"),
                migrationDir.resolve("V12__init_pending_tool_invocations.sql"),
                StandardCopyOption.REPLACE_EXISTING
        );
    }

}

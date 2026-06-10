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

class AgentMapperGenerationTaskMysqlContractTest {

    private static final String JDBC_URL = "jdbc:h2:mem:agent_generation_task_contract;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
    private static final String MIGRATION_DIR = "target/test-migrations/agent-generation-task-schema";

    @BeforeAll
    static void migrateSchema() throws IOException {
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
        Flyway.configure()
                .dataSource(JDBC_URL, "sa", "")
                .locations("filesystem:" + MIGRATION_DIR)
                .load()
                .migrate();
    }

    @Test
    void should_define_agent_run_event_checkpoint_runtime_schema() throws Exception {
        assertThat(columnsOf("agent_runs"))
                .contains("run_id", "session_id", "turn_id", "project_id")
                .contains("run_status", "run_phase", "active_approval_id", "latest_event_seq", "latest_checkpoint_id")
                .contains("started_at", "finished_at", "trace_id");

        assertThat(columnsOf("agent_run_inputs"))
                .contains("run_id", "prompt_snapshot", "task_type", "chapter_id", "selected_text")
                .contains("style_snapshot_json", "model_snapshot_json", "plugin_bindings_json", "input_hash");

        assertThat(columnsOf("agent_events"))
                .contains("event_id", "run_id", "project_id", "session_id", "turn_id")
                .contains("sequence", "schema_version", "event_type", "payload_json");

        assertThat(columnsOf("agent_checkpoints"))
                .contains("checkpoint_id", "run_id", "checkpoint_no", "last_event_seq", "state_json", "state_size_bytes");

        assertThat(columnsOf("agent_run_projections"))
                .contains("run_id", "project_id", "session_id", "turn_id")
                .contains("run_status", "run_phase", "active_approval_id", "latest_sequence", "result_artifact_id");

        assertThat(columnsOf("agent_tool_call_projections"))
                .contains("run_id", "tool_call_id", "tool_code", "status", "approval_id", "output_artifact_id");

        assertThat(columnsOf("agent_todo_projections"))
                .contains("run_id", "todo_id", "title", "status", "sort_order");

        assertThat(columnsOf("agent_artifacts"))
                .contains("artifact_id", "run_id", "artifact_type", "content_type", "content_text", "metadata_json");

        assertThat(columnsOf("agent_tasks")).isEmpty();
        assertThat(columnsOf("agent_task_results")).isEmpty();
        assertThat(columnsOf("agent_task_contexts")).isEmpty();
        assertThat(columnsOf("agent_generation_tasks")).isEmpty();

        String v11Sql = Files.readString(Path.of("src/main/resources/db/migration/V11__init_agent_and_ops_domains.sql"));
        assertThat(v11Sql)
                .contains("UNIQUE KEY uk_agent_runs_run_id (run_id)")
                .contains("UNIQUE KEY uk_agent_runs_turn_id (turn_id)")
                .contains("UNIQUE KEY uk_agent_run_inputs_run_id (run_id)")
                .contains("UNIQUE KEY uk_agent_events_run_seq (run_id, sequence)")
                .contains("UNIQUE KEY uk_agent_checkpoints_run_no (run_id, checkpoint_no)")
                .contains("UNIQUE KEY uk_agent_run_projections_run_id (run_id)")
                .contains("UNIQUE KEY uk_agent_tool_call_projection (run_id, tool_call_id)")
                .contains("UNIQUE KEY uk_agent_todo_projection (run_id, todo_id)")
                .contains("KEY idx_agent_artifacts_run_type (run_id, artifact_type)");
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

}

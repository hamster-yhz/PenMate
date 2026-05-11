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
    void should_define_agent_task_recovery_columns_for_generation_runtime() throws Exception {
        assertThat(columnsOf("agent_tasks"))
                .contains("task_id", "session_id", "turn_id", "project_id")
                .contains("prompt_snapshot", "request_context_id", "result_id", "active_approval_id", "stream_channel_key")
                .contains("task_status", "started_at", "finished_at", "trace_id");

        assertThat(columnsOf("agent_task_results"))
                .contains("result_id", "task_id", "result_status", "output_markdown")
                .contains("output_structured_json", "tool_trace_json", "token_usage_json", "cost_usage_json");

        assertThat(columnsOf("agent_task_contexts"))
                .contains("context_id", "task_id", "style_snapshot_json", "model_snapshot_json", "context_hash");

        assertThat(columnsOf("agent_generation_tasks")).isEmpty();

        String v11Sql = Files.readString(Path.of("src/main/resources/db/migration/V11__init_agent_and_ops_domains.sql"));
        assertThat(v11Sql)
                .contains("UNIQUE KEY uk_agent_task_contexts_context_id (context_id)")
                .contains("UNIQUE KEY uk_agent_task_contexts_task_id (task_id)")
                .contains("UNIQUE KEY uk_agent_task_results_result_id (result_id)")
                .contains("UNIQUE KEY uk_agent_task_results_task_id (task_id)")
                .contains("KEY idx_agent_tasks_project_status (project_id, task_status)")
                .contains("KEY idx_agent_tasks_session_created (session_id, created_at)");
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

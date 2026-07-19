package com.penmate.backend.infrastructure.persistence.todo;

import com.penmate.backend.testinfra.PostgreSqlTestDatabase;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class SessionTodoSchemaPostgreSqlContractTest {

    private static DataSource dataSource;

    @BeforeAll
    static void migrateSchema() {
        dataSource = PostgreSqlTestDatabase.migratedDataSource("session_todo_schema_contract");
    }

    @Test
    void should_define_session_scoped_todo_table_and_indexes() throws Exception {
        assertThat(PostgreSqlTestDatabase.columnsOf(dataSource, "agent_session_todos"))
                .contains(
                        "todo_id", "project_id", "session_id", "source_run_id", "source_type",
                        "todo_status", "title", "description", "completed_at", "deleted_at");
        assertThat(PostgreSqlTestDatabase.indexesOf(dataSource, "agent_session_todos"))
                .contains(
                        "uk_agent_session_todos_todo_id",
                        "idx_agent_session_todos_session_status_deleted",
                        "idx_agent_session_todos_session_created",
                        "idx_agent_session_todos_source_run");

        String sql = Files.readString(Path.of("src/main/resources/db/migration/V5__create_agent_domain.sql"));
        assertThat(sql)
                .contains("CREATE TABLE IF NOT EXISTS agent_session_todos")
                .contains("todo_id BIGINT NOT NULL")
                .contains("completed_at TIMESTAMPTZ(3) NULL")
                .contains("deleted_at TIMESTAMPTZ(3) NULL");
    }
}

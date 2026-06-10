package com.penmate.backend.infrastructure.persistence.todo;

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

class SessionTodoSchemaMysqlContractTest {

    private static final String JDBC_URL = "jdbc:h2:mem:session_todo_schema_contract;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
    private static final String MIGRATION_DIR = "target/test-migrations/session-todo-schema";

    @BeforeAll
    static void migrateSchema() throws IOException {
        prepareTask4MigrationsOnly();
        Flyway.configure()
                .dataSource(JDBC_URL, "sa", "")
                .locations("filesystem:" + MIGRATION_DIR)
                .load()
                .migrate();
    }

    @Test
    void should_define_session_scoped_todo_table_and_indexes() throws Exception {
        assertThat(columnsOf("agent_session_todos"))
                .contains(
                        "todo_id",
                        "project_id",
                        "session_id",
                        "source_run_id",
                        "source_type",
                        "todo_status",
                        "title",
                        "description",
                        "completed_at",
                        "deleted_at"
                );

        String v13Sql = Files.readString(Path.of("src/main/resources/db/migration/V13__init_agent_session_todos.sql"));
        assertThat(v13Sql)
                .contains("CREATE TABLE IF NOT EXISTS agent_session_todos")
                .contains("todo_id BIGINT UNSIGNED NOT NULL")
                .contains("session_id BIGINT UNSIGNED NOT NULL")
                .contains("source_run_id BIGINT UNSIGNED NULL")
                .contains("source_type VARCHAR(32) NOT NULL")
                .contains("todo_status VARCHAR(32) NOT NULL")
                .contains("deleted_at DATETIME(3) NULL")
                .contains("UNIQUE KEY uk_agent_session_todos_todo_id (todo_id)")
                .contains("KEY idx_agent_session_todos_session_status_deleted (session_id, todo_status, deleted_at)")
                .contains("KEY idx_agent_session_todos_session_created (session_id, created_at)")
                .contains("KEY idx_agent_session_todos_source_run (source_run_id)")
                .contains("会话级 Todo 持久化表")
                .contains("软删除时间")
                .contains("来源类型：USER_REQUEST/QUALITY_REVIEW/STORY_BIBLE_UPDATE/PLANNING")
                .contains("待办状态：TODO/IN_PROGRESS/BLOCKED/DONE");
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

    private static void prepareTask4MigrationsOnly() throws IOException {
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
        Files.copy(
                Path.of("src/main/resources/db/migration/V13__init_agent_session_todos.sql"),
                migrationDir.resolve("V13__init_agent_session_todos.sql"),
                StandardCopyOption.REPLACE_EXISTING
        );
    }
}

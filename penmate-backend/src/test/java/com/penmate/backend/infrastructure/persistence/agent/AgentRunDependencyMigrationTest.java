package com.penmate.backend.infrastructure.persistence.agent;

import org.junit.jupiter.api.Test;
import org.flywaydb.core.Flyway;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class AgentRunDependencyMigrationTest {

    @Test
    void defines_chapter_revision_epoch_dependency_and_successor_link() throws Exception {
        String sql = Files.readString(Path.of(
                "src/main/resources/db/migration/V18__add_agent_run_dependency_revisions.sql"));

        assertThat(sql)
                .contains("novel_chapters ADD COLUMN content_revision")
                .contains("agent_context_epochs ADD COLUMN active_chapter_content_revision")
                .contains("agent_runs ADD COLUMN predecessor_run_id")
                .contains("DROP INDEX uk_agent_runs_turn_id")
                .contains("idx_agent_runs_predecessor");
        assertThat(Files.readString(Path.of(
                "src/main/resources/db/migration/V23__enforce_single_agent_run_successor.sql")))
                .contains("UNIQUE KEY uk_agent_runs_predecessor (predecessor_run_id)");
    }

    @Test
    void migration_applies_to_mysql_compatible_schema() throws Exception {
        Path directory = Path.of("target/test-migrations/agent-run-dependencies");
        Files.createDirectories(directory);
        copy(directory, "V4__init_novel_volume_and_chapter.sql");
        copy(directory, "V11__init_agent_and_ops_domains.sql");
        copy(directory, "V12__init_pending_tool_invocations.sql");
        copy(directory, "V17__add_agent_run_leases.sql");
        copy(directory, "V18__add_agent_run_dependency_revisions.sql");
        copy(directory, "V23__enforce_single_agent_run_successor.sql");
        String url = "jdbc:h2:mem:agent_run_dependencies;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
        Flyway.configure().dataSource(url, "sa", "").locations("filesystem:" + directory).load().migrate();

        assertThat(columnsOf(url, "novel_chapters")).contains("content_revision");
        assertThat(columnsOf(url, "agent_context_epochs")).contains("active_chapter_content_revision");
        assertThat(columnsOf(url, "agent_runs")).contains("predecessor_run_id");
    }

    private void copy(Path directory, String file) throws Exception {
        Files.copy(Path.of("src/main/resources/db/migration", file), directory.resolve(file),
                StandardCopyOption.REPLACE_EXISTING);
    }

    private Set<String> columnsOf(String url, String table) throws Exception {
        try (Connection connection = DriverManager.getConnection(url, "sa", "");
             ResultSet result = connection.getMetaData().getColumns(null, null, table, null)) {
            Set<String> columns = new LinkedHashSet<>();
            while (result.next()) columns.add(result.getString("COLUMN_NAME"));
            return columns;
        }
    }
}

package com.penmate.backend.infrastructure.persistence.agent;

import com.penmate.backend.testinfra.PostgreSqlTestDatabase;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class AgentRunDependencyMigrationTest {

    private static DataSource dataSource;

    @BeforeAll
    static void migrateSchema() {
        dataSource = PostgreSqlTestDatabase.migratedDataSource("agent_run_dependency_migration");
    }

    @Test
    void defines_chapter_revision_epoch_dependency_and_single_successor_link() throws Exception {
        assertThat(PostgreSqlTestDatabase.columnsOf(dataSource, "novel_chapters"))
                .contains("content_revision");
        assertThat(PostgreSqlTestDatabase.columnsOf(dataSource, "agent_context_epochs"))
                .contains("active_chapter_content_revision");
        assertThat(PostgreSqlTestDatabase.columnsOf(dataSource, "agent_runs"))
                .contains("predecessor_run_id");
        assertThat(PostgreSqlTestDatabase.indexesOf(dataSource, "agent_runs"))
                .contains("uk_agent_runs_predecessor");

        String sql = Files.readString(Path.of(
                "src/main/resources/db/migration/V5__create_agent_domain.sql"));
        assertThat(sql)
                .contains("active_chapter_content_revision BIGINT NOT NULL DEFAULT 0")
                .contains("predecessor_run_id BIGINT NULL")
                .contains("CONSTRAINT uk_agent_runs_predecessor UNIQUE (predecessor_run_id)");
    }
}

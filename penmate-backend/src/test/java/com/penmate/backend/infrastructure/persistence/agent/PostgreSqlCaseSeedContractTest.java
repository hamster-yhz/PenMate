package com.penmate.backend.infrastructure.persistence.agent;

import com.penmate.backend.testinfra.PostgreSqlTestDatabase;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PostgreSqlCaseSeedContractTest {

    private static final List<String> SEED_SCRIPTS = List.of(
            "db/cases/seed/01_book_and_story_bible.sql",
            "db/cases/seed/02_agent.sql",
            "db/cases/seed/03_rag_and_storage.sql",
            "db/cases/seed/04_plugin.sql",
            "db/cases/seed/05_boundary.sql",
            "db/cases/seed/06_concurrency.sql"
    );

    @Test
    void seeds_and_cleans_explicit_postgresql_cases() throws Exception {
        DataSource dataSource = PostgreSqlTestDatabase.migratedDataSource("postgresql_case_seed");
        for (String script : SEED_SCRIPTS) execute(dataSource, script);

        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM novel_projects WHERE project_id BETWEEN 920000 AND 922999",
                Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject(
                "SELECT attributes_json ->> 'role' FROM story_bible_nodes WHERE node_id = 920601",
                String.class)).isEqualTo("protagonist");
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM agent_runs WHERE run_id BETWEEN 920000 AND 922999",
                Integer.class)).isEqualTo(3);
        assertThat(jdbc.queryForObject(
                "SELECT metadata_json ->> 'storyBibleNodeId' FROM rag_chunks WHERE chunk_id = 920921",
                Long.class)).isEqualTo(920601L);
        assertThat(jdbc.queryForObject(
                "SELECT config_json ->> 'mode' FROM plugin_project_installs WHERE plugin_install_id = 920941",
                String.class)).isEqualTo("strict");

        execute(dataSource, "db/cases/cleanup.sql");
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM novel_projects WHERE project_id BETWEEN 920000 AND 922999",
                Integer.class)).isZero();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM agent_sessions WHERE session_id BETWEEN 920000 AND 922999",
                Integer.class)).isZero();
    }

    private void execute(DataSource dataSource, String resource) throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource(resource));
        }
    }
}

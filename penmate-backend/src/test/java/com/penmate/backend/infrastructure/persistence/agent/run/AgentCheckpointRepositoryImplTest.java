package com.penmate.backend.infrastructure.persistence.agent.run;

import com.penmate.backend.domain.agent.run.model.AgentCheckpoint;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

class AgentCheckpointRepositoryImplTest {

    private static final String JDBC_URL = "jdbc:h2:mem:agent_checkpoint_repository;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
    private static final String MIGRATION_DIR = "target/test-migrations/agent-checkpoint-repository";

    private AgentCheckpointRepositoryImpl repository;

    @BeforeEach
    void setUp() throws Exception {
        DataSource dataSource = new UnpooledDataSource("org.h2.Driver", JDBC_URL, "sa", "");
        SqlSessionFactory sqlSessionFactory = buildSqlSessionFactory(dataSource);
        recreateSchema(dataSource);
        repository = new AgentCheckpointRepositoryImpl(sqlSessionFactory);
    }

    @Test
    void finds_latest_checkpoint_by_run() {
        repository.save(new AgentCheckpoint(80001L, 70001L, 1L, 5L, "{\"phase\":\"context\"}", 19, null));
        repository.save(new AgentCheckpoint(80002L, 70001L, 2L, 9L, "{\"phase\":\"tool_call\"}", 21, null));

        AgentCheckpoint latest = repository.findLatest(70001L);

        assertThat(latest.checkpointNo()).isEqualTo(2L);
        assertThat(latest.lastEventSeq()).isEqualTo(9L);
    }

    @Test
    void retains_only_the_latest_two_checkpoints() {
        repository.save(new AgentCheckpoint(80001L, 70001L, 1L, 5L, "{}", 2, null));
        repository.save(new AgentCheckpoint(80002L, 70001L, 2L, 9L, "{}", 2, null));
        repository.save(new AgentCheckpoint(80003L, 70001L, 3L, 12L, "{}", 2, null));

        assertThat(repository.deleteOlderThanLatest(70001L, 2)).isEqualTo(1);
        assertThat(repository.findLatest(70001L, 2))
                .extracting(AgentCheckpoint::checkpointNo)
                .containsExactly(3L, 2L);
    }

    private SqlSessionFactory buildSqlSessionFactory(DataSource dataSource) {
        Configuration configuration = new Configuration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(AgentCheckpointMapper.class);
        configuration.setEnvironment(new Environment("test", new JdbcTransactionFactory(), dataSource));
        return new SqlSessionFactoryBuilder().build(configuration);
    }

    private void recreateSchema(DataSource dataSource) throws Exception {
        try (Connection connection = dataSource.getConnection();
            Statement statement = connection.createStatement()) {
            statement.execute("DROP ALL OBJECTS");
            statement.execute("DROP TABLE IF EXISTS flyway_schema_history");
            statement.execute("DROP TABLE IF EXISTS agent_events");
            statement.execute("DROP TABLE IF EXISTS agent_run_inputs");
            statement.execute("DROP TABLE IF EXISTS agent_artifacts");
            statement.execute("DROP TABLE IF EXISTS agent_todo_projections");
            statement.execute("DROP TABLE IF EXISTS agent_tool_call_projections");
            statement.execute("DROP TABLE IF EXISTS agent_run_projections");
            statement.execute("DROP TABLE IF EXISTS agent_checkpoints");
            statement.execute("DROP TABLE IF EXISTS agent_runs");
            statement.execute("DROP TABLE IF EXISTS agent_messages");
            statement.execute("DROP TABLE IF EXISTS agent_turns");
            statement.execute("DROP TABLE IF EXISTS agent_session_style_bindings");
            statement.execute("DROP TABLE IF EXISTS agent_sessions");
            statement.execute("DROP TABLE IF EXISTS ops_async_jobs");
            statement.execute("DROP TABLE IF EXISTS ops_migrations");
        }
        prepareMigration();
        Flyway.configure()
                .dataSource(dataSource)
                .locations("filesystem:" + MIGRATION_DIR)
                .load()
                .migrate();
        executeSqlResource(dataSource, "src/test/resources/db/cases/seed_agent_run_runtime_base.sql");
    }

    private void prepareMigration() throws Exception {
        Path migrationDir = Path.of(MIGRATION_DIR);
        Files.createDirectories(migrationDir);
        Files.copy(
                Path.of("src/main/resources/db/migration/V11__init_agent_and_ops_domains.sql"),
                migrationDir.resolve("V11__init_agent_and_ops_domains.sql"),
                StandardCopyOption.REPLACE_EXISTING
        );
        Files.copy(
                Path.of("src/main/resources/db/migration/V17__harden_agent_checkpoints.sql"),
                migrationDir.resolve("V17__harden_agent_checkpoints.sql"),
                StandardCopyOption.REPLACE_EXISTING
        );
    }

    private void executeSqlResource(DataSource dataSource, String path) throws Exception {
        String sql = Files.readString(Path.of(path)).replace("NOW(3)", "CURRENT_TIMESTAMP");
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            for (String statementSql : sql.split(";")) {
                String trimmed = statementSql.trim();
                if (!trimmed.isEmpty()) {
                    statement.execute(trimmed);
                }
            }
        }
    }
}

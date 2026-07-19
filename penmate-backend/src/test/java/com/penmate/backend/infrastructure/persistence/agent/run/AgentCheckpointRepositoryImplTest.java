package com.penmate.backend.infrastructure.persistence.agent.run;

import com.penmate.backend.domain.agent.run.model.AgentCheckpoint;
import com.penmate.backend.testinfra.PostgreSqlTestDatabase;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class AgentCheckpointRepositoryImplTest {

    private AgentCheckpointRepositoryImpl repository;
    private DataSource dataSource;

    @BeforeEach
    void setUp() throws Exception {
        dataSource = PostgreSqlTestDatabase.migratedDataSource("agent_checkpoint_repository");
        SqlSessionFactory sqlSessionFactory = buildSqlSessionFactory(dataSource);
        executeSqlResource(dataSource, "src/test/resources/db/cases/seed_agent_run_runtime_base.sql");
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

    @Test
    void archives_and_expires_only_terminal_hot_checkpoints() throws Exception {
        String state = "{\"phase\":\"done\"}";
        repository.save(new AgentCheckpoint(80001L, 70001L, 1L, 5L, state, state.length(), null));
        Instant now = java.time.LocalDateTime.of(2026, 7, 17, 3, 15).toInstant(java.time.ZoneOffset.UTC);
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("UPDATE agent_runs SET run_status='DONE', finished_at=TIMESTAMP '2026-07-09 03:15:00' "
                    + "WHERE run_id=70001");
        }

        assertThat(repository.findTerminalHotBefore(now.minus(7, java.time.temporal.ChronoUnit.DAYS), 10)).hasSize(1);
        assertThat(repository.markCold(
                80001L, "{\"externalState\":true}", "checkpoint-key", "a".repeat(64),
                now, now.plus(90, java.time.temporal.ChronoUnit.DAYS))).isEqualTo(1);

        AgentCheckpoint archived = repository.findLatest(70001L);
        assertThat(archived.isCold()).isTrue();
        assertThat(archived.stateObjectKey()).isEqualTo("checkpoint-key");
        assertThat(repository.findExpiredCold(now.plus(91, java.time.temporal.ChronoUnit.DAYS), 10)).hasSize(1);
        assertThat(repository.deleteCold(80001L)).isEqualTo(1);
    }

    private SqlSessionFactory buildSqlSessionFactory(DataSource dataSource) {
        Configuration configuration = new Configuration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(AgentCheckpointMapper.class);
        configuration.setEnvironment(new Environment("test", new JdbcTransactionFactory(), dataSource));
        return new SqlSessionFactoryBuilder().build(configuration);
    }

    private void executeSqlResource(DataSource dataSource, String path) throws Exception {
        String sql = Files.readString(Path.of(path));
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

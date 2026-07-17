package com.penmate.backend.infrastructure.persistence.agent.run;

import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
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
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class AgentRunExecutionTokenRepositoryImplTest {

    private static final String JDBC_URL =
            "jdbc:h2:mem:agent_run_execution_token_repository;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
    private static final String MIGRATION_DIR = "target/test-migrations/agent-run-execution-token-repository";

    private DataSource dataSource;
    private SqlSessionFactory sessions;

    @BeforeEach
    void setUp() throws Exception {
        dataSource = new UnpooledDataSource("org.h2.Driver", JDBC_URL, "sa", "");
        prepareMigrations();
        Flyway.configure().cleanDisabled(false).dataSource(dataSource)
                .locations("filesystem:" + MIGRATION_DIR).load().clean();
        Flyway.configure().dataSource(dataSource)
                .locations("filesystem:" + MIGRATION_DIR).load().migrate();

        Configuration configuration = new Configuration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(AgentRunMapper.class);
        configuration.setEnvironment(new Environment("test", new JdbcTransactionFactory(), dataSource));
        sessions = new SqlSessionFactoryBuilder().build(configuration);
    }

    @Test
    void execution_token_must_match_a_running_unexpired_lease() throws Exception {
        LocalDateTime now = LocalDateTime.now();
        insertRun(now.plusMinutes(1));
        try (SqlSession session = sessions.openSession(true)) {
            AgentRunRepositoryImpl repository = new AgentRunRepositoryImpl(session.getMapper(AgentRunMapper.class));

            assertThat(repository.ownsExecutionToken(70001L, 7L, now)).isTrue();
            assertThat(repository.ownsExecutionToken(70001L, 6L, now)).isFalse();
            assertThat(repository.ownsExecutionToken(70001L, 7L, now.plusMinutes(2))).isFalse();
        }
    }

    @Test
    void cancelling_a_recoverable_run_revokes_the_execution_token() throws Exception {
        LocalDateTime now = LocalDateTime.now();
        insertRun(now.plusMinutes(1));
        try (SqlSession session = sessions.openSession(true)) {
            AgentRunRepositoryImpl repository = new AgentRunRepositoryImpl(session.getMapper(AgentRunMapper.class));

            assertThat(repository.cancelRecoverable(
                    70001L, "AGENT_RUN_CANCELLED", "Cancelled by user")).isTrue();
            assertThat(repository.ownsExecutionToken(70001L, 7L, now)).isFalse();
            assertThat(runStatus()).isEqualTo("CANCELLED");
            assertThat(repository.cancelRecoverable(
                    70001L, "AGENT_RUN_CANCELLED", "Cancelled by user")).isFalse();
        }
    }

    private void insertRun(LocalDateTime leaseUntil) throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO agent_runs(
                         run_id, project_id, session_id, turn_id, owner_user_id, run_status, run_phase,
                         lease_owner, lease_until, execution_token
                     ) VALUES (70001, 101, 90001, 50001, 201, 'RUNNING', 'executing', 'worker', ?, 7)
                     """)) {
            statement.setObject(1, leaseUntil);
            statement.executeUpdate();
        }
    }

    private String runStatus() throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT run_status FROM agent_runs WHERE run_id = 70001");
             ResultSet result = statement.executeQuery()) {
            assertThat(result.next()).isTrue();
            return result.getString(1);
        }
    }

    private void prepareMigrations() throws Exception {
        Path directory = Path.of(MIGRATION_DIR);
        Files.createDirectories(directory);
        Files.copy(Path.of("src/main/resources/db/migration/V11__init_agent_and_ops_domains.sql"),
                directory.resolve("V11__init_agent_and_ops_domains.sql"), StandardCopyOption.REPLACE_EXISTING);
        Files.copy(Path.of("src/main/resources/db/migration/V17__add_agent_run_leases.sql"),
                directory.resolve("V17__add_agent_run_leases.sql"), StandardCopyOption.REPLACE_EXISTING);
    }
}

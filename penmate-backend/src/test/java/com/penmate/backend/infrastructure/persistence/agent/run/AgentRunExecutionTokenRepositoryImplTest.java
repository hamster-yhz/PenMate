package com.penmate.backend.infrastructure.persistence.agent.run;

import com.penmate.backend.domain.agent.run.model.AgentRunLease;
import com.penmate.backend.domain.agent.run.model.AgentRunStatus;
import com.penmate.backend.testinfra.PostgreSqlTestDatabase;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

class AgentRunExecutionTokenRepositoryImplTest {

    private DataSource dataSource;
    private SqlSessionFactory sessions;

    @BeforeEach
    void setUp() {
        dataSource = PostgreSqlTestDatabase.migratedDataSource(
                "agent_run_execution_token_repository");

        Configuration configuration = new Configuration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(AgentRunMapper.class);
        configuration.setEnvironment(new Environment("test", new JdbcTransactionFactory(), dataSource));
        sessions = new SqlSessionFactoryBuilder().build(configuration);
    }

    @Test
    void execution_token_must_match_a_running_unexpired_lease() throws Exception {
        Instant now = Instant.now();
        insertRun(now.plus(1, java.time.temporal.ChronoUnit.MINUTES));
        try (SqlSession session = sessions.openSession(true)) {
            AgentRunRepositoryImpl repository = new AgentRunRepositoryImpl(session.getMapper(AgentRunMapper.class));

            assertThat(repository.ownsExecutionToken(70001L, 7L, now)).isTrue();
            assertThat(repository.ownsExecutionToken(70001L, 6L, now)).isFalse();
            assertThat(repository.ownsExecutionToken(70001L, 7L, now.plus(2, java.time.temporal.ChronoUnit.MINUTES))).isFalse();
        }
    }

    @Test
    void cancelling_a_recoverable_run_revokes_the_execution_token() throws Exception {
        Instant now = Instant.now();
        insertRun(now.plus(1, java.time.temporal.ChronoUnit.MINUTES));
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

    @Test
    void acquiring_a_pending_run_returns_the_atomically_updated_lease() throws Exception {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        Instant leaseUntil = now.plusSeconds(30);
        insertPendingRun();
        try (SqlSession session = sessions.openSession(true)) {
            AgentRunRepositoryImpl repository = new AgentRunRepositoryImpl(session.getMapper(AgentRunMapper.class));

            AgentRunLease lease = repository.tryAcquireLease(70002L, "worker-2", now, leaseUntil)
                    .orElseThrow();

            assertThat(lease.runId()).isEqualTo(70002L);
            assertThat(lease.owner()).isEqualTo("worker-2");
            assertThat(lease.executionToken()).isEqualTo(1L);
            assertThat(lease.attemptCount()).isEqualTo(1);
            assertThat(lease.acquiredFrom()).isEqualTo(AgentRunStatus.PENDING);
            assertThat(lease.expiresAt()).isEqualTo(leaseUntil);
            assertThat(runStatus(70002L)).isEqualTo("RUNNING");
        }
    }

    @Test
    void suspending_an_expired_run_persists_the_postgresql_retry_timestamp() throws Exception {
        Instant now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
        Instant nextRetryAt = now.plusSeconds(5);
        insertRun(now.minusSeconds(1));
        try (SqlSession session = sessions.openSession(true)) {
            AgentRunRepositoryImpl repository = new AgentRunRepositoryImpl(session.getMapper(AgentRunMapper.class));

            assertThat(repository.suspendExpiredRuns(now, nextRetryAt, 3)).isEqualTo(1);
            assertThat(runStatus()).isEqualTo("SUSPENDED");
            assertThat(runNextRetryAt()).isEqualTo(nextRetryAt);
        }
    }

    private void insertRun(Instant leaseUntil) throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO agent_runs(
                         run_id, project_id, session_id, turn_id, owner_user_id, run_status, run_phase,
                         lease_owner, lease_until, execution_token
                     ) VALUES (70001, 101, 90001, 50001, 201, 'RUNNING', 'executing', 'worker', ?, 7)
                     """)) {
            statement.setTimestamp(1, Timestamp.from(leaseUntil));
            statement.executeUpdate();
        }
    }

    private String runStatus() throws Exception {
        return runStatus(70001L);
    }

    private String runStatus(long runId) throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT run_status FROM agent_runs WHERE run_id = ?")) {
            statement.setLong(1, runId);
            try (ResultSet result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                return result.getString(1);
            }
        }
    }

    private Instant runNextRetryAt() throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT next_retry_at FROM agent_runs WHERE run_id = 70001");
             ResultSet result = statement.executeQuery()) {
            assertThat(result.next()).isTrue();
            return result.getTimestamp(1).toInstant();
        }
    }

    private void insertPendingRun() throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO agent_runs(
                         run_id, project_id, session_id, turn_id, owner_user_id, run_status, run_phase
                     ) VALUES (70002, 101, 90002, 50002, 201, 'PENDING', 'created')
                     """)) {
            statement.executeUpdate();
        }
    }
}

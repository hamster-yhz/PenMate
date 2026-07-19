package com.penmate.backend.infrastructure.persistence.agent.run;

import com.penmate.backend.domain.agent.run.model.AgentToolCallExecution;
import com.penmate.backend.domain.agent.run.model.AgentToolCallExecutionStatus;
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
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class AgentToolCallExecutionRepositoryImplTest {

    private SqlSessionFactory sessions;

    @BeforeEach
    void setUp() {
        DataSource dataSource = PostgreSqlTestDatabase.migratedDataSource(
                "agent_tool_call_execution_repository");

        Configuration configuration = new Configuration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(AgentToolCallExecutionMapper.class);
        configuration.setEnvironment(new Environment("test", new JdbcTransactionFactory(), dataSource));
        sessions = new SqlSessionFactoryBuilder().build(configuration);
    }

    @Test
    void stores_and_conditionally_finishes_execution() {
        AgentToolCallExecution started = started(1001L, 7L);
        try (SqlSession session = sessions.openSession(true)) {
            AgentToolCallExecutionRepositoryImpl repository = repository(session);
            assertThat(repository.tryInsertStarted(started)).isTrue();
            assertThat(repository.markFinished(1001L, 7L, AgentToolCallExecutionStatus.SUCCEEDED,
                    "{\"status\":\"SUCCESS\"}", null, null, Instant.now())).isEqualTo(1);
            assertThat(repository.markFinished(1001L, 7L, AgentToolCallExecutionStatus.FAILED,
                    null, "late", "late", Instant.now())).isZero();

            AgentToolCallExecution stored = repository.find(11L, "call-1");
            assertThat(stored.status()).isEqualTo(AgentToolCallExecutionStatus.SUCCEEDED);
            assertThat(stored.resultJson()).contains("SUCCESS");
        }
    }

    @Test
    void unique_run_and_tool_call_claim_has_one_winner_under_concurrency() throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<Boolean>> claims = List.of(
                    pool.submit(() -> claim(started(2001L, 7L), ready, start)),
                    pool.submit(() -> claim(started(2002L, 8L), ready, start))
            );
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            assertThat(claims.stream().map(this::get).filter(Boolean::booleanValue).count()).isEqualTo(1L);
            try (SqlSession session = sessions.openSession(true)) {
                AgentToolCallExecution stored = repository(session).find(11L, "call-1");
                assertThat(stored).isNotNull();
                assertThat(stored.executionToken()).isIn(7L, 8L);
            }
        } finally {
            start.countDown();
            pool.shutdownNow();
        }
    }

    private boolean claim(AgentToolCallExecution execution, CountDownLatch ready,
                          CountDownLatch start) throws Exception {
        ready.countDown();
        assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
        try (SqlSession session = sessions.openSession(true)) {
            return repository(session).tryInsertStarted(execution);
        }
    }

    private Boolean get(Future<Boolean> future) {
        try {
            return future.get(5, TimeUnit.SECONDS);
        } catch (Exception ex) {
            throw new AssertionError(ex);
        }
    }

    private AgentToolCallExecutionRepositoryImpl repository(SqlSession session) {
        return new AgentToolCallExecutionRepositoryImpl(session.getMapper(AgentToolCallExecutionMapper.class));
    }

    private AgentToolCallExecution started(Long executionId, Long token) {
        return AgentToolCallExecution.started(executionId, 11L, "call-1", "test_tool",
                "a".repeat(64), token, Instant.now());
    }
}

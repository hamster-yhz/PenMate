package com.penmate.backend.infrastructure.persistence.agent.run;

import com.penmate.backend.domain.agent.run.model.AgentToolCallExecution;
import com.penmate.backend.domain.agent.run.model.AgentToolCallExecutionStatus;
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
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class AgentToolCallExecutionRepositoryImplTest {

    private static final String JDBC_URL =
            "jdbc:h2:mem:agent_tool_call_execution_repository;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
    private static final String MIGRATION_DIR = "target/test-migrations/agent-tool-call-execution-repository";

    private SqlSessionFactory sessions;

    @BeforeEach
    void setUp() throws Exception {
        DataSource dataSource = new UnpooledDataSource("org.h2.Driver", JDBC_URL, "sa", "");
        prepareMigration();
        Flyway.configure().cleanDisabled(false).dataSource(dataSource)
                .locations("filesystem:" + MIGRATION_DIR).load().clean();
        Flyway.configure().dataSource(dataSource)
                .locations("filesystem:" + MIGRATION_DIR).load().migrate();

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
                    "{\"status\":\"SUCCESS\"}", null, null, LocalDateTime.now())).isEqualTo(1);
            assertThat(repository.markFinished(1001L, 7L, AgentToolCallExecutionStatus.FAILED,
                    null, "late", "late", LocalDateTime.now())).isZero();

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
                "a".repeat(64), token, LocalDateTime.now());
    }

    private void prepareMigration() throws Exception {
        Path directory = Path.of(MIGRATION_DIR);
        Files.createDirectories(directory);
        Files.copy(Path.of("src/main/resources/db/migration/V20__add_agent_tool_call_executions.sql"),
                directory.resolve("V20__add_agent_tool_call_executions.sql"),
                StandardCopyOption.REPLACE_EXISTING);
    }
}

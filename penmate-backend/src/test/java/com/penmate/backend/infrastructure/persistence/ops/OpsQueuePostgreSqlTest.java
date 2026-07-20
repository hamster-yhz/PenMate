package com.penmate.backend.infrastructure.persistence.ops;

import com.penmate.backend.domain.ops.model.OpsAsyncJob;
import com.penmate.backend.testinfra.PostgreSqlTestDatabase;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

class OpsQueuePostgreSqlTest {
    private static SqlSessionFactory sessions;

    @BeforeAll
    static void setup() {
        DataSource dataSource = PostgreSqlTestDatabase.migratedDataSource("ops_queue");
        Configuration configuration = new Configuration(new Environment("test", new JdbcTransactionFactory(), dataSource));
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(OpsMapper.class);
        sessions = new SqlSessionFactoryBuilder().build(configuration);
    }

    @BeforeEach
    void clear() {
        try (SqlSession session = sessions.openSession(true)) {
            try (var statement = session.getConnection().createStatement()) {
                statement.execute("DELETE FROM ops_async_jobs");
            } catch (Exception exception) {
                throw new IllegalStateException(exception);
            }
        }
    }

    @Test
    void claimsExactlyOnceAcrossConcurrentWorkersAndCompletes() throws Exception {
        insert(1001L, "project:1:revision:1");
        var pool = Executors.newFixedThreadPool(2);
        try {
            List<Callable<OpsAsyncJob>> calls = List.of(() -> claim("worker-a"), () -> claim("worker-b"));
            List<OpsAsyncJob> claimed = pool.invokeAll(calls).stream().map(future -> {
                try { return future.get(); } catch (Exception exception) { throw new IllegalStateException(exception); }
            }).filter(java.util.Objects::nonNull).toList();
            assertThat(claimed).singleElement().satisfies(job -> {
                assertThat(job.getStatus()).isEqualTo("RUNNING");
                assertThat(job.getAttemptCount()).isEqualTo(1);
                assertThat(job.getLeaseUntil()).isAfter(Instant.now());
            });
            OpsAsyncJob job = claimed.getFirst();
            try (SqlSession session = sessions.openSession(true)) {
                OpsMapper mapper = session.getMapper(OpsMapper.class);
                assertThat(mapper.heartbeat(job.getJobId(), job.getLeaseOwner(), 5L, 10L, "halfway")).isEqualTo(1);
                assertThat(mapper.completeJob(job.getJobId(), job.getLeaseOwner(), "{\"ok\":true}")).isEqualTo(1);
                assertThat(mapper.findJobById(job.getJobId()).getStatus()).isEqualTo("SUCCEEDED");
            }
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void failedAttemptMovesToRetryWaitAndCanBeCancelled() {
        insert(1002L, "project:1:revision:2");
        OpsAsyncJob claimed = claim("worker-a");
        try (SqlSession session = sessions.openSession(true)) {
            OpsMapper mapper = session.getMapper(OpsMapper.class);
            assertThat(mapper.failJob(1002L, "worker-a", "TEMPORARY", "timeout")).isEqualTo(1);
            OpsAsyncJob waiting = mapper.findJobById(1002L);
            assertThat(waiting.getStatus()).isEqualTo("RETRY_WAIT");
            assertThat(waiting.getScheduledAt()).isAfter(Instant.now());
            assertThat(mapper.requestCancel(1002L)).isEqualTo(1);
            assertThat(mapper.findJobById(1002L).getStatus()).isEqualTo("CANCELLED");
        }
    }

    private void insert(Long id, String key) {
        OpsAsyncJob job = new OpsAsyncJob();
        job.setJobId(id);
        job.setJobType("RAG_REBUILD_PROJECT");
        job.setBizKey(key);
        job.setProjectId(1L);
        job.setPayloadJson("{}");
        job.setStatus("QUEUED");
        job.setAttemptCount(0);
        job.setMaxAttempts(5);
        job.setScheduledAt(Instant.now());
        job.setProgressCurrent(0L);
        job.setProgressTotal(0L);
        try (SqlSession session = sessions.openSession(true)) {
            assertThat(session.getMapper(OpsMapper.class).insertJob(job)).isEqualTo(1);
        }
    }

    private OpsAsyncJob claim(String worker) {
        try (SqlSession session = sessions.openSession(true)) {
            return session.getMapper(OpsMapper.class).claimNext(worker);
        }
    }
}

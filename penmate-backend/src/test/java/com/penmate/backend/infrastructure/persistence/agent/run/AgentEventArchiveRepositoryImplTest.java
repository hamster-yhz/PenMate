package com.penmate.backend.infrastructure.persistence.agent.run;

import com.penmate.backend.domain.agent.run.model.AgentEventArchive;
import com.penmate.backend.testinfra.PostgreSqlTestDatabase;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class AgentEventArchiveRepositoryImplTest {

    @Test
    void persists_verifies_and_expires_archive_manifest() throws Exception {
        var dataSource = PostgreSqlTestDatabase.migratedDataSource("agent_event_archive_repository");
        Configuration configuration = new Configuration(new Environment(
                "test", new JdbcTransactionFactory(), dataSource));
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(AgentEventArchiveMapper.class);
        var factory = new SqlSessionFactoryBuilder().build(configuration);
        try (SqlSession session = factory.openSession(true)) {
            var repository = new AgentEventArchiveRepositoryImpl(
                    session.getMapper(AgentEventArchiveMapper.class));
            Instant now = java.time.LocalDateTime.of(2026, 7, 17, 3, 0).toInstant(java.time.ZoneOffset.UTC);
            AgentEventArchive archive = new AgentEventArchive(
                    99L, 70L, 1L, 2L, 2, "key", 100L, "a".repeat(64),
                    "UPLOADED", null, now.plus(90, java.time.temporal.ChronoUnit.DAYS), null);

            assertThat(repository.upsertUploaded(archive)).isGreaterThan(0);
            assertThat(repository.markVerified(99L, now)).isEqualTo(1);
            assertThat(repository.findByRunId(70L).verified()).isTrue();
            assertThat(repository.findExpiredVerified(now.plus(91, java.time.temporal.ChronoUnit.DAYS), 10)).hasSize(1);
            assertThat(repository.delete(99L)).isEqualTo(1);
        }
    }
}

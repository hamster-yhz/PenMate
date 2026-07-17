package com.penmate.backend.infrastructure.persistence.agent.run;

import com.penmate.backend.domain.agent.run.model.AgentEventArchive;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class AgentEventArchiveRepositoryImplTest {

    @Test
    void persists_verifies_and_expires_archive_manifest() throws Exception {
        String url = "jdbc:h2:mem:agent_event_archive;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
        Path directory = Path.of("target/test-migrations/agent-event-archive");
        Files.createDirectories(directory);
        Files.copy(Path.of("src/main/resources/db/migration/V11__init_agent_and_ops_domains.sql"),
                directory.resolve("V11__init_agent_and_ops_domains.sql"), StandardCopyOption.REPLACE_EXISTING);
        Files.copy(Path.of("src/main/resources/db/migration/V20__add_agent_event_archives.sql"),
                directory.resolve("V20__add_agent_event_archives.sql"), StandardCopyOption.REPLACE_EXISTING);
        Flyway.configure().dataSource(url, "sa", "").locations("filesystem:" + directory).load().migrate();
        var dataSource = new UnpooledDataSource("org.h2.Driver", url, "sa", "");
        Configuration configuration = new Configuration(new Environment(
                "test", new JdbcTransactionFactory(), dataSource));
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(AgentEventArchiveMapper.class);
        var factory = new SqlSessionFactoryBuilder().build(configuration);
        var repository = new AgentEventArchiveRepositoryImpl(
                factory.openSession(true).getMapper(AgentEventArchiveMapper.class));
        LocalDateTime now = LocalDateTime.of(2026, 7, 17, 3, 0);
        AgentEventArchive archive = new AgentEventArchive(
                99L, 70L, 1L, 2L, 2, "key", 100L, "a".repeat(64),
                "UPLOADED", null, now.plusDays(90), null);

        assertThat(repository.upsertUploaded(archive)).isGreaterThan(0);
        assertThat(repository.markVerified(99L, now)).isEqualTo(1);
        assertThat(repository.findByRunId(70L).verified()).isTrue();
        assertThat(repository.findExpiredVerified(now.plusDays(91), 10)).hasSize(1);
        assertThat(repository.delete(99L)).isEqualTo(1);
    }
}

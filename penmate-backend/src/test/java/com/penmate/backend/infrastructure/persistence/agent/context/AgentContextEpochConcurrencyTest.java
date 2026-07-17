package com.penmate.backend.infrastructure.persistence.agent.context;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.penmate.backend.application.agent.context.AgentContextEpochService;
import com.penmate.backend.application.agent.context.ContextEpochSnapshotCache;
import com.penmate.backend.domain.shared.service.BusinessIdGenerator;
import com.penmate.backend.domain.shared.service.ObjectStorageService;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.Statement;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentContextEpochConcurrencyTest {

    private static final String JDBC_URL = "jdbc:h2:mem:context_epoch_concurrency;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
    private static final String MIGRATION_DIR = "target/test-migrations/context-epoch-concurrency";
    private static DataSource dataSource;

    @BeforeAll
    static void setUpSchema() throws Exception {
        dataSource = new UnpooledDataSource("org.h2.Driver", JDBC_URL, "sa", "");
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("DROP ALL OBJECTS");
        }
        Path migrationDir = Path.of(MIGRATION_DIR);
        Files.createDirectories(migrationDir);
        Files.copy(
                Path.of("src/main/resources/db/migration/V4__init_novel_volume_and_chapter.sql"),
                migrationDir.resolve("V4__init_novel_volume_and_chapter.sql"),
                StandardCopyOption.REPLACE_EXISTING
        );
        Files.copy(
                Path.of("src/main/resources/db/migration/V11__init_agent_and_ops_domains.sql"),
                migrationDir.resolve("V11__init_agent_and_ops_domains.sql"),
                StandardCopyOption.REPLACE_EXISTING
        );
        Files.copy(
                Path.of("src/main/resources/db/migration/V16__add_agent_run_dependency_revisions.sql"),
                migrationDir.resolve("V16__add_agent_run_dependency_revisions.sql"),
                StandardCopyOption.REPLACE_EXISTING
        );
        Flyway.configure().dataSource(dataSource).locations("filesystem:" + MIGRATION_DIR).load().migrate();
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("""
                    INSERT INTO agent_sessions(session_id, project_id, owner_user_id, title)
                    VALUES(20, 10, 7, 'Session')
                    """);
            statement.execute("""
                    INSERT INTO agent_runs(run_id, project_id, session_id, turn_id, owner_user_id, run_status, run_phase)
                    VALUES(30, 10, 20, 40, 7, 'RUNNING', 'CONTEXT'),
                          (31, 10, 20, 41, 7, 'RUNNING', 'CONTEXT')
                    """);
        }
    }

    @Test
    void concurrent_identical_bindings_create_one_epoch_and_bind_both_runs() throws Exception {
        SqlSessionFactory factory = sqlSessionFactory(dataSource);
        AgentContextEpochMapper mapper = new SqlSessionTemplate(factory).getMapper(AgentContextEpochMapper.class);
        AgentContextEpochRepositoryImpl repository = new AgentContextEpochRepositoryImpl(mapper);
        AtomicLong ids = new AtomicLong(900L);
        BusinessIdGenerator idGenerator = ids::incrementAndGet;
        ObjectStorageService storage = mock(ObjectStorageService.class);
        when(storage.putText(anyString(), anyString(), anyString())).thenAnswer(invocation -> {
            String content = invocation.getArgument(1);
            return new ObjectStorageService.PutObjectResult(
                    "etag", (long) content.getBytes(StandardCharsets.UTF_8).length, null);
        });
        AgentContextEpochService service = new AgentContextEpochService(
                repository, idGenerator, storage, new ObjectMapper(), mock(ContextEpochSnapshotCache.class));
        TransactionTemplate transactions = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        CyclicBarrier start = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<AgentContextEpochService.Binding> first = executor.submit(() -> {
                start.await();
                return transactions.execute(status -> service.bind(request(30L)));
            });
            Future<AgentContextEpochService.Binding> second = executor.submit(() -> {
                start.await();
                return transactions.execute(status -> service.bind(request(31L)));
            });

            List<AgentContextEpochService.Binding> bindings = List.of(first.get(), second.get());

            assertThat(bindings).extracting(binding -> binding.epoch().epochId()).containsOnly(901L);
            assertThat(bindings).extracting(AgentContextEpochService.Binding::reused)
                    .containsExactlyInAnyOrder(false, true);
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM agent_context_epochs", Integer.class)).isEqualTo(1);
            assertThat(jdbc.queryForList(
                    "SELECT context_epoch_id FROM agent_runs ORDER BY run_id", Long.class))
                    .containsExactly(901L, 901L);
        } finally {
            executor.shutdownNow();
        }
    }

    private AgentContextEpochService.BindRequest request(Long runId) {
        return new AgentContextEpochService.BindRequest(
                20L, runId, 4L, 3L, 40L, 2L, "RETRIEVAL", null, 0L,
                "prompt", "skills", "tools", "{\"catalog\":[]}"
        );
    }

    private SqlSessionFactory sqlSessionFactory(DataSource source) {
        Configuration configuration = new Configuration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(AgentContextEpochMapper.class);
        configuration.setEnvironment(new Environment("test", new SpringManagedTransactionFactory(), source));
        return new SqlSessionFactoryBuilder().build(configuration);
    }
}

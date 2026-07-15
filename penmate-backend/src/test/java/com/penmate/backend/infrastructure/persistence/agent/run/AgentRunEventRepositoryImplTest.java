package com.penmate.backend.infrastructure.persistence.agent.run;

import com.penmate.backend.domain.agent.run.model.AgentEvent;
import com.penmate.backend.domain.shared.service.BusinessIdGenerator;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.Statement;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class AgentRunEventRepositoryImplTest {

    private static final String JDBC_URL = "jdbc:h2:mem:agent_run_event_repository;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
    private static final String MIGRATION_DIR = "target/test-migrations/agent-run-event-repository";

    private AgentRunEventRepositoryImpl repository;
    private SqlSessionFactory sqlSessionFactory;

    @BeforeEach
    void setUp() throws Exception {
        DataSource dataSource = new UnpooledDataSource("org.h2.Driver", JDBC_URL, "sa", "");
        sqlSessionFactory = buildSqlSessionFactory(dataSource);
        recreateSchema(dataSource);
        repository = new AgentRunEventRepositoryImpl(
                sqlSessionFactory,
                new TransactionTemplate(new DataSourceTransactionManager(dataSource)),
                incrementingIds(10000L)
        );
    }

    @Test
    void append_event_locks_run_and_increments_sequence() {
        AgentEvent first = repository.append(70001L, "run.started", "{\"phase\":\"created\"}");
        AgentEvent second = repository.append(70001L, "run.phase.changed", "{\"phase\":\"context\"}");

        assertThat(first.sequence()).isEqualTo(1L);
        assertThat(second.sequence()).isEqualTo(2L);
        assertThat(repository.listAfter(70001L, 0L)).extracting(AgentEvent::eventType)
                .containsExactly("run.started", "run.phase.changed");
    }

    @Test
    void concurrent_appends_for_same_run_get_unique_ordered_sequences() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<AgentEvent>> futures = List.of(
                    executor.submit(appendDelta("a")),
                    executor.submit(appendDelta("b"))
            );

            List<Long> sequences = futures.stream()
                    .map(this::uncheckedGet)
                    .map(AgentEvent::sequence)
                    .sorted()
                    .toList();

            assertThat(sequences).containsExactly(1L, 2L);
        } finally {
            executor.shutdownNow();
        }
    }

    private Callable<AgentEvent> appendDelta(String text) {
        return () -> repository.append(70001L, "message.delta", "{\"text\":\"" + text + "\"}");
    }

    private AgentEvent uncheckedGet(Future<AgentEvent> future) {
        try {
            return future.get();
        } catch (Exception ex) {
            throw new AssertionError(ex);
        }
    }

    private SqlSessionFactory buildSqlSessionFactory(DataSource dataSource) {
        Configuration configuration = new Configuration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(AgentRunEventMapper.class);
        configuration.setEnvironment(new Environment("test", new JdbcTransactionFactory(), dataSource));
        return new SqlSessionFactoryBuilder().build(configuration);
    }

    private void recreateSchema(DataSource dataSource) throws Exception {
        try (Connection connection = dataSource.getConnection();
            Statement statement = connection.createStatement()) {
            statement.execute("DROP ALL OBJECTS");
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

    private BusinessIdGenerator incrementingIds(long start) {
        AtomicLong next = new AtomicLong(start);
        return next::incrementAndGet;
    }
}

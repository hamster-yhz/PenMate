package com.penmate.backend.infrastructure.persistence.ledger;

import com.penmate.backend.domain.ledger.model.ProjectLedger;
import com.penmate.backend.testinfra.PostgreSqlTestDatabase;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.*;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class ProjectLedgerMapperPostgreSqlTest {
    private static SqlSessionFactory sessions;

    @BeforeAll
    static void migrate() {
        DataSource dataSource = PostgreSqlTestDatabase.migratedDataSource("project_ledger_mapper");
        Environment environment = new Environment("test", new JdbcTransactionFactory(), dataSource);
        Configuration configuration = new Configuration(environment);
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(ProjectLedgerMapper.class);
        sessions = new SqlSessionFactoryBuilder().build(configuration);
    }

    @BeforeEach
    void reset() throws Exception {
        execute("DELETE FROM project_ledgers WHERE project_id = 930001");
        execute("DELETE FROM novel_projects WHERE project_id = 930001");
        execute("INSERT INTO novel_projects(project_id, owner_user_id, title) VALUES (930001, 930002, 'Ledger test')");
    }

    @Test
    void active_ai_lease_blocks_user_writes_and_only_accepts_the_matching_agent_token() {
        try (SqlSession session = sessions.openSession(true)) {
            ProjectLedgerMapper mapper = session.getMapper(ProjectLedgerMapper.class);
            ProjectLedger ledger = ledger();
            assertThat(mapper.insert(ledger)).isEqualTo(1);
            assertThat(mapper.acquireAiLease(930001L, 930003L, 930004L, "lease-a",
                    Instant.now().plusSeconds(30))).isEqualTo(1);

            assertThat(mapper.update(930001L, 930003L, 1L, "User edit", "blocked")).isZero();
            assertThat(mapper.delete(930001L, 930003L, 1L)).isZero();
            assertThat(mapper.updateWithAiLease(
                    930001L, 930003L, 1L, "AI edit", "updated", "wrong-token")).isZero();
            assertThat(mapper.updateWithAiLease(
                    930001L, 930003L, 1L, "AI edit", "updated", "lease-a")).isEqualTo(1);

            assertThat(mapper.releaseAiLease(930001L, 930003L, "lease-a")).isEqualTo(1);
            assertThat(mapper.update(930001L, 930003L, 2L, "User edit", "after release")).isEqualTo(1);
            assertThat(mapper.find(930001L, 930003L)).satisfies(saved -> {
                assertThat(saved.getTitle()).isEqualTo("User edit");
                assertThat(saved.getContent()).isEqualTo("after release");
                assertThat(saved.getContentRevision()).isEqualTo(3L);
                assertThat(saved.getLeaseToken()).isNull();
            });
        }
    }

    private ProjectLedger ledger() {
        ProjectLedger ledger = new ProjectLedger();
        ledger.setLedgerId(930003L);
        ledger.setProjectId(930001L);
        ledger.setTitle("Plan");
        ledger.setContent("initial");
        ledger.setContentRevision(1L);
        return ledger;
    }

    private static void execute(String sql) throws Exception {
        DataSource dataSource = sessions.getConfiguration().getEnvironment().getDataSource();
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }
}

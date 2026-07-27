package com.penmate.backend.infrastructure.persistence.agent;

import com.penmate.backend.infrastructure.persistence.storybible.StoryBibleMapper;
import com.penmate.backend.infrastructure.persistence.agent.run.AgentRunPendingApprovalMapper;
import com.penmate.backend.infrastructure.persistence.agent.run.AgentRunProjectionMapper;
import com.penmate.backend.domain.agent.run.model.AgentRunPendingApproval;
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
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class AgentRedesignPersistencePostgreSqlTest {
    private static SqlSessionFactory sessions;
    private static DataSource dataSource;

    @BeforeAll
    static void migrate() {
        dataSource = PostgreSqlTestDatabase.migratedDataSource("agent_redesign_persistence");
        Environment environment = new Environment("test", new JdbcTransactionFactory(), dataSource);
        Configuration configuration = new Configuration(environment);
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(AgentSafetyPreferenceMapper.class);
        configuration.addMapper(AgentQueuedRequestMapper.class);
        configuration.addMapper(AgentRunPendingApprovalMapper.class);
        configuration.addMapper(AgentRunProjectionMapper.class);
        configuration.addMapper(StoryBibleMapper.class);
        sessions = new SqlSessionFactoryBuilder().build(configuration);
    }

    @BeforeEach
    void reset() throws Exception {
        execute("DELETE FROM agent_session_queued_requests WHERE request_id = 940101");
        execute("DELETE FROM agent_run_pending_approvals WHERE approval_id = 940501");
        execute("DELETE FROM agent_tool_call_projections WHERE run_id = 940701");
        execute("DELETE FROM user_agent_preferences WHERE user_id = 940201");
        execute("DELETE FROM iam_users WHERE user_id = 940201");
        execute("DELETE FROM story_bible_changesets WHERE story_bible_id IN (940301, 940302)");
        execute("DELETE FROM story_bibles WHERE story_bible_id IN (940301, 940302)");
    }

    @Test
    void safety_mode_is_one_global_persistent_preference_per_user() throws Exception {
        execute("INSERT INTO iam_users(user_id, email, password_hash, display_name) "
                + "VALUES (940201, 'agent-safety-940201@example.test', 'hash', 'Safety test')");
        try (SqlSession session = sessions.openSession(true)) {
            AgentSafetyPreferenceMapper mapper = session.getMapper(AgentSafetyPreferenceMapper.class);
            assertThat(mapper.findByUserId(940201L)).isNull();
            assertThat(mapper.upsert(940201L, "AUTONOMOUS")).isOne();
            assertThat(mapper.findByUserId(940201L)).isEqualTo("AUTONOMOUS");
            assertThat(mapper.upsert(940201L, "STRICT")).isOne();
            assertThat(mapper.findByUserId(940201L)).isEqualTo("STRICT");
        }
        assertThat(queryLong("SELECT count(*) FROM user_agent_preferences WHERE user_id = 940201")).isOne();
    }

    @Test
    void rolled_back_claim_returns_the_queued_request_to_pending() throws Exception {
        execute("INSERT INTO agent_session_queued_requests(request_id, project_id, session_id, owner_user_id, request_type) "
                + "VALUES (940101, 940001, 940011, 940021, 'COMPRESS')");
        try (SqlSession transaction = sessions.openSession(false)) {
            var claimed = transaction.getMapper(AgentQueuedRequestMapper.class).claimNextIdle();
            assertThat(claimed).containsEntry("requestStatus", "EXECUTING")
                    .containsEntry("attemptCount", 1);
            transaction.rollback();
        }
        assertThat(queryString("SELECT request_status FROM agent_session_queued_requests WHERE request_id = 940101"))
                .isEqualTo("PENDING");
        assertThat(queryLong("SELECT attempt_count FROM agent_session_queued_requests WHERE request_id = 940101"))
                .isZero();
    }

    @Test
    void story_bible_archival_selects_rows_older_than_seven_days_or_outside_the_newest_five_thousand()
            throws Exception {
        execute("INSERT INTO story_bibles(story_bible_id, project_id, title) VALUES "
                + "(940301, 940401, 'Count retention'), (940302, 940402, 'Age retention')");
        execute("INSERT INTO story_bible_changesets(changeset_id, story_bible_id, content_revision, actor_type, actor_id, change_summary, created_at) "
                + "SELECT 950000 + value, 940301, value, 'USER', 940201, 'count', CURRENT_TIMESTAMP - INTERVAL '1 day' "
                + "FROM generate_series(1, 5001) AS value");
        execute("INSERT INTO story_bible_changesets(changeset_id, story_bible_id, content_revision, actor_type, actor_id, change_summary, created_at) VALUES "
                + "(960001, 940302, 1, 'USER', 940201, 'old', CURRENT_TIMESTAMP - INTERVAL '8 days'), "
                + "(960002, 940302, 2, 'USER', 940201, 'recent', CURRENT_TIMESTAMP - INTERVAL '1 day')");

        try (SqlSession session = sessions.openSession(true)) {
            StoryBibleMapper mapper = session.getMapper(StoryBibleMapper.class);
            Instant cutoff = Instant.now().minus(java.time.Duration.ofDays(7));
            assertThat(mapper.findChangesetsBefore(940301L, cutoff, 5_000))
                    .extracting(change -> change.getChangesetId())
                    .containsExactly(950001L);
            assertThat(mapper.findChangesetsBefore(940302L, cutoff, 5_000))
                    .extracting(change -> change.getChangesetId())
                    .containsExactly(960001L);
            assertThat(mapper.findStoryBiblesWithChangesetsBefore(cutoff, 5_000))
                    .extracting(root -> root.getStoryBibleId())
                    .containsExactly(940301L, 940302L);
        }
    }

    @Test
    void pending_tool_approval_maps_json_binding_after_timestamps() {
        String binding = "{\"toolCode\":\"story_bible_node_write\",\"contextEpochId\":940601}";
        AgentRunPendingApproval pending = new AgentRunPendingApproval(
                null, 940501L, 940501L, 940502L, 940503L, 940504L, 940505L,
                "call-940501", "story_bible_node_write", "{}", "{}", "[]",
                "940502:call-940501", "PENDING", 940506L, "trace-940501",
                null, null, binding);

        try (SqlSession session = sessions.openSession(true)) {
            AgentRunPendingApprovalMapper mapper = session.getMapper(AgentRunPendingApprovalMapper.class);
            assertThat(mapper.insert(pending)).isOne();
            AgentRunPendingApproval loaded = mapper.findByApprovalId(940501L);

            assertThat(loaded.createdAt()).isNotNull();
            assertThat(loaded.updatedAt()).isNotNull();
            assertThat(loaded.approvalBindingJson()).contains("story_bible_node_write", "940601");
        }
    }

    @Test
    void tool_projection_preserves_started_arguments_when_completion_has_only_output() throws Exception {
        try (SqlSession session = sessions.openSession(true)) {
            AgentRunProjectionMapper mapper = session.getMapper(AgentRunProjectionMapper.class);
            assertThat(mapper.upsertToolCall(940701L, "call-940701", "story_bible_inspect", "Inspect",
                    "running", 3, "{\"operation\":\"catalog\"}", null, null, null, null, null)).isOne();
            assertThat(mapper.upsertToolCall(940701L, "call-940701", "story_bible_inspect", null,
                    "success", null, null, "ok", null, null, null, null)).isOne();
        }

        assertThat(queryString("SELECT arguments_preview_json::text FROM agent_tool_call_projections "
                + "WHERE run_id = 940701 AND tool_call_id = 'call-940701'"))
                .contains("catalog");
        assertThat(queryString("SELECT status FROM agent_tool_call_projections "
                + "WHERE run_id = 940701 AND tool_call_id = 'call-940701'"))
                .isEqualTo("success");
    }

    private static void execute(String sql) throws Exception {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private static long queryLong(String sql) throws Exception {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(sql)) {
            result.next();
            return result.getLong(1);
        }
    }

    private static String queryString(String sql) throws Exception {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(sql)) {
            result.next();
            return result.getString(1);
        }
    }
}

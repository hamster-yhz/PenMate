package com.penmate.backend.infrastructure.persistence.agent;

import com.penmate.backend.domain.agent.model.PendingToolInvocationSnapshot;
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
import java.sql.Statement;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PendingToolInvocationMapperDbCaseTest {

    private static final String H2_URL = "jdbc:h2:mem:pending_tool_invocation_dbcase;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";

    private static SqlSessionFactory sqlSessionFactory;

    @BeforeAll
    static void setUpDatabase() {
        sqlSessionFactory = buildSqlSessionFactory();
    }

    @BeforeEach
    void resetSchema() throws Exception {
        recreateSchema();
        seedRows();
    }

    @Test
    void should_mark_status_only_when_expected_status_matches() {
        try (SqlSession sqlSession = sqlSessionFactory.openSession(true)) {
            PendingToolInvocationMapper mapper = sqlSession.getMapper(PendingToolInvocationMapper.class);

            assertThat(mapper.markStatus(88001L, "pending", "executing")).isEqualTo(1);
            assertThat(mapper.markStatus(88001L, "pending", "completed")).isEqualTo(0);

            PendingToolInvocationSnapshot snapshot = mapper.findByApprovalId(88001L);
            assertThat(snapshot.status()).isEqualTo("executing");
        }
    }

    @Test
    void should_find_only_stale_executing_snapshots_ordered_by_updated_at_and_limited() {
        try (SqlSession sqlSession = sqlSessionFactory.openSession(true)) {
            PendingToolInvocationMapper mapper = sqlSession.getMapper(PendingToolInvocationMapper.class);

            List<PendingToolInvocationSnapshot> snapshots = mapper.findStaleExecutingSnapshots(10, 2);

            assertThat(snapshots)
                    .extracting(PendingToolInvocationSnapshot::approvalId)
                    .containsExactly(88003L, 88004L);
        }
    }

    @Test
    void should_persist_loop_resume_fields() {
        try (SqlSession sqlSession = sqlSessionFactory.openSession(true)) {
            PendingToolInvocationMapper mapper = sqlSession.getMapper(PendingToolInvocationMapper.class);

            PendingToolInvocationSnapshot snapshot = new PendingToolInvocationSnapshot(
                    88101L,
                    10001L,
                    8101L,
                    7101L,
                    "book_crud",
                    "{\"operation\":\"delete\"}",
                    "{}",
                    1001L,
                    "trace-loop-1",
                    "idem-loop-1",
                    "pending",
                    "loop-1",
                    2,
                    "call_9",
                    "[{\"id\":\"call_9\"}]",
                    "[{\"role\":\"user\"}]",
                    "RESUME_LOOP",
                    "{\"approvalType\":\"BOOK_DELETE\"}"
            );

            assertThat(mapper.insert(snapshot)).isEqualTo(1);

            PendingToolInvocationSnapshot loaded = mapper.findByApprovalId(88101L);
            assertThat(loaded.loopRunId()).isEqualTo("loop-1");
            assertThat(loaded.llmTurnIndex()).isEqualTo(2);
            assertThat(loaded.toolCallId()).isEqualTo("call_9");
            assertThat(loaded.assistantToolCallsJson()).isEqualTo("[{\"id\":\"call_9\"}]");
            assertThat(loaded.conversationMessagesJson()).isEqualTo("[{\"role\":\"user\"}]");
            assertThat(loaded.resumeMode()).isEqualTo("RESUME_LOOP");
            assertThat(loaded.approvalSummaryJson()).isEqualTo("{\"approvalType\":\"BOOK_DELETE\"}");
        }
    }

    private static SqlSessionFactory buildSqlSessionFactory() {
        DataSource dataSource = new org.apache.ibatis.datasource.unpooled.UnpooledDataSource(
                "org.h2.Driver",
                H2_URL,
                "sa",
                "");

        Environment environment = new Environment("test", new JdbcTransactionFactory(), dataSource);
        Configuration configuration = new Configuration(environment);
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(PendingToolInvocationMapper.class);
        return new SqlSessionFactoryBuilder().build(configuration);
    }

    private static void recreateSchema() throws Exception {
        try (Connection connection = sqlSessionFactory.getConfiguration().getEnvironment().getDataSource().getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("DROP ALL OBJECTS");
            statement.execute("""
                    CREATE TABLE pending_tool_invocations (
                        id BIGINT PRIMARY KEY AUTO_INCREMENT,
                        approval_id BIGINT NOT NULL,
                        project_id BIGINT NOT NULL,
                        task_id BIGINT NOT NULL,
                        conversation_id BIGINT NULL,
                        tool_code VARCHAR(100) NOT NULL,
                        tool_args_json VARCHAR(2000) NULL,
                        context_json VARCHAR(2000) NULL,
                        operator_id BIGINT NULL,
                        trace_id VARCHAR(64) NULL,
                        idempotency_key VARCHAR(128) NOT NULL,
                        status VARCHAR(20) NOT NULL,
                        loop_run_id VARCHAR(64) NULL,
                        llm_turn_index INT NULL,
                        tool_call_id VARCHAR(128) NULL,
                        assistant_tool_calls_json VARCHAR(4000) NULL,
                        conversation_messages_json VARCHAR(4000) NULL,
                        resume_mode VARCHAR(64) NULL,
                        approval_summary_json VARCHAR(4000) NULL,
                        created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        UNIQUE (approval_id),
                        UNIQUE (idempotency_key)
                    )
                    """);
        }
    }

    private static void seedRows() throws Exception {
        try (Connection connection = sqlSessionFactory.getConfiguration().getEnvironment().getDataSource().getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    INSERT INTO pending_tool_invocations(
                        approval_id, project_id, task_id, conversation_id, tool_code,
                        tool_args_json, context_json, operator_id, trace_id, idempotency_key,
                        status, loop_run_id, llm_turn_index, tool_call_id,
                        assistant_tool_calls_json, conversation_messages_json, resume_mode,
                        approval_summary_json, created_at, updated_at
                    ) VALUES
                    (88001, 10001, 8001, 7001, 'book_crud', '{}', '{}', 1001, 'trace-1', 'idem-1', 'pending', NULL, NULL, NULL, NULL, NULL, NULL, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
                    (88002, 10001, 8002, 7002, 'book_crud', '{}', '{}', 1001, 'trace-2', 'idem-2', 'executing', NULL, NULL, NULL, NULL, NULL, NULL, NULL, CURRENT_TIMESTAMP, TIMESTAMPADD(MINUTE, -5, CURRENT_TIMESTAMP)),
                    (88003, 10001, 8003, 7003, 'book_crud', '{}', '{}', 1001, 'trace-3', 'idem-3', 'executing', NULL, NULL, NULL, NULL, NULL, NULL, NULL, CURRENT_TIMESTAMP, TIMESTAMPADD(MINUTE, -30, CURRENT_TIMESTAMP)),
                    (88004, 10001, 8004, 7004, 'book_crud', '{}', '{}', 1001, 'trace-4', 'idem-4', 'executing', NULL, NULL, NULL, NULL, NULL, NULL, NULL, CURRENT_TIMESTAMP, TIMESTAMPADD(MINUTE, -20, CURRENT_TIMESTAMP)),
                    (88005, 10001, 8005, 7005, 'book_crud', '{}', '{}', 1001, 'trace-5', 'idem-5', 'failed', NULL, NULL, NULL, NULL, NULL, NULL, NULL, CURRENT_TIMESTAMP, TIMESTAMPADD(MINUTE, -40, CURRENT_TIMESTAMP))
                    """);
        }
    }
}

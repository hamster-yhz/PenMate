package com.penmate.backend.infrastructure.persistence.agent;

import com.penmate.backend.domain.agent.model.AgentGenerationTask;
import com.penmate.backend.domain.agent.model.AgentMessage;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

class AgentRepositoryRuntimeSchemaDbCaseTest {

    private static final String H2_URL = "jdbc:h2:mem:agent_repository_runtime_dbcase;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";

    private static SqlSessionFactory sqlSessionFactory;

    @BeforeAll
    static void setUpDatabase() {
        sqlSessionFactory = buildSqlSessionFactory();
    }

    @BeforeEach
    void resetSchema() throws Exception {
        recreateSchema();
        seedSessionRows();
    }

    @Test
    void should_insert_message_into_agent_messages_runtime_schema() throws Exception {
        try (SqlSession sqlSession = sqlSessionFactory.openSession(true)) {
            AgentRepositoryImpl repository = new AgentRepositoryImpl(
                    sqlSession.getMapper(AgentMapper.class),
                    sqlSession.getMapper(AgentSessionMapper.class)
            );

            AgentMessage message = new AgentMessage();
            message.setMessageId(930401L);
            message.setConversationId(920002L);
            message.setRole("USER");
            message.setUserMessageType("CHAT");
            message.setContentMd("请继续写作");
            message.setSeqNo(1);

            assertThat(repository.insertMessage(message)).isEqualTo(1);
            assertThat(countRows("agent_messages")).isEqualTo(1);
            assertThat(singleLong("SELECT session_id FROM agent_messages WHERE message_id = 930401")).isEqualTo(920002L);
        }
    }

    @Test
    void should_insert_generation_task_into_agent_tasks_runtime_schema() throws Exception {
        try (SqlSession sqlSession = sqlSessionFactory.openSession(true)) {
            AgentRepositoryImpl repository = new AgentRepositoryImpl(
                    sqlSession.getMapper(AgentMapper.class),
                    sqlSession.getMapper(AgentSessionMapper.class)
            );

            AgentGenerationTask task = new AgentGenerationTask();
            task.setTaskId(940401L);
            task.setProjectId(920001L);
            task.setConversationId(920002L);
            task.setChapterId(3001L);
            task.setTaskType("WRITE");
            task.setPromptSnapshot("请继续写作");
            task.setTraceId("trace-runtime-task-1");
            task.setStatus("pending");

            assertThat(repository.insertGenerationTask(task)).isEqualTo(1);
            assertThat(countRows("agent_tasks")).isEqualTo(1);
            assertThat(singleLong("SELECT session_id FROM agent_tasks WHERE task_id = 940401")).isEqualTo(920002L);
        }
    }

    @Test
    void should_lock_session_before_reading_max_message_sequence() {
        AgentMapper agentMapper = org.mockito.Mockito.mock(AgentMapper.class);
        AgentSessionMapper agentSessionMapper = org.mockito.Mockito.mock(AgentSessionMapper.class);
        AgentRepositoryImpl repository = new AgentRepositoryImpl(agentMapper, agentSessionMapper);
        when(agentMapper.maxMessageSeq(920002L)).thenReturn(3);

        repository.nextMessageSeq(920002L);

        org.mockito.InOrder inOrder = inOrder(agentSessionMapper, agentMapper);
        inOrder.verify(agentSessionMapper).lockSessionForTurnAppend(null, 920002L);
        inOrder.verify(agentMapper).maxMessageSeq(920002L);
    }

    @Test
    void should_reject_duplicate_message_sequence_within_same_session() throws Exception {
        try (Connection connection = sqlSessionFactory.getConfiguration().getEnvironment().getDataSource().getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    INSERT INTO agent_messages(message_id, session_id, turn_id, role, message_kind, content_markdown, render_blocks_json, tool_call_id, approval_id, delivery_status, seq_no)
                    VALUES (930501, 920002, NULL, 'USER', 'CHAT', 'first', NULL, NULL, NULL, 'FINAL', 1)
                    """);

            org.assertj.core.api.Assertions.assertThatThrownBy(() -> statement.execute("""
                    INSERT INTO agent_messages(message_id, session_id, turn_id, role, message_kind, content_markdown, render_blocks_json, tool_call_id, approval_id, delivery_status, seq_no)
                    VALUES (930502, 920002, NULL, 'USER', 'CHAT', 'duplicate', NULL, NULL, NULL, 'FINAL', 1)
                    """))
                    .isInstanceOf(java.sql.SQLException.class);
        }
    }

    @Test
    void should_touch_last_message_timestamp_on_agent_sessions_runtime_schema() throws Exception {
        try (SqlSession sqlSession = sqlSessionFactory.openSession(true)) {
            AgentRepositoryImpl repository = new AgentRepositoryImpl(
                    sqlSession.getMapper(AgentMapper.class),
                    sqlSession.getMapper(AgentSessionMapper.class)
            );

            assertThat(repository.touchConversationLastMessage(920002L)).isEqualTo(1);
            assertThat(singleLong("SELECT CASE WHEN last_message_at IS NULL THEN 0 ELSE 1 END FROM agent_sessions WHERE session_id = 920002"))
                    .isEqualTo(1L);
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
        configuration.addMapper(AgentMapper.class);
        configuration.addMapper(AgentSessionMapper.class);
        return new SqlSessionFactoryBuilder().build(configuration);
    }

    private static void recreateSchema() throws Exception {
        try (Connection connection = sqlSessionFactory.getConfiguration().getEnvironment().getDataSource().getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("DROP TABLE IF EXISTS agent_tasks");
            statement.execute("DROP TABLE IF EXISTS agent_messages");
            statement.execute("DROP TABLE IF EXISTS agent_sessions");
            statement.execute("""
                    CREATE TABLE agent_sessions (
                        id BIGINT PRIMARY KEY AUTO_INCREMENT,
                        session_id BIGINT NOT NULL,
                        project_id BIGINT NOT NULL,
                        owner_user_id BIGINT NOT NULL,
                        title VARCHAR(200) NOT NULL,
                        session_status VARCHAR(20) NOT NULL,
                        bound_style_id BIGINT NULL,
                        active_context_version INT NOT NULL,
                        last_turn_id BIGINT NULL,
                        last_task_id BIGINT NULL,
                        last_message_at TIMESTAMP NULL,
                        resumed_at TIMESTAMP NULL,
                        created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        deleted_at TIMESTAMP NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE agent_messages (
                        id BIGINT PRIMARY KEY AUTO_INCREMENT,
                        message_id BIGINT NOT NULL,
                        session_id BIGINT NOT NULL,
                        turn_id BIGINT NULL,
                        role VARCHAR(20) NOT NULL,
                        message_kind VARCHAR(30) NOT NULL,
                        content_markdown VARCHAR(2000) NOT NULL,
                        render_blocks_json VARCHAR(2000) NULL,
                        tool_call_id VARCHAR(128) NULL,
                        approval_id BIGINT NULL,
                        delivery_status VARCHAR(20) NOT NULL,
                        seq_no INT NOT NULL,
                        created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        CONSTRAINT uk_agent_messages_session_seq UNIQUE(session_id, seq_no)
                    )
                    """);
            statement.execute("""
                    CREATE TABLE agent_tasks (
                        id BIGINT PRIMARY KEY AUTO_INCREMENT,
                        task_id BIGINT NOT NULL,
                        session_id BIGINT NOT NULL,
                        turn_id BIGINT NOT NULL,
                        project_id BIGINT NOT NULL,
                        task_type VARCHAR(32) NOT NULL,
                        task_status VARCHAR(24) NOT NULL,
                        request_context_id BIGINT NULL,
                        result_id BIGINT NULL,
                        active_approval_id BIGINT NULL,
                        stream_channel_key VARCHAR(128) NULL,
                        trace_id VARCHAR(64) NULL,
                        started_at TIMESTAMP NULL,
                        finished_at TIMESTAMP NULL,
                        created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                    )
                    """);
        }
    }

    private static void seedSessionRows() throws Exception {
        try (Connection connection = sqlSessionFactory.getConfiguration().getEnvironment().getDataSource().getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    INSERT INTO agent_sessions(session_id, project_id, owner_user_id, title, session_status, bound_style_id, active_context_version, last_turn_id, last_task_id, last_message_at, resumed_at)
                    VALUES (920002, 920001, 1001, 'Session-A', 'ACTIVE', 81, 1, NULL, NULL, NULL, NULL)
                    """);
        }
    }

    private long countRows(String tableName) throws Exception {
        try (Connection connection = sqlSessionFactory.getConfiguration().getEnvironment().getDataSource().getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT COUNT(*) FROM " + tableName)) {
            resultSet.next();
            return resultSet.getLong(1);
        }
    }

    private long singleLong(String sql) throws Exception {
        try (Connection connection = sqlSessionFactory.getConfiguration().getEnvironment().getDataSource().getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            resultSet.next();
            return resultSet.getLong(1);
        }
    }
}

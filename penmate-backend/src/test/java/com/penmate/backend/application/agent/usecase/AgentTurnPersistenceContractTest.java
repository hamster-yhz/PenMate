package com.penmate.backend.application.agent.usecase;

import com.penmate.backend.application.style.usecase.SessionStyleBindingAppService;
import com.penmate.backend.domain.agent.model.AgentSession;
import com.penmate.backend.domain.agent.model.AgentSessionRecoverySnapshot;
import com.penmate.backend.domain.shared.service.BusinessIdGenerator;
import com.penmate.backend.infrastructure.persistence.agent.AgentMapper;
import com.penmate.backend.infrastructure.persistence.agent.AgentRepositoryImpl;
import com.penmate.backend.infrastructure.persistence.agent.AgentSessionMapper;
import com.penmate.backend.infrastructure.persistence.agent.AgentSessionRepositoryImpl;
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

class AgentTurnPersistenceContractTest {

    private static final String H2_URL = "jdbc:h2:mem:agent_turn_persistence_contract;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";

    private static SqlSessionFactory sqlSessionFactory;

    @BeforeAll
    static void setUpDatabase() {
        sqlSessionFactory = buildSqlSessionFactory();
    }

    @BeforeEach
    void resetSchema() throws Exception {
        recreateSchema();
        seedSession();
    }

    @Test
    void should_persist_turn_message_task_context_and_update_session_pointers() throws Exception {
        try (SqlSession sqlSession = sqlSessionFactory.openSession(true)) {
            BusinessIdGenerator businessIdGenerator = sequenceIdGenerator(930001L, 940001L, 950001L, 960001L);
            AgentSessionRepositoryImpl sessionRepository = new AgentSessionRepositoryImpl(
                    sqlSession.getMapper(AgentSessionMapper.class),
                    businessIdGenerator
            );
            AgentRepositoryImpl agentRepository = new AgentRepositoryImpl(
                    sqlSession.getMapper(AgentMapper.class),
                    sqlSession.getMapper(AgentSessionMapper.class)
            );
            SessionStyleBindingAppService styleBindingAppService = new SessionStyleBindingAppService(sessionRepository);
            AgentTurnAppService service = new AgentTurnAppService(
                    styleBindingAppService,
                    agentRepository,
                    sessionRepository,
                    businessIdGenerator
            );

            service.createTurn(
                    920001L,
                    920002L,
                    new AgentTurnCommand(
                            1001L,
                            "请继续写作",
                            new AgentTurnCommand.TaskRequest("WRITE", 3001L, null, "selected text")
                    ),
                    "trace-turn-persist-1"
            );

            assertThat(countRows("agent_messages")).as("recovery message should be persisted exactly once").isEqualTo(1);
            assertThat(countRows("agent_generation_tasks")).as("legacy generation task table should no longer receive createTurn writes").isZero();
            assertThat(countRows("agent_tasks")).as("recovery runtime task should be persisted").isEqualTo(1);
            assertThat(countRows("agent_turns")).as("turn should be persisted").isEqualTo(1);
            assertThat(countRows("agent_task_contexts")).as("task context should be persisted").isEqualTo(1);
            assertThat(singleLong("SELECT last_task_id FROM agent_sessions WHERE session_id = 920002"))
                    .as("session last_task_id should point to created task")
                    .isEqualTo(940001L);
            assertThat(singleLong("SELECT turn_id FROM agent_tasks WHERE task_id = 940001"))
                    .as("runtime task should reference the created turn id")
                    .isEqualTo(950001L);
            assertThat(singleString("SELECT prompt_snapshot FROM agent_tasks WHERE task_id = 940001"))
                    .as("runtime task should persist prompt snapshot for async workflow recovery")
                    .isEqualTo("请继续写作");
        }
    }

    @Test
    void should_restore_created_turn_via_recovery_snapshot_contract() throws Exception {
        try (SqlSession sqlSession = sqlSessionFactory.openSession(true)) {
            BusinessIdGenerator businessIdGenerator = sequenceIdGenerator(930011L, 940011L, 950011L, 960011L);
            AgentSessionRepositoryImpl sessionRepository = new AgentSessionRepositoryImpl(
                    sqlSession.getMapper(AgentSessionMapper.class),
                    businessIdGenerator
            );
            AgentRepositoryImpl agentRepository = new AgentRepositoryImpl(
                    sqlSession.getMapper(AgentMapper.class),
                    sqlSession.getMapper(AgentSessionMapper.class)
            );
            SessionStyleBindingAppService styleBindingAppService = new SessionStyleBindingAppService(sessionRepository);
            AgentTurnAppService service = new AgentTurnAppService(
                    styleBindingAppService,
                    agentRepository,
                    sessionRepository,
                    businessIdGenerator
            );

            service.createTurn(
                    920001L,
                    920002L,
                    new AgentTurnCommand(
                            1001L,
                            "恢复测试消息",
                            new AgentTurnCommand.TaskRequest("WRITE", 3002L, null, "恢复测试选中文本")
                    ),
                    "trace-turn-recovery-1"
            );

            AgentSessionRecoverySnapshot snapshot = sessionRepository.findRecoverySnapshot(920001L, 920002L);

            assertThat(snapshot).isNotNull();
            assertThat(snapshot.getSession()).isNotNull();
            assertThat(snapshot.getSession().getLastTaskId()).isEqualTo(940011L);
            assertThat(snapshot.getActiveTask()).isNotNull();
            assertThat(snapshot.getActiveTask().getTaskId()).isEqualTo(940011L);
            assertThat(snapshot.getActiveTask().getTaskStatus()).isEqualTo("pending");
            assertThat(snapshot.getActiveTask().getSelectedText()).isEqualTo("恢复测试选中文本");
            assertThat(snapshot.getActiveTask().getActiveToolCallsSnapshot()).isNull();
            assertThat(snapshot.getActiveTask().getLastRuntimeStatus()).isNull();
            assertThat(snapshot.getActiveTask().getRecoveryCursor()).isNull();
            assertThat(agentRepository.findGenerationTask(920001L, 940011L))
                    .as("runtime task repository reload should restore promptSnapshot from agent_tasks")
                    .extracting(com.penmate.backend.domain.agent.model.AgentGenerationTask::getPromptSnapshot)
                    .isEqualTo("恢复测试消息");
            assertThat(snapshot.getMessages()).hasSize(1);
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
            statement.execute("DROP TABLE IF EXISTS agent_task_contexts");
            statement.execute("DROP TABLE IF EXISTS agent_task_results");
            statement.execute("DROP TABLE IF EXISTS agent_tasks");
            statement.execute("DROP TABLE IF EXISTS agent_generation_tasks");
            statement.execute("DROP TABLE IF EXISTS agent_turns");
            statement.execute("DROP TABLE IF EXISTS agent_session_style_bindings");
            statement.execute("DROP TABLE IF EXISTS agent_sessions");
            statement.execute("DROP TABLE IF EXISTS agent_messages");
            statement.execute("DROP TABLE IF EXISTS agent_conversations");
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
                    CREATE TABLE agent_turns (
                        id BIGINT PRIMARY KEY AUTO_INCREMENT,
                        turn_id BIGINT NOT NULL,
                        session_id BIGINT NOT NULL,
                        turn_seq INT NOT NULL,
                        user_message_id BIGINT NULL,
                        assistant_message_id BIGINT NULL,
                        task_id BIGINT NULL,
                        turn_status VARCHAR(24) NOT NULL,
                        resume_token VARCHAR(128) NULL,
                        created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                    )
                    """);
            statement.execute("""
                    CREATE TABLE agent_conversations (
                        id BIGINT PRIMARY KEY AUTO_INCREMENT,
                        conversation_id BIGINT NOT NULL,
                        project_id BIGINT NOT NULL,
                        user_id BIGINT NOT NULL,
                        title VARCHAR(200) NOT NULL,
                        context_scope_json VARCHAR(2000) NULL,
                        last_message_at TIMESTAMP NULL,
                        status VARCHAR(20) NOT NULL,
                        created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        deleted_at TIMESTAMP NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE agent_messages (
                        id BIGINT PRIMARY KEY AUTO_INCREMENT,
                        message_id BIGINT NOT NULL,
                        conversation_id BIGINT NULL,
                        session_id BIGINT NULL,
                        turn_id BIGINT NULL,
                        role VARCHAR(20) NOT NULL,
                        user_message_type VARCHAR(30) NULL,
                        message_kind VARCHAR(30) NULL,
                        content_md VARCHAR(2000) NULL,
                        content_markdown VARCHAR(2000) NULL,
                        attachments_json VARCHAR(2000) NULL,
                        render_blocks_json VARCHAR(2000) NULL,
                        tool_calls_json VARCHAR(2000) NULL,
                        tool_call_id VARCHAR(128) NULL,
                        approval_id BIGINT NULL,
                        delivery_status VARCHAR(20) NULL,
                        seq_no INT NOT NULL,
                        created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                    )
                    """);
            statement.execute("""
                    CREATE TABLE agent_generation_tasks (
                        id BIGINT PRIMARY KEY AUTO_INCREMENT,
                        task_id BIGINT NOT NULL,
                        project_id BIGINT NOT NULL,
                        conversation_id BIGINT NOT NULL,
                        chapter_id BIGINT NULL,
                        model_config_id BIGINT NULL,
                        task_type VARCHAR(32) NOT NULL,
                        prompt_snapshot VARCHAR(2000) NULL,
                        plugin_snapshot VARCHAR(2000) NULL,
                        token_usage_json VARCHAR(2000) NULL,
                        cost_json VARCHAR(2000) NULL,
                        trace_id VARCHAR(64) NULL,
                        status VARCHAR(24) NOT NULL,
                        started_at TIMESTAMP NULL,
                        finished_at TIMESTAMP NULL,
                        error_msg VARCHAR(500) NULL,
                        created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
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
                        prompt_snapshot VARCHAR(4000) NULL,
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
            statement.execute("""
                    CREATE TABLE agent_task_contexts (
                        id BIGINT PRIMARY KEY AUTO_INCREMENT,
                        context_id BIGINT NOT NULL,
                        task_id BIGINT NOT NULL,
                        chapter_id BIGINT NULL,
                        selected_text VARCHAR(2000) NULL,
                        outline_snapshot_json VARCHAR(2000) NULL,
                        cards_snapshot_json VARCHAR(2000) NULL,
                        rag_snapshot_json VARCHAR(2000) NULL,
                        plugin_bindings_json VARCHAR(2000) NULL,
                        style_snapshot_json VARCHAR(2000) NULL,
                        model_snapshot_json VARCHAR(2000) NULL,
                        task_profile_json VARCHAR(2000) NULL,
                        prompt_plan_json VARCHAR(2000) NULL,
                        context_package_json VARCHAR(2000) NULL,
                        active_tool_calls_snapshot VARCHAR(2000) NULL,
                        last_runtime_status VARCHAR(64) NULL,
                        recovery_cursor VARCHAR(255) NULL,
                        context_hash VARCHAR(128) NOT NULL,
                        created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                    )
                    """);
            statement.execute("""
                    CREATE TABLE agent_task_results (
                        id BIGINT PRIMARY KEY AUTO_INCREMENT,
                        result_id BIGINT NULL,
                        task_id BIGINT NOT NULL,
                        draft_summary VARCHAR(2000) NULL,
                        quality_report_summary VARCHAR(2000) NULL,
                        todo_summary VARCHAR(2000) NULL,
                        story_bible_proposal_summary VARCHAR(2000) NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE agent_session_style_bindings (
                        id BIGINT PRIMARY KEY AUTO_INCREMENT,
                        binding_id BIGINT NOT NULL,
                        session_id BIGINT NOT NULL,
                        style_id BIGINT NOT NULL,
                        source VARCHAR(24) NOT NULL,
                        activated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        deactivated_at TIMESTAMP NULL,
                        created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                    )
                    """);
        }
    }

    private static void seedSession() throws Exception {
        AgentSession session = AgentSession.active(920002L, 920001L, 1001L, "Session-A");
        session.bindStyle(81L);
        try (SqlSession sqlSession = sqlSessionFactory.openSession(true)) {
            AgentSessionRepositoryImpl repository = new AgentSessionRepositoryImpl(
                    sqlSession.getMapper(AgentSessionMapper.class),
                    sequenceIdGenerator(990001L)
            );
            repository.insertSession(session);
            repository.updateBoundStyle(920001L, 920002L, 81L, 1001L);
            Statement statement = sqlSession.getConnection().createStatement();
            statement.execute("""
                    INSERT INTO agent_conversations(conversation_id, project_id, user_id, title, context_scope_json, status)
                    VALUES (920002, 920001, 1001, 'Session-A', NULL, 'ACTIVE')
                    """);
            statement.close();
        }
    }

    private static BusinessIdGenerator sequenceIdGenerator(Long... ids) {
        return new BusinessIdGenerator() {
            private int index = 0;

            @Override
            public Long nextId() {
                if (index >= ids.length) {
                    return ids[ids.length - 1];
                }
                return ids[index++];
            }
        };
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

    private String singleString(String sql) throws Exception {
        try (Connection connection = sqlSessionFactory.getConfiguration().getEnvironment().getDataSource().getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            resultSet.next();
            return resultSet.getString(1);
        }
    }
}

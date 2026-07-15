package com.penmate.backend.application.agent.usecase;

import com.penmate.backend.application.agent.run.AgentRunAppService;
import com.penmate.backend.application.agent.run.AgentRunDispatcher;
import com.penmate.backend.application.agent.run.AgentRunEventPublisher;
import com.penmate.backend.application.style.usecase.SessionStyleBindingAppService;
import com.penmate.backend.application.agent.run.AgentRunAppService;
import com.penmate.backend.application.agent.run.AsyncAgentRunDispatcher;
import com.penmate.backend.domain.agent.model.AgentSession;
import com.penmate.backend.domain.agent.run.model.AgentEvent;
import com.penmate.backend.domain.agent.run.model.AgentRunInput;
import com.penmate.backend.domain.shared.service.BusinessIdGenerator;
import com.penmate.backend.infrastructure.persistence.agent.AgentMapper;
import com.penmate.backend.infrastructure.persistence.agent.AgentRepositoryImpl;
import com.penmate.backend.infrastructure.persistence.agent.AgentSessionMapper;
import com.penmate.backend.infrastructure.persistence.agent.AgentSessionRepositoryImpl;
import com.penmate.backend.infrastructure.persistence.agent.run.AgentRunMapper;
import com.penmate.backend.infrastructure.persistence.agent.run.AgentRunRepositoryImpl;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
    void should_persist_turn_message_run_input_and_update_session_run_pointer() throws Exception {
        AgentRunDispatcher dispatcher = mock(AgentRunDispatcher.class);
        try (SqlSession sqlSession = sqlSessionFactory.openSession(true)) {
            BusinessIdGenerator businessIdGenerator = sequenceIdGenerator(930001L, 940001L, 950001L);
            AgentSessionRepositoryImpl sessionRepository = new AgentSessionRepositoryImpl(
                    sqlSession.getMapper(AgentSessionMapper.class),
                    businessIdGenerator
            );
            AgentRepositoryImpl agentRepository = new AgentRepositoryImpl(
                    sqlSession.getMapper(AgentMapper.class),
                    sqlSession.getMapper(AgentSessionMapper.class)
            );
            AgentRunEventPublisher eventPublisher = mock(AgentRunEventPublisher.class);
            when(eventPublisher.publish(eq(950001L), eq("run.started"), any()))
                    .thenReturn(new AgentEvent(960001L, 950001L, 920001L, 920002L, 940001L, 1L, 1, "run.started", "{\"schemaVersion\":1}", null));
            AgentTurnAppService service = new AgentTurnAppService(
                    new SessionStyleBindingAppService(sessionRepository),
                    agentRepository,
                    sessionRepository,
                    businessIdGenerator,
                    new AgentRunAppService(
                            new AgentRunRepositoryImpl(sqlSession.getMapper(AgentRunMapper.class)),
                            eventPublisher,
                            dispatcher
                    ),
                    dispatcher
            );

            AgentTurnResult result = service.createTurn(
                    920001L,
                    920002L,
                    command("Write the next beat.", 3001L, 4001L, "selected text"),
                    "trace-turn-persist-1"
            );

            assertThat(result.activeRun().runId()).isEqualTo(950001L);
            assertThat(result.activeRun().turnId()).isEqualTo(940001L);
            assertThat(countRows("agent_messages")).isEqualTo(1);
            assertThat(countRows("agent_turns")).isEqualTo(1);
            assertThat(countRows("agent_runs")).isEqualTo(1);
            assertThat(countRows("agent_run_inputs")).isEqualTo(1);
            assertThat(singleLong("SELECT last_run_id FROM agent_sessions WHERE session_id = 920002"))
                    .isEqualTo(950001L);
            assertThat(singleLong("SELECT run_id FROM agent_turns WHERE turn_id = 940001"))
                    .isEqualTo(950001L);
            assertThat(singleString("SELECT prompt_snapshot FROM agent_run_inputs WHERE run_id = 950001"))
                    .isEqualTo("Write the next beat.");
            assertThat(singleString("SELECT style_snapshot_json FROM agent_run_inputs WHERE run_id = 950001"))
                    .isEqualTo("{\"styleId\":81}");
            verify(dispatcher).dispatchInitialRun(950001L, "trace-turn-persist-1");
        }
    }

    @Test
    void should_reload_run_input_from_created_turn() {
        try (SqlSession sqlSession = sqlSessionFactory.openSession(true)) {
            BusinessIdGenerator businessIdGenerator = sequenceIdGenerator(930011L, 940011L, 950011L);
            AgentSessionRepositoryImpl sessionRepository = new AgentSessionRepositoryImpl(
                    sqlSession.getMapper(AgentSessionMapper.class),
                    businessIdGenerator
            );
            AgentRepositoryImpl agentRepository = new AgentRepositoryImpl(
                    sqlSession.getMapper(AgentMapper.class),
                    sqlSession.getMapper(AgentSessionMapper.class)
            );
            AgentRunEventPublisher eventPublisher = mock(AgentRunEventPublisher.class);
            when(eventPublisher.publish(eq(950011L), eq("run.started"), any()))
                    .thenReturn(new AgentEvent(960011L, 950011L, 920001L, 920002L, 940011L, 1L, 1, "run.started", "{\"schemaVersion\":1}", null));
            AgentRunRepositoryImpl runRepository = new AgentRunRepositoryImpl(sqlSession.getMapper(AgentRunMapper.class));
            AgentTurnAppService service = new AgentTurnAppService(
                    new SessionStyleBindingAppService(sessionRepository),
                    agentRepository,
                    sessionRepository,
                    businessIdGenerator,
                    new AgentRunAppService(runRepository, eventPublisher, mock(AgentRunDispatcher.class)),
                    mock(AgentRunDispatcher.class)
            );

            service.createTurn(
                    920001L,
                    920002L,
                    command("Recover this turn.", 3002L, null, "recovery selection"),
                    "trace-turn-reload-1"
            );

            AgentRunInput input = runRepository.findInput(950011L);

            assertThat(input).isNotNull();
            assertThat(input.runId()).isEqualTo(950011L);
            assertThat(input.taskType()).isEqualTo("WRITE");
            assertThat(input.promptSnapshot()).isEqualTo("Recover this turn.");
            assertThat(input.chapterId()).isEqualTo(3002L);
            assertThat(input.selectedText()).isEqualTo("recovery selection");
            assertThat(input.modelSnapshotJson()).contains("\"operatorId\":1001", "\"modelConfigId\":null");
            assertThat(input.inputHash()).isNotBlank();
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
        configuration.addMapper(AgentRunMapper.class);
        return new SqlSessionFactoryBuilder().build(configuration);
    }

    private static void recreateSchema() throws Exception {
        try (Connection connection = sqlSessionFactory.getConfiguration().getEnvironment().getDataSource().getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("DROP TABLE IF EXISTS agent_run_inputs");
            statement.execute("DROP TABLE IF EXISTS agent_runs");
            statement.execute("DROP TABLE IF EXISTS agent_turns");
            statement.execute("DROP TABLE IF EXISTS agent_session_style_bindings");
            statement.execute("DROP TABLE IF EXISTS agent_sessions");
            statement.execute("DROP TABLE IF EXISTS agent_messages");
            statement.execute("""
                    CREATE TABLE agent_sessions (
                        id BIGINT PRIMARY KEY AUTO_INCREMENT,
                        session_id BIGINT NOT NULL,
                        project_id BIGINT NOT NULL,
                        owner_user_id BIGINT NOT NULL,
                        title VARCHAR(200) NOT NULL,
                        session_status VARCHAR(20) NOT NULL,
                        bound_style_id BIGINT NULL,
                        story_bible_routing_mode VARCHAR(32) NULL,
                        router_model_config_id BIGINT NULL,
                        active_context_epoch_id BIGINT NULL,
                        last_turn_id BIGINT NULL,
                        last_run_id BIGINT NULL,
                        last_message_at TIMESTAMP NULL,
                        resumed_at TIMESTAMP NULL,
                        total_prompt_tokens INT NOT NULL DEFAULT 0,
                        total_completion_tokens INT NOT NULL DEFAULT 0,
                        total_tokens INT NOT NULL DEFAULT 0,
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
                        run_id BIGINT NULL,
                        turn_status VARCHAR(24) NOT NULL,
                        resume_token VARCHAR(128) NULL,
                        created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
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
                    CREATE TABLE agent_runs (
                        id BIGINT PRIMARY KEY AUTO_INCREMENT,
                        run_id BIGINT NOT NULL,
                        project_id BIGINT NOT NULL,
                        session_id BIGINT NOT NULL,
                        turn_id BIGINT NOT NULL,
                        owner_user_id BIGINT NOT NULL,
                        run_status VARCHAR(24) NOT NULL,
                        run_phase VARCHAR(32) NOT NULL,
                        context_epoch_id BIGINT NULL,
                        active_approval_id BIGINT NULL,
                        latest_event_seq BIGINT NOT NULL DEFAULT 0,
                        latest_checkpoint_id BIGINT NULL,
                        trace_id VARCHAR(128) NULL,
                        started_at TIMESTAMP NULL,
                        finished_at TIMESTAMP NULL,
                        created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                    )
                    """);
            statement.execute("""
                    CREATE TABLE agent_run_inputs (
                        id BIGINT PRIMARY KEY AUTO_INCREMENT,
                        run_id BIGINT NOT NULL,
                        prompt_snapshot VARCHAR(4000) NULL,
                        task_type VARCHAR(32) NOT NULL,
                        chapter_id BIGINT NULL,
                        selected_text VARCHAR(2000) NULL,
                        style_snapshot_json VARCHAR(2000) NULL,
                        model_snapshot_json VARCHAR(2000) NULL,
                        plugin_bindings_json VARCHAR(2000) NULL,
                        input_hash VARCHAR(128) NOT NULL,
                        created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
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
        }
    }

    private static AgentTurnCommand command(String message, Long chapterId, Long modelConfigId, String selectedText) {
        return new AgentTurnCommand(
                1001L,
                message,
                new AgentTurnCommand.TaskRequest("WRITE", chapterId, modelConfigId, selectedText)
        );
    }

    private static BusinessIdGenerator sequenceIdGenerator(Long... ids) {
        return new BusinessIdGenerator() {
            private int index;

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

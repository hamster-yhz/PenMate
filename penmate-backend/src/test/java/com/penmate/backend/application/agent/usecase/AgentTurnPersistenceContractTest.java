package com.penmate.backend.application.agent.usecase;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.penmate.backend.application.agent.run.AgentRunAppService;
import com.penmate.backend.application.agent.run.AgentRunDispatcher;
import com.penmate.backend.application.agent.run.AgentRunEventPublisher;
import com.penmate.backend.application.agent.run.AgentRunRecoveryPromptService;
import com.penmate.backend.application.agent.skill.AgentSkillActivationService;
import com.penmate.backend.application.style.usecase.SessionStyleBindingAppService;
import com.penmate.backend.application.agent.run.AgentRunAppService;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentTurnPersistenceContractTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static SqlSessionFactory sqlSessionFactory;

    @BeforeAll
    static void setUpDatabase() {
        sqlSessionFactory = buildSqlSessionFactory(
                PostgreSqlTestDatabase.migratedDataSource("agent_turn_persistence_contract"));
    }

    @BeforeEach
    void resetSchema() throws Exception {
        resetRows();
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
                    dispatcher,
                    mock(AgentSkillActivationService.class),
                    passthroughRecoveryPrompts()
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
            assertThat(singleLong("SELECT turn_id FROM agent_messages WHERE message_id = 930001"))
                    .isEqualTo(940001L);
            assertThat(singleString("SELECT prompt_snapshot FROM agent_run_inputs WHERE run_id = 950001"))
                    .isEqualTo("Write the next beat.");
            assertThat(JSON.readTree(singleString(
                    "SELECT style_snapshot_json FROM agent_run_inputs WHERE run_id = 950001")))
                    .isEqualTo(JSON.readTree("{\"styleId\":81}"));
            verify(dispatcher).dispatchInitialRun(950001L, "trace-turn-persist-1");
        }
    }

    @Test
    void should_reload_run_input_from_created_turn() throws Exception {
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
                    mock(AgentRunDispatcher.class),
                    mock(AgentSkillActivationService.class),
                    passthroughRecoveryPrompts()
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
            JsonNode modelSnapshot = JSON.readTree(input.modelSnapshotJson());
            assertThat(modelSnapshot.path("operatorId").asLong()).isEqualTo(1001L);
            assertThat(modelSnapshot.get("modelConfigId").isNull()).isTrue();
            assertThat(input.inputHash()).isNotBlank();
        }
    }

    @Test
    void should_list_only_messages_before_the_current_turn() throws Exception {
        try (Connection connection = sqlSessionFactory.getConfiguration().getEnvironment().getDataSource().getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    INSERT INTO agent_messages(
                        message_id, session_id, role, message_kind, content_markdown, seq_no
                    ) VALUES
                        (930101, 920002, 'user', 'CHAT', 'Earlier request', 1),
                        (930102, 920002, 'assistant', 'CHAT', 'Earlier answer', 2),
                        (930103, 920002, 'user', 'CHAT', 'Current request', 3)
                    """);
            statement.execute("""
                    INSERT INTO agent_turns(
                        turn_id, session_id, turn_seq, user_message_id, assistant_message_id,
                        turn_status
                    ) VALUES
                        (940101, 920002, 1, 930101, 930102, 'COMPLETED'),
                        (940102, 920002, 2, 930103, NULL, 'PENDING')
                    """);
        }

        try (SqlSession sqlSession = sqlSessionFactory.openSession(true)) {
            var messages = sqlSession.getMapper(AgentMapper.class)
                    .listMessagesBeforeTurn(920002L, 940102L);

            assertThat(messages)
                    .extracting("messageId")
                    .containsExactly(930101L, 930102L);

            assertThat(sqlSession.getMapper(AgentSessionMapper.class).listMessageRows(920002L))
                    .extracting(row -> row.get("turnId"))
                    .containsExactly(940101L, 940101L, 940102L);
        }
    }

    private static AgentRunRecoveryPromptService passthroughRecoveryPrompts() {
        AgentRunRecoveryPromptService service = mock(AgentRunRecoveryPromptService.class);
        when(service.attachToManualRequest(any(), any(), any())).thenAnswer(invocation -> invocation.getArgument(2));
        return service;
    }

    private static SqlSessionFactory buildSqlSessionFactory(DataSource dataSource) {
        Environment environment = new Environment("test", new JdbcTransactionFactory(), dataSource);
        Configuration configuration = new Configuration(environment);
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(AgentMapper.class);
        configuration.addMapper(AgentSessionMapper.class);
        configuration.addMapper(AgentRunMapper.class);
        return new SqlSessionFactoryBuilder().build(configuration);
    }

    private static void resetRows() throws Exception {
        try (Connection connection = sqlSessionFactory.getConfiguration().getEnvironment().getDataSource().getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("DELETE FROM agent_run_inputs WHERE run_id BETWEEN 950000 AND 950999");
            statement.execute("DELETE FROM agent_runs WHERE run_id BETWEEN 950000 AND 950999");
            statement.execute("DELETE FROM agent_turns WHERE turn_id BETWEEN 940000 AND 940999");
            statement.execute("DELETE FROM agent_session_style_bindings WHERE session_id = 920002");
            statement.execute("DELETE FROM agent_sessions WHERE session_id = 920002");
            statement.execute("DELETE FROM agent_messages WHERE message_id BETWEEN 930000 AND 930999");
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
                java.util.List.of(),
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

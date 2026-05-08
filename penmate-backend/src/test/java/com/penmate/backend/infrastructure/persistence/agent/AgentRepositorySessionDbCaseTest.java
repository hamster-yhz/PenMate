package com.penmate.backend.infrastructure.persistence.agent;

import com.penmate.backend.application.agent.command.AgentCommands.CreateConversationCommand;
import com.penmate.backend.application.agent.usecase.AgentConversationAppService;
import com.penmate.backend.domain.agent.model.AgentConversation;
import com.penmate.backend.domain.shared.service.BusinessIdGenerator;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.StreamUtils;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AgentRepositorySessionDbCaseTest {

    private static final String H2_URL = "jdbc:h2:mem:agent_repository_session_dbcase;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";

    private static SqlSessionFactory sqlSessionFactory;

    @BeforeAll
    static void setUpDatabase() {
        sqlSessionFactory = buildSqlSessionFactory();
    }

    @BeforeEach
    void resetSchema() throws Exception {
        recreateSchema();
        seedBaseAgentSessionRows();
    }

    @Test
    void should_list_conversations_from_agent_sessions_seed() {
        try (SqlSession sqlSession = sqlSessionFactory.openSession(true)) {
            AgentRepositoryImpl repository = new AgentRepositoryImpl(
                    sqlSession.getMapper(AgentMapper.class),
                    sqlSession.getMapper(AgentSessionMapper.class)
            );

            List<AgentConversation> conversations = repository.listConversations(920001L);

            assertThat(conversations).hasSize(2);
            assertThat(conversations)
                    .extracting(AgentConversation::getConversationId)
                    .containsExactly(920002L, 920001L);
            assertThat(conversations)
                    .filteredOn(conversation -> conversation.getConversationId().equals(920001L))
                    .singleElement()
                    .satisfies(conversation -> {
                        assertThat(conversation.getUserId()).isEqualTo(920002L);
                        assertThat(conversation.getTitle()).isEqualTo("第一卷创作会话");
                        assertThat(conversation.getStatus()).isEqualTo("ACTIVE");
                    });
        }
    }

    @Test
    void should_insert_and_find_conversation_via_agent_sessions() throws Exception {
        try (SqlSession sqlSession = sqlSessionFactory.openSession(true)) {
            AgentRepositoryImpl repository = new AgentRepositoryImpl(
                    sqlSession.getMapper(AgentMapper.class),
                    sqlSession.getMapper(AgentSessionMapper.class)
            );

            AgentConversation conversation = new AgentConversation();
            conversation.setConversationId(930001L);
            conversation.setProjectId(920001L);
            conversation.setUserId(920002L);
            conversation.setTitle("新建会话");
            conversation.setStatus("ACTIVE");

            assertThat(repository.insertConversation(conversation)).isEqualTo(1);
            assertThat(conversation.getId()).isNotNull();

            AgentConversation loaded = repository.findConversation(920001L, 930001L);
            assertThat(loaded).isNotNull();
            assertThat(loaded.getConversationId()).isEqualTo(930001L);
            assertThat(loaded.getTitle()).isEqualTo("新建会话");
            assertThat(loaded.getStatus()).isEqualTo("ACTIVE");
            assertThat(countRows("agent_sessions")).isEqualTo(3);
        }
    }

    @Test
    void should_create_via_app_service_and_round_trip_through_session_schema() {
        try (SqlSession sqlSession = sqlSessionFactory.openSession(true)) {
            AgentRepositoryImpl repository = new AgentRepositoryImpl(
                    sqlSession.getMapper(AgentMapper.class),
                    sqlSession.getMapper(AgentSessionMapper.class)
            );
            AgentConversationAppService appService = new AgentConversationAppService(repository, () -> 930101L);

            AgentConversation created = appService.createConversation(
                    920001L,
                    new CreateConversationCommand(920002L, "工作台新会话", "active", 920002L),
                    "TRACE-AGENT-CREATE-1"
            );

            AgentConversation loaded = repository.findConversation(920001L, 930101L);
            List<AgentConversation> listed = repository.listConversations(920001L);

            assertThat(created.getConversationId()).isEqualTo(930101L);
            assertThat(created.getUserId()).isEqualTo(920002L);
            assertThat(created.getTitle()).isEqualTo("工作台新会话");
            assertThat(created.getStatus()).isEqualTo("ACTIVE");
            assertThat(created.getContextScopeJson()).isNull();

            assertThat(loaded).isNotNull();
            assertThat(loaded.getConversationId()).isEqualTo(930101L);
            assertThat(loaded.getUserId()).isEqualTo(920002L);
            assertThat(loaded.getTitle()).isEqualTo("工作台新会话");
            assertThat(loaded.getStatus()).isEqualTo("ACTIVE");
            assertThat(loaded.getContextScopeJson()).isNull();

            assertThat(listed)
                    .extracting(AgentConversation::getConversationId)
                    .contains(930101L);
        }
    }

    @Test
    void should_persist_style_binding_with_distinct_binding_id_and_business_session_id() throws Exception {
        try (SqlSession sqlSession = sqlSessionFactory.openSession(true)) {
            BusinessIdGenerator businessIdGenerator = () -> 940001L;
            AgentSessionRepositoryImpl repository = new AgentSessionRepositoryImpl(
                    sqlSession.getMapper(AgentSessionMapper.class),
                    businessIdGenerator
            );

            assertThat(repository.updateBoundStyle(920001L, 920001L, 920002L, 920100L)).isEqualTo(1);
            assertThat(repository.insertStyleBinding(920001L, 920001L, 920002L, 920100L, "TRACE-BIND-1")).isEqualTo(1);

            assertThat(singleLong("SELECT bound_style_id FROM agent_sessions WHERE session_id = 920001")).isEqualTo(920002L);
            assertThat(singleLong("SELECT binding_id FROM agent_session_style_bindings WHERE session_id = 920001 AND deactivated_at IS NULL ORDER BY id DESC LIMIT 1"))
                    .isEqualTo(940001L);
            assertThat(singleLong("SELECT session_id FROM agent_session_style_bindings WHERE binding_id = 940001"))
                    .isEqualTo(920001L);
            assertThat(singleLong("SELECT style_id FROM agent_session_style_bindings WHERE binding_id = 940001"))
                    .isEqualTo(920002L);
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
            statement.execute("DROP TABLE IF EXISTS agent_session_style_bindings");
            statement.execute("DROP TABLE IF EXISTS agent_sessions");
            statement.execute("DROP TABLE IF EXISTS agent_messages");
            statement.execute("DROP TABLE IF EXISTS agent_tasks");
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
            statement.execute("CREATE TABLE agent_messages (id BIGINT PRIMARY KEY, message_id BIGINT NOT NULL)");
            statement.execute("CREATE TABLE agent_tasks (id BIGINT PRIMARY KEY, task_id BIGINT NOT NULL)");
        }
    }

    private static void seedBaseAgentSessionRows() throws Exception {
        String sql;
        try (var inputStream = new ClassPathResource("db/cases/seed_all_domain_base.sql").getInputStream()) {
            sql = StreamUtils.copyToString(inputStream, StandardCharsets.UTF_8);
        }

        int start = sql.indexOf("-- Agent + 审批");
        int end = sql.indexOf("-- RAG + 对象存储");
        String block = sql.substring(start, end)
                .replaceAll("(?m)^\\s*--.*$", "")
                .replace("SET NAMES utf8mb4;", "")
                .replace("NOW(3)", "CURRENT_TIMESTAMP");

        try (Connection connection = sqlSessionFactory.getConfiguration().getEnvironment().getDataSource().getConnection();
             Statement statement = connection.createStatement()) {
            for (String statementSql : block.split(";")) {
                String trimmed = statementSql.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                if (trimmed.startsWith("INSERT INTO agent_turns")
                        || trimmed.startsWith("INSERT INTO agent_task_contexts")
                        || trimmed.startsWith("INSERT INTO agent_task_results")
                        || trimmed.startsWith("INSERT INTO agent_pending_approvals")
                        || trimmed.startsWith("INSERT INTO agent_approval_requests")
                        || trimmed.startsWith("INSERT INTO agent_approval_actions")) {
                    continue;
                }
                statement.execute(trimmed);
            }
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

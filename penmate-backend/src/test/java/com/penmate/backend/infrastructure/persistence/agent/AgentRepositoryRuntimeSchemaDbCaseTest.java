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
import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
            task.setUserId(1001L);
            task.setConversationId(920002L);
            task.setChapterId(3001L);
            task.setModelConfigId(88001L);
            task.setTaskType("WRITE");
            task.setPromptSnapshot("请继续写作");
            task.setPluginSnapshot("{\"tools\":[\"search\"]}");
            task.setTraceId("trace-runtime-task-1");
            task.setStatus("pending");

            assertThat(repository.insertGenerationTask(task)).isEqualTo(1);
            assertThat(countRows("agent_tasks")).isEqualTo(1);
            assertThat(singleLong("SELECT session_id FROM agent_tasks WHERE task_id = 940401")).isEqualTo(920002L);
            assertThat(singleString("SELECT prompt_snapshot FROM agent_tasks WHERE task_id = 940401"))
                    .as("runtime schema insert should persist promptSnapshot")
                    .isEqualTo("请继续写作");
        }
    }

    @Test
    void should_read_generation_task_with_user_and_model_config_from_runtime_schema() throws Exception {
        try (Connection connection = sqlSessionFactory.getConfiguration().getEnvironment().getDataSource().getConnection();
             Statement statement = connection.createStatement();
             SqlSession sqlSession = sqlSessionFactory.openSession(true)) {
            statement.execute("""
                    INSERT INTO agent_tasks(task_id, session_id, turn_id, project_id, task_type, task_status, prompt_snapshot, request_context_id, result_id, active_approval_id, stream_channel_key, trace_id)
                    VALUES (940402, 920002, 1, 920001, 'WRITE', 'pending', '恢复后的 prompt', 950402, NULL, NULL, NULL, 'trace-runtime-task-2')
                    """);
            statement.execute("""
                    INSERT INTO agent_task_contexts(context_id, task_id, chapter_id, selected_text, outline_snapshot_json, cards_snapshot_json, rag_snapshot_json, plugin_bindings_json, style_snapshot_json, model_snapshot_json, context_hash)
                    VALUES (950402, 940402, 3002, '选中文本', NULL, NULL, NULL, NULL, NULL, '{"operatorId":1001,"modelConfigId":88002}', 'hash-940402')
                    """);

            assertThat(singleLong("SELECT COUNT(*) FROM agent_tasks WHERE project_id = 920001 AND task_id = 940402")).isEqualTo(1L);

            AgentMapper mapper = sqlSession.getMapper(AgentMapper.class);
            assertThat(mapper.findGenerationTask(920001L, 940402L)).isNotNull();

            AgentRepositoryImpl repository = new AgentRepositoryImpl(
                    mapper,
                    sqlSession.getMapper(AgentSessionMapper.class)
            );

            AgentGenerationTask loaded = repository.findGenerationTask(920001L, 940402L);

            assertThat(loaded).isNotNull();
            assertThat(loaded.getTaskId()).isEqualTo(940402L);
            assertThat(loaded.getUserId()).isEqualTo(1001L);
            assertThat(loaded.getModelConfigId()).isEqualTo(88002L);
            assertThat(loaded.getChapterId()).isEqualTo(3002L);
            assertThat(loaded.getConversationId()).isEqualTo(920002L);
            assertThat(loaded.getPromptSnapshot()).isEqualTo("恢复后的 prompt");
        }
    }

    @Test
    void should_update_generation_task_status_after_loading_task_from_runtime_schema() throws Exception {
        try (Connection connection = sqlSessionFactory.getConfiguration().getEnvironment().getDataSource().getConnection();
             Statement statement = connection.createStatement();
             SqlSession sqlSession = sqlSessionFactory.openSession(true)) {
            statement.execute("""
                    INSERT INTO agent_tasks(task_id, session_id, turn_id, project_id, task_type, task_status, prompt_snapshot, request_context_id, result_id, active_approval_id, stream_channel_key, trace_id)
                    VALUES (940403, 920002, 1, 920001, 'WRITE', 'pending', '状态更新 prompt', 950403, NULL, NULL, NULL, 'trace-runtime-task-3')
                    """);

            AgentRepositoryImpl repository = new AgentRepositoryImpl(
                    sqlSession.getMapper(AgentMapper.class),
                    sqlSession.getMapper(AgentSessionMapper.class)
            );

            AgentGenerationTask loaded = repository.findGenerationTask(920001L, 940403L);

            assertThat(loaded).isNotNull();
            assertThat(loaded.getTaskId()).isEqualTo(940403L);
            assertThat(repository.updateGenerationTaskStatus(920001L, loaded.getTaskId(), "running", null)).isEqualTo(1);
            assertThat(singleLong("SELECT COUNT(*) FROM agent_tasks WHERE project_id = 920001 AND task_id = 940403 AND task_status = 'running'"))
                    .isEqualTo(1L);
        }
    }

    @Test
    void should_read_persisted_task_context_snapshot_from_runtime_schema() throws Exception {
        try (Connection connection = sqlSessionFactory.getConfiguration().getEnvironment().getDataSource().getConnection();
             Statement statement = connection.createStatement();
             SqlSession sqlSession = sqlSessionFactory.openSession(true)) {
            statement.execute("""
                    INSERT INTO agent_tasks(task_id, session_id, turn_id, project_id, task_type, task_status, prompt_snapshot, request_context_id, result_id, active_approval_id, stream_channel_key, trace_id)
                    VALUES (940404, 920002, 1, 920001, 'WRITE', 'pending', '上下文恢复 prompt', 950404, NULL, NULL, NULL, 'trace-runtime-task-4')
                    """);
            statement.execute("""
                    INSERT INTO agent_task_contexts(context_id, task_id, chapter_id, selected_text, outline_snapshot_json, cards_snapshot_json, rag_snapshot_json, plugin_bindings_json, style_snapshot_json, model_snapshot_json, context_hash)
                    VALUES (950404, 940404, 3004, '冻结选中文本', '{"outline":true}', '{"cards":true}', '{"rag":true}', '{"plugins":true}', '{"styleId":81}', '{"modelConfigId":88004}', 'hash-940404')
                    """);

            AgentRepositoryImpl repository = new AgentRepositoryImpl(
                    sqlSession.getMapper(AgentMapper.class),
                    sqlSession.getMapper(AgentSessionMapper.class)
            );

            com.penmate.backend.domain.agent.model.AgentTaskContext loaded = repository.findTaskContext(940404L);

            assertThat(loaded).isNotNull();
            assertThat(loaded.getContextId()).isEqualTo(950404L);
            assertThat(loaded.getTaskId()).isEqualTo(940404L);
            assertThat(loaded.getChapterId()).isEqualTo(3004L);
            assertThat(loaded.getSelectedText()).isEqualTo("冻结选中文本");
            assertThat(loaded.getStyleSnapshotJson()).isEqualTo("{\"styleId\":81}");
            assertThat(loaded.getOutlineSnapshotJson()).isEqualTo("{\"outline\":true}");
            assertThat(loaded.getCardsSnapshotJson()).isEqualTo("{\"cards\":true}");
            assertThat(loaded.getRagSnapshotJson()).isEqualTo("{\"rag\":true}");
            assertThat(loaded.getPluginBindingsJson()).isEqualTo("{\"plugins\":true}");
            assertThat(loaded.getModelSnapshotJson()).isEqualTo("{\"modelConfigId\":88004}");
            assertThat(loaded.getContextHash()).isEqualTo("hash-940404");
        }
    }

    @Test
    void should_read_structured_task_snapshots_from_runtime_task_context_schema() throws Exception {
        try (Connection connection = sqlSessionFactory.getConfiguration().getEnvironment().getDataSource().getConnection();
             Statement statement = connection.createStatement();
             SqlSession sqlSession = sqlSessionFactory.openSession(true)) {
            statement.execute("""
                    INSERT INTO agent_tasks(task_id, session_id, turn_id, project_id, task_type, task_status, prompt_snapshot, request_context_id, result_id, active_approval_id, stream_channel_key, trace_id)
                    VALUES (940405, 920002, 1, 920001, 'WRITE', 'pending', '快照恢复 prompt', 950405, NULL, NULL, NULL, 'trace-runtime-task-5')
                    """);
            statement.execute("""
                    INSERT INTO agent_task_contexts(
                        context_id, task_id, chapter_id, selected_text,
                        outline_snapshot_json, cards_snapshot_json, rag_snapshot_json,
                        plugin_bindings_json, style_snapshot_json, model_snapshot_json,
                        task_profile_json, prompt_plan_json, context_package_json,
                        active_tool_calls_snapshot, last_runtime_status, recovery_cursor, context_hash
                    ) VALUES (
                        950405, 940405, 3005, '冻结快照文本',
                        NULL, NULL, NULL,
                        NULL, '{"styleId":82}', '{"modelConfigId":88005}',
                        '{"executionProfile":"default"}',
                        '{"finalProfile":"default"}',
                        '{"missingContextFlags":["story_bible_missing"]}',
                        '[{"toolCode":"quality_review","status":"WAITING_APPROVAL"}]',
                        'WAITING_APPROVAL',
                        'approval:950405',
                        'hash-940405'
                    )
                    """);

            AgentRepositoryImpl repository = new AgentRepositoryImpl(
                    sqlSession.getMapper(AgentMapper.class),
                    sqlSession.getMapper(AgentSessionMapper.class)
            );

            com.penmate.backend.domain.agent.model.AgentTaskContext loaded = repository.findTaskContext(940405L);

            assertThat(readField(loaded, "taskProfileJson")).isEqualTo("{\"executionProfile\":\"default\"}");
            assertThat(readField(loaded, "promptPlanJson")).isEqualTo("{\"finalProfile\":\"default\"}");
            assertThat(readField(loaded, "contextPackageJson")).isEqualTo("{\"missingContextFlags\":[\"story_bible_missing\"]}");
            assertThat(loaded.getActiveToolCallsSnapshot()).isEqualTo("[{\"toolCode\":\"quality_review\",\"status\":\"WAITING_APPROVAL\"}]");
            assertThat(loaded.getLastRuntimeStatus()).isEqualTo("WAITING_APPROVAL");
            assertThat(loaded.getRecoveryCursor()).isEqualTo("approval:950405");
        }
    }

    @Test
    void should_update_structured_task_snapshots_into_runtime_task_context_schema() throws Exception {
        try (Connection connection = sqlSessionFactory.getConfiguration().getEnvironment().getDataSource().getConnection();
             Statement statement = connection.createStatement();
             SqlSession sqlSession = sqlSessionFactory.openSession(true)) {
            statement.execute("""
                    INSERT INTO agent_tasks(task_id, session_id, turn_id, project_id, task_type, task_status, prompt_snapshot, request_context_id, result_id, active_approval_id, stream_channel_key, trace_id)
                    VALUES (940406, 920002, 1, 920001, 'WRITE', 'pending', '快照写回 prompt', 950406, NULL, NULL, NULL, 'trace-runtime-task-6')
                    """);
            statement.execute("""
                    INSERT INTO agent_task_contexts(
                        context_id, task_id, chapter_id, selected_text,
                        outline_snapshot_json, cards_snapshot_json, rag_snapshot_json,
                        plugin_bindings_json, style_snapshot_json, model_snapshot_json,
                        task_profile_json, prompt_plan_json, context_package_json, context_hash
                    ) VALUES (
                        950406, 940406, 3006, '待写回快照文本',
                        NULL, NULL, NULL,
                        NULL, '{"styleId":83}', '{"modelConfigId":88006}',
                        NULL, NULL, NULL,
                        'hash-940406'
                    )
                    """);

            AgentRepositoryImpl repository = new AgentRepositoryImpl(
                    sqlSession.getMapper(AgentMapper.class),
                    sqlSession.getMapper(AgentSessionMapper.class)
            );

            assertThat(repository.updateGenerationTaskSnapshots(
                    920001L,
                    940406L,
                    "{\"executionProfile\":\"default\"}",
                    "{\"finalProfile\":\"default\"}",
                    "{\"chapterScope\":\"chapter:3006\"}",
                    "[{\"toolCode\":\"quality_review\",\"status\":\"RUNNING\"}]",
                    "QUALITY_REVIEW",
                    "tool_call:quality_review:1"
            )).isEqualTo(1);
            assertThat(singleString("SELECT task_profile_json FROM agent_task_contexts WHERE task_id = 940406"))
                    .isEqualTo("{\"executionProfile\":\"default\"}");
            assertThat(singleString("SELECT prompt_plan_json FROM agent_task_contexts WHERE task_id = 940406"))
                    .isEqualTo("{\"finalProfile\":\"default\"}");
            assertThat(singleString("SELECT context_package_json FROM agent_task_contexts WHERE task_id = 940406"))
                    .isEqualTo("{\"chapterScope\":\"chapter:3006\"}");
            assertThat(singleString("SELECT active_tool_calls_snapshot FROM agent_task_contexts WHERE task_id = 940406"))
                    .isEqualTo("[{\"toolCode\":\"quality_review\",\"status\":\"RUNNING\"}]");
            assertThat(singleString("SELECT last_runtime_status FROM agent_task_contexts WHERE task_id = 940406"))
                    .isEqualTo("QUALITY_REVIEW");
            assertThat(singleString("SELECT recovery_cursor FROM agent_task_contexts WHERE task_id = 940406"))
                    .isEqualTo("tool_call:quality_review:1");
        }
    }

    @Test
    void should_persist_runtime_token_and_cost_json_into_agent_task_results() throws Exception {
        try (Connection connection = sqlSessionFactory.getConfiguration().getEnvironment().getDataSource().getConnection();
             Statement statement = connection.createStatement();
             SqlSession sqlSession = sqlSessionFactory.openSession(true)) {
            statement.execute("""
                    INSERT INTO agent_tasks(task_id, session_id, turn_id, project_id, task_type, task_status, prompt_snapshot, request_context_id, result_id, active_approval_id, stream_channel_key, trace_id)
                    VALUES (940407, 920002, 1, 920001, 'WRITE', 'done', 'runtime 写回 prompt', NULL, 960407, NULL, NULL, 'trace-runtime-task-7')
                    """);
            statement.execute("""
                    INSERT INTO agent_task_results(result_id, task_id, result_status, assistant_message_id, output_markdown, output_structured_json, tool_trace_json, token_usage_json, cost_usage_json, error_code, error_message)
                    VALUES (960407, 940407, 'SUCCEEDED', NULL, '正文', NULL, NULL, NULL, NULL, NULL, NULL)
                    """);

            AgentRepositoryImpl repository = new AgentRepositoryImpl(
                    sqlSession.getMapper(AgentMapper.class),
                    sqlSession.getMapper(AgentSessionMapper.class)
            );

            assertThat(repository.updateGenerationTaskRuntime(
                    920001L,
                    940407L,
                    "{\"inputTokens\":11,\"outputTokens\":10}",
                    "{\"currency\":\"USD\",\"estimated\":0.000020}",
                    "trace-runtime-task-7-updated"
            )).isEqualTo(1);
            assertThat(singleString("SELECT token_usage_json FROM agent_task_results WHERE result_id = 960407"))
                    .isEqualTo("{\"inputTokens\":11,\"outputTokens\":10}");
            assertThat(singleString("SELECT cost_usage_json FROM agent_task_results WHERE result_id = 960407"))
                    .isEqualTo("{\"currency\":\"USD\",\"estimated\":0.000020}");
        }
    }

    @Test
    void should_persist_result_summaries_and_recover_them_from_runtime_snapshot() throws Exception {
        try (Connection connection = sqlSessionFactory.getConfiguration().getEnvironment().getDataSource().getConnection();
             Statement statement = connection.createStatement();
             SqlSession sqlSession = sqlSessionFactory.openSession(true)) {
            statement.execute("""
                    UPDATE agent_sessions
                    SET last_turn_id = 1,
                        last_task_id = 940408
                    WHERE session_id = 920002
                    """);
            statement.execute("""
                    INSERT INTO agent_tasks(task_id, session_id, turn_id, project_id, task_type, task_status, prompt_snapshot, request_context_id, result_id, active_approval_id, stream_channel_key, trace_id)
                    VALUES (940408, 920002, 1, 920001, 'WRITE', 'WAITING_APPROVAL', '恢复摘要 prompt', 950408, NULL, 980408, NULL, 'trace-runtime-task-8')
                    """);
            statement.execute("""
                    INSERT INTO agent_task_contexts(
                        context_id, task_id, chapter_id, selected_text,
                        outline_snapshot_json, cards_snapshot_json, rag_snapshot_json,
                        plugin_bindings_json, style_snapshot_json, model_snapshot_json,
                        task_profile_json, prompt_plan_json, context_package_json,
                        active_tool_calls_snapshot, last_runtime_status, recovery_cursor, context_hash
                    ) VALUES (
                        950408, 940408, 3008, '恢复摘要文本',
                        '{"chapter":"第八章"}', NULL, NULL,
                        NULL, '{"styleId":88}', '{"modelConfigId":88008}',
                        '{"executionProfile":"default"}',
                        '{"finalProfile":"default"}',
                        '{"chapterScope":"chapter:3008"}',
                        '[{"toolCode":"quality_review","status":"WAITING_APPROVAL"}]',
                        'WAITING_APPROVAL',
                        'approval:980408',
                        'hash-940408'
                    )
                    """);

            AgentRepositoryImpl repository = new AgentRepositoryImpl(
                    sqlSession.getMapper(AgentMapper.class),
                    sqlSession.getMapper(AgentSessionMapper.class)
            );
            com.penmate.backend.domain.agent.model.AgentTaskResult result = new com.penmate.backend.domain.agent.model.AgentTaskResult();
            result.setResultId(960408L);
            result.setTaskId(940408L);
            result.setResultStatus("SUCCEEDED");
            result.setOutputMarkdown("最终答复");
            result.setDraftSummary("{\"draftText\":\"第三章初稿正文\"}");
            result.setQualityReportSummary("{\"reviewSummary\":\"存在剧情逻辑问题，需要修订。\"}");
            result.setTodoSummary("{\"planTitle\":\"第三章修订待办\"}");
            result.setStoryBibleProposalSummary("{\"proposalSummary\":\"建议补充侍从知晓密令的设定\"}");

            assertThat(repository.insertTaskResult(result)).isEqualTo(1);
            assertThat(singleString("SELECT draft_summary FROM agent_task_results WHERE task_id = 940408"))
                    .isEqualTo("{\"draftText\":\"第三章初稿正文\"}");
            assertThat(singleString("SELECT quality_report_summary FROM agent_task_results WHERE task_id = 940408"))
                    .isEqualTo("{\"reviewSummary\":\"存在剧情逻辑问题，需要修订。\"}");
            assertThat(singleString("SELECT todo_summary FROM agent_task_results WHERE task_id = 940408"))
                    .isEqualTo("{\"planTitle\":\"第三章修订待办\"}");
            assertThat(singleString("SELECT story_bible_proposal_summary FROM agent_task_results WHERE task_id = 940408"))
                    .isEqualTo("{\"proposalSummary\":\"建议补充侍从知晓密令的设定\"}");

            com.penmate.backend.domain.shared.service.BusinessIdGenerator businessIdGenerator = () -> 990408L;
            AgentSessionRepositoryImpl sessionRepository = new AgentSessionRepositoryImpl(
                    sqlSession.getMapper(AgentSessionMapper.class),
                    businessIdGenerator
            );
            com.penmate.backend.domain.agent.model.AgentSessionRecoverySnapshot snapshot = sessionRepository.findRecoverySnapshot(920001L, 920002L);

            assertThat(snapshot.getWorkbenchContext()).contains("\"draftSummary\":{\"draftText\":\"第三章初稿正文\"}");
            assertThat(snapshot.getWorkbenchContext()).contains("\"qualityReportSummary\":{\"reviewSummary\":\"存在剧情逻辑问题，需要修订。\"}");
            assertThat(snapshot.getWorkbenchContext()).contains("\"todoSummary\":{\"planTitle\":\"第三章修订待办\"}");
            assertThat(snapshot.getWorkbenchContext()).contains("\"storyBibleProposalSummary\":{\"proposalSummary\":\"建议补充侍从知晓密令的设定\"}");
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
            statement.execute("DROP TABLE IF EXISTS agent_task_results");
            statement.execute("DROP TABLE IF EXISTS agent_task_contexts");
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
                        outline_snapshot_json VARCHAR(4000) NULL,
                        cards_snapshot_json VARCHAR(4000) NULL,
                        rag_snapshot_json VARCHAR(4000) NULL,
                        plugin_bindings_json VARCHAR(4000) NULL,
                        style_snapshot_json VARCHAR(4000) NULL,
                        model_snapshot_json VARCHAR(4000) NULL,
                        task_profile_json VARCHAR(4000) NULL,
                        prompt_plan_json VARCHAR(4000) NULL,
                        context_package_json VARCHAR(4000) NULL,
                        active_tool_calls_snapshot VARCHAR(4000) NULL,
                        last_runtime_status VARCHAR(64) NULL,
                        recovery_cursor VARCHAR(128) NULL,
                        context_hash VARCHAR(128) NOT NULL,
                        created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                    )
                    """);
            statement.execute("""
                    CREATE TABLE agent_task_results (
                        id BIGINT PRIMARY KEY AUTO_INCREMENT,
                        result_id BIGINT NOT NULL,
                        task_id BIGINT NOT NULL,
                        result_status VARCHAR(24) NOT NULL,
                        assistant_message_id BIGINT NULL,
                        output_markdown VARCHAR(4000) NULL,
                        output_structured_json VARCHAR(4000) NULL,
                        tool_trace_json VARCHAR(4000) NULL,
                        draft_summary VARCHAR(4000) NULL,
                        quality_report_summary VARCHAR(4000) NULL,
                        todo_summary VARCHAR(4000) NULL,
                        story_bible_proposal_summary VARCHAR(4000) NULL,
                        token_usage_json VARCHAR(4000) NULL,
                        cost_usage_json VARCHAR(4000) NULL,
                        error_code VARCHAR(64) NULL,
                        error_message VARCHAR(500) NULL,
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

    private String singleString(String sql) throws Exception {
        try (Connection connection = sqlSessionFactory.getConfiguration().getEnvironment().getDataSource().getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            resultSet.next();
            return resultSet.getString(1);
        }
    }

    private Object readField(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(target);
    }
}

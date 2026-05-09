package com.penmate.backend.application.agent.usecase;

import com.penmate.backend.application.style.usecase.SessionStyleBindingAppService;
import com.penmate.backend.domain.agent.model.AgentGenerationTask;
import com.penmate.backend.domain.agent.model.AgentMessage;
import com.penmate.backend.domain.agent.model.AgentSession;
import com.penmate.backend.domain.agent.model.AgentTaskContext;
import com.penmate.backend.domain.agent.repository.AgentRepository;
import com.penmate.backend.domain.agent.repository.AgentSessionRepository;
import com.penmate.backend.domain.shared.service.BusinessIdGenerator;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentTurnAppServiceTest {

    private static BusinessIdGenerator businessIdGenerator(Long... ids) {
        BusinessIdGenerator generator = mock(BusinessIdGenerator.class);
        if (ids != null && ids.length > 0) {
            Long first = ids[0];
            Long[] rest = java.util.Arrays.copyOfRange(ids, 1, ids.length);
            when(generator.nextId()).thenReturn(first, rest);
        }
        return generator;
    }

    @Test
    void should_create_turn_message_task_context_and_result_pipeline() {
        AgentTurnAppService agentTurnAppService = new AgentTurnAppService(
                bindingAppServiceWithBoundStyle(null),
                agentRepository(),
                sessionRepository(),
                businessIdGenerator(930001L, 940001L, 950001L)
        );
        Long projectId = 920001L;
        Long sessionId = AgentSession.active(920002L, projectId, 1001L, "Session-A").getSessionId();

        AgentTurnCommand command = new AgentTurnCommand(
                1001L,
                "请继续写作",
                new AgentTurnCommand.TaskRequest("WRITE", 3001L, null, "selected text")
        );

        AgentTurnResult result = agentTurnAppService.createTurn(projectId, sessionId, command, "trace-turn-1");

        assertThat(result.taskType()).isEqualTo("WRITE");
        assertThat(result.activeTask()).isNotNull();
        assertThat(result.activeTask().taskId()).isNotNull();
        assertThat(result.activeTask().requestContextId())
                .as("requestContextId should be exposed on activeTask result")
                .isNotNull();
    }

    @Test
    void should_write_style_snapshot_json_into_created_task_context() {
        AgentTaskContext[] capturedContext = new AgentTaskContext[1];
        SessionStyleBindingAppService bindingAppService = bindingAppServiceWithBoundStyle(81L);
        AgentTurnAppService agentTurnAppService = new AgentTurnAppService(
                bindingAppService,
                agentRepository(),
                sessionRepository(),
                businessIdGenerator(930002L, 940002L, 950002L)
        ) {
            @Override
            protected AgentTaskContext createTaskContext(Long projectId,
                                                         Long sessionId,
                                                         AgentTurnCommand command,
                                                         AgentMessage userMessage,
                                                         AgentGenerationTask task,
                                                         String traceId) {
                AgentTaskContext context = super.createTaskContext(projectId, sessionId, command, userMessage, task, traceId);
                capturedContext[0] = context;
                return context;
            }
        };

        AgentTurnCommand command = new AgentTurnCommand(
                1001L,
                "将风格绑定写入上下文",
                new AgentTurnCommand.TaskRequest("WRITE", 3003L, null, "带风格的上下文")
        );

        agentTurnAppService.createTurn(920001L, 920002L, command, "trace-turn-style-missing");

        assertThat(capturedContext[0]).isNotNull();
        assertThat(capturedContext[0].getStyleSnapshotJson()).isEqualTo("{\"styleId\":81}");
    }

    @Test
    void should_return_ids_from_created_message_and_task_pipeline() {
        AgentTurnAppService agentTurnAppService = new AgentTurnAppService(
                bindingAppServiceWithBoundStyle(null),
                agentRepository(),
                sessionRepository(),
                businessIdGenerator(930003L, 940003L, 950003L)
        ) {
            @Override
            protected AgentMessage createUserMessage(Long projectId, Long sessionId, AgentTurnCommand command, String traceId) {
                AgentMessage message = new AgentMessage();
                message.setMessageId(930001L);
                message.setConversationId(sessionId);
                message.setContentMd(command.userMessage());
                message.setSeqNo(1);
                return message;
            }

            @Override
            protected AgentGenerationTask createGenerationTask(Long projectId,
                                                               Long sessionId,
                                                               AgentTurnCommand command,
                                                               AgentMessage userMessage,
                                                               String traceId) {
                AgentGenerationTask task = new AgentGenerationTask();
                task.setTaskId(940001L);
                task.setProjectId(projectId);
                task.setConversationId(sessionId);
                task.setTaskType(command.taskRequest().taskType());
                task.setStatus("pending");
                return task;
            }
        };

        AgentTurnCommand command = new AgentTurnCommand(
                1001L,
                "继续写作",
                new AgentTurnCommand.TaskRequest("WRITE", 3001L, null, null)
        );

        AgentTurnResult result = agentTurnAppService.createTurn(920001L, 920002L, command, "trace-turn-2");

        assertThat(result.activeTask().taskId()).isEqualTo(940001L);
        assertThat(result.userMessage()).isEqualTo("继续写作");
        assertThat(result.taskType()).isEqualTo("WRITE");
    }

    @Test
    void should_return_request_context_id_from_created_task_context() {
        AgentTurnAppService agentTurnAppService = new AgentTurnAppService(
                bindingAppServiceWithBoundStyle(null),
                agentRepository(),
                sessionRepository(),
                businessIdGenerator(930004L, 940004L, 950004L)
        ) {
            @Override
            protected AgentMessage createUserMessage(Long projectId, Long sessionId, AgentTurnCommand command, String traceId) {
                AgentMessage message = new AgentMessage();
                message.setMessageId(930002L);
                message.setConversationId(sessionId);
                message.setContentMd(command.userMessage());
                message.setSeqNo(1);
                return message;
            }

            @Override
            protected AgentGenerationTask createGenerationTask(Long projectId,
                                                               Long sessionId,
                                                               AgentTurnCommand command,
                                                               AgentMessage userMessage,
                                                               String traceId) {
                AgentGenerationTask task = new AgentGenerationTask();
                task.setTaskId(940002L);
                task.setProjectId(projectId);
                task.setConversationId(sessionId);
                task.setTaskType(command.taskRequest().taskType());
                task.setStatus("pending");
                return task;
            }

            @Override
            protected AgentTaskContext createTaskContext(Long projectId,
                                                         Long sessionId,
                                                         AgentTurnCommand command,
                                                         AgentMessage userMessage,
                                                         AgentGenerationTask task,
                                                         String traceId) {
                AgentTaskContext context = new AgentTaskContext();
                setField(context, "contextId", 950002L);
                setField(context, "taskId", task.getTaskId());
                setField(context, "taskStatus", "pending");
                return context;
            }
        };

        AgentTurnCommand command = new AgentTurnCommand(
                1001L,
                "将上下文一起写入",
                new AgentTurnCommand.TaskRequest("WRITE", 3002L, null, "片段选择")
        );

        AgentTurnResult result = agentTurnAppService.createTurn(920001L, 920002L, command, "trace-turn-3");

        assertThat(result.activeTask().taskId()).isEqualTo(940002L);
        assertThat(result.activeTask().requestContextId()).isEqualTo(950002L);
    }

    @Test
    void should_generate_message_task_and_context_ids_via_business_id_generator() {
        AgentTurnAppService agentTurnAppService = new AgentTurnAppService(
                bindingAppServiceWithBoundStyle(null),
                agentRepository(),
                sessionRepository(),
                businessIdGenerator(930101L, 940101L, 950101L)
        );

        AgentTurnCommand command = new AgentTurnCommand(
                1001L,
                "统一业务 ID",
                new AgentTurnCommand.TaskRequest("WRITE", 3002L, null, "片段选择")
        );

        AgentTurnResult result = agentTurnAppService.createTurn(920001L, 920002L, command, "trace-turn-id-1");

        assertThat(result.activeTask().taskId()).isEqualTo(940101L);
        assertThat(result.activeTask().requestContextId()).isEqualTo(950101L);
    }

    private static SessionStyleBindingAppService bindingAppServiceWithBoundStyle(Long boundStyleId) {
        AgentSessionRepository repository = mock(AgentSessionRepository.class);
        AgentSession session = AgentSession.active(920002L, 920001L, 1001L, "Session-A");
        if (boundStyleId != null) {
            session.bindStyle(boundStyleId);
        }
        when(repository.findSession(920001L, 920002L)).thenReturn(session);
        when(repository.findSession(920001L, 920001L)).thenReturn(session);
        when(repository.nextTurnSeq(920002L)).thenReturn(1);
        when(repository.insertTurn(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.isNull())).thenReturn(1);
        when(repository.insertSessionMessage(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyInt())).thenReturn(1);
        when(repository.insertRuntimeTask(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.nullable(String.class))).thenReturn(1);
        when(repository.insertTaskContext(any())).thenReturn(1);
        when(repository.updateLastTurn(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyLong())).thenReturn(1);
        when(repository.updateLastRunningTask(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyLong())).thenReturn(1);
        return new SessionStyleBindingAppService(repository);
    }

    private static AgentRepository agentRepository() {
        AgentRepository repository = mock(AgentRepository.class);
        when(repository.nextMessageSeq(920002L)).thenReturn(1);
        when(repository.insertMessage(any())).thenReturn(1);
        when(repository.touchConversationLastMessage(920002L)).thenReturn(1);
        return repository;
    }

    private static AgentSessionRepository sessionRepository() {
        AgentSessionRepository repository = mock(AgentSessionRepository.class);
        when(repository.nextTurnSeq(920002L)).thenReturn(1);
        when(repository.insertTurn(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.isNull())).thenReturn(1);
        when(repository.insertSessionMessage(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyInt())).thenReturn(1);
        when(repository.insertRuntimeTask(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.nullable(String.class))).thenReturn(1);
        when(repository.updateRuntimeTaskTurnLink(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyLong())).thenReturn(1);
        when(repository.insertTaskContext(any())).thenReturn(1);
        when(repository.updateLastTurn(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyLong())).thenReturn(1);
        when(repository.updateLastRunningTask(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyLong())).thenReturn(1);
        return repository;
    }

    private static void setField(Object target, String fieldName, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("failed to set field: " + fieldName, ex);
        }
    }
}

package com.penmate.backend.application.agent.usecase;

import com.penmate.backend.application.style.usecase.SessionStyleBindingAppService;
import com.penmate.backend.domain.agent.model.AgentGenerationTask;
import com.penmate.backend.domain.agent.model.AgentMessage;
import com.penmate.backend.domain.agent.model.AgentTaskContext;
import com.penmate.backend.domain.agent.model.AgentTaskStatus;
import com.penmate.backend.domain.agent.repository.AgentRepository;
import com.penmate.backend.domain.agent.repository.AgentSessionRepository;
import com.penmate.backend.domain.shared.service.BusinessIdGenerator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Agent turn 创建用例应用服务。
 * <p>turn context 必须携带会话显式绑定风格，不再依赖旧 generation payload 兼容字段。</p>
 */
@Service
@Slf4j
public class AgentTurnAppService {

    private final SessionStyleBindingAppService sessionStyleBindingAppService;
    private final AgentRepository agentRepository;
    private final AgentSessionRepository agentSessionRepository;
    private final BusinessIdGenerator businessIdGenerator;

    public AgentTurnAppService(SessionStyleBindingAppService sessionStyleBindingAppService,
                               AgentRepository agentRepository,
                               AgentSessionRepository agentSessionRepository,
                               BusinessIdGenerator businessIdGenerator) {
        this.sessionStyleBindingAppService = sessionStyleBindingAppService;
        this.agentRepository = agentRepository;
        this.agentSessionRepository = agentSessionRepository;
        this.businessIdGenerator = businessIdGenerator;
    }

    @Transactional
    public AgentTurnResult createTurn(Long projectId,
                                      Long sessionId,
                                      AgentTurnCommand command,
                                      String traceId) {
        log.info("Agent turn creation started: projectId={}, sessionId={}, operatorId={}, traceId={}, taskType={}, chapterId={}, selectedTextLength={}, userMessageLength={}",
                projectId,
                sessionId,
                command == null ? null : command.operatorId(),
                traceId,
                command == null || command.taskRequest() == null ? null : command.taskRequest().taskType(),
                command == null || command.taskRequest() == null ? null : command.taskRequest().chapterId(),
                command == null || command.taskRequest() == null || command.taskRequest().selectedText() == null ? 0 : command.taskRequest().selectedText().length(),
                command == null || command.userMessage() == null ? 0 : command.userMessage().length());

        AgentMessage userMessage = createUserMessage(projectId, sessionId, command, traceId);
        log.debug("Agent turn user message prepared: projectId={}, sessionId={}, traceId={}, messageId={}, seqNo={}",
                projectId,
                sessionId,
                traceId,
                userMessage == null ? null : userMessage.getMessageId(),
                userMessage == null ? null : userMessage.getSeqNo());
        persistUserMessage(sessionId, userMessage);
        log.info("Agent turn user message persisted: projectId={}, sessionId={}, traceId={}, messageId={}",
                projectId,
                sessionId,
                traceId,
                userMessage == null ? null : userMessage.getMessageId());

        AgentGenerationTask task = createGenerationTask(projectId, sessionId, command, userMessage, traceId);
        Long turnId = businessIdGenerator.nextId();
        int turnSeq = agentSessionRepository.nextTurnSeq(sessionId);
        log.debug("Agent turn runtime task prepared: projectId={}, sessionId={}, traceId={}, taskId={}, turnId={}, turnSeq={}, taskStatus={}",
                projectId,
                sessionId,
                traceId,
                task == null ? null : task.getTaskId(),
                turnId,
                turnSeq,
                task == null ? null : task.getStatus());
        persistTurn(sessionId, userMessage, task, turnId, turnSeq);
        log.info("Agent turn persisted: projectId={}, sessionId={}, traceId={}, turnId={}, turnSeq={}, taskId={}",
                projectId,
                sessionId,
                traceId,
                turnId,
                turnSeq,
                task == null ? null : task.getTaskId());

        AgentTaskContext taskContext = createTaskContext(projectId, sessionId, command, userMessage, task, traceId);
        log.debug("Agent task context prepared: projectId={}, sessionId={}, traceId={}, taskId={}, contextId={}, styleSnapshotPresent={}",
                projectId,
                sessionId,
                traceId,
                task == null ? null : task.getTaskId(),
                taskContext == null ? null : taskContext.getContextId(),
                taskContext != null && taskContext.getStyleSnapshotJson() != null && !taskContext.getStyleSnapshotJson().isBlank());
        persistTaskContext(taskContext);
        log.info("Agent task context persisted: projectId={}, sessionId={}, traceId={}, taskId={}, contextId={}",
                projectId,
                sessionId,
                traceId,
                task == null ? null : task.getTaskId(),
                taskContext == null ? null : taskContext.getContextId());
        persistRuntimeTask(projectId, sessionId, turnId, task, taskContext);
        log.info("Agent runtime task persisted: projectId={}, sessionId={}, traceId={}, taskId={}, turnId={}, contextId={}, taskStatus={}",
                projectId,
                sessionId,
                traceId,
                task == null ? null : task.getTaskId(),
                turnId,
                taskContext == null ? null : taskContext.getContextId(),
                task == null ? null : task.getStatus());

        int updatedTurn = agentSessionRepository.updateLastTurn(projectId, sessionId, turnId);
        if (updatedTurn != 1) {
            throw new IllegalStateException("failed to update session last turn");
        }
        log.debug("Agent session last turn updated: projectId={}, sessionId={}, traceId={}, turnId={}, affected={}",
                projectId,
                sessionId,
                traceId,
                turnId,
                updatedTurn);
        int updatedTask = agentSessionRepository.updateLastRunningTask(projectId, sessionId, task.getTaskId());
        if (updatedTask != 1) {
            throw new IllegalStateException("failed to update session last task");
        }
        log.debug("Agent session last running task updated: projectId={}, sessionId={}, traceId={}, taskId={}, affected={}",
                projectId,
                sessionId,
                traceId,
                task.getTaskId(),
                updatedTask);

        Long boundStyleId = sessionStyleBindingAppService.getBoundStyleId(projectId, sessionId);
        log.info("Agent turn created: projectId={}, sessionId={}, taskId={}, turnId={}, contextId={}, taskType={}, chapterId={}, selectedTextLength={}, boundStyleId={}, traceId={}",
                projectId,
                sessionId,
                task.getTaskId(),
                turnId,
                taskContext == null ? null : taskContext.getContextId(),
                command == null || command.taskRequest() == null ? null : command.taskRequest().taskType(),
                command == null || command.taskRequest() == null ? null : command.taskRequest().chapterId(),
                command == null || command.taskRequest() == null || command.taskRequest().selectedText() == null ? 0 : command.taskRequest().selectedText().length(),
                boundStyleId,
                traceId);
        AgentTurnResult.BoundStyleView boundStyle = boundStyleId == null
                ? null
                : new AgentTurnResult.BoundStyleView(boundStyleId, null);
        return new AgentTurnResult(
                new AgentTurnResult.SessionView(sessionId, "Session-" + sessionId, "ACTIVE", boundStyle, AgentTaskStatus.PENDING.value()),
                new AgentTurnResult.ActiveTaskView(turnId, task.getTaskId(), AgentTaskStatus.PENDING.value(), taskContext == null ? null : taskContext.getContextId()),
                task.getTaskType(),
                userMessage.getContentMd()
        );
    }

    protected AgentMessage createUserMessage(Long projectId,
                                             Long sessionId,
                                             AgentTurnCommand command,
                                             String traceId) {
        AgentMessage message = new AgentMessage();
        message.setMessageId(businessIdGenerator.nextId());
        message.setConversationId(sessionId);
        message.setRole("user");
        message.setUserMessageType("CHAT");
        message.setContentMd(command.userMessage());
        message.setSeqNo(agentRepository.nextMessageSeq(sessionId));
        return message;
    }

    protected AgentGenerationTask createGenerationTask(Long projectId,
                                                       Long sessionId,
                                                       AgentTurnCommand command,
                                                       AgentMessage userMessage,
                                                       String traceId) {
        AgentGenerationTask task = new AgentGenerationTask();
        task.setTaskId(businessIdGenerator.nextId());
        task.setProjectId(projectId);
        task.setConversationId(sessionId);
        task.setChapterId(command == null || command.taskRequest() == null ? null : command.taskRequest().chapterId());
        task.setModelConfigId(command == null || command.taskRequest() == null ? null : command.taskRequest().modelConfigId());
        task.setTaskType(command.taskRequest().taskType());
        task.setPromptSnapshot(userMessage == null ? command.userMessage() : userMessage.getContentMd());
        task.setTraceId(traceId);
        task.setStatus(AgentTaskStatus.PENDING.value());
        return task;
    }

    protected AgentTaskContext createTaskContext(Long projectId,
                                                 Long sessionId,
                                                 AgentTurnCommand command,
                                                 AgentMessage userMessage,
                                                 AgentGenerationTask task,
                                                 String traceId) {
        Long contextId = task == null || task.getTaskId() == null ? null : businessIdGenerator.nextId();
        AgentTurnCommand.TaskRequest taskRequest = command.taskRequest();
        AgentTaskContext context = AgentTaskContext.runningOf(
                contextId,
                task == null ? null : task.getTaskId(),
                AgentTaskStatus.PENDING.value(),
                taskRequest == null ? null : taskRequest.chapterId(),
                taskRequest == null ? null : taskRequest.selectedText()
        );
        context.setStyleSnapshotJson(sessionStyleBindingAppService.getBoundStyleSnapshotJson(projectId, sessionId));
        Long operatorId = command == null ? null : command.operatorId();
        Long modelConfigId = taskRequest == null ? null : taskRequest.modelConfigId();
        if (operatorId != null || modelConfigId != null) {
            setField(context, "modelSnapshotJson", "{\"operatorId\":"
                    + (operatorId == null ? "null" : operatorId)
                    + ",\"modelConfigId\":"
                    + (modelConfigId == null ? "null" : modelConfigId)
                    + "}");
        }
        setField(context, "contextHash", contextHash(command, task, context));
        return context;
    }

    private void persistUserMessage(Long sessionId, AgentMessage userMessage) {
        int affected = agentRepository.insertMessage(userMessage);
        if (affected != 1) {
            throw new IllegalStateException("failed to insert user message");
        }
        agentRepository.touchConversationLastMessage(sessionId);
    }

    private void persistTurn(Long sessionId,
                             AgentMessage userMessage,
                             AgentGenerationTask task,
                             Long turnId,
                             int turnSeq) {
        int affected = agentSessionRepository.insertTurn(
                sessionId,
                turnId,
                turnSeq,
                userMessage == null ? null : userMessage.getMessageId(),
                task == null ? null : task.getTaskId(),
                AgentTaskStatus.PENDING.value(),
                null
        );
        if (affected != 1) {
            throw new IllegalStateException("failed to insert turn");
        }
    }

    private void persistTaskContext(AgentTaskContext taskContext) {
        int affected = agentSessionRepository.insertTaskContext(taskContext);
        if (affected != 1) {
            throw new IllegalStateException("failed to insert task context");
        }
    }

    private void persistRuntimeTask(Long projectId,
                                    Long sessionId,
                                    Long turnId,
                                    AgentGenerationTask task,
                                    AgentTaskContext taskContext) {
        int affected = agentSessionRepository.insertRuntimeTask(
                task.getTaskId(),
                sessionId,
                turnId,
                projectId,
                task.getTaskType(),
                AgentTaskStatus.PENDING.value(),
                task.getPromptSnapshot(),
                taskContext == null ? null : taskContext.getContextId(),
                task.getTraceId()
        );
        if (affected != 1) {
            throw new IllegalStateException("failed to insert runtime task");
        }
    }

    private String contextHash(AgentTurnCommand command,
                               AgentGenerationTask task,
                               AgentTaskContext context) {
        String seed = String.join("|",
                command == null || command.userMessage() == null ? "" : command.userMessage(),
                task == null || task.getTaskId() == null ? "" : String.valueOf(task.getTaskId()),
                context == null || context.getChapterId() == null ? "" : String.valueOf(context.getChapterId()),
                context == null || context.getSelectedText() == null ? "" : context.getSelectedText(),
                context == null || context.getStyleSnapshotJson() == null ? "" : context.getStyleSnapshotJson()
        );
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(seed.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte value : bytes) {
                builder.append(String.format("%02x", value));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("failed to hash task context", ex);
        }
    }

    private void setField(Object target, String fieldName, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("failed to set field: " + fieldName, ex);
        }
    }
}

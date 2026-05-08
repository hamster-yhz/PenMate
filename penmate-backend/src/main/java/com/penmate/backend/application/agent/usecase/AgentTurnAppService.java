package com.penmate.backend.application.agent.usecase;

import com.penmate.backend.application.style.usecase.SessionStyleBindingAppService;
import com.penmate.backend.domain.agent.model.AgentGenerationTask;
import com.penmate.backend.domain.agent.model.AgentMessage;
import com.penmate.backend.domain.agent.model.AgentTaskContext;
import com.penmate.backend.domain.shared.service.BusinessIdGenerator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Agent turn 创建用例应用服务。
 * <p>turn context 必须携带会话显式绑定风格，不再依赖旧 generation payload 兼容字段。</p>
 */
@Service
@Slf4j
public class AgentTurnAppService {

    private final SessionStyleBindingAppService sessionStyleBindingAppService;
    private final BusinessIdGenerator businessIdGenerator;

    public AgentTurnAppService(SessionStyleBindingAppService sessionStyleBindingAppService,
                               BusinessIdGenerator businessIdGenerator) {
        this.sessionStyleBindingAppService = sessionStyleBindingAppService;
        this.businessIdGenerator = businessIdGenerator;
    }

    public AgentTurnResult createTurn(Long projectId,
                                      Long sessionId,
                                      AgentTurnCommand command,
                                      String traceId) {
        AgentMessage userMessage = createUserMessage(projectId, sessionId, command, traceId);
        AgentGenerationTask task = createGenerationTask(projectId, sessionId, command, userMessage, traceId);
        AgentTaskContext taskContext = createTaskContext(projectId, sessionId, command, userMessage, task, traceId);
        Long taskId = task.getTaskId();
        Long boundStyleId = sessionStyleBindingAppService.getBoundStyleId(projectId, sessionId);
        log.info("Agent turn created: projectId={}, sessionId={}, taskId={}, contextId={}, taskType={}, chapterId={}, selectedTextLength={}, boundStyleId={}, traceId={}",
                projectId,
                sessionId,
                taskId,
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
                new AgentTurnResult.SessionView(sessionId, "Session-" + sessionId, "ACTIVE", boundStyle, "RUNNING"),
                new AgentTurnResult.ActiveTaskView(taskId, "RUNNING", taskContext == null ? null : taskContext.getContextId()),
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
        message.setContentMd(command.userMessage());
        return message;
    }

    protected AgentGenerationTask createGenerationTask(Long projectId,
                                                       Long sessionId,
                                                       AgentTurnCommand command,
                                                       AgentMessage userMessage,
                                                       String traceId) {
        AgentGenerationTask task = new AgentGenerationTask();
        task.setTaskId(businessIdGenerator.nextId());
        task.setConversationId(sessionId);
        task.setTaskType(command.taskRequest().taskType());
        task.setPromptSnapshot(userMessage == null ? command.userMessage() : userMessage.getContentMd());
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
                "RUNNING",
                taskRequest == null ? null : taskRequest.chapterId(),
                taskRequest == null ? null : taskRequest.selectedText()
        );
        context.setStyleSnapshotJson(sessionStyleBindingAppService.getBoundStyleSnapshotJson(projectId, sessionId));
        return context;
    }
}

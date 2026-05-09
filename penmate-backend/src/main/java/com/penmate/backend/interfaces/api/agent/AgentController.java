package com.penmate.backend.interfaces.api.agent;

import com.penmate.backend.application.agent.command.AgentCommands.CreateConversationCommand;
import com.penmate.backend.application.agent.usecase.AgentConversationAppService;
import com.penmate.backend.application.agent.usecase.AgentSessionRecoveryAppService;
import com.penmate.backend.application.agent.usecase.AgentSessionRecoveryResult;
import com.penmate.backend.application.agent.usecase.AgentTurnAppService;
import com.penmate.backend.application.agent.usecase.AgentTurnCommand;
import com.penmate.backend.application.agent.usecase.AgentTurnResult;
import com.penmate.backend.domain.agent.model.AgentConversation;
import com.penmate.backend.domain.agent.model.AgentTaskContext;
import com.penmate.backend.domain.agent.repository.AgentSessionRepository;
import com.penmate.backend.domain.shared.service.GenerationStreamService;
import com.penmate.backend.interfaces.api.agent.dto.AgentRecoverySnapshotDto;
import com.penmate.backend.interfaces.api.agent.dto.AgentTaskDto;
import com.penmate.backend.interfaces.api.agent.dto.AgentSessionDto;
import com.penmate.backend.interfaces.api.agent.dto.CreateAgentConversationDto;
import com.penmate.backend.interfaces.api.agent.dto.CreateAgentTurnDto;
import com.penmate.backend.interfaces.api.agent.dto.ResumeAgentSessionDto;
import com.penmate.backend.interfaces.api.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Objects;

@RestController
@RequestMapping("/api/v1/novels/{projectId}/agent")
@Slf4j
public class AgentController {

    private final AgentConversationAppService agentConversationAppService;
    private final AgentSessionRecoveryAppService agentSessionRecoveryAppService;
    private final AgentTurnAppService agentTurnAppService;
    private final AgentSessionRepository agentSessionRepository;
    private final GenerationStreamService generationStreamService;

    public AgentController(AgentConversationAppService agentConversationAppService,
                           AgentSessionRecoveryAppService agentSessionRecoveryAppService,
                           AgentTurnAppService agentTurnAppService,
                           AgentSessionRepository agentSessionRepository,
                           GenerationStreamService generationStreamService) {
        this.agentConversationAppService = agentConversationAppService;
        this.agentSessionRecoveryAppService = agentSessionRecoveryAppService;
        this.agentTurnAppService = agentTurnAppService;
        this.agentSessionRepository = agentSessionRepository;
        this.generationStreamService = generationStreamService;
    }

    /**
     * 查询当前项目下的会话列表�?
     */
    @GetMapping("/sessions")
    public ApiResponse<List<AgentSessionDto>> listSessions(@PathVariable String projectId,
                                                           @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return ApiResponse.success(
                agentConversationAppService.listConversations(requireLongId(projectId, "projectId"))
                        .stream()
                        .map(this::toSessionDto)
                        .toList(),
                traceId
        );
    }

    /**
     * 创建一个新会话�?
     */
    @PostMapping("/sessions")
    public ApiResponse<AgentSessionDto> createSession(@PathVariable String projectId,
                                                      @Valid @RequestBody CreateAgentConversationDto dto,
                                                      @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        log.info("Create agent session request: projectId={}, userId={}, title={}, traceId={}",
                projectId, dto.getUserId(), dto.getTitle(), traceId);
        return ApiResponse.success(
                toSessionDto(agentConversationAppService.createConversation(requireLongId(projectId, "projectId"), toCommand(dto), traceId)),
                traceId
        );
    }

    /**
     * 查询会话恢复快照�?
     * <p>控制器仅负责 HTTP 参数绑定�?traceId 透传，具体恢复查询下沉到应用服务�?/p>
     */
    @GetMapping("/sessions/{sessionId}/recovery")
    public ApiResponse<AgentRecoverySnapshotDto> getRecovery(@PathVariable String projectId,
                                                             @PathVariable String sessionId,
                                                             @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return ApiResponse.success(toRecoveryDto(agentSessionRecoveryAppService.getRecovery(
                requireLongId(projectId, "projectId"),
                requireLongId(sessionId, "sessionId"),
                traceId)), traceId);
    }

    /**
     * 恢复一个会话并返回最新恢复快照�?
     * <p>控制器不拼装恢复快照，只�?DTO 到用例入参的转换�?/p>
     */
    @PostMapping("/sessions/{sessionId}/resume")
    public ApiResponse<AgentRecoverySnapshotDto> resume(@PathVariable String projectId,
                                                        @PathVariable String sessionId,
                                                        @Valid @RequestBody ResumeAgentSessionDto dto,
                                                        @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        log.info("Resume agent session request: projectId={}, sessionId={}, operatorId={}, trigger={}, traceId={}",
                projectId, sessionId, dto.getOperatorId(), dto.getTrigger(), traceId);
        return ApiResponse.success(
                toRecoveryDto(agentSessionRecoveryAppService.resumeSession(
                        requireLongId(projectId, "projectId"),
                        requireLongId(sessionId, "sessionId"),
                        requireLongId(dto.getOperatorId(), "operatorId"),
                        dto.getTrigger(),
                        traceId)),
                traceId
        );
    }

    /**
     * 创建新的 agent turn，并返回当前运行中的任务视图�?
     * <p>该接口是新的 workflow entry，不再暴露历�?createMessage/createGeneration 双接口�?/p>
     */
    @PostMapping("/sessions/{sessionId}/turns")
    public ApiResponse<AgentTaskDto> createTurn(@PathVariable String projectId,
                                                @PathVariable String sessionId,
                                                @Valid @RequestBody CreateAgentTurnDto dto,
                                                @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        log.info("Create agent turn request: projectId={}, sessionId={}, operatorId={}, taskType={}, traceId={}",
                projectId, sessionId, dto.getOperatorId(), dto.getTaskRequest().getTaskType(), traceId);
        AgentTurnResult result = agentTurnAppService.createTurn(
                requireLongId(projectId, "projectId"),
                requireLongId(sessionId, "sessionId"),
                toCommand(dto),
                traceId);
        return ApiResponse.success(toTaskDto(result), traceId);
    }

    @GetMapping(path = "/sessions/{sessionId}/turns/{turnId}/stream", produces = "text/event-stream")
    public SseEmitter openTurnStream(@PathVariable String projectId,
                                     @PathVariable String sessionId,
                                     @PathVariable String turnId,
                                     @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        Long resolvedProjectId = requireLongId(projectId, "projectId");
        Long resolvedSessionId = requireLongId(sessionId, "sessionId");
        Long resolvedTurnId = requireLongId(turnId, "turnId");
        AgentTaskContext task = agentSessionRepository.findTaskByTurnId(resolvedProjectId, resolvedSessionId, resolvedTurnId);
        if (task == null || task.getTaskId() == null) {
            throw new IllegalArgumentException("turnId does not resolve to an active task stream");
        }
        log.info("Open agent turn stream request: projectId={}, sessionId={}, turnId={}, taskId={}, traceId={}",
                resolvedProjectId, resolvedSessionId, resolvedTurnId, task.getTaskId(), traceId);
        return generationStreamService.openStream(task.getTaskId());
    }

    private CreateConversationCommand toCommand(CreateAgentConversationDto dto) {
        Long userId = requireLongId(dto.getUserId(), "userId");
        return new CreateConversationCommand(
                userId,
                dto.getTitle(),
                dto.getStatus(),
                userId
        );
    }

    private AgentTurnCommand toCommand(CreateAgentTurnDto dto) {
        CreateAgentTurnDto.TaskRequest request = dto.getTaskRequest();
        return new AgentTurnCommand(
                requireLongId(dto.getOperatorId(), "operatorId"),
                dto.getUserMessage(),
                request == null
                        ? null
                        : new AgentTurnCommand.TaskRequest(
                                request.getTaskType(),
                                optionalLongId(request.getChapterId(), "chapterId"),
                                request.getSelectedText())
        );
    }

    private Long requireLongId(String rawValue, String fieldName) {
        String normalized = Objects.requireNonNull(rawValue, fieldName + " must not be null").trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        if (!normalized.matches("^\\d+$")) {
            throw new IllegalArgumentException(fieldName + " must be a numeric string business id");
        }
        try {
            return Long.valueOf(normalized);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(fieldName + " must be a valid numeric string business id", ex);
        }
    }

    private Long optionalLongId(String rawValue, String fieldName) {
        if (rawValue == null || rawValue.isBlank()) {
            return null;
        }
        return requireLongId(rawValue, fieldName);
    }

    private String stringifyBusinessId(Long value) {
        return value == null ? null : String.valueOf(value);
    }

    private AgentRecoverySnapshotDto toRecoveryDto(AgentSessionRecoveryResult result) {
        AgentSessionRecoveryResult.SessionView session = result == null ? null : result.session();
        AgentSessionRecoveryResult.BoundStyleView boundStyle = session == null ? null : session.boundStyle();
        AgentSessionRecoveryResult.ActiveTaskView activeTask = result == null ? null : result.activeTask();
        return new AgentRecoverySnapshotDto(
                new AgentRecoverySnapshotDto.SessionDto(
                        session == null ? null : stringifyBusinessId(session.sessionId()),
                        session == null ? null : session.title(),
                        session == null ? null : session.status(),
                        boundStyle == null ? null : new AgentRecoverySnapshotDto.BoundStyleDto(stringifyBusinessId(boundStyle.styleId()), boundStyle.name()),
                        session == null ? null : session.taskStatus()
                ),
                activeTask == null ? null : new AgentRecoverySnapshotDto.ActiveTaskDto(
                        stringifyBusinessId(activeTask.turnId()),
                        stringifyBusinessId(activeTask.taskId()),
                        activeTask.taskStatus(),
                        stringifyBusinessId(activeTask.requestContextId())
                ),
                result == null ? null : result.pendingApproval(),
                result == null ? java.util.List.of() : result.messages(),
                result == null ? null : result.workbenchContext()
        );
    }

    private AgentSessionDto toSessionDto(AgentConversation conversation) {
        return new AgentSessionDto(
                conversation == null ? null : stringifyBusinessId(conversation.getConversationId()),
                conversation == null ? null : conversation.getTitle(),
                conversation == null ? null : conversation.getStatus()
        );
    }

    private AgentTaskDto toTaskDto(AgentTurnResult result) {
        AgentTurnResult.SessionView session = result.session();
        AgentTurnResult.BoundStyleView boundStyle = session == null ? null : session.boundStyle();
        AgentTurnResult.ActiveTaskView activeTask = result.activeTask();
        return new AgentTaskDto(
                new AgentRecoverySnapshotDto.SessionDto(
                        session == null ? null : stringifyBusinessId(session.sessionId()),
                        session == null ? null : session.title(),
                        session == null ? null : session.status(),
                        boundStyle == null ? null : new AgentRecoverySnapshotDto.BoundStyleDto(stringifyBusinessId(boundStyle.styleId()), boundStyle.name()),
                        session == null ? null : session.taskStatus()
                ),
                new AgentRecoverySnapshotDto.ActiveTaskDto(
                        activeTask == null ? null : stringifyBusinessId(activeTask.turnId()),
                        activeTask == null ? null : stringifyBusinessId(activeTask.taskId()),
                        activeTask == null ? null : activeTask.taskStatus(),
                        activeTask == null ? null : stringifyBusinessId(activeTask.requestContextId())
                ),
                result.taskType(),
                result.userMessage()
        );
    }
}



package com.penmate.backend.interfaces.api.agent;

import com.penmate.backend.application.agent.command.AgentCommands.CreateConversationCommand;
import com.penmate.backend.application.agent.usecase.AgentConversationAppService;
import com.penmate.backend.application.agent.usecase.AgentSessionRecoveryAppService;
import com.penmate.backend.application.agent.usecase.AgentSessionRecoveryResult;
import com.penmate.backend.application.agent.usecase.AgentTurnAppService;
import com.penmate.backend.application.agent.usecase.AgentTurnCommand;
import com.penmate.backend.application.agent.usecase.AgentTurnResult;
import com.penmate.backend.domain.agent.model.AgentConversation;
import com.penmate.backend.interfaces.api.agent.dto.AgentRecoverySnapshotDto;
import com.penmate.backend.interfaces.api.agent.dto.AgentTaskDto;
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

import java.util.List;
import java.util.Objects;

@RestController
@RequestMapping("/api/v1/novels/{projectId}/agent")
@Slf4j
public class AgentController {

    private final AgentConversationAppService agentConversationAppService;
    private final AgentSessionRecoveryAppService agentSessionRecoveryAppService;
    private final AgentTurnAppService agentTurnAppService;

    public AgentController(AgentConversationAppService agentConversationAppService,
                           AgentSessionRecoveryAppService agentSessionRecoveryAppService,
                           AgentTurnAppService agentTurnAppService) {
        this.agentConversationAppService = agentConversationAppService;
        this.agentSessionRecoveryAppService = agentSessionRecoveryAppService;
        this.agentTurnAppService = agentTurnAppService;
    }

    /**
     * 查询当前项目下的会话列表。
     */
    @GetMapping("/sessions")
    public ApiResponse<List<AgentConversation>> listSessions(@PathVariable String projectId,
                                                             @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return ApiResponse.success(agentConversationAppService.listConversations(parseBusinessId(projectId, "projectId")), traceId);
    }

    /**
     * 创建一个新会话。
     */
    @PostMapping("/sessions")
    public ApiResponse<AgentConversation> createSession(@PathVariable String projectId,
                                                         @Valid @RequestBody CreateAgentConversationDto dto,
                                                         @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        log.info("Create agent session request: projectId={}, userId={}, title={}, traceId={}",
                projectId, dto.getUserId(), dto.getTitle(), traceId);
        return ApiResponse.success(
                agentConversationAppService.createConversation(parseBusinessId(projectId, "projectId"), toCommand(dto), traceId),
                traceId
        );
    }

    /**
     * 查询会话恢复快照。
     * <p>控制器仅负责 HTTP 参数绑定与 traceId 透传，具体恢复查询下沉到应用服务。</p>
     */
    @GetMapping("/sessions/{sessionId}/recovery")
    public ApiResponse<AgentRecoverySnapshotDto> getRecovery(@PathVariable String projectId,
                                                             @PathVariable String sessionId,
                                                             @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return ApiResponse.success(toRecoveryDto(agentSessionRecoveryAppService.getRecovery(
                parseBusinessId(projectId, "projectId"),
                parseBusinessId(sessionId, "sessionId"),
                traceId)), traceId);
    }

    /**
     * 恢复一个会话并返回最新恢复快照。
     * <p>控制器不拼装恢复快照，只做 DTO 到用例入参的转换。</p>
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
                        parseBusinessId(projectId, "projectId"),
                        parseBusinessId(sessionId, "sessionId"),
                        parseBusinessId(dto.getOperatorId(), "operatorId"),
                        dto.getTrigger(),
                        traceId)),
                traceId
        );
    }

    /**
     * 创建新的 agent turn，并返回当前运行中的任务视图。
     * <p>该接口是新的 workflow entry，不再暴露历史 createMessage/createGeneration 双接口。</p>
     */
    @PostMapping("/sessions/{sessionId}/turns")
    public ApiResponse<AgentTaskDto> createTurn(@PathVariable String projectId,
                                                @PathVariable String sessionId,
                                                @Valid @RequestBody CreateAgentTurnDto dto,
                                                @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        log.info("Create agent turn request: projectId={}, sessionId={}, operatorId={}, taskType={}, traceId={}",
                projectId, sessionId, dto.getOperatorId(), dto.getTaskRequest().getTaskType(), traceId);
        AgentTurnResult result = agentTurnAppService.createTurn(
                parseBusinessId(projectId, "projectId"),
                parseBusinessId(sessionId, "sessionId"),
                toCommand(dto),
                traceId);
        return ApiResponse.success(toTaskDto(result), traceId);
    }

    private CreateConversationCommand toCommand(CreateAgentConversationDto dto) {
        Long userId = parseBusinessId(dto.getUserId(), "userId");
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
                parseBusinessId(dto.getOperatorId(), "operatorId"),
                dto.getUserMessage(),
                request == null
                        ? null
                        : new AgentTurnCommand.TaskRequest(
                                request.getTaskType(),
                                parseOptionalBusinessId(request.getChapterId(), "chapterId"),
                                request.getSelectedText())
        );
    }

    private Long parseBusinessId(String rawValue, String fieldName) {
        try {
            return Long.valueOf(Objects.requireNonNull(rawValue, fieldName + " must not be null"));
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(fieldName + " must be a numeric business id", ex);
        }
    }

    private Long parseOptionalBusinessId(String rawValue, String fieldName) {
        if (rawValue == null || rawValue.isBlank()) {
            return null;
        }
        return parseBusinessId(rawValue, fieldName);
    }

    private AgentRecoverySnapshotDto toRecoveryDto(AgentSessionRecoveryResult result) {
        AgentSessionRecoveryResult.SessionView session = result == null ? null : result.session();
        AgentSessionRecoveryResult.BoundStyleView boundStyle = session == null ? null : session.boundStyle();
        AgentSessionRecoveryResult.ActiveTaskView activeTask = result == null ? null : result.activeTask();
        return new AgentRecoverySnapshotDto(
                new AgentRecoverySnapshotDto.SessionDto(
                        session == null ? null : session.sessionId(),
                        session == null ? null : session.title(),
                        session == null ? null : session.status(),
                        boundStyle == null ? null : new AgentRecoverySnapshotDto.BoundStyleDto(boundStyle.styleId(), boundStyle.name()),
                        session == null ? null : session.taskStatus()
                ),
                activeTask == null ? null : new AgentRecoverySnapshotDto.ActiveTaskDto(
                        activeTask.taskId(),
                        activeTask.taskStatus(),
                        activeTask.requestContextId()
                ),
                result == null ? null : result.pendingApproval(),
                result == null ? java.util.List.of() : result.messages(),
                result == null ? null : result.workbenchContext()
        );
    }

    private AgentTaskDto toTaskDto(AgentTurnResult result) {
        AgentTurnResult.SessionView session = result.session();
        AgentTurnResult.BoundStyleView boundStyle = session == null ? null : session.boundStyle();
        AgentTurnResult.ActiveTaskView activeTask = result.activeTask();
        return new AgentTaskDto(
                new AgentRecoverySnapshotDto.SessionDto(
                        session == null ? null : session.sessionId(),
                        session == null ? null : session.title(),
                        session == null ? null : session.status(),
                        boundStyle == null ? null : new AgentRecoverySnapshotDto.BoundStyleDto(boundStyle.styleId(), boundStyle.name()),
                        session == null ? null : session.taskStatus()
                ),
                new AgentRecoverySnapshotDto.ActiveTaskDto(
                        activeTask == null ? null : activeTask.taskId(),
                        activeTask == null ? null : activeTask.taskStatus(),
                        activeTask == null ? null : activeTask.requestContextId()
                ),
                result.taskType(),
                result.userMessage()
        );
    }
}

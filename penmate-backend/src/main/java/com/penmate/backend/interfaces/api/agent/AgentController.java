package com.penmate.backend.interfaces.api.agent;

import com.penmate.backend.application.agent.command.AgentCommands.CreateConversationCommand;
import com.penmate.backend.application.agent.context.StoryBibleRoutingPreferenceResolver;
import com.penmate.backend.application.agent.run.AgentRunCancellationService;
import com.penmate.backend.application.agent.run.AgentRunRecoveryAppService;
import com.penmate.backend.application.agent.run.AgentRunRecoveryResult;
import com.penmate.backend.application.agent.run.AgentRunRetryService;
import com.penmate.backend.application.agent.runtime.SessionTokenUsageView;
import com.penmate.backend.application.agent.usecase.AgentConversationAppService;
import com.penmate.backend.application.agent.usecase.AgentSessionTokenUsageAppService;
import com.penmate.backend.application.agent.usecase.AgentTurnAppService;
import com.penmate.backend.application.agent.usecase.AgentTurnCommand;
import com.penmate.backend.application.agent.usecase.AgentTurnResult;
import com.penmate.backend.domain.agent.model.AgentConversation;
import com.penmate.backend.infrastructure.realtime.AgentRunEventStreamService;
import com.penmate.backend.interfaces.api.agent.dto.AgentRecoverySnapshotDto;
import com.penmate.backend.interfaces.api.agent.dto.AgentRunDto;
import com.penmate.backend.interfaces.api.agent.dto.AgentSessionDto;
import com.penmate.backend.interfaces.api.agent.dto.CancelAgentRunDto;
import com.penmate.backend.interfaces.api.agent.dto.CreateAgentConversationDto;
import com.penmate.backend.interfaces.api.agent.dto.CreateAgentTurnDto;
import com.penmate.backend.interfaces.api.agent.dto.ResumeAgentSessionDto;
import com.penmate.backend.interfaces.api.agent.dto.RetryAgentRunDto;
import com.penmate.backend.interfaces.api.agent.dto.StoryBibleRoutingPreferenceDto;
import com.penmate.backend.interfaces.api.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Objects;

@RestController
@RequestMapping("/api/v1/novels/{projectId}/agent")
@Slf4j
public class AgentController {

    private final AgentConversationAppService agentConversationAppService;
    private final AgentRunRecoveryAppService agentRunRecoveryAppService;
    private final AgentSessionTokenUsageAppService agentSessionTokenUsageAppService;
    private final AgentTurnAppService agentTurnAppService;
    private final AgentRunEventStreamService agentRunEventStreamService;
    private final StoryBibleRoutingPreferenceResolver routingPreferences;
    private final AgentRunCancellationService runCancellationService;
    private final AgentRunRetryService runRetryService;

    public AgentController(AgentConversationAppService agentConversationAppService,
                           AgentRunRecoveryAppService agentRunRecoveryAppService,
                           AgentSessionTokenUsageAppService agentSessionTokenUsageAppService,
                           AgentTurnAppService agentTurnAppService,
                           AgentRunEventStreamService agentRunEventStreamService,
                           StoryBibleRoutingPreferenceResolver routingPreferences,
                           AgentRunCancellationService runCancellationService,
                           AgentRunRetryService runRetryService) {
        this.agentConversationAppService = agentConversationAppService;
        this.agentRunRecoveryAppService = agentRunRecoveryAppService;
        this.agentSessionTokenUsageAppService = agentSessionTokenUsageAppService;
        this.agentTurnAppService = agentTurnAppService;
        this.agentRunEventStreamService = agentRunEventStreamService;
        this.routingPreferences = routingPreferences;
        this.runCancellationService = runCancellationService;
        this.runRetryService = runRetryService;
    }

    @GetMapping("/routing-preference")
    public ApiResponse<StoryBibleRoutingPreferenceDto.View> getUserRoutingPreference(
            @PathVariable String projectId, @RequestParam String userId,
            @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        requireLongId(projectId, "projectId");
        var result = routingPreferences.getUserDefault(requireLongId(userId, "userId"));
        return ApiResponse.success(toRoutingView(result, false), traceId);
    }

    @PutMapping("/routing-preference")
    public ApiResponse<StoryBibleRoutingPreferenceDto.View> updateUserRoutingPreference(
            @PathVariable String projectId, @RequestParam String userId,
            @RequestBody StoryBibleRoutingPreferenceDto.Update dto,
            @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        requireLongId(projectId, "projectId");
        Long parsedUserId = requireLongId(userId, "userId");
        routingPreferences.saveUserDefault(parsedUserId, dto.mode(), optionalLongId(dto.routerModelConfigId(), "routerModelConfigId"));
        return ApiResponse.success(toRoutingView(routingPreferences.getUserDefault(parsedUserId), false), traceId);
    }

    @GetMapping("/sessions/{sessionId}/routing-preference")
    public ApiResponse<StoryBibleRoutingPreferenceDto.View> getSessionRoutingPreference(
            @PathVariable String projectId, @PathVariable String sessionId, @RequestParam String userId,
            @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        var result = routingPreferences.resolve(requireLongId(projectId, "projectId"),
                requireLongId(sessionId, "sessionId"), requireLongId(userId, "userId"));
        return ApiResponse.success(toRoutingView(result, !result.sessionOverride()), traceId);
    }

    @PutMapping("/sessions/{sessionId}/routing-preference")
    public ApiResponse<StoryBibleRoutingPreferenceDto.View> updateSessionRoutingPreference(
            @PathVariable String projectId, @PathVariable String sessionId, @RequestParam String userId,
            @RequestBody StoryBibleRoutingPreferenceDto.Update dto,
            @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        Long parsedProjectId = requireLongId(projectId, "projectId");
        Long parsedSessionId = requireLongId(sessionId, "sessionId");
        Long parsedUserId = requireLongId(userId, "userId");
        routingPreferences.saveSessionOverride(parsedProjectId, parsedSessionId, parsedUserId, dto.mode(),
                optionalLongId(dto.routerModelConfigId(), "routerModelConfigId"));
        var result = routingPreferences.resolve(parsedProjectId, parsedSessionId, parsedUserId);
        return ApiResponse.success(toRoutingView(result, !result.sessionOverride()), traceId);
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
        return ApiResponse.success(toRecoveryDto(agentRunRecoveryAppService.getRecovery(
                requireLongId(projectId, "projectId"),
                requireLongId(sessionId, "sessionId"),
                traceId)), traceId);
    }

    @GetMapping("/sessions/{sessionId}/token-usage")
    public ApiResponse<SessionTokenUsageView> getTokenUsage(@PathVariable String projectId,
                                                            @PathVariable String sessionId,
                                                            @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return ApiResponse.success(
                agentSessionTokenUsageAppService.getTokenUsage(
                        requireLongId(projectId, "projectId"),
                        requireLongId(sessionId, "sessionId"),
                        traceId
                ),
                traceId
        );
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
                toRecoveryDto(agentRunRecoveryAppService.resumeSession(
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
     * <p>This endpoint is the run workflow entry and does not expose legacy split request APIs.</p>
     */
    @PostMapping("/sessions/{sessionId}/turns")
    public ApiResponse<AgentRunDto> createTurn(@PathVariable String projectId,
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
        return ApiResponse.success(toRunDto(result, requireLongId(sessionId, "sessionId")), traceId);
    }

    @GetMapping(path = "/runs/{runId}/stream", produces = "text/event-stream")
    public SseEmitter openRunStream(@PathVariable String projectId,
                                    @PathVariable String runId,
                                    @RequestParam(value = "after", required = false) String after,
                                    @RequestHeader(value = "Last-Event-ID", required = false) String lastEventId,
                                    @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        Long resolvedProjectId = requireLongId(projectId, "projectId");
        Long resolvedRunId = requireLongId(runId, "runId");
        Long replayCursor = Math.max(optionalSequence(after), optionalSequence(lastEventId));
        log.info("Open agent run stream request: projectId={}, runId={}, after={}, lastEventId={}, replayCursor={}, traceId={}",
                resolvedProjectId, resolvedRunId, after, lastEventId, replayCursor, traceId);
        return agentRunEventStreamService.openStream(resolvedRunId, replayCursor);
    }

    @PostMapping("/runs/{runId}/cancel")
    public ApiResponse<AgentRunDto.ActiveRunDto> cancelRun(
            @PathVariable String projectId,
            @PathVariable String runId,
            @Valid @RequestBody CancelAgentRunDto dto,
            @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        var run = runCancellationService.cancel(
                requireLongId(projectId, "projectId"),
                requireLongId(runId, "runId"),
                requireLongId(dto.getOperatorId(), "operatorId"),
                dto.getReason());
        return ApiResponse.success(new AgentRunDto.ActiveRunDto(
                stringifyBusinessId(run.turnId()),
                stringifyBusinessId(run.runId()),
                run.runStatus(),
                run.runPhase(),
                stringifyBusinessId(run.latestEventSeq())), traceId);
    }

    @PostMapping("/runs/{runId}/retry")
    public ApiResponse<AgentRunDto.ActiveRunDto> retryRun(
            @PathVariable String projectId,
            @PathVariable String runId,
            @Valid @RequestBody RetryAgentRunDto dto,
            @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        var run = runRetryService.retry(
                requireLongId(projectId, "projectId"),
                requireLongId(runId, "runId"),
                requireLongId(dto.getOperatorId(), "operatorId"),
                traceId);
        return ApiResponse.success(new AgentRunDto.ActiveRunDto(
                stringifyBusinessId(run.turnId()),
                stringifyBusinessId(run.runId()),
                run.runStatus(),
                run.runPhase(),
                stringifyBusinessId(run.latestEventSeq())), traceId);
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
                                optionalLongId(request.getModelConfigId(), "modelConfigId"),
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

    private StoryBibleRoutingPreferenceDto.View toRoutingView(
            StoryBibleRoutingPreferenceResolver.EffectivePreference value, boolean inherited) {
        return new StoryBibleRoutingPreferenceDto.View(value.mode(), stringifyBusinessId(value.routerModelConfigId()),
                value.routerModelConfigRevision(), inherited);
    }

    private AgentRecoverySnapshotDto toRecoveryDto(AgentRunRecoveryResult result) {
        AgentRunRecoveryResult.SessionView session = result == null ? null : result.session();
        AgentRunRecoveryResult.BoundStyleView boundStyle = session == null ? null : session.boundStyle();
        AgentRunRecoveryResult.ActiveRunView activeRun = result == null ? null : result.activeRun();
        return new AgentRecoverySnapshotDto(
                new AgentRecoverySnapshotDto.SessionDto(
                        session == null ? null : stringifyBusinessId(session.sessionId()),
                        session == null ? null : session.title(),
                        session == null ? null : session.status(),
                        boundStyle == null ? null : new AgentRecoverySnapshotDto.BoundStyleDto(stringifyBusinessId(boundStyle.styleId()), boundStyle.name()),
                        session == null ? null : session.lastRunStatus()
                ),
                activeRun == null ? null : new AgentRecoverySnapshotDto.ActiveRunDto(
                        stringifyBusinessId(activeRun.turnId()),
                        stringifyBusinessId(activeRun.runId()),
                        activeRun.runStatus(),
                        activeRun.runPhase(),
                        stringifyBusinessId(activeRun.latestSequence())
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

    private AgentRunDto toRunDto(AgentTurnResult result, Long fallbackSessionId) {
        AgentTurnResult.ActiveRunView activeRun = result.activeRun();
        Long sessionId = result.sessionId() == null ? fallbackSessionId : result.sessionId();
        return new AgentRunDto(
                new AgentRecoverySnapshotDto.SessionDto(
                        stringifyBusinessId(sessionId),
                        null,
                        null,
                        null,
                        null
                ),
                activeRun == null ? null : new AgentRunDto.ActiveRunDto(
                        stringifyBusinessId(activeRun.turnId()),
                        stringifyBusinessId(activeRun.runId()),
                        activeRun.runStatus(),
                        activeRun.runPhase(),
                        stringifyBusinessId(activeRun.latestSequence())
                ),
                null,
                null
        );
    }

    private Long optionalSequence(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return 0L;
        }
        try {
            long parsed = Long.parseLong(rawValue.trim());
            return Math.max(parsed, 0L);
        } catch (NumberFormatException ex) {
            return 0L;
        }
    }
}



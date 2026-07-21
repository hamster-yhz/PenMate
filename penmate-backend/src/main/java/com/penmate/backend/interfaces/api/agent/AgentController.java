package com.penmate.backend.interfaces.api.agent;

import com.penmate.backend.application.agent.command.AgentCommands.CreateConversationCommand;
import com.penmate.backend.application.agent.context.StoryBibleRoutingPreferenceResolver;
import com.penmate.backend.application.agent.query.AgentRunHistoryQueryService;
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
import com.penmate.backend.interfaces.api.agent.dto.AgentRunEventDto;
import com.penmate.backend.interfaces.api.agent.dto.AgentRunHistoryDto;
import com.penmate.backend.interfaces.api.agent.dto.AgentSessionDto;
import com.penmate.backend.interfaces.api.agent.dto.CancelAgentRunDto;
import com.penmate.backend.interfaces.api.agent.dto.CreateAgentConversationDto;
import com.penmate.backend.interfaces.api.agent.dto.CreateAgentTurnDto;
import com.penmate.backend.interfaces.api.agent.dto.ResumeAgentSessionDto;
import com.penmate.backend.interfaces.api.agent.dto.StoryBibleRoutingPreferenceDto;
import com.penmate.backend.interfaces.api.agent.dto.UpdateAgentSessionDto;
import com.penmate.backend.interfaces.api.common.ApiResponse;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
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
    private final AgentRunHistoryQueryService runHistoryQueryService;

    public AgentController(AgentConversationAppService agentConversationAppService,
                           AgentRunRecoveryAppService agentRunRecoveryAppService,
                           AgentSessionTokenUsageAppService agentSessionTokenUsageAppService,
                           AgentTurnAppService agentTurnAppService,
                           AgentRunEventStreamService agentRunEventStreamService,
                           StoryBibleRoutingPreferenceResolver routingPreferences,
                           AgentRunCancellationService runCancellationService,
                           AgentRunRetryService runRetryService,
                           AgentRunHistoryQueryService runHistoryQueryService) {
        this.agentConversationAppService = agentConversationAppService;
        this.agentRunRecoveryAppService = agentRunRecoveryAppService;
        this.agentSessionTokenUsageAppService = agentSessionTokenUsageAppService;
        this.agentTurnAppService = agentTurnAppService;
        this.agentRunEventStreamService = agentRunEventStreamService;
        this.routingPreferences = routingPreferences;
        this.runCancellationService = runCancellationService;
        this.runRetryService = runRetryService;
        this.runHistoryQueryService = runHistoryQueryService;
    }

    @GetMapping("/routing-preference")
    public ApiResponse<StoryBibleRoutingPreferenceDto.View> getUserRoutingPreference(
            @PathVariable String projectId, Authentication authentication,
            @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        var result = routingPreferences.resolveProject(requireLongId(projectId, "projectId"), principalId(authentication));
        return ApiResponse.success(toRoutingView(result), traceId);
    }

    @PutMapping("/routing-preference")
    public ApiResponse<StoryBibleRoutingPreferenceDto.View> updateUserRoutingPreference(
            @PathVariable String projectId, Authentication authentication,
            @RequestBody StoryBibleRoutingPreferenceDto.Update dto,
            @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        var result = routingPreferences.saveProject(requireLongId(projectId, "projectId"), principalId(authentication),
                dto.mode(), optionalLongId(dto.routerModelConfigId(), "routerModelConfigId"));
        return ApiResponse.success(toRoutingView(result), traceId);
    }

    /**
     * 查询当前项目下的会话列表�?
     */
    @GetMapping("/sessions")
    public ApiResponse<List<AgentSessionDto>> listSessions(@PathVariable String projectId,
                                                           @RequestParam(value = "deleted", defaultValue = "false") boolean deleted,
                                                           Authentication authentication,
                                                           @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return ApiResponse.success(
                agentConversationAppService.listConversations(
                                requireLongId(projectId, "projectId"), principalId(authentication), deleted)
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
                                                      Authentication authentication,
                                                      @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        log.info("Create agent session request: projectId={}, userId={}, title={}, traceId={}",
                projectId, principalId(authentication), dto.getTitle(), traceId);
        return ApiResponse.success(
                toSessionDto(agentConversationAppService.createConversation(requireLongId(projectId, "projectId"), toCommand(dto, principalId(authentication)), traceId)),
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
                                                        Authentication authentication,
                                                        @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        log.info("Resume agent session request: projectId={}, sessionId={}, operatorId={}, trigger={}, traceId={}",
                projectId, sessionId, principalId(authentication), dto.getTrigger(), traceId);
        return ApiResponse.success(
                toRecoveryDto(agentRunRecoveryAppService.resumeSession(
                        requireLongId(projectId, "projectId"),
                        requireLongId(sessionId, "sessionId"),
                        principalId(authentication),
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
                                               Authentication authentication,
                                               @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        log.info("Create agent turn request: projectId={}, sessionId={}, operatorId={}, taskType={}, traceId={}",
                projectId, sessionId, principalId(authentication), dto.getTaskRequest().getTaskType(), traceId);
        AgentTurnResult result = agentTurnAppService.createTurn(
                requireLongId(projectId, "projectId"),
                requireLongId(sessionId, "sessionId"),
                toCommand(dto, principalId(authentication)),
                traceId);
        return ApiResponse.success(toRunDto(result, requireLongId(sessionId, "sessionId")), traceId);
    }

    @GetMapping(path = "/runs/{runId}/stream", produces = "text/event-stream")
    public SseEmitter openRunStream(@PathVariable String projectId,
                                    @PathVariable String runId,
                                    @RequestParam(value = "after", required = false) String after,
                                    @RequestHeader(value = "Last-Event-ID", required = false) String lastEventId,
                                    @RequestHeader(value = "X-Trace-Id", required = false) String traceId,
                                    HttpServletResponse response) {
        Long resolvedProjectId = requireLongId(projectId, "projectId");
        Long resolvedRunId = requireLongId(runId, "runId");
        Long replayCursor = Math.max(optionalSequence(after), optionalSequence(lastEventId));
        log.info("Open agent run stream request: projectId={}, runId={}, after={}, lastEventId={}, replayCursor={}, traceId={}",
                resolvedProjectId, resolvedRunId, after, lastEventId, replayCursor, traceId);
        response.setHeader("Cache-Control", "no-cache, no-transform");
        response.setHeader("X-Accel-Buffering", "no");
        return agentRunEventStreamService.openStream(resolvedRunId, replayCursor);
    }

    @PostMapping("/runs/{runId}/cancel")
    public ApiResponse<AgentRunDto.ActiveRunDto> cancelRun(
            @PathVariable String projectId,
            @PathVariable String runId,
            @Valid @RequestBody CancelAgentRunDto dto,
            Authentication authentication,
            @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        var run = runCancellationService.cancel(
                requireLongId(projectId, "projectId"),
                requireLongId(runId, "runId"),
                principalId(authentication),
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
            Authentication authentication,
            @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        var run = runRetryService.retry(
                requireLongId(projectId, "projectId"),
                requireLongId(runId, "runId"),
                principalId(authentication),
                traceId);
        return ApiResponse.success(new AgentRunDto.ActiveRunDto(
                stringifyBusinessId(run.turnId()),
                stringifyBusinessId(run.runId()),
                run.runStatus(),
                run.runPhase(),
                stringifyBusinessId(run.latestEventSeq())), traceId);
    }

    private CreateConversationCommand toCommand(CreateAgentConversationDto dto, Long userId) {
        return new CreateConversationCommand(
                userId,
                dto.getTitle(),
                dto.getStatus(),
                userId
        );
    }

    private AgentTurnCommand toCommand(CreateAgentTurnDto dto, Long actorUserId) {
        CreateAgentTurnDto.TaskRequest request = dto.getTaskRequest();
        return new AgentTurnCommand(
                actorUserId,
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
            StoryBibleRoutingPreferenceResolver.EffectivePreference value) {
        return new StoryBibleRoutingPreferenceDto.View(value.mode(), stringifyBusinessId(value.routerModelConfigId()));
    }

    @PatchMapping("/sessions/{sessionId}")
    public ApiResponse<AgentSessionDto> renameSession(@PathVariable String projectId,
                                                      @PathVariable String sessionId,
                                                      @Valid @RequestBody UpdateAgentSessionDto dto,
                                                      Authentication authentication,
                                                      @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return ApiResponse.success(toSessionDto(agentConversationAppService.renameConversation(
                requireLongId(projectId, "projectId"), requireLongId(sessionId, "sessionId"),
                principalId(authentication), dto.getTitle())), traceId);
    }

    @DeleteMapping("/sessions/{sessionId}")
    public ApiResponse<String> deleteSession(@PathVariable String projectId,
                                             @PathVariable String sessionId,
                                             Authentication authentication,
                                             @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        agentConversationAppService.deleteConversation(requireLongId(projectId, "projectId"),
                requireLongId(sessionId, "sessionId"), principalId(authentication));
        return ApiResponse.success("deleted", traceId);
    }

    @PostMapping("/sessions/{sessionId}/restore")
    public ApiResponse<AgentSessionDto> restoreSession(@PathVariable String projectId,
                                                       @PathVariable String sessionId,
                                                       Authentication authentication,
                                                       @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return ApiResponse.success(toSessionDto(agentConversationAppService.restoreConversation(
                requireLongId(projectId, "projectId"), requireLongId(sessionId, "sessionId"),
                principalId(authentication))), traceId);
    }

    @GetMapping("/sessions/{sessionId}/runs")
    public ApiResponse<List<AgentRunHistoryDto>> listSessionRuns(@PathVariable String projectId,
                                                                 @PathVariable String sessionId,
                                                                 Authentication authentication,
                                                                 @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return ApiResponse.success(runHistoryQueryService.list(
                        requireLongId(projectId, "projectId"), requireLongId(sessionId, "sessionId"),
                        principalId(authentication)).stream().map(this::toRunHistoryDto).toList(),
                traceId);
    }

    private Long principalId(Authentication authentication) {
        if (authentication == null) throw com.penmate.backend.application.common.exception.BusinessException.of("Login required");
        return requireLongId(authentication.getName(), "principal userId");
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
                conversation == null ? null : conversation.getStatus(),
                conversation == null ? null : conversation.getLastRunStatus(),
                instant(conversation == null ? null : conversation.getLastMessageAt()),
                instant(conversation == null ? null : conversation.getCreatedAt()),
                instant(conversation == null ? null : conversation.getUpdatedAt()),
                instant(conversation == null ? null : conversation.getDeletedAt())
        );
    }

    private AgentRunHistoryDto toRunHistoryDto(AgentRunHistoryQueryService.RunHistory history) {
        var run = history.run();
        return new AgentRunHistoryDto(
                stringifyBusinessId(run.runId()), stringifyBusinessId(run.turnId()),
                stringifyBusinessId(run.predecessorRunId()), run.runStatus(), run.runPhase(), run.attemptCount(),
                run.lastErrorCode(), run.lastErrorMessage(), stringifyBusinessId(run.latestEventSeq()),
                instant(run.startedAt()), instant(run.finishedAt()),
                history.output() == null ? null : new AgentRunHistoryDto.OutputDto(
                        history.output().text(), history.output().offset(),
                        stringifyBusinessId(history.output().sequence()), history.output().state(),
                        instant(history.output().updatedAt())),
                history.events().stream().map(event -> new AgentRunEventDto(
                        stringifyBusinessId(event.eventId()), stringifyBusinessId(event.runId()),
                        stringifyBusinessId(event.projectId()), stringifyBusinessId(event.sessionId()),
                        stringifyBusinessId(event.turnId()), stringifyBusinessId(event.sequence()),
                        event.schemaVersion(), event.eventType(), event.payloadJson(), instant(event.createdAt())
                )).toList()
        );
    }

    private String instant(java.time.Instant value) {
        return value == null ? null : value.toString();
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



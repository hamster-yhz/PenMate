package com.penmate.backend.application.agent.usecase;

import com.penmate.backend.application.agent.run.AgentRunAppService;
import com.penmate.backend.application.agent.run.AgentRunCommand;
import com.penmate.backend.application.agent.run.AgentRunResult;
import com.penmate.backend.application.agent.run.AgentRunDispatcher;
import com.penmate.backend.application.agent.run.AgentRunRecoveryPromptService;
import com.penmate.backend.application.agent.AgentModelRoutingService;
import com.penmate.backend.application.agent.skill.AgentSkillActivationService;
import com.penmate.backend.application.agent.safety.AgentSafetyModeApplicationService;
import com.penmate.backend.application.common.exception.BusinessErrorType;
import com.penmate.backend.application.common.exception.BusinessException;
import com.penmate.backend.application.style.usecase.SessionStyleBindingAppService;
import com.penmate.backend.domain.agent.model.AgentMessage;
import com.penmate.backend.domain.agent.repository.AgentRepository;
import com.penmate.backend.domain.agent.repository.AgentSessionRepository;
import com.penmate.backend.domain.shared.service.BusinessIdGenerator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
public class AgentTurnAppService {

    private final AgentRepository agentRepository;
    private final AgentSessionRepository agentSessionRepository;
    private final BusinessIdGenerator businessIdGenerator;
    private final AgentRunAppService agentRunAppService;
    private final AgentRunDispatcher runDispatcher;
    private final SessionStyleBindingAppService sessionStyleBindingAppService;
    private final AgentSkillActivationService skillActivationService;
    private final AgentRunRecoveryPromptService recoveryPromptService;
    private final AgentSafetyModeApplicationService safetyModes;
    private final AgentModelRoutingService modelRoutingService;

    @Autowired
    public AgentTurnAppService(SessionStyleBindingAppService sessionStyleBindingAppService,
                               AgentRepository agentRepository,
                               AgentSessionRepository agentSessionRepository,
                               BusinessIdGenerator businessIdGenerator,
                               AgentRunAppService agentRunAppService,
                               AgentRunDispatcher runDispatcher,
                               AgentSkillActivationService skillActivationService,
                               AgentRunRecoveryPromptService recoveryPromptService,
                               AgentSafetyModeApplicationService safetyModes,
                               AgentModelRoutingService modelRoutingService) {
        this.sessionStyleBindingAppService = sessionStyleBindingAppService;
        this.agentRepository = agentRepository;
        this.agentSessionRepository = agentSessionRepository;
        this.businessIdGenerator = businessIdGenerator;
        this.agentRunAppService = agentRunAppService;
        this.runDispatcher = runDispatcher;
        this.skillActivationService = skillActivationService;
        this.recoveryPromptService = recoveryPromptService;
        this.safetyModes = safetyModes;
        this.modelRoutingService = modelRoutingService;
    }

    public AgentTurnAppService(SessionStyleBindingAppService sessionStyleBindingAppService,
                               AgentRepository agentRepository,
                               AgentSessionRepository agentSessionRepository,
                               BusinessIdGenerator businessIdGenerator,
                               AgentRunAppService agentRunAppService,
                               AgentRunDispatcher runDispatcher,
                               AgentSkillActivationService skillActivationService,
                               AgentRunRecoveryPromptService recoveryPromptService,
                               AgentSafetyModeApplicationService safetyModes) {
        this(sessionStyleBindingAppService, agentRepository, agentSessionRepository, businessIdGenerator,
                agentRunAppService, runDispatcher, skillActivationService, recoveryPromptService,
                safetyModes, null);
    }

    @Transactional
    public AgentTurnResult createTurn(Long projectId,
                                      Long sessionId,
                                      AgentTurnCommand command,
                                      String traceId) {
        log.info("Agent turn creation started: projectId={}, sessionId={}, traceId={}",
                projectId, sessionId, traceId);

        agentSessionRepository.lockSessionForTurnAppend(projectId, sessionId);
        var session = agentSessionRepository.findSession(projectId, sessionId);
        if (session == null) {
            throw BusinessException.notFound("Agent session not found");
        }
        if (!session.getOwnerUserId().equals(command.operatorId())) {
            throw BusinessException.forbidden("Agent session belongs to another user");
        }
        if (agentRepository.countActiveRuns(sessionId) > 0) {
            throw BusinessException.of(BusinessErrorType.CONFLICT, "SESSION_RUN_ACTIVE",
                    "This Agent session already has an active Run", null);
        }
        skillActivationService.replaceSessionSkills(sessionId,
                command == null ? null : command.activeSkills());

        String userRequest = command == null || command.userMessage() == null ? "" : command.userMessage();
        String effectiveRequest = recoveryPromptService.attachToManualRequest(projectId, sessionId, userRequest);
        if (effectiveRequest == null) effectiveRequest = userRequest;

        AgentMessage userMessage = createUserMessage(projectId, sessionId, command, traceId);
        persistMessage(sessionId, userMessage);

        Long turnId = businessIdGenerator.nextId();
        Long runId = businessIdGenerator.nextId();
        int turnSeq = agentSessionRepository.nextTurnSeq(sessionId);
        persistTurn(sessionId, userMessage, turnId, runId, turnSeq);
        requireOne(agentRepository.bindMessageToTurn(sessionId, userMessage.getMessageId(), turnId),
                "failed to bind user message to turn");
        log.info("Agent turn persisted: projectId={}, sessionId={}, turnId={}, turnSeq={}",
                projectId, sessionId, turnId, turnSeq);

        Long modelConfigId = command == null || command.taskRequest() == null
                ? null : command.taskRequest().modelConfigId();
        Long operatorId = command == null ? 0L : command.operatorId();
        String modelSnapshotJson = modelSnapshot(operatorId, modelConfigId);
        String styleSnapshotJson = sessionStyleBindingAppService.getBoundStyleSnapshotJson(projectId, sessionId);
        String promptText = effectiveRequest;
        var safetyMode = safetyModes.get(operatorId);
        AgentRunResult runResult = agentRunAppService.createRun(new AgentRunCommand(
                projectId,
                sessionId,
                turnId,
                operatorId,
                runId,
                promptText,
                command == null || command.taskRequest() == null ? null
                        : command.taskRequest().chapterId(),
                command == null || command.taskRequest() == null ? java.util.List.of()
                        : command.taskRequest().chapterIds(),
                command == null || command.taskRequest() == null ? null
                        : command.taskRequest().selectedText(),
                styleSnapshotJson,
                modelSnapshotJson,
                null,
                safetyMode == null ? com.penmate.backend.domain.agent.model.AgentSafetyMode.STANDARD.name() : safetyMode.name(),
                null,
                traceId
        ));
        skillActivationService.bindSessionSkillsToRun(sessionId, runResult.runId());
        requireOne(agentSessionRepository.updateLastTurn(projectId, sessionId, turnId), "failed to update session last turn");
        requireOne(agentSessionRepository.updateLastRun(projectId, sessionId, runResult.runId()), "failed to update session last run");

        log.info("Agent turn created with run: projectId={}, sessionId={}, turnId={}, runId={}",
                projectId, sessionId, turnId, runResult.runId());
        return AgentTurnResult.forRun(
                sessionId,
                turnId,
                runResult.runId(),
                runResult.runStatus(),
                runResult.runPhase(),
                runResult.latestSequence()
        );
    }

    private String modelSnapshot(Long operatorId, Long modelConfigId) {
        if (modelRoutingService == null) {
            return "{\"operatorId\":" + operatorId + ",\"modelConfigId\":"
                    + (modelConfigId == null ? "null" : modelConfigId) + "}";
        }
        var snapshot = modelRoutingService.resolveSnapshot(operatorId, modelConfigId);
        var policy = snapshot.reasoningPolicy();
        return "{\"operatorId\":" + operatorId
                + ",\"modelConfigId\":" + snapshot.modelConfigId()
                + ",\"reasoningEffort\":\"" + policy.effort()
                + "\",\"reasoningMode\":\"" + policy.mode()
                + "\",\"reasoningSummary\":\"" + policy.summary() + "\"}";
    }

    private AgentMessage createUserMessage(Long projectId,
                                           Long sessionId,
                                           AgentTurnCommand command,
                                           String traceId) {
        Long userMessageId = businessIdGenerator.nextId();
        int seqNo = agentRepository.nextMessageSeq(sessionId);
        AgentMessage msg = new AgentMessage();
        msg.setMessageId(userMessageId);
        msg.setConversationId(sessionId);
        msg.setRole("user");
        msg.setContentMd(command == null ? "" : command.userMessage());
        msg.setSeqNo(seqNo);
        return msg;
    }

    private void persistMessage(Long sessionId, AgentMessage message) {
        int affected = agentRepository.insertMessage(message);
        if (affected != 1) {
            throw new IllegalStateException("failed to insert user message");
        }
        agentRepository.touchConversationLastMessage(sessionId);
    }

    private void persistTurn(Long sessionId,
                             AgentMessage userMessage,
                             Long turnId,
                             Long runId,
                             int turnSeq) {
        int affected = agentSessionRepository.insertTurn(
                sessionId,
                turnId,
                turnSeq,
                userMessage == null ? null : userMessage.getMessageId(),
                runId,
                "PENDING",
                null
        );
        requireOne(affected, "failed to insert turn");
    }

    private void requireOne(int affected, String message) {
        if (affected != 1) {
            throw new IllegalStateException(message);
        }
    }
}

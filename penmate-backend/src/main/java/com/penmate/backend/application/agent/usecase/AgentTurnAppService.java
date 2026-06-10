package com.penmate.backend.application.agent.usecase;

import com.penmate.backend.application.style.usecase.SessionStyleBindingAppService;
import com.penmate.backend.application.agent.run.AgentRunAppService;
import com.penmate.backend.application.agent.run.AgentRunCommand;
import com.penmate.backend.application.agent.run.AgentRunResult;
import com.penmate.backend.domain.agent.model.AgentMessage;
import com.penmate.backend.domain.agent.model.AgentSession;
import com.penmate.backend.domain.agent.repository.AgentRepository;
import com.penmate.backend.domain.agent.repository.AgentSessionRepository;
import com.penmate.backend.domain.shared.service.BusinessIdGenerator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final AgentRunAppService agentRunAppService;

    public AgentTurnAppService(SessionStyleBindingAppService sessionStyleBindingAppService,
                               AgentRepository agentRepository,
                               AgentSessionRepository agentSessionRepository,
                               BusinessIdGenerator businessIdGenerator) {
        this(sessionStyleBindingAppService, agentRepository, agentSessionRepository, businessIdGenerator, null);
    }

    public AgentTurnAppService(SessionStyleBindingAppService sessionStyleBindingAppService,
                               AgentRepository agentRepository,
                               AgentSessionRepository agentSessionRepository,
                               BusinessIdGenerator businessIdGenerator,
                               AgentRunAppService agentRunAppService) {
        this.sessionStyleBindingAppService = sessionStyleBindingAppService;
        this.agentRepository = agentRepository;
        this.agentSessionRepository = agentSessionRepository;
        this.businessIdGenerator = businessIdGenerator;
        this.agentRunAppService = agentRunAppService;
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

        Long turnId = businessIdGenerator.nextId();
        Long runId = businessIdGenerator.nextId();
        int turnSeq = agentSessionRepository.nextTurnSeq(sessionId);
        persistTurn(sessionId, userMessage, runId, turnId, turnSeq);
        log.info("Agent turn persisted: projectId={}, sessionId={}, traceId={}, turnId={}, turnSeq={}, runId={}",
                projectId,
                sessionId,
                traceId,
                turnId,
                turnSeq,
                runId);

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
        int updatedRun = agentSessionRepository.updateLastRun(projectId, sessionId, runId);
        if (updatedRun != 1) {
            throw new IllegalStateException("failed to update session last run");
        }
        AgentRunResult run = createRun(projectId, sessionId, turnId, runId, command, userMessage, traceId);

        Long boundStyleId = sessionStyleBindingAppService.getBoundStyleId(projectId, sessionId);
        log.info("Agent turn created: projectId={}, sessionId={}, runId={}, turnId={}, taskType={}, chapterId={}, selectedTextLength={}, boundStyleId={}, traceId={}",
                projectId,
                sessionId,
                runId,
                turnId,
                command == null || command.taskRequest() == null ? null : command.taskRequest().taskType(),
                command == null || command.taskRequest() == null ? null : command.taskRequest().chapterId(),
                command == null || command.taskRequest() == null || command.taskRequest().selectedText() == null ? 0 : command.taskRequest().selectedText().length(),
                boundStyleId,
                traceId);
        AgentTurnResult.BoundStyleView boundStyle = boundStyleId == null
                ? null
                : new AgentTurnResult.BoundStyleView(boundStyleId, null);
        return new AgentTurnResult(
                new AgentTurnResult.SessionView(sessionId, "Session-" + sessionId, "ACTIVE", boundStyle, run.runStatus()),
                new AgentTurnResult.ActiveRunView(turnId, run.runId(), run.runStatus(), run.runPhase(), run.latestSequence()),
                command.taskRequest().taskType(),
                userMessage.getContentMd()
        );
    }

    private AgentRunResult createRun(Long projectId,
                                     Long sessionId,
                                     Long turnId,
                                     Long runId,
                                     AgentTurnCommand command,
                                     AgentMessage userMessage,
                                     String traceId) {
        if (agentRunAppService == null) {
            return new AgentRunResult(runId, "pending", "created", 0L);
        }
        AgentSession session = agentSessionRepository.findSession(projectId, sessionId);
        Long ownerUserId = command == null || command.operatorId() == null
                ? session == null ? null : session.getOwnerUserId()
                : command.operatorId();
        AgentTurnCommand.TaskRequest taskRequest = command == null ? null : command.taskRequest();
        String modelSnapshotJson = "{\"operatorId\":"
                + (ownerUserId == null ? "null" : ownerUserId)
                + ",\"modelConfigId\":"
                + (taskRequest == null || taskRequest.modelConfigId() == null ? "null" : taskRequest.modelConfigId())
                + "}";
        return agentRunAppService.createRun(new AgentRunCommand(
                projectId,
                sessionId,
                turnId,
                ownerUserId,
                runId,
                taskRequest == null ? "CHAT" : taskRequest.taskType(),
                userMessage == null ? command.userMessage() : userMessage.getContentMd(),
                taskRequest == null ? null : taskRequest.chapterId(),
                taskRequest == null ? null : taskRequest.selectedText(),
                sessionStyleBindingAppService.getBoundStyleSnapshotJson(projectId, sessionId),
                modelSnapshotJson,
                null,
                contextHash(command),
                traceId
        ));
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

    private void persistUserMessage(Long sessionId, AgentMessage userMessage) {
        int affected = agentRepository.insertMessage(userMessage);
        if (affected != 1) {
            throw new IllegalStateException("failed to insert user message");
        }
        agentRepository.touchConversationLastMessage(sessionId);
    }

    private void persistTurn(Long sessionId,
                             AgentMessage userMessage,
                             Long runId,
                             Long turnId,
                             int turnSeq) {
        int affected = agentSessionRepository.insertTurn(
                sessionId,
                turnId,
                turnSeq,
                userMessage == null ? null : userMessage.getMessageId(),
                runId,
                "pending",
                null
        );
        if (affected != 1) {
            throw new IllegalStateException("failed to insert turn");
        }
    }

    private String contextHash(AgentTurnCommand command) {
        AgentTurnCommand.TaskRequest taskRequest = command == null ? null : command.taskRequest();
        String seed = String.join("|",
                command == null || command.userMessage() == null ? "" : command.userMessage(),
                taskRequest == null || taskRequest.taskType() == null ? "" : taskRequest.taskType(),
                taskRequest == null || taskRequest.chapterId() == null ? "" : String.valueOf(taskRequest.chapterId()),
                taskRequest == null || taskRequest.selectedText() == null ? "" : taskRequest.selectedText()
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

}

package com.penmate.backend.application.agent.usecase;

import com.penmate.backend.application.agent.run.AgentRunAppService;
import com.penmate.backend.application.agent.run.AgentRunCommand;
import com.penmate.backend.application.agent.run.AgentRunResult;
import com.penmate.backend.application.agent.run.AgentRunDispatcher;
import com.penmate.backend.application.style.usecase.SessionStyleBindingAppService;
import com.penmate.backend.domain.agent.model.AgentMessage;
import com.penmate.backend.domain.agent.repository.AgentRepository;
import com.penmate.backend.domain.agent.repository.AgentSessionRepository;
import com.penmate.backend.domain.shared.service.BusinessIdGenerator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
public class AgentTurnAppService {

    private final AgentRepository agentRepository;
    private final AgentSessionRepository agentSessionRepository;
    private final BusinessIdGenerator businessIdGenerator;
    private final AgentRunAppService agentRunAppService;
    private final AgentRunDispatcher runDispatcher;

    public AgentTurnAppService(SessionStyleBindingAppService sessionStyleBindingAppService,
                               AgentRepository agentRepository,
                               AgentSessionRepository agentSessionRepository,
                               BusinessIdGenerator businessIdGenerator,
                               AgentRunAppService agentRunAppService,
                               AgentRunDispatcher runDispatcher) {
        this.agentRepository = agentRepository;
        this.agentSessionRepository = agentSessionRepository;
        this.businessIdGenerator = businessIdGenerator;
        this.agentRunAppService = agentRunAppService;
        this.runDispatcher = runDispatcher;
    }

    @Transactional
    public AgentTurnResult createTurn(Long projectId,
                                      Long sessionId,
                                      AgentTurnCommand command,
                                      String traceId) {
        log.info("Agent turn creation started: projectId={}, sessionId={}, traceId={}",
                projectId, sessionId, traceId);

        AgentMessage userMessage = createUserMessage(projectId, sessionId, command, traceId);
        persistMessage(sessionId, userMessage);

        Long turnId = businessIdGenerator.nextId();
        int turnSeq = agentSessionRepository.nextTurnSeq(sessionId);
        persistTurn(sessionId, userMessage, turnId, turnSeq);
        log.info("Agent turn persisted: projectId={}, sessionId={}, turnId={}, turnSeq={}",
                projectId, sessionId, turnId, turnSeq);

        Long runId = businessIdGenerator.nextId();
        Long modelConfigId = command == null || command.taskRequest() == null
                ? null : command.taskRequest().modelConfigId();
        String modelSnapshotJson = modelConfigId == null ? null : "{\"modelConfigId\":\"" + modelConfigId + "\"}";
        String promptText = command == null || command.userMessage() == null
                ? "" : command.userMessage();
        AgentRunResult runResult = agentRunAppService.createRun(new AgentRunCommand(
                projectId,
                sessionId,
                turnId,
                command == null ? 0L : command.operatorId(),
                runId,
                command == null || command.taskRequest() == null ? "WRITE"
                        : command.taskRequest().taskType(),
                promptText,
                command == null || command.taskRequest() == null ? null
                        : command.taskRequest().chapterId(),
                command == null || command.taskRequest() == null ? null
                        : command.taskRequest().selectedText(),
                null,
                modelSnapshotJson,
                null,
                null,
                traceId
        ));

        log.info("Agent turn created with run: projectId={}, sessionId={}, turnId={}, runId={}",
                projectId, sessionId, turnId, runResult.runId());
        return AgentTurnResult.forRun(runResult.runId(), runResult.runStatus());
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
                             int turnSeq) {
        int affected = agentSessionRepository.insertTurn(
                sessionId,
                turnId,
                turnSeq,
                userMessage == null ? null : userMessage.getMessageId(),
                null,
                "PENDING",
                null
        );
        if (affected != 1) {
            throw new IllegalStateException("failed to insert turn");
        }
    }
}

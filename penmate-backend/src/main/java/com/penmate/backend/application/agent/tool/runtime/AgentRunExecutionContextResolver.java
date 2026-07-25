package com.penmate.backend.application.agent.tool.runtime;

import com.penmate.backend.domain.agent.model.AgentSession;
import com.penmate.backend.domain.agent.repository.AgentSessionRepository;
import com.penmate.backend.domain.agent.run.model.AgentRun;
import com.penmate.backend.domain.agent.run.model.AgentRunInput;
import com.penmate.backend.domain.agent.run.repository.AgentRunRepository;
import com.penmate.backend.domain.novel.model.NovelProject;
import com.penmate.backend.domain.novel.repository.NovelGateway;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Objects;

@Component
public class AgentRunExecutionContextResolver {

    private final AgentRunRepository runs;
    private final AgentSessionRepository sessions;
    private final NovelGateway novels;

    public AgentRunExecutionContextResolver(AgentRunRepository runs,
                                            AgentSessionRepository sessions,
                                            NovelGateway novels) {
        this.runs = runs;
        this.sessions = sessions;
        this.novels = novels;
    }

    public AuthorizedAgentRunContext resolve(ToolCallRequest request) {
        if (request == null || request.runId() == null || request.executionToken() == null) {
            throw rejected("AGENT_RUN_EXECUTION_CONTEXT_REQUIRED", "Run execution context is required");
        }
        AgentRun run = runs.findRun(request.runId());
        AgentRunInput input = runs.findInput(request.runId());
        if (run == null || input == null) {
            throw rejected("AGENT_RUN_NOT_FOUND", "Agent Run or its immutable input is missing");
        }
        if (!runs.ownsExecutionToken(run.runId(), request.executionToken(), Instant.now())) {
            throw rejected("AGENT_RUN_EXECUTION_FENCED", "Agent Run no longer owns the current execution token");
        }
        NovelProject project = novels.findProjectById(run.projectId());
        AgentSession session = sessions.findSession(run.projectId(), run.sessionId());
        if (project == null || session == null
                || !Objects.equals(run.runId(), request.runId())
                || !Objects.equals(project.getProjectId(), run.projectId())
                || !Objects.equals(project.getOwnerUserId(), run.ownerUserId())
                || !Objects.equals(session.getSessionId(), run.sessionId())
                || !Objects.equals(session.getProjectId(), run.projectId())
                || !Objects.equals(session.getOwnerUserId(), run.ownerUserId())
                || !Objects.equals(input.runId(), run.runId())) {
            throw rejected("AGENT_RUN_RESOURCE_CONTEXT_MISMATCH",
                    "Agent Run resource ownership is inconsistent");
        }
        return new AuthorizedAgentRunContext(
                run.runId(), run.projectId(), run.sessionId(), run.turnId(), run.ownerUserId(), run.contextEpochId(),
                request.executionToken(), run.traceId(), input);
    }

    public void assertExecutionOwned(AuthorizedAgentRunContext context) {
        if (context == null) {
            throw rejected("AGENT_RUN_EXECUTION_CONTEXT_REQUIRED", "Run execution context is required");
        }
        AgentRun run = runs.findRun(context.runId());
        if (run == null
                || !Objects.equals(run.runId(), context.runId())
                || !Objects.equals(run.projectId(), context.projectId())
                || !Objects.equals(run.sessionId(), context.sessionId())
                || !Objects.equals(run.turnId(), context.turnId())
                || !Objects.equals(run.ownerUserId(), context.ownerUserId())
                || !Objects.equals(run.contextEpochId(), context.contextEpochId())) {
            throw rejected("AGENT_RUN_RESOURCE_CONTEXT_MISMATCH",
                    "Agent Run resource ownership is inconsistent");
        }
        if (!runs.ownsExecutionToken(context.runId(), context.executionToken(), Instant.now())) {
            throw rejected("AGENT_RUN_EXECUTION_FENCED", "Agent Run no longer owns the current execution token");
        }
    }

    private AgentRunExecutionRejectedException rejected(String code, String message) {
        return new AgentRunExecutionRejectedException(code, message);
    }
}

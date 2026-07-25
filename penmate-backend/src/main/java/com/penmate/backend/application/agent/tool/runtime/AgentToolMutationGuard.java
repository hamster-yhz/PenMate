package com.penmate.backend.application.agent.tool.runtime;

import com.penmate.backend.application.agent.context.AgentRunContextArtifactService;
import com.penmate.backend.application.agent.context.AgentRunDependencyValidator;
import com.penmate.backend.domain.agent.run.model.AgentRun;
import com.penmate.backend.domain.agent.run.model.AgentRunInput;
import com.penmate.backend.domain.agent.run.repository.AgentRunRepository;
import org.springframework.stereotype.Component;

@Component
public class AgentToolMutationGuard {

    private final AgentRunRepository runs;
    private final AgentRunExecutionContextResolver executionContexts;
    private final AgentRunContextArtifactService contextArtifacts;
    private final AgentRunDependencyValidator dependencyValidator;

    public AgentToolMutationGuard(AgentRunRepository runs,
                                  AgentRunExecutionContextResolver executionContexts,
                                  AgentRunContextArtifactService contextArtifacts,
                                  AgentRunDependencyValidator dependencyValidator) {
        this.runs = runs;
        this.executionContexts = executionContexts;
        this.contextArtifacts = contextArtifacts;
        this.dependencyValidator = dependencyValidator;
    }

    public void assertExecutable(AuthorizedAgentRunContext context, boolean mutatesState) {
        executionContexts.assertExecutionOwned(context);
        if (!mutatesState) return;

        AgentRun run = runs.findRun(context.runId());
        AgentRunInput input = context.input();
        if (run == null || input == null) {
            throw new Rejection("AGENT_RUN_NOT_FOUND", "Agent Run or its immutable input is missing");
        }
        AgentRunContextArtifactService.ResolvedArtifact artifact;
        try {
            artifact = contextArtifacts.loadLatestContextForRun(context.runId());
        } catch (RuntimeException ex) {
            throw new Rejection("AGENT_RUN_CONTEXT_MISSING", "Agent Run resolved context is unavailable");
        }
        if (run.contextEpochId() == null || !run.contextEpochId().equals(artifact.contextEpochId())) {
            throw new Rejection("AGENT_RUN_CONTEXT_MISMATCH",
                    "Agent Run Context Epoch does not match its immutable artifact");
        }
        var validation = dependencyValidator.validate(run, input, artifact);
        if (!validation.current()) {
            throw new Rejection("AGENT_RUN_DEPENDENCY_CHANGED",
                    "Agent Run dependencies changed: " + String.join(",", validation.changedFields()));
        }
    }

    public static final class Rejection extends AgentRunExecutionRejectedException {
        public Rejection(String errorCode, String message) {
            super(errorCode, message);
        }
    }
}

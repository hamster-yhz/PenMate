package com.penmate.backend.application.agent.orchestration;

import com.penmate.backend.application.agent.context.ContextPackage;
import com.penmate.backend.application.agent.orchestration.profile.TaskProfile;
import com.penmate.backend.application.agent.prompt.PromptPlan;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class AgentTaskRuntimeUpdaterContractTest {

    @Test
    void UT_APP_AGENT_TASK_RUNTIME_UPDATER_SHOULD_EXPOSE_SNAPSHOT_AWARE_RUNTIME_UPDATE_ENTRY() {
        boolean found = Arrays.stream(AgentTaskRuntimeUpdater.class.getDeclaredMethods())
                .filter(method -> method.getName().equals("updateGenerationRuntime"))
                .anyMatch(method -> Arrays.equals(method.getParameterTypes(), new Class<?>[] {
                        Long.class,
                        Long.class,
                        String.class,
                        String.class,
                        String.class,
                        TaskProfile.class,
                        PromptPlan.class,
                        ContextPackage.class
                }));

        assertThat(found)
                .as("expected snapshot-aware updateGenerationRuntime overload for task profile, prompt plan and context package")
                .isTrue();
    }
}

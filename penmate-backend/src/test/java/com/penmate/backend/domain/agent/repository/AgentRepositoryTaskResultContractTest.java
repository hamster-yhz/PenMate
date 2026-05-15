package com.penmate.backend.domain.agent.repository;

import com.penmate.backend.domain.agent.model.AgentTaskResult;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class AgentRepositoryTaskResultContractTest {

    @Test
    void UT_DOMAIN_AGENT_REPOSITORY_SHOULD_EXPOSE_TASK_RESULT_INSERT_ENTRY() {
        boolean found = Arrays.stream(AgentRepository.class.getDeclaredMethods())
                .filter(method -> method.getName().equals("insertTaskResult"))
                .anyMatch(method -> Arrays.equals(method.getParameterTypes(), new Class<?>[] {
                        AgentTaskResult.class
                }));

        assertThat(found)
                .as("expected task result persistence entry for agent_task_results")
                .isTrue();
    }

    @Test
    void UT_DOMAIN_AGENT_REPOSITORY_SHOULD_EXPOSE_TASK_TO_RESULT_LINK_UPDATE_ENTRY() {
        boolean found = Arrays.stream(AgentRepository.class.getDeclaredMethods())
                .filter(method -> method.getName().equals("updateGenerationTaskResultLink"))
                .anyMatch(method -> Arrays.equals(method.getParameterTypes(), new Class<?>[] {
                        Long.class,
                        Long.class,
                        Long.class
                }));

        assertThat(found)
                .as("expected result link update entry for projectId, taskId and resultId")
                .isTrue();
    }
}

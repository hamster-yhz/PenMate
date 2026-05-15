package com.penmate.backend.domain.agent.repository;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class AgentRepositorySnapshotContractTest {

    @Test
    void UT_DOMAIN_AGENT_REPOSITORY_SHOULD_EXPOSE_TASK_SNAPSHOT_PERSISTENCE_ENTRY() {
        boolean found = Arrays.stream(AgentRepository.class.getDeclaredMethods())
                .filter(method -> method.getName().equals("updateGenerationTaskSnapshots"))
                .anyMatch(method -> Arrays.equals(method.getParameterTypes(), new Class<?>[] {
                        Long.class,
                        Long.class,
                        String.class,
                        String.class,
                        String.class,
                        String.class,
                        String.class,
                        String.class
                }));

        assertThat(found)
                .as("expected snapshot persistence entry for taskProfileJson, promptPlanJson, contextPackageJson, activeToolCallsSnapshot, lastRuntimeStatus and recoveryCursor")
                .isTrue();
    }
}

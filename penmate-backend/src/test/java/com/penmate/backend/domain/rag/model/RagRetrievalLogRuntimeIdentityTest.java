package com.penmate.backend.domain.rag.model;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class RagRetrievalLogRuntimeIdentityTest {

    @Test
    void exposesRunIdInsteadOfTaskId() {
        assertThat(Arrays.stream(RagRetrievalLog.class.getDeclaredFields()).map(Field::getName))
                .contains("runId")
                .doesNotContain("taskId");
    }
}

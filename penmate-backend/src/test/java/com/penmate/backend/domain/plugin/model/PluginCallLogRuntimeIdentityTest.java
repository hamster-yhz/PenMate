package com.penmate.backend.domain.plugin.model;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class PluginCallLogRuntimeIdentityTest {

    @Test
    void exposesRunIdInsteadOfTaskId() {
        assertThat(Arrays.stream(PluginCallLog.class.getDeclaredFields()).map(Field::getName))
                .contains("runId")
                .doesNotContain("taskId");
    }
}

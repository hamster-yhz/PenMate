package com.penmate.backend.application.agent.context;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class AgentWorkingSetPromotionServiceTest {

    @Test
    void retries_once_then_returns_without_changing_the_resolved_run_context() {
        AgentWorkingSetService workingSet = mock(AgentWorkingSetService.class);
        RuntimeException failure = new RuntimeException("temporary database failure");
        doThrow(failure).doThrow(failure).when(workingSet)
                .promote(10L, 20L, List.of(30L), BigDecimal.ONE);
        AgentWorkingSetPromotionService service = new AgentWorkingSetPromotionService(workingSet);

        assertThatCode(() -> service.promoteBestEffort(10L, 20L, List.of(30L), BigDecimal.ONE))
                .doesNotThrowAnyException();

        verify(workingSet, times(2)).promote(10L, 20L, List.of(30L), BigDecimal.ONE);
    }
}

package com.penmate.backend.application.agent.context;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import com.penmate.backend.domain.agent.context.model.AgentWorkingSetEntry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentWorkingSetPromotionServiceTest {

    @Test
    void retries_once_then_returns_without_changing_the_resolved_run_context() {
        AgentWorkingSetService workingSet = mock(AgentWorkingSetService.class);
        RuntimeException failure = new RuntimeException("temporary database failure");
        doThrow(failure).doThrow(failure).when(workingSet)
                .promote(10L, 20L, List.of(30L), BigDecimal.ONE);
        AgentWorkingSetPromotionService service = new AgentWorkingSetPromotionService(workingSet);

        var result = service.promoteBestEffort(10L, 20L, List.of(30L), BigDecimal.ONE);

        verify(workingSet, times(2)).promote(10L, 20L, List.of(30L), BigDecimal.ONE);
        assertThat(result).isEqualTo(new AgentWorkingSetPromotionService.PromotionSummary(1, 0, 0, false));
    }

    @Test
    void reports_candidates_promotions_and_evictions() {
        AgentWorkingSetService workingSet = mock(AgentWorkingSetService.class);
        when(workingSet.list(10L)).thenReturn(List.of(entry(10L), entry(11L)));
        when(workingSet.promote(10L, 20L, List.of(12L), BigDecimal.ONE))
                .thenReturn(List.of(entry(11L), entry(12L)));
        AgentWorkingSetPromotionService service = new AgentWorkingSetPromotionService(workingSet);

        var result = service.promoteBestEffort(10L, 20L, List.of(12L), BigDecimal.ONE);

        assertThat(result).isEqualTo(new AgentWorkingSetPromotionService.PromotionSummary(1, 1, 1, true));
    }

    private AgentWorkingSetEntry entry(Long nodeId) {
        return new AgentWorkingSetEntry(10L, nodeId, BigDecimal.ONE, 20L, 1, false, null);
    }
}

package com.penmate.backend.application.agent.context;

import com.penmate.backend.domain.agent.context.model.AgentWorkingSetEntry;
import com.penmate.backend.domain.agent.context.repository.AgentWorkingSetRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentWorkingSetServiceTest {

    private final AgentWorkingSetRepository repository = mock(AgentWorkingSetRepository.class);
    private final AgentWorkingSetService service = new AgentWorkingSetService(repository);

    @Test
    void promotes_unique_nodes_then_applies_turn_retention_and_automatic_cap() {
        AgentWorkingSetEntry retained = new AgentWorkingSetEntry(
                20L, 101L, BigDecimal.ONE, 30L, 1, false, null);
        when(repository.promote(20L, 101L, BigDecimal.ONE, 30L)).thenReturn(1);
        when(repository.promote(20L, 102L, BigDecimal.ONE, 30L)).thenReturn(1);
        when(repository.findBySessionId(20L)).thenReturn(List.of(retained));

        List<AgentWorkingSetEntry> result = service.promote(
                20L, 30L, List.of(101L, 101L, 102L), BigDecimal.ZERO);

        var ordered = inOrder(repository);
        ordered.verify(repository).promote(20L, 101L, BigDecimal.ONE, 30L);
        ordered.verify(repository).promote(20L, 102L, BigDecimal.ONE, 30L);
        ordered.verify(repository).evictExpired(20L, 30L, AgentWorkingSetService.RETENTION_TURNS);
        ordered.verify(repository).evictOverflow(20L, AgentWorkingSetService.AUTOMATIC_CAP);
        ordered.verify(repository).findBySessionId(20L);
        assertThat(result).containsExactly(retained);
    }

    @Test
    void changes_pin_state_without_rebuilding_an_epoch() {
        when(repository.setPinned(20L, 101L, true)).thenReturn(1);

        service.setPinned(20L, 101L, true);

        verify(repository).setPinned(20L, 101L, true);
    }
}

package com.penmate.backend.domain.agent.run.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AgentEventWindowTest {

    @Test
    void resets_only_when_requested_cursor_has_fallen_behind_hot_events() {
        AgentEventWindow window = new AgentEventWindow(51L, 80L);

        assertThat(window.requiresResetAfter(49L)).isTrue();
        assertThat(window.requiresResetAfter(50L)).isFalse();
        assertThat(window.requiresResetAfter(80L)).isFalse();
    }

    @Test
    void resets_when_all_events_are_archived_and_cursor_is_not_current() {
        AgentEventWindow window = new AgentEventWindow(null, 80L);

        assertThat(window.requiresResetAfter(79L)).isTrue();
        assertThat(window.requiresResetAfter(80L)).isFalse();
    }
}

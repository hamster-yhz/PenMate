package com.penmate.backend.application.agent.context;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NoopStoryBibleSemanticRetrieverTest {
    @Test
    void should_report_unavailable_without_candidates_or_external_work() {
        var result = new NoopStoryBibleSemanticRetriever().retrieve(1L, "Mira", 20);
        assertThat(result.available()).isFalse();
        assertThat(result.candidates()).isEmpty();
    }
}

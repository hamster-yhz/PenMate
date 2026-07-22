package com.penmate.backend.application.rag.job;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RagJobPayloadTest {

    @Test
    void accepts_numeric_and_string_business_ids() {
        assertThat(RagJobPayload.requiredLong(Map.of("projectId", 42), "projectId")).isEqualTo(42L);
        assertThat(RagJobPayload.requiredLong(Map.of("projectId", "9007199254740993"), "projectId"))
                .isEqualTo(9_007_199_254_740_993L);
        assertThat(RagJobPayload.longOrDefault(Map.of(), "ownerUserId", 7L)).isEqualTo(7L);
    }

    @Test
    void rejects_missing_or_invalid_required_ids() {
        assertThatThrownBy(() -> RagJobPayload.requiredLong(Map.of("projectId", "invalid"), "projectId"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Missing RAG job field: projectId");
    }
}

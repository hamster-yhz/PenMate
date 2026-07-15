package com.penmate.backend.application.agent.context;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ContextEpochSnapshotCodecTest {
    @Test
    void should_round_trip_core_context_and_compact_catalog() {
        ContextEpochSnapshotCodec codec = new ContextEpochSnapshotCodec(new ObjectMapper());
        var snapshot = new ContextEpochSnapshotCodec.Snapshot(1, 1L, 2L, 3L, 4L, 5L,
                List.of(new ContextEpochSnapshotCodec.CoreNode(6L, 7L, "Mira", "Pilot", "Body", "{}")),
                List.of(new StoryBibleRouteRequest.CatalogEntry(6L, "Mira", "CHARACTER", "Pilot",
                        "ALWAYS_INCLUDE", "CANON")));
        assertThat(codec.decode(codec.encode(snapshot))).isEqualTo(snapshot);
    }
}

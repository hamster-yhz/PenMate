package com.penmate.backend.application.agent.context;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.penmate.backend.infrastructure.serialization.JacksonJsonCodec;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ContextEpochSnapshotCodecTest {
    @Test
    void should_round_trip_core_context_and_compact_catalog() {
        ContextEpochSnapshotCodec codec = new ContextEpochSnapshotCodec(
                new JacksonJsonCodec(new ObjectMapper()));
        var snapshot = new ContextEpochSnapshotCodec.Snapshot(2, 1L, 2L, 3L, 4L, 5L,
                List.of(new ContextEpochSnapshotCodec.CoreNode(6L, 7L, "CHARACTER", "CHARACTER", "Mira",
                        Map.of("title", "Captain Mira"), List.of(8L), List.of())),
                List.of(new StoryBibleRouteRequest.CatalogEntry(6L, "CHARACTER", "CHARACTER", "Mira",
                        List.of("Captain"), "Pilot", List.of(new StoryBibleRouteRequest.CatalogRelation(
                        "OUT", "ALLY_OF", 9L, "Nox")), "{\"title\":\"Captain Mira\"}",
                        "ALWAYS_INCLUDE", "CANON")));
        assertThat(codec.decode(codec.encode(snapshot))).isEqualTo(snapshot);
    }
}

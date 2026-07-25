package com.penmate.backend.application.agent.context;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ContextPackageContractTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void should_round_trip_context_package_with_prd_contract_field_names() throws Exception {
        ContextPackage contextPackage = new ContextPackage(
                List.of("style-profile", "story-bible"),
                List.of("missing-memory"),
                List.of("story-bible:hero.identity vs user-request:hero.origin"),
                List.of("hero.identity=林烬是守夜人见习生", "world.taboo=不可直呼古神真名"),
                "第一人称、冷峻、短句推进",
                "chapter:3003"
        );

        String json = objectMapper.writeValueAsString(contextPackage);
        Map<String, Object> tree = objectMapper.readValue(json, Map.class);
        ContextPackage restored = objectMapper.readValue(json, ContextPackage.class);

        assertThat(tree).containsOnlyKeys(
                "sources",
                "missingContextFlags",
                "conflicts",
                "storyBibleEntries",
                "coreStoryBibleEntries",
                "workingSetEntries",
                "selectedStoryBibleEntries",
                "styleSnapshot",
                "chapterScope",
                "authorProfileSnapshot"
        );
        assertThat(tree.get("styleSnapshot")).isEqualTo("第一人称、冷峻、短句推进");
        assertThat(tree.get("chapterScope")).isEqualTo("chapter:3003");
        assertThat(restored).isEqualTo(contextPackage);
    }

    @Test
    void should_deserialize_raw_context_package_json_with_null_lists_and_trimmed_strings() throws Exception {
        String json = """
                {
                  "sources": null,
                  "missingContextFlags": null,
                  "conflicts": null,
                  "storyBibleEntries": null,
                  "styleSnapshot": "  第一人称、短句推进  ",
                  "chapterScope": "  chapter:3003  "
                }
                """;

        ContextPackage restored = objectMapper.readValue(json, ContextPackage.class);

        assertThat(restored.sources()).isEmpty();
        assertThat(restored.missingContextFlags()).isEmpty();
        assertThat(restored.conflicts()).isEmpty();
        assertThat(restored.storyBibleEntries()).isEmpty();
        assertThat(restored.styleSnapshot()).isEqualTo("第一人称、短句推进");
        assertThat(restored.chapterScope()).isEqualTo("chapter:3003");
        assertThat(restored.sources()).isUnmodifiable();
    }
}

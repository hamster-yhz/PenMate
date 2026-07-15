package com.penmate.backend.domain.storybible;

import com.penmate.backend.domain.storybible.model.StoryBibleCanonStatus;
import com.penmate.backend.domain.storybible.model.StoryBibleInclusionPolicy;
import com.penmate.backend.domain.storybible.model.StoryBibleSemanticFamily;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StoryBibleDomainTest {

    @Test
    void should_keep_stable_semantic_families() {
        assertThat(StoryBibleSemanticFamily.values())
                .extracting(Enum::name)
                .containsExactly("CORE", "CHARACTER", "WORLD", "THING", "NARRATIVE", "TIMELINE");
    }

    @Test
    void should_not_model_agent_proposals_as_current_node_status() {
        assertThat(StoryBibleCanonStatus.values())
                .extracting(Enum::name)
                .containsExactly("DRAFT", "CANON", "ARCHIVED")
                .doesNotContain("PROPOSED");
    }

    @Test
    void should_define_the_three_inclusion_policies() {
        assertThat(StoryBibleInclusionPolicy.values())
                .extracting(Enum::name)
                .containsExactly("ALWAYS_INCLUDE", "AUTO_RETRIEVE", "MANUAL_ONLY");
    }
}

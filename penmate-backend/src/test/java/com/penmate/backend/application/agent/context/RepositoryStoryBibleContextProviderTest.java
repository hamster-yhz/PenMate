package com.penmate.backend.application.agent.context;

import com.penmate.backend.application.agent.orchestration.preflight.AgentBehaviorType;
import com.penmate.backend.application.agent.orchestration.preflight.AgentPreflightDecision;
import com.penmate.backend.domain.storybible.model.StoryBibleEntry;
import com.penmate.backend.domain.storybible.model.StoryBibleSourceRef;
import com.penmate.backend.domain.storybible.repository.StoryBibleRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RepositoryStoryBibleContextProviderTest {

    @Test
    void should_load_story_bible_context_from_repository_when_preflight_enables_it() {
        StoryBibleRepository repository = mock(StoryBibleRepository.class);
        RepositoryStoryBibleContextProvider provider = new RepositoryStoryBibleContextProvider(repository);
        AgentPreflightDecision decision = new AgentPreflightDecision(
                AgentBehaviorType.STORY_BIBLE_QUERY_CANDIDATE,
                "default",
                false,
                false,
                true,
                "需要故事圣经上下文",
                "{\"includeStoryBibleContext\":true}"
        );
        StoryBibleEntry entry = new StoryBibleEntry();
        entry.setEntryKey("hero.identity");
        entry.setTitle("主角身份");
        entry.setContent("林烬是守夜人见习生");
        entry.setEntryType("character");
        entry.setCanonicalStatus("CANON");
        entry.setVersionNo(1);
        StoryBibleSourceRef ref = new StoryBibleSourceRef();
        ref.setRefType("chapter");
        ref.setRefId(920001L);
        ref.setNote("chapter-1");
        entry.setSourceRefs(List.of(ref));
        when(repository.findActiveEntries(1001L, 3003L)).thenReturn(List.of(entry));

        List<StoryBibleContextEntryView> result = provider.loadContext(1001L, 2002L, 3003L, "检查角色设定", decision);

        verify(repository).findActiveEntries(1001L, 3003L);
        assertThat(result)
                .singleElement()
                .satisfies(view -> {
                    assertThat(view.source()).isEqualTo("repository");
                    assertThat(view.entryKey()).isEqualTo("hero.identity");
                    assertThat(view.title()).isEqualTo("主角身份");
                    assertThat(view.content()).isEqualTo("林烬是守夜人见习生");
                    assertThat(view.entryType()).isEqualTo("character");
                    assertThat(view.canonicalStatus()).isEqualTo("CANON");
                    assertThat(view.versionNo()).isEqualTo(1);
                });
    }
}

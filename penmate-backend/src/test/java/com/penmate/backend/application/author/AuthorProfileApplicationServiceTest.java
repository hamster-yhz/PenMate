package com.penmate.backend.application.author;

import com.penmate.backend.domain.author.model.AuthorProfile;
import com.penmate.backend.domain.author.repository.AuthorProfileRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthorProfileApplicationServiceTest {
    private final AuthorProfileRepository repository = mock(AuthorProfileRepository.class);
    private final AuthorProfileApplicationService service = new AuthorProfileApplicationService(repository);

    @Test
    void returns_lightweight_defaults_without_persisting_them() {
        AuthorProfile profile = service.get(1001L);

        assertThat(profile.getUserId()).isEqualTo(1001L);
        assertThat(profile.getDefaultLanguage()).isEqualTo("zh-CN");
        assertThat(profile.getDefaultPov()).isEqualTo("PROJECT_DEFAULT");
        assertThat(profile.getLongTermMemory()).isEmpty();
    }

    @Test
    void validates_and_saves_only_explicit_author_preferences() {
        AuthorProfile candidate = profile();
        when(repository.upsert(org.mockito.ArgumentMatchers.any())).thenReturn(1);
        when(repository.findByUserId(1001L)).thenAnswer(invocation -> candidate);

        service.save(1001L, candidate);

        ArgumentCaptor<AuthorProfile> saved = ArgumentCaptor.forClass(AuthorProfile.class);
        verify(repository).upsert(saved.capture());
        assertThat(saved.getValue().getUserId()).isEqualTo(1001L);
        assertThat(saved.getValue().getLongTermMemory()).isEqualTo("所有项目都避免全知旁白");
    }

    @Test
    void rejects_unknown_enum_values_and_oversized_long_term_memory() {
        AuthorProfile invalidPov = profile();
        invalidPov.setDefaultPov("SECOND_PERSON");
        assertThatThrownBy(() -> service.save(1001L, invalidPov)).hasMessage("defaultPov is invalid");

        AuthorProfile oversized = profile();
        oversized.setLongTermMemory("x".repeat(5001));
        assertThatThrownBy(() -> service.save(1001L, oversized)).hasMessage("longTermMemory is too long");
    }

    @Test
    void renders_profile_as_preference_snapshot_without_project_fields() {
        AuthorProfile stored = profile();
        when(repository.findByUserId(1001L)).thenReturn(stored);

        assertThat(service.promptSnapshot(1001L))
                .contains("defaultLanguage: zh-CN", "longTermMemory: 所有项目都避免全知旁白")
                .doesNotContain("projectId", "storyBible");
    }

    private AuthorProfile profile() {
        AuthorProfile value = new AuthorProfile();
        value.setUserId(1001L);
        value.setDefaultLanguage("zh-CN");
        value.setCollaborationMode("COLLABORATIVE");
        value.setDefaultPov("PROJECT_DEFAULT");
        value.setDefaultTense("PROJECT_DEFAULT");
        value.setDescriptionDensity("MEDIUM");
        value.setDialoguePreference("对白简短");
        value.setBannedExpressions("显而易见");
        value.setLongTermMemory("所有项目都避免全知旁白");
        return value;
    }
}

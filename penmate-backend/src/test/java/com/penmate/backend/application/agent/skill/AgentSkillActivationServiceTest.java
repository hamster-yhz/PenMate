package com.penmate.backend.application.agent.skill;

import com.penmate.backend.application.agent.prompt.LoadedSkill;
import com.penmate.backend.application.agent.prompt.SkillCatalogItem;
import com.penmate.backend.application.agent.prompt.SkillPromptRegistry;
import com.penmate.backend.application.agent.prompt.SystemPromptDocument;
import com.penmate.backend.domain.agent.run.model.AgentRun;
import com.penmate.backend.domain.agent.run.repository.AgentRunRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentSkillActivationServiceTest {

    private final SkillPromptRegistry registry = mock(SkillPromptRegistry.class);
    private final AgentSkillBindingRepository bindings = mock(AgentSkillBindingRepository.class);
    private final AgentRunRepository runs = mock(AgentRunRepository.class);
    private final AgentSkillActivationService service = new AgentSkillActivationService(registry, bindings, runs);

    @Test
    void activates_current_registry_version_and_persists_one_run_binding() {
        LoadedSkill writer = skill("writer", "hash-writer", "Write vivid prose");
        when(runs.findRunForUpdate(10L)).thenReturn(mock(AgentRun.class));
        when(bindings.countRunBindings(10L)).thenReturn(1);
        when(registry.load("writer")).thenReturn(writer);
        when(bindings.findRunBinding(10L, "writer"))
                .thenReturn(null, binding("writer", "hash-writer", "AUTO", "Write vivid prose"));

        var result = service.activateAutomatically(10L, "writer", "call-1");

        assertThat(result.status()).isEqualTo("ACTIVATED");
        assertThat(result.binding().content()).isEqualTo("Write vivid prose");
        verify(bindings).saveSnapshot("hash-writer", "Write vivid prose");
        verify(bindings).insertRunBinding(10L, "writer", "hash-writer", "AUTO", "call-1");
    }

    @Test
    void repeated_activation_is_idempotent_and_does_not_return_body_again() {
        when(runs.findRunForUpdate(10L)).thenReturn(mock(AgentRun.class));
        when(bindings.findRunBinding(10L, "writer"))
                .thenReturn(binding("writer", "hash-writer", "EXPLICIT", "Write vivid prose"));

        var result = service.activateAutomatically(10L, "writer", "call-2");

        assertThat(result.status()).isEqualTo("ALREADY_ACTIVE");
        verify(registry, never()).load("writer");
        verify(bindings, never()).insertRunBinding(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void fifth_unique_skill_fails_only_the_activation() {
        when(runs.findRunForUpdate(10L)).thenReturn(mock(AgentRun.class));
        when(bindings.countRunBindings(10L)).thenReturn(4);

        assertThatThrownBy(() -> service.activateAutomatically(10L, "writer", "call-5"))
                .isInstanceOfSatisfying(AgentSkillActivationService.SkillActivationFailure.class,
                        failure -> assertThat(failure.errorCode()).isEqualTo("SKILL_LIMIT_REACHED"));
        verify(registry, never()).load("writer");
    }

    @Test
    void renders_explicit_skill_bodies_in_canonical_order() {
        when(bindings.listRunBindings(10L)).thenReturn(List.of(
                binding("writer", "h2", "EXPLICIT", "Writer instructions"),
                binding("checker", "h1", "EXPLICIT", "Checker instructions"),
                binding("planner", "h3", "AUTO", "Planner instructions")));

        String prompt = service.renderExplicitSkills(10L);

        assertThat(prompt).contains("do not call skill_load for these names");
        assertThat(prompt.indexOf("name=\"checker\""))
                .isLessThan(prompt.indexOf("name=\"writer\""));
        assertThat(prompt).doesNotContain("Planner instructions");
    }

    private LoadedSkill skill(String name, String hash, String content) {
        return new LoadedSkill(
                new SkillCatalogItem(name, name + " description", hash),
                new SystemPromptDocument("SKILL.md", "skills/" + name + "/SKILL.md", content));
    }

    private AgentRunSkillBinding binding(String name, String hash, String source, String content) {
        return new AgentRunSkillBinding(10L, name, hash, source, null, content, null);
    }
}

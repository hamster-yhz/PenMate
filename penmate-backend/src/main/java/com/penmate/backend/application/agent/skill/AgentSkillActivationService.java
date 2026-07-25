package com.penmate.backend.application.agent.skill;

import com.penmate.backend.application.agent.prompt.LoadedSkill;
import com.penmate.backend.application.agent.prompt.SkillPromptRegistry;
import com.penmate.backend.application.common.exception.BusinessErrorType;
import com.penmate.backend.application.common.exception.BusinessException;
import com.penmate.backend.domain.agent.run.repository.AgentRunRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

@Service
public class AgentSkillActivationService {

    public static final int MAX_ACTIVE_SKILLS = 4;

    private final SkillPromptRegistry registry;
    private final AgentSkillBindingRepository bindings;
    private final AgentRunRepository runs;

    public AgentSkillActivationService(SkillPromptRegistry registry,
                                       AgentSkillBindingRepository bindings,
                                       AgentRunRepository runs) {
        this.registry = registry;
        this.bindings = bindings;
        this.runs = runs;
    }

    @Transactional
    public List<String> replaceSessionSkills(Long sessionId, List<String> requestedSkills) {
        List<String> names = validateExplicitSkills(requestedSkills);
        bindings.replaceSessionSkillNames(sessionId, names);
        return names;
    }

    public List<String> listSessionSkills(Long sessionId) {
        if (sessionId == null) return List.of();
        return bindings.listSessionSkillNames(sessionId);
    }

    @Transactional
    public void bindSessionSkillsToRun(Long sessionId, Long runId) {
        List<String> names = bindings.listSessionSkillNames(sessionId);
        if (names.size() > MAX_ACTIVE_SKILLS) {
            throw skillLimitReached();
        }
        for (String name : names) {
            LoadedSkill loaded = loadExplicit(name);
            persistBinding(runId, loaded, "EXPLICIT", null);
        }
    }

    @Transactional
    public String renderExplicitSkills(Long runId) {
        List<AgentRunSkillBinding> explicit = bindings.listRunBindings(runId).stream()
                .filter(binding -> "EXPLICIT".equals(binding.activationSource()))
                .sorted(Comparator.comparing(AgentRunSkillBinding::skillName))
                .map(this::requireSnapshot)
                .toList();
        if (explicit.isEmpty()) return "";

        StringBuilder prompt = new StringBuilder();
        prompt.append("The user explicitly activated the following Skills for this Run. ")
                .append("Their instructions are already loaded; follow them and do not call skill_load for these names.\n\n");
        for (AgentRunSkillBinding binding : explicit) {
            prompt.append("<activated_skill name=\"")
                    .append(binding.skillName())
                    .append("\">\n")
                    .append(binding.content().trim())
                    .append("\n</activated_skill>\n\n");
        }
        return prompt.toString().trim();
    }

    @Transactional
    public AutoActivation activateAutomatically(Long runId, String requestedSkill, String toolCallId) {
        String name = requireCanonicalName(requestedSkill);
        if (runs.findRunForUpdate(runId) == null) {
            throw new SkillActivationFailure("SKILL_RUN_NOT_FOUND", "Agent Run not found: " + runId);
        }

        AgentRunSkillBinding existing = bindings.findRunBinding(runId, name);
        if (existing != null) {
            AgentRunSkillBinding resolved = requireSnapshot(existing);
            return new AutoActivation("ALREADY_ACTIVE", resolved, null);
        }
        if (bindings.countRunBindings(runId) >= MAX_ACTIVE_SKILLS) {
            throw new SkillActivationFailure("SKILL_LIMIT_REACHED",
                    "A Run can activate at most " + MAX_ACTIVE_SKILLS + " Skills");
        }

        LoadedSkill loaded;
        try {
            loaded = registry.load(name);
        } catch (IllegalArgumentException ex) {
            throw new SkillActivationFailure("SKILL_NOT_FOUND", ex.getMessage());
        }
        persistBinding(runId, loaded, "AUTO", toolCallId);
        AgentRunSkillBinding created = bindings.findRunBinding(runId, loaded.descriptor().name());
        if (created == null) {
            throw new IllegalStateException("Created Run Skill binding is unavailable");
        }
        return new AutoActivation("ACTIVATED", requireSnapshot(created), loaded.instructions().path());
    }

    private List<String> validateExplicitSkills(List<String> requestedSkills) {
        if (requestedSkills == null) {
            throw BusinessException.of(BusinessErrorType.INVALID_REQUEST, "ACTIVE_SKILLS_REQUIRED",
                    "activeSkills must be provided", null);
        }
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        for (String requested : requestedSkills) {
            String name;
            try {
                name = requireCanonicalName(requested);
                registry.load(name);
            } catch (IllegalArgumentException ex) {
                throw BusinessException.of(BusinessErrorType.INVALID_REQUEST, "SKILL_NOT_FOUND",
                        ex.getMessage(), null);
            }
            unique.add(name);
        }
        if (unique.size() > MAX_ACTIVE_SKILLS) throw skillLimitReached();
        return unique.stream().sorted().toList();
    }

    private LoadedSkill loadExplicit(String name) {
        try {
            return registry.load(name);
        } catch (IllegalArgumentException ex) {
            throw BusinessException.of(BusinessErrorType.INVALID_REQUEST, "SKILL_NOT_FOUND",
                    ex.getMessage(), null);
        }
    }

    private void persistBinding(Long runId, LoadedSkill loaded, String source, String toolCallId) {
        bindings.saveSnapshot(loaded.descriptor().contentHash(), loaded.instructions().content());
        bindings.insertRunBinding(runId, loaded.descriptor().name(), loaded.descriptor().contentHash(),
                source, toolCallId);
    }

    private AgentRunSkillBinding requireSnapshot(AgentRunSkillBinding binding) {
        if (binding.content() != null && !binding.content().isBlank()) return binding;
        LoadedSkill current;
        try {
            current = registry.load(binding.skillName());
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("Skill snapshot is unavailable for " + binding.skillName());
        }
        if (!Objects.equals(current.descriptor().contentHash(), binding.contentHash())) {
            throw new IllegalStateException("Skill snapshot version is unavailable for " + binding.skillName());
        }
        bindings.saveSnapshot(binding.contentHash(), current.instructions().content());
        return new AgentRunSkillBinding(binding.runId(), binding.skillName(), binding.contentHash(),
                binding.activationSource(), binding.toolCallId(), current.instructions().content(),
                binding.activatedAt());
    }

    private String requireCanonicalName(String skill) {
        if (skill == null || skill.isBlank()) {
            throw new IllegalArgumentException("skill must not be blank");
        }
        String name = skill.trim();
        if (!name.matches("[a-z0-9]+(?:-[a-z0-9]+)*") || name.length() > 64) {
            throw new IllegalArgumentException("Invalid canonical Skill name: " + skill);
        }
        return name;
    }

    private BusinessException skillLimitReached() {
        return BusinessException.of(BusinessErrorType.INVALID_REQUEST, "SKILL_LIMIT_REACHED",
                "At most " + MAX_ACTIVE_SKILLS + " Skills can be active", null);
    }

    public record AutoActivation(String status, AgentRunSkillBinding binding, String path) {
    }

    public static class SkillActivationFailure extends RuntimeException {
        private final String errorCode;

        public SkillActivationFailure(String errorCode, String message) {
            super(message == null || message.isBlank() ? "Skill activation failed" : message);
            this.errorCode = errorCode;
        }

        public String errorCode() {
            return errorCode;
        }
    }
}

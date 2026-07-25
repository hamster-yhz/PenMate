package com.penmate.backend.application.agent.context;

import com.penmate.backend.application.agent.prompt.SkillPromptRegistry;
import com.penmate.backend.application.agent.prompt.SystemPromptProvider;
import com.penmate.backend.application.agent.orchestration.profile.TaskProfile;
import com.penmate.backend.application.agent.tool.selection.AgentToolSelectionPolicy;
import com.penmate.backend.application.common.exception.BusinessException;
import com.penmate.backend.application.common.serialization.JsonCodec;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.HexFormat;

@Service
public class AgentContextCatalogHashService {
    private final SystemPromptProvider prompts;
    private final SkillPromptRegistry skills;
    private final AgentToolSelectionPolicy toolSelectionPolicy;
    private final JsonCodec jsonCodec;

    public AgentContextCatalogHashService(SystemPromptProvider prompts, SkillPromptRegistry skills,
                                          AgentToolSelectionPolicy toolSelectionPolicy, JsonCodec jsonCodec) {
        this.prompts = prompts;
        this.skills = skills;
        this.toolSelectionPolicy = toolSelectionPolicy;
        this.jsonCodec = jsonCodec;
    }

    public Hashes hashes(TaskProfile profile) {
        String executionProfile = profile == null ? "default" : profile.executionProfile();
        var prompt = new PromptBundles(
                prompts.loadBundle("execution", executionProfile),
                prompts.loadBundle("context-selector", "default")
        );
        var skillCatalog = skills.listAvailableSkills().stream()
                .map(item -> new SkillCatalogMetadata(item.name(), item.description()))
                .sorted(Comparator.comparing(item -> item.name() == null ? "" : item.name())).toList();
        var toolCatalog = toolSelectionPolicy.select(profile).stream()
                .sorted(Comparator.comparing(schema -> schema.toolCode() == null ? "" : schema.toolCode())).toList();
        return new Hashes(hash(prompt), hash(skillCatalog), hash(toolCatalog));
    }

    public Hashes hashes(String executionProfile) {
        return hashes(TaskProfile.fromTaskType(executionProfile));
    }

    private String hash(Object value) {
        try {
            byte[] encoded = jsonCodec.write(value).getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(encoded));
        } catch (RuntimeException ex) {
            throw BusinessException.of("Failed to hash Agent context catalog");
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    public record Hashes(String promptBundleHash, String skillCatalogHash, String toolCatalogHash) {
    }

    private record PromptBundles(Object execution, Object selector) {
    }

    private record SkillCatalogMetadata(String name, String description) {
    }
}

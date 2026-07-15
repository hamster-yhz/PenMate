package com.penmate.backend.application.agent.context;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.penmate.backend.application.agent.prompt.SkillPromptRegistry;
import com.penmate.backend.application.agent.prompt.SystemPromptProvider;
import com.penmate.backend.application.agent.tool.definition.AgentToolDefinitionSource;
import com.penmate.backend.application.common.exception.BusinessException;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;

@Service
public class AgentContextCatalogHashService {
    private final SystemPromptProvider prompts;
    private final SkillPromptRegistry skills;
    private final AgentToolDefinitionSource tools;
    private final ObjectMapper objectMapper;

    public AgentContextCatalogHashService(SystemPromptProvider prompts, SkillPromptRegistry skills,
                                          AgentToolDefinitionSource tools, ObjectMapper objectMapper) {
        this.prompts = prompts;
        this.skills = skills;
        this.tools = tools;
        this.objectMapper = objectMapper;
    }

    public Hashes hashes(String executionProfile) {
        var prompt = prompts.loadBundle("execution", executionProfile);
        var skillCatalog = skills.listAvailableSkills().stream()
                .sorted(Comparator.comparing(item -> item.name() == null ? "" : item.name())).toList();
        var toolCatalog = tools.listLlmSchemas().stream()
                .sorted(Comparator.comparing(schema -> schema.toolCode() == null ? "" : schema.toolCode())).toList();
        return new Hashes(hash(prompt), hash(skillCatalog), hash(toolCatalog));
    }

    private String hash(Object value) {
        try {
            byte[] encoded = objectMapper.writeValueAsBytes(value);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(encoded));
        } catch (JsonProcessingException ex) {
            throw BusinessException.of("Failed to hash Agent context catalog");
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    public record Hashes(String promptBundleHash, String skillCatalogHash, String toolCatalogHash) {
    }
}

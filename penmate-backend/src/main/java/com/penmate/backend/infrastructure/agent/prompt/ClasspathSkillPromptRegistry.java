package com.penmate.backend.infrastructure.agent.prompt;

import com.penmate.backend.application.agent.prompt.LoadedSkill;
import com.penmate.backend.application.agent.prompt.SkillCatalogItem;
import com.penmate.backend.application.agent.prompt.SkillPromptRegistry;
import com.penmate.backend.application.agent.prompt.SystemPromptDocument;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Component
public class ClasspathSkillPromptRegistry implements SkillPromptRegistry {

    private static final String SKILL_ROOT = "prompts/agent/system/skills/";
    private static final String SKILL_PATTERN = "classpath*:" + SKILL_ROOT + "*/SKILL.md";
    private static final Pattern SKILL_NAME = Pattern.compile("[a-z0-9]+(?:-[a-z0-9]+)*");
    private static final Set<String> FRONTMATTER_FIELDS = Set.of("name", "description");
    private final Map<String, LoadedSkill> skills;
    private final List<SkillCatalogItem> catalog;

    public ClasspathSkillPromptRegistry() {
        this(new PathMatchingResourcePatternResolver());
    }

    ClasspathSkillPromptRegistry(ResourcePatternResolver resources) {
        this.skills = discover(resources);
        this.catalog = skills.values().stream()
                .map(LoadedSkill::descriptor)
                .sorted(Comparator.comparing(SkillCatalogItem::name))
                .toList();
    }

    @Override
    public List<SkillCatalogItem> listAvailableSkills() {
        return catalog;
    }

    @Override
    public LoadedSkill load(String skill) {
        String requested = requireSkillName(skill);
        LoadedSkill loaded = skills.get(requested);
        if (loaded == null) {
            throw new IllegalArgumentException("Skill not found: " + skill);
        }
        return loaded;
    }

    private Map<String, LoadedSkill> discover(ResourcePatternResolver resources) {
        try {
            Resource[] candidates = resources.getResources(SKILL_PATTERN);
            if (candidates.length == 0) {
                throw new IllegalStateException("No skills found under classpath: " + SKILL_ROOT);
            }
            Map<String, LoadedSkill> discovered = new LinkedHashMap<>();
            for (Resource resource : candidates) {
                LoadedSkill skill = parse(resource);
                LoadedSkill duplicate = discovered.putIfAbsent(skill.descriptor().name(), skill);
                if (duplicate != null) {
                    throw new IllegalStateException("Duplicated skill name: " + skill.descriptor().name());
                }
            }
            return Map.copyOf(discovered);
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to discover classpath skills", ex);
        }
    }

    private LoadedSkill parse(Resource resource) {
        try {
            String source = normalizeLineEndings(resource.getContentAsString(StandardCharsets.UTF_8)).trim();
            FrontmatterDocument document = splitFrontmatter(source, resource.getDescription());
            Map<String, Object> metadata = readFrontmatter(document.frontmatter(), resource.getDescription());
            String name = requiredString(metadata, "name", resource.getDescription());
            String description = requiredString(metadata, "description", resource.getDescription());
            validateName(name, resource.getDescription());
            String path = classpathPath(resource);
            validateDirectoryName(path, name);
            String contentHash = sha256(source);
            SkillCatalogItem descriptor = new SkillCatalogItem(name, description, contentHash);
            SystemPromptDocument instructions = new SystemPromptDocument("SKILL.md", path, document.body());
            return new LoadedSkill(descriptor, instructions);
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to read skill: " + resource.getDescription(), ex);
        }
    }

    private FrontmatterDocument splitFrontmatter(String source, String resource) {
        if (!source.startsWith("---\n")) {
            throw new IllegalStateException("Skill frontmatter is missing: " + resource);
        }
        int end = source.indexOf("\n---\n", 4);
        if (end < 0) {
            throw new IllegalStateException("Skill frontmatter is not closed: " + resource);
        }
        String body = source.substring(end + 5).trim();
        if (body.isEmpty()) {
            throw new IllegalStateException("Skill instructions must not be blank: " + resource);
        }
        return new FrontmatterDocument(source.substring(4, end), body);
    }

    private Map<String, Object> readFrontmatter(String source, String resource) {
        Object value = new Yaml(new SafeConstructor(new LoaderOptions())).load(source);
        if (!(value instanceof Map<?, ?> raw)) {
            throw new IllegalStateException("Skill frontmatter must be a YAML object: " + resource);
        }
        Map<String, Object> metadata = new LinkedHashMap<>();
        raw.forEach((key, fieldValue) -> metadata.put(String.valueOf(key), fieldValue));
        List<String> unsupported = new ArrayList<>(metadata.keySet());
        unsupported.removeAll(FRONTMATTER_FIELDS);
        if (!unsupported.isEmpty()) {
            throw new IllegalStateException("Unsupported skill frontmatter fields " + unsupported + ": " + resource);
        }
        return metadata;
    }

    private String requiredString(Map<String, Object> metadata, String field, String resource) {
        Object value = metadata.get(field);
        String normalized = value == null ? "" : String.valueOf(value).trim();
        if (normalized.isEmpty()) {
            throw new IllegalStateException("Skill " + field + " must not be blank: " + resource);
        }
        return normalized;
    }

    private void validateName(String name, String resource) {
        if (name.length() > 64 || !SKILL_NAME.matcher(name).matches()) {
            throw new IllegalStateException("Invalid skill name '" + name + "': " + resource);
        }
    }

    private void validateDirectoryName(String path, String name) {
        String relative = path.substring(SKILL_ROOT.length());
        String directory = relative.substring(0, relative.indexOf('/'));
        if (!directory.equals(name)) {
            throw new IllegalStateException("Skill directory '" + directory + "' must match name '" + name + "'");
        }
    }

    private String classpathPath(Resource resource) throws IOException {
        String location = resource.getURL().toExternalForm().replace('\\', '/');
        int root = location.indexOf(SKILL_ROOT);
        if (root < 0) {
            throw new IllegalStateException("Skill is outside expected classpath root: " + resource.getDescription());
        }
        return location.substring(root);
    }

    private String requireSkillName(String skill) {
        if (skill == null || skill.isBlank()) {
            throw new IllegalArgumentException("skill must not be blank");
        }
        String name = skill.trim();
        if (!SKILL_NAME.matcher(name).matches()) {
            throw new IllegalArgumentException("Invalid skill name: " + skill);
        }
        return name;
    }

    private String normalizeLineEndings(String value) {
        return value.replace("\r\n", "\n").replace('\r', '\n');
    }

    private String sha256(String value) {
        try {
            byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    private record FrontmatterDocument(String frontmatter, String body) {
    }
}

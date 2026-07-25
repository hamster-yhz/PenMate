package com.penmate.backend.application.author;

import com.penmate.backend.application.common.exception.BusinessException;
import com.penmate.backend.domain.author.model.AuthorProfile;
import com.penmate.backend.domain.author.repository.AuthorProfileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Service
public class AuthorProfileApplicationService {
    private static final Set<String> COLLABORATION = Set.of("DIRECT", "COLLABORATIVE", "EXPLORATORY");
    private static final Set<String> POV = Set.of("PROJECT_DEFAULT", "FIRST_PERSON", "THIRD_LIMITED", "THIRD_OMNISCIENT");
    private static final Set<String> TENSE = Set.of("PROJECT_DEFAULT", "PAST", "PRESENT");
    private static final Set<String> DENSITY = Set.of("LIGHT", "MEDIUM", "RICH");

    private final AuthorProfileRepository profiles;

    public AuthorProfileApplicationService(AuthorProfileRepository profiles) {
        this.profiles = profiles;
    }

    public AuthorProfile get(Long userId) {
        AuthorProfile stored = profiles.findByUserId(userId);
        return stored == null ? defaults(userId) : stored;
    }

    @Transactional
    public AuthorProfile save(Long userId, AuthorProfile candidate) {
        AuthorProfile profile = new AuthorProfile();
        profile.setUserId(userId);
        profile.setDefaultLanguage(required(candidate.getDefaultLanguage(), "defaultLanguage", 32));
        profile.setCollaborationMode(member(candidate.getCollaborationMode(), "collaborationMode", COLLABORATION));
        profile.setDefaultPov(member(candidate.getDefaultPov(), "defaultPov", POV));
        profile.setDefaultTense(member(candidate.getDefaultTense(), "defaultTense", TENSE));
        profile.setDescriptionDensity(member(candidate.getDescriptionDensity(), "descriptionDensity", DENSITY));
        profile.setDialoguePreference(optional(candidate.getDialoguePreference(), "dialoguePreference", 1000));
        profile.setBannedExpressions(optional(candidate.getBannedExpressions(), "bannedExpressions", 2000));
        profile.setLongTermMemory(optional(candidate.getLongTermMemory(), "longTermMemory", 5000));
        if (profiles.upsert(profile) != 1) {
            throw BusinessException.of("Author profile update failed");
        }
        return get(userId);
    }

    public String promptSnapshot(Long userId) {
        AuthorProfile value = get(userId);
        return """
                defaultLanguage: %s
                collaborationMode: %s
                defaultPov: %s
                defaultTense: %s
                descriptionDensity: %s
                dialoguePreference: %s
                bannedExpressions: %s
                longTermMemory: %s
                """.formatted(
                value.getDefaultLanguage(), value.getCollaborationMode(), value.getDefaultPov(),
                value.getDefaultTense(), value.getDescriptionDensity(), value.getDialoguePreference(),
                value.getBannedExpressions(), value.getLongTermMemory()).trim();
    }

    private AuthorProfile defaults(Long userId) {
        AuthorProfile profile = new AuthorProfile();
        profile.setUserId(userId);
        profile.setDefaultLanguage("zh-CN");
        profile.setCollaborationMode("COLLABORATIVE");
        profile.setDefaultPov("PROJECT_DEFAULT");
        profile.setDefaultTense("PROJECT_DEFAULT");
        profile.setDescriptionDensity("MEDIUM");
        profile.setDialoguePreference("");
        profile.setBannedExpressions("");
        profile.setLongTermMemory("");
        return profile;
    }

    private String member(String value, String field, Set<String> allowed) {
        String normalized = required(value, field, 32).toUpperCase();
        if (!allowed.contains(normalized)) {
            throw BusinessException.badRequest(field + " is invalid");
        }
        return normalized;
    }

    private String required(String value, String field, int max) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) throw BusinessException.badRequest(field + " is required");
        if (normalized.length() > max) throw BusinessException.badRequest(field + " is too long");
        return normalized;
    }

    private String optional(String value, String field, int max) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.length() > max) throw BusinessException.badRequest(field + " is too long");
        return normalized;
    }
}

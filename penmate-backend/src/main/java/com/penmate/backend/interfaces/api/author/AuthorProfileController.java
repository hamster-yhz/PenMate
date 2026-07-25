package com.penmate.backend.interfaces.api.author;

import com.penmate.backend.application.author.AuthorProfileApplicationService;
import com.penmate.backend.domain.author.model.AuthorProfile;
import com.penmate.backend.interfaces.api.author.dto.UpdateAuthorProfileDto;
import com.penmate.backend.interfaces.api.common.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.penmate.backend.interfaces.api.common.AuthenticatedActor.id;

@RestController
@RequestMapping("/api/v1/author-profile")
public class AuthorProfileController {
    private final AuthorProfileApplicationService profiles;

    public AuthorProfileController(AuthorProfileApplicationService profiles) {
        this.profiles = profiles;
    }

    @GetMapping
    public ApiResponse<AuthorProfile> get(Authentication authentication,
                                          @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return ApiResponse.success(profiles.get(id(authentication)), traceId);
    }

    @PutMapping
    public ApiResponse<AuthorProfile> save(Authentication authentication,
                                           @Valid @RequestBody UpdateAuthorProfileDto dto,
                                           @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        AuthorProfile candidate = new AuthorProfile();
        candidate.setDefaultLanguage(dto.defaultLanguage());
        candidate.setCollaborationMode(dto.collaborationMode());
        candidate.setDefaultPov(dto.defaultPov());
        candidate.setDefaultTense(dto.defaultTense());
        candidate.setDescriptionDensity(dto.descriptionDensity());
        candidate.setDialoguePreference(dto.dialoguePreference());
        candidate.setBannedExpressions(dto.bannedExpressions());
        candidate.setLongTermMemory(dto.longTermMemory());
        return ApiResponse.success(profiles.save(id(authentication), candidate), traceId);
    }
}

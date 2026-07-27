package com.penmate.backend.interfaces.api.agent;

import com.penmate.backend.application.agent.safety.AgentSafetyModeApplicationService;
import com.penmate.backend.interfaces.api.common.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/agent/preferences/safety")
@RequiredArgsConstructor
public class AgentSafetyPreferenceController {
    private final AgentSafetyModeApplicationService safetyModes;

    @GetMapping
    public ApiResponse<SafetyModeDto> get(Authentication authentication,
                                           @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        var mode = safetyModes.get(user(authentication));
        return ApiResponse.success(new SafetyModeDto(mode.name(), mode.maximumAutomaticRisk()), traceId);
    }

    @PutMapping
    public ApiResponse<SafetyModeDto> save(@Valid @RequestBody SaveSafetyModeDto dto, Authentication authentication,
                                            @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        var mode = safetyModes.save(user(authentication), dto.mode());
        return ApiResponse.success(new SafetyModeDto(mode.name(), mode.maximumAutomaticRisk()), traceId);
    }

    private Long user(Authentication authentication) {
        if (authentication == null || authentication.getName() == null || !authentication.getName().matches("^[1-9]\\d*$")) {
            throw new IllegalArgumentException("Login required");
        }
        return Long.valueOf(authentication.getName());
    }

    public record SaveSafetyModeDto(
            @NotBlank @Pattern(regexp = "STRICT|STANDARD|AUTONOMOUS|FULL_AUTHORITY") String mode) {}
    public record SafetyModeDto(String mode, int maximumAutomaticRisk) {}
}

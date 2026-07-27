package com.penmate.backend.interfaces.api.ledger;

import com.penmate.backend.application.ledger.ProjectLedgerApplicationService;
import com.penmate.backend.domain.ledger.model.ProjectLedger;
import com.penmate.backend.interfaces.api.common.ApiResponse;
import com.penmate.backend.interfaces.api.ledger.dto.CreateProjectLedgerDto;
import com.penmate.backend.interfaces.api.ledger.dto.ProjectLedgerDto;
import com.penmate.backend.interfaces.api.ledger.dto.UpdateProjectLedgerDto;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/projects/{projectId}/ledgers")
@RequiredArgsConstructor
public class ProjectLedgerController {
    private final ProjectLedgerApplicationService ledgers;

    @GetMapping
    public ApiResponse<List<ProjectLedgerDto>> list(@PathVariable String projectId,
                                                     Authentication authentication,
                                                     @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        Long project = id(projectId, "projectId");
        return ApiResponse.success(ledgers.list(project, user(authentication)).stream().map(this::metadata).toList(), traceId);
    }

    @GetMapping("/{ledgerId}")
    public ApiResponse<ProjectLedgerDto> read(@PathVariable String projectId, @PathVariable String ledgerId,
                                               @RequestParam(defaultValue = "0") int offset,
                                               @RequestParam(defaultValue = "20000") int limit,
                                               Authentication authentication,
                                               @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        var slice = ledgers.read(id(projectId, "projectId"), id(ledgerId, "ledgerId"), offset, limit,
                user(authentication));
        return ApiResponse.success(slice(slice), traceId);
    }

    @PostMapping
    public ApiResponse<ProjectLedgerDto> create(@PathVariable String projectId,
                                                 @Valid @RequestBody CreateProjectLedgerDto dto,
                                                 Authentication authentication,
                                                 @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        ProjectLedger ledger = ledgers.create(id(projectId, "projectId"), dto.title(), dto.content(), user(authentication));
        return ApiResponse.success(metadata(ledger), traceId);
    }

    @PutMapping("/{ledgerId}")
    public ApiResponse<ProjectLedgerDto> update(@PathVariable String projectId, @PathVariable String ledgerId,
                                                 @Valid @RequestBody UpdateProjectLedgerDto dto,
                                                 Authentication authentication,
                                                 @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        ProjectLedger ledger = ledgers.update(id(projectId, "projectId"), id(ledgerId, "ledgerId"),
                id(dto.expectedRevision(), "expectedRevision"), dto.title(), dto.start(), dto.end(), dto.replacement(),
                user(authentication));
        return ApiResponse.success(metadata(ledger), traceId);
    }

    @DeleteMapping("/{ledgerId}")
    public ApiResponse<String> delete(@PathVariable String projectId, @PathVariable String ledgerId,
                                      @RequestParam String expectedRevision, Authentication authentication,
                                      @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        ledgers.delete(id(projectId, "projectId"), id(ledgerId, "ledgerId"),
                id(expectedRevision, "expectedRevision"), user(authentication));
        return ApiResponse.success("deleted", traceId);
    }

    private ProjectLedgerDto metadata(ProjectLedger ledger) {
        return new ProjectLedgerDto(text(ledger.getLedgerId()), text(ledger.getProjectId()), ledger.getTitle(), null,
                text(ledger.getContentRevision()), activeLease(ledger) ? ledger.getLeaseOwnerType() : null,
                activeLease(ledger) ? text(ledger.getLeaseOwnerId()) : null,
                activeLease(ledger) ? instant(ledger.getLeaseExpiresAt()) : null,
                instant(ledger.getCreatedAt()), instant(ledger.getUpdatedAt()),
                null, null, null, null);
    }

    private ProjectLedgerDto slice(ProjectLedgerApplicationService.LedgerSlice value) {
        ProjectLedger ledger = value.ledger();
        return new ProjectLedgerDto(text(ledger.getLedgerId()), text(ledger.getProjectId()), ledger.getTitle(), value.content(),
                text(ledger.getContentRevision()), activeLease(ledger) ? ledger.getLeaseOwnerType() : null,
                activeLease(ledger) ? text(ledger.getLeaseOwnerId()) : null,
                activeLease(ledger) ? instant(ledger.getLeaseExpiresAt()) : null,
                instant(ledger.getCreatedAt()), instant(ledger.getUpdatedAt()),
                value.offset(), value.end(), value.totalCharacters(), value.complete());
    }

    private boolean activeLease(ProjectLedger ledger) {
        return ledger.getLeaseExpiresAt() != null && ledger.getLeaseExpiresAt().isAfter(java.time.Instant.now());
    }

    private Long user(Authentication authentication) {
        if (authentication == null) throw new IllegalArgumentException("Login required");
        return id(authentication.getName(), "userId");
    }
    private Long id(String value, String field) {
        if (value == null || !value.trim().matches("^[1-9]\\d*$")) {
            throw new IllegalArgumentException(field + " must be a positive numeric string");
        }
        return Long.valueOf(value.trim());
    }
    private String text(Long value) { return value == null ? null : String.valueOf(value); }
    private String instant(java.time.Instant value) { return value == null ? null : value.toString(); }
}

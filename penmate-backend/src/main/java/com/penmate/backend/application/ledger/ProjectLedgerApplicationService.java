package com.penmate.backend.application.ledger;

import com.penmate.backend.application.common.exception.BusinessException;
import com.penmate.backend.domain.ledger.model.ProjectLedger;
import com.penmate.backend.domain.ledger.repository.ProjectLedgerRepository;
import com.penmate.backend.domain.novel.model.NovelProject;
import com.penmate.backend.domain.novel.repository.NovelGateway;
import com.penmate.backend.domain.shared.service.BusinessIdGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class ProjectLedgerApplicationService {
    public static final int MAX_LEDGERS = 100;
    public static final int MAX_CONTENT_CHARACTERS = 200_000;
    public static final int MAX_DELTA_CHARACTERS = 20_000;
    public static final int MAX_TITLE_CHARACTERS = 120;

    private final ProjectLedgerRepository ledgers;
    private final NovelGateway novels;
    private final BusinessIdGenerator ids;

    @Transactional(readOnly = true)
    public List<ProjectLedger> list(Long projectId, Long userId) {
        requireProject(projectId, userId, false);
        return ledgers.listByProject(projectId);
    }

    @Transactional(readOnly = true)
    public LedgerSlice read(Long projectId, Long ledgerId, int offset, int limit, Long userId) {
        requireProject(projectId, userId, false);
        if (offset < 0) throw BusinessException.badRequest("offset must be greater than or equal to 0");
        if (limit < 1 || limit > MAX_DELTA_CHARACTERS) {
            throw BusinessException.badRequest("limit must be between 1 and " + MAX_DELTA_CHARACTERS);
        }
        ProjectLedger ledger = requireLedger(projectId, ledgerId);
        String content = value(ledger.getContent());
        int total = codePointCount(content);
        if (offset > total) throw BusinessException.badRequest("offset exceeds ledger character count");
        int end = Math.min(total, offset + limit);
        return new LedgerSlice(ledger, slice(content, offset, end), offset, end, total, end == total);
    }

    @Transactional
    public ProjectLedger create(Long projectId, String title, String content, Long userId) {
        requireProject(projectId, userId, true);
        if (ledgers.countByProject(projectId) >= MAX_LEDGERS) {
            throw BusinessException.badRequest("A project can contain at most " + MAX_LEDGERS + " ledgers");
        }
        String normalizedTitle = title(title);
        String normalizedContent = value(content);
        requireAtMost(normalizedContent, MAX_DELTA_CHARACTERS, "initial content");
        ProjectLedger ledger = new ProjectLedger();
        ledger.setLedgerId(ids.nextId());
        ledger.setProjectId(projectId);
        ledger.setTitle(normalizedTitle);
        ledger.setContent(normalizedContent);
        ledger.setContentRevision(1L);
        requireOne(ledgers.insert(ledger), "Failed to create ledger");
        return requireLedger(projectId, ledger.getLedgerId());
    }

    @Transactional
    public ProjectLedger update(Long projectId, Long ledgerId, Long expectedRevision, String title,
                                Integer start, Integer end, String replacement, Long userId) {
        requireProject(projectId, userId, false);
        if (expectedRevision == null || expectedRevision < 1) {
            throw BusinessException.badRequest("expectedRevision is required");
        }
        ProjectLedger current = requireLedger(projectId, ledgerId);
        if (!Objects.equals(expectedRevision, current.getContentRevision())) {
            throw BusinessException.conflict("Ledger changed after it was read");
        }
        String nextTitle = title == null ? current.getTitle() : title(title);
        String nextContent = value(current.getContent());
        boolean patchesContent = start != null || end != null || replacement != null;
        if (patchesContent) {
            if (start == null || end == null || replacement == null) {
                throw BusinessException.badRequest("start, end, and replacement must be provided together");
            }
            int total = codePointCount(nextContent);
            if (start < 0 || end < start || end > total) {
                throw BusinessException.badRequest("Invalid ledger content range");
            }
            int removed = end - start;
            requireAtMost(replacement, MAX_DELTA_CHARACTERS, "replacement");
            if (removed > MAX_DELTA_CHARACTERS) {
                throw BusinessException.badRequest("Removed range exceeds " + MAX_DELTA_CHARACTERS + " characters");
            }
            nextContent = slice(nextContent, 0, start) + replacement + slice(nextContent, end, total);
            requireAtMost(nextContent, MAX_CONTENT_CHARACTERS, "ledger content");
        }
        if (!patchesContent && title == null) throw BusinessException.badRequest("No ledger changes supplied");
        if (Objects.equals(nextTitle, current.getTitle()) && Objects.equals(nextContent, current.getContent())) {
            return current;
        }
        if (ledgers.update(projectId, ledgerId, expectedRevision, nextTitle, nextContent) != 1) {
            throw BusinessException.conflict("Ledger changed while the update was being saved");
        }
        return requireLedger(projectId, ledgerId);
    }

    @Transactional
    public void delete(Long projectId, Long ledgerId, Long expectedRevision, Long userId) {
        requireProject(projectId, userId, false);
        if (expectedRevision == null || expectedRevision < 1) {
            throw BusinessException.badRequest("expectedRevision is required");
        }
        requireLedger(projectId, ledgerId);
        if (ledgers.delete(projectId, ledgerId, expectedRevision) != 1) {
            throw BusinessException.conflict("Ledger changed before it could be deleted");
        }
    }

    @Transactional
    public AiLedgerLease acquireAiLease(Long projectId, Long ledgerId, Long runId, Long userId) {
        requireProject(projectId, userId, false);
        requireLedger(projectId, ledgerId);
        String token = String.valueOf(ids.nextId());
        Instant expiresAt = Instant.now().plusSeconds(30);
        if (ledgers.acquireAiLease(projectId, ledgerId, runId, token, expiresAt) != 1) {
            ProjectLedger current = requireLedger(projectId, ledgerId);
            return new AiLedgerLease(false, null, current.getContentRevision(), current.getLeaseExpiresAt(),
                    "Ledger is currently being edited");
        }
        ProjectLedger leased = requireLedger(projectId, ledgerId);
        return new AiLedgerLease(true, token, leased.getContentRevision(), expiresAt, null);
    }

    @Transactional
    public ProjectLedger updateByAgent(Long projectId, Long ledgerId, Long expectedRevision, String title,
                                       Integer start, Integer end, String replacement, String leaseToken,
                                       Long userId) {
        requireProject(projectId, userId, false);
        String requiredLeaseToken = requireLeaseToken(leaseToken);
        ProjectLedger current = requireLedger(projectId, ledgerId);
        if (!Objects.equals(expectedRevision, current.getContentRevision())) {
            throw BusinessException.conflict("Ledger changed after it was read");
        }
        String nextTitle = title == null ? current.getTitle() : title(title);
        String nextContent = value(current.getContent());
        boolean patchesContent = start != null || end != null || replacement != null;
        if (patchesContent) {
            if (start == null || end == null || replacement == null) {
                throw BusinessException.badRequest("start, end, and replacement must be provided together");
            }
            int total = codePointCount(nextContent);
            if (start < 0 || end < start || end > total) throw BusinessException.badRequest("Invalid ledger content range");
            requireAtMost(replacement, MAX_DELTA_CHARACTERS, "replacement");
            if (end - start > MAX_DELTA_CHARACTERS) {
                throw BusinessException.badRequest("Removed range exceeds " + MAX_DELTA_CHARACTERS + " characters");
            }
            nextContent = slice(nextContent, 0, start) + replacement + slice(nextContent, end, total);
            requireAtMost(nextContent, MAX_CONTENT_CHARACTERS, "ledger content");
        }
        if (!patchesContent && title == null) throw BusinessException.badRequest("No ledger changes supplied");
        if (Objects.equals(nextTitle, current.getTitle()) && Objects.equals(nextContent, current.getContent())) return current;
        if (ledgers.updateWithAiLease(projectId, ledgerId, expectedRevision, nextTitle, nextContent, requiredLeaseToken) != 1) {
            throw BusinessException.conflict("Ledger lease or revision changed while the update was being saved");
        }
        return requireLedger(projectId, ledgerId);
    }

    @Transactional
    public void deleteByAgent(Long projectId, Long ledgerId, Long expectedRevision, String leaseToken, Long userId) {
        requireProject(projectId, userId, false);
        String requiredLeaseToken = requireLeaseToken(leaseToken);
        requireLedger(projectId, ledgerId);
        if (ledgers.deleteWithAiLease(projectId, ledgerId, expectedRevision, requiredLeaseToken) != 1) {
            throw BusinessException.conflict("Ledger lease or revision changed before it could be deleted");
        }
    }

    @Transactional
    public void releaseAiLease(Long projectId, Long ledgerId, String leaseToken) {
        if (leaseToken != null) ledgers.releaseAiLease(projectId, ledgerId, leaseToken);
    }

    private NovelProject requireProject(Long projectId, Long userId, boolean lock) {
        NovelProject project = lock ? novels.lockProject(projectId) : novels.findProjectById(projectId);
        if (project == null || !Objects.equals(project.getOwnerUserId(), userId)) {
            throw BusinessException.notFound("Novel project not found");
        }
        return project;
    }

    private ProjectLedger requireLedger(Long projectId, Long ledgerId) {
        ProjectLedger ledger = ledgers.find(projectId, ledgerId);
        if (ledger == null) throw BusinessException.notFound("Ledger not found");
        return ledger;
    }

    private String title(String value) {
        String normalized = value == null ? "" : value.trim();
        int count = codePointCount(normalized);
        if (count < 1 || count > MAX_TITLE_CHARACTERS) {
            throw BusinessException.badRequest("title must contain between 1 and " + MAX_TITLE_CHARACTERS + " characters");
        }
        return normalized;
    }

    private void requireAtMost(String value, int maximum, String field) {
        if (codePointCount(value(value)) > maximum) {
            throw BusinessException.badRequest(field + " exceeds " + maximum + " characters");
        }
    }

    private int codePointCount(String value) { return value.codePointCount(0, value.length()); }
    private String value(String value) { return value == null ? "" : value; }
    private String requireLeaseToken(String leaseToken) {
        if (leaseToken == null || leaseToken.isBlank()) {
            throw BusinessException.badRequest("leaseToken is required for an Agent ledger mutation");
        }
        return leaseToken.trim();
    }
    private String slice(String value, int start, int end) {
        int startIndex = value.offsetByCodePoints(0, start);
        int endIndex = value.offsetByCodePoints(0, end);
        return value.substring(startIndex, endIndex);
    }
    private void requireOne(int affected, String message) {
        if (affected != 1) throw new IllegalStateException(message);
    }

    public record LedgerSlice(ProjectLedger ledger, String content, int offset, int end,
                              int totalCharacters, boolean complete) {
    }

    public record AiLedgerLease(boolean editable, String leaseToken, Long contentRevision,
                                Instant expiresAt, String reason) {
    }
}

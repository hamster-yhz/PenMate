package com.penmate.backend.application.agent.tool.handler;

import com.penmate.backend.application.agent.tool.runtime.AuthorizedAgentRunContext;
import com.penmate.backend.application.agent.tool.runtime.ToolCallRequest;
import com.penmate.backend.application.agent.tool.runtime.ToolCallResult;
import com.penmate.backend.application.common.serialization.JsonCodec;
import com.penmate.backend.application.common.serialization.JsonValues;
import com.penmate.backend.application.ledger.ProjectLedgerApplicationService;
import com.penmate.backend.domain.ledger.model.ProjectLedger;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class LedgerCrudToolHandler implements AgentToolHandler {
    private static final Set<String> OPERATIONS = Set.of("list", "read", "create", "update", "delete");
    private final ProjectLedgerApplicationService ledgers;
    private final JsonCodec jsonCodec;

    @Override public String toolCode() { return "ledger_crud"; }

    @Override
    public boolean mutatesState(AuthorizedAgentRunContext context, ToolCallRequest request) {
        String operation = operation(request);
        return !"list".equals(operation) && !"read".equals(operation);
    }

    @Override
    public void validate(AuthorizedAgentRunContext context, ToolCallRequest request) {
        Map<String, Object> args = args(request);
        String operation = JsonValues.string(args, "operation").toLowerCase();
        if (!OPERATIONS.contains(operation)) throw new IllegalArgumentException("Unsupported ledger operation");
        switch (operation) {
            case "list" -> only(args, Set.of("operation"));
            case "read" -> {
                only(args, Set.of("operation", "ledgerId", "offset", "limit"));
                requiredId(args, "ledgerId");
            }
            case "create" -> {
                only(args, Set.of("operation", "title", "content"));
                requiredText(args, "title");
            }
            case "update" -> {
                only(args, Set.of("operation", "ledgerId", "expectedRevision", "title", "start", "end", "replacement"));
                requiredId(args, "ledgerId");
                requiredId(args, "expectedRevision");
            }
            case "delete" -> {
                only(args, Set.of("operation", "ledgerId", "expectedRevision"));
                requiredId(args, "ledgerId");
                requiredId(args, "expectedRevision");
            }
            default -> throw new IllegalArgumentException("Unsupported ledger operation");
        }
    }

    @Override
    public ToolCallResult execute(AuthorizedAgentRunContext context, ToolCallRequest request) {
        try {
            validate(context, request);
            Map<String, Object> args = args(request);
            String operation = JsonValues.string(args, "operation").toLowerCase();
            Long ledgerId = JsonValues.longValue(args, "ledgerId");
            Object output = switch (operation) {
                case "list" -> Map.of("operation", "list", "changed", false, "items",
                        ledgers.list(context.projectId(), context.ownerUserId()).stream().map(this::metadata).toList());
                case "read" -> read(context, args, ledgerId);
                case "create" -> changed("create", ledgers.create(context.projectId(),
                        JsonValues.string(args, "title"), JsonValues.nullableString(args, "content"), context.ownerUserId()));
                case "update" -> update(context, args, ledgerId);
                case "delete" -> delete(context, args, ledgerId);
                default -> throw new IllegalArgumentException("Unsupported ledger operation");
            };
            return ToolCallResult.success(jsonCodec.write(output));
        } catch (RuntimeException exception) {
            return ToolCallResult.failed("LEDGER_CRUD_FAILED", message(exception));
        }
    }

    private Map<String, Object> read(AuthorizedAgentRunContext context, Map<String, Object> args, Long ledgerId) {
        int offset = integer(args, "offset") == null ? 0 : integer(args, "offset");
        int limit = integer(args, "limit") == null ? ProjectLedgerApplicationService.MAX_DELTA_CHARACTERS : integer(args, "limit");
        var slice = ledgers.read(context.projectId(), ledgerId, offset, limit, context.ownerUserId());
        Map<String, Object> output = metadata(slice.ledger());
        output.put("operation", "read");
        output.put("changed", false);
        output.put("content", slice.content());
        output.put("offset", slice.offset());
        output.put("end", slice.end());
        output.put("totalCharacters", slice.totalCharacters());
        output.put("isComplete", slice.complete());
        return output;
    }

    private Map<String, Object> changed(String operation, ProjectLedger ledger) {
        Map<String, Object> output = metadata(ledger);
        output.put("operation", operation);
        output.put("changed", true);
        return output;
    }

    private Map<String, Object> update(AuthorizedAgentRunContext context, Map<String, Object> args, Long ledgerId) {
        var lease = acquireLease(context, ledgerId);
        if (!lease.editable()) throw new IllegalStateException(lease.reason());
        try {
            return changed("update", ledgers.updateByAgent(context.projectId(), ledgerId,
                    JsonValues.longValue(args, "expectedRevision"), JsonValues.nullableString(args, "title"),
                    integer(args, "start"), integer(args, "end"), JsonValues.nullableString(args, "replacement"),
                    lease.leaseToken(), context.ownerUserId()));
        } finally {
            ledgers.releaseAiLease(context.projectId(), ledgerId, lease.leaseToken());
        }
    }

    private Map<String, Object> delete(AuthorizedAgentRunContext context, Map<String, Object> args, Long ledgerId) {
        var lease = acquireLease(context, ledgerId);
        if (!lease.editable()) throw new IllegalStateException(lease.reason());
        try {
            ledgers.deleteByAgent(context.projectId(), ledgerId, JsonValues.longValue(args, "expectedRevision"),
                    lease.leaseToken(), context.ownerUserId());
            return Map.of("operation", "delete", "ledgerId", String.valueOf(ledgerId), "deleted", true, "changed", true);
        } finally {
            ledgers.releaseAiLease(context.projectId(), ledgerId, lease.leaseToken());
        }
    }

    private ProjectLedgerApplicationService.AiLedgerLease acquireLease(AuthorizedAgentRunContext context, Long ledgerId) {
        ProjectLedgerApplicationService.AiLedgerLease lease = null;
        for (int attempt = 0; attempt < 20; attempt++) {
            lease = ledgers.acquireAiLease(context.projectId(), ledgerId, context.runId(), context.ownerUserId());
            if (lease.editable()) return lease;
            if (attempt < 19) {
                try { Thread.sleep(250L); }
                catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Ledger write was interrupted", exception);
                }
            }
        }
        return lease;
    }

    private Map<String, Object> metadata(ProjectLedger ledger) {
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("ledgerId", String.valueOf(ledger.getLedgerId()));
        output.put("title", ledger.getTitle());
        output.put("contentRevision", String.valueOf(ledger.getContentRevision()));
        output.put("updatedAt", ledger.getUpdatedAt() == null ? null : ledger.getUpdatedAt().toString());
        output.put("leaseOwnerType", activeLease(ledger) ? ledger.getLeaseOwnerType() : null);
        output.put("leaseOwnerId", activeLease(ledger) && ledger.getLeaseOwnerId() != null
                ? String.valueOf(ledger.getLeaseOwnerId()) : null);
        output.put("leaseExpiresAt", activeLease(ledger) && ledger.getLeaseExpiresAt() != null
                ? ledger.getLeaseExpiresAt().toString() : null);
        return output;
    }

    private boolean activeLease(ProjectLedger ledger) {
        return ledger.getLeaseExpiresAt() != null && ledger.getLeaseExpiresAt().isAfter(java.time.Instant.now());
    }

    private Map<String, Object> args(ToolCallRequest request) {
        if (request == null || request.toolArgsJson() == null) throw new IllegalArgumentException("Tool arguments are required");
        return jsonCodec.readObject(request.toolArgsJson());
    }
    private String operation(ToolCallRequest request) { return JsonValues.string(args(request), "operation").toLowerCase(); }
    private Integer integer(Map<String, Object> args, String field) {
        Object value = args.get(field);
        if (value == null) return null;
        if (value instanceof Number number) return number.intValue();
        throw new IllegalArgumentException(field + " must be an integer");
    }
    private void requiredId(Map<String, Object> args, String field) {
        Long value = JsonValues.longValue(args, field);
        if (value == null || value < 1) throw new IllegalArgumentException(field + " is required");
    }
    private void requiredText(Map<String, Object> args, String field) {
        if (JsonValues.string(args, field).isBlank()) throw new IllegalArgumentException(field + " is required");
    }
    private void only(Map<String, Object> args, Set<String> allowed) {
        args.keySet().stream().filter(key -> !allowed.contains(key)).findFirst().ifPresent(key -> {
            throw new IllegalArgumentException("Unexpected field: " + key);
        });
    }
    private String message(RuntimeException exception) {
        return exception.getMessage() == null || exception.getMessage().isBlank()
                ? "Ledger operation failed" : exception.getMessage();
    }
}

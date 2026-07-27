package com.penmate.backend.application.agent.tool.handler;

import com.penmate.backend.application.agent.run.AgentRunEventPublisher;
import com.penmate.backend.application.agent.tool.runtime.AuthorizedAgentRunContext;
import com.penmate.backend.application.agent.tool.runtime.ToolCallRequest;
import com.penmate.backend.application.agent.tool.runtime.ToolCallResult;
import com.penmate.backend.application.common.serialization.JsonCodec;
import com.penmate.backend.application.common.serialization.JsonValues;
import com.penmate.backend.application.novel.NovelApplicationService;
import com.penmate.backend.domain.novel.model.NovelChapter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Component
class ChapterReadToolHandler implements AgentToolHandler {
    private final NovelApplicationService novels;
    private final JsonCodec jsonCodec;

    ChapterReadToolHandler(NovelApplicationService novels, JsonCodec jsonCodec) {
        this.novels = novels;
        this.jsonCodec = jsonCodec;
    }

    @Override
    public String toolCode() {
        return "chapter_read";
    }

    @Override
    public void validate(AuthorizedAgentRunContext context, ToolCallRequest request) {
        ChapterToolSupport.requireContext(context, request);
        Map<String, Object> arguments = jsonCodec.readObject(request.toolArgsJson());
        ChapterToolSupport.rejectUnexpected(arguments, Set.of("chapterId"), toolCode());
        ChapterToolSupport.requireChapterId(arguments);
    }

    @Override
    public ToolCallResult execute(AuthorizedAgentRunContext context, ToolCallRequest request) {
        Map<String, Object> arguments = jsonCodec.readObject(request.toolArgsJson());
        NovelChapter chapter = novels.getChapter(context.projectId(), ChapterToolSupport.requireChapterId(arguments));
        return ToolCallResult.success(jsonCodec.write(ChapterToolSupport.readOutput(chapter)));
    }
}

@Slf4j
abstract class AbstractChapterMutationToolHandler implements AgentToolHandler {
    private static final int LEASE_ACQUIRE_ATTEMPTS = 20;
    private static final long LEASE_ACQUIRE_RETRY_MILLIS = 250L;

    protected final NovelApplicationService novels;
    protected final JsonCodec jsonCodec;
    private final AgentRunEventPublisher events;

    AbstractChapterMutationToolHandler(NovelApplicationService novels,
                                       JsonCodec jsonCodec,
                                       AgentRunEventPublisher events) {
        this.novels = novels;
        this.jsonCodec = jsonCodec;
        this.events = events;
    }

    @Override
    public boolean mutatesState(AuthorizedAgentRunContext context, ToolCallRequest request) {
        return true;
    }

    @Override
    public final void validate(AuthorizedAgentRunContext context, ToolCallRequest request) {
        ChapterToolSupport.requireContext(context, request);
        Map<String, Object> arguments = jsonCodec.readObject(request.toolArgsJson());
        ChapterToolSupport.requireChapterId(arguments);
        ChapterToolSupport.requireExpectedState(arguments);
        validateMutation(arguments);
    }

    protected abstract void validateMutation(Map<String, Object> arguments);

    protected abstract Mutation buildMutation(String currentContent, Map<String, Object> arguments);

    @Override
    public final ToolCallResult execute(AuthorizedAgentRunContext context, ToolCallRequest request) {
        Map<String, Object> arguments = jsonCodec.readObject(request.toolArgsJson());
        Long chapterId = ChapterToolSupport.requireChapterId(arguments);
        NovelApplicationService.AiChapterLeaseView lease = null;
        try {
            lease = acquireLease(context, chapterId);
            if (!lease.editable()) {
                return failure(context, request, chapterId, "CHAPTER_AI_BUSY", lease.reason());
            }
            publish(context, request, chapterId, "chapter.edit.started", Map.of(
                    "contentRevision", lease.contentRevision(),
                    "leaseExpiresAt", lease.expiresAt()));

            long expectedRevision = JsonValues.longValue(arguments, "expectedRevision");
            String expectedHash = JsonValues.string(arguments, "expectedContentHash");
            String beforeContent = ChapterToolSupport.value(lease.content());
            String beforeHash = ChapterToolSupport.sha256(beforeContent);
            if (!Objects.equals(lease.contentRevision(), expectedRevision)
                    || !beforeHash.equals(expectedHash)) {
                return failure(context, request, chapterId, "CHAPTER_CONTENT_CONFLICT",
                        "Chapter revision or content hash no longer matches; call chapter_read and retry");
            }

            Mutation mutation = buildMutation(beforeContent, arguments);
            if (!mutation.changed()) {
                Map<String, Object> receipt = receipt(chapterId, lease.contentRevision(), beforeHash,
                        lease.contentRevision(), beforeHash, false, null, null,
                        ChapterToolSupport.countWords(beforeContent), mutation.replacementsApplied());
                publish(context, request, chapterId, "chapter.edit.completed", receipt);
                return ToolCallResult.success(jsonCodec.write(receipt));
            }

            NovelApplicationService.AiChapterEditResult saved = novels.saveAiChapterEdit(
                    context.projectId(), chapterId, context.ownerUserId(), context.runId(), request.toolCallId(),
                    lease.leaseToken(), lease.contentRevision(), mutation.content());
            String afterHash = ChapterToolSupport.sha256(ChapterToolSupport.value(saved.chapter().getContent()));
            Map<String, Object> receipt = receipt(chapterId, lease.contentRevision(), beforeHash,
                    saved.chapter().getContentRevision(), afterHash, true,
                    saved.undo().operationId(), saved.undo().expiresAt(), saved.chapter().getWordCount(),
                    mutation.replacementsApplied());
            publish(context, request, chapterId, "chapter.edit.completed", receipt);
            return ToolCallResult.success(jsonCodec.write(receipt));
        } catch (ChapterPatchRejectedException exception) {
            return failure(context, request, chapterId, "CHAPTER_PATCH_MISMATCH", exception.getMessage());
        } catch (Exception exception) {
            String message = ChapterToolSupport.rootMessage(exception);
            return failure(context, request, chapterId, "CHAPTER_WRITE_FAILED", message);
        } finally {
            releaseQuietly(context, chapterId, lease);
        }
    }

    private NovelApplicationService.AiChapterLeaseView acquireLease(AuthorizedAgentRunContext context, Long chapterId) {
        NovelApplicationService.AiChapterLeaseView lease = null;
        for (int attempt = 0; attempt < LEASE_ACQUIRE_ATTEMPTS; attempt++) {
            lease = novels.acquireChapterAiLease(
                    context.projectId(), chapterId, context.ownerUserId(), context.runId());
            if (lease.editable()) return lease;
            if (attempt + 1 < LEASE_ACQUIRE_ATTEMPTS) {
                try {
                    Thread.sleep(LEASE_ACQUIRE_RETRY_MILLIS);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Chapter write was interrupted", exception);
                }
            }
        }
        return lease;
    }

    private void releaseQuietly(AuthorizedAgentRunContext context, Long chapterId,
                                NovelApplicationService.AiChapterLeaseView lease) {
        if (lease == null || !lease.editable() || lease.leaseToken() == null) return;
        try {
            novels.releaseChapterAiLease(context.projectId(), chapterId, context.ownerUserId(), lease.leaseToken());
        } catch (Exception exception) {
            log.warn("{} lease release failed: chapterId={}, runId={}", toolCode(), chapterId, context.runId(), exception);
        }
    }

    private ToolCallResult failure(AuthorizedAgentRunContext context, ToolCallRequest request,
                                   Long chapterId, String code, String message) {
        publish(context, request, chapterId, "chapter.edit.failed", Map.of(
                "errorCode", code,
                "errorMessage", message == null ? code : message));
        return ToolCallResult.failed(code, message);
    }

    private void publish(AuthorizedAgentRunContext context, ToolCallRequest request, Long chapterId,
                         String eventType, Map<String, Object> additions) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("projectId", String.valueOf(context.projectId()));
        payload.put("chapterId", String.valueOf(chapterId));
        payload.put("runId", String.valueOf(context.runId()));
        payload.put("toolCallId", request.toolCallId());
        payload.putAll(additions);
        events.publish(context.runId(), eventType, payload);
    }

    private Map<String, Object> receipt(Long chapterId,
                                        Long beforeRevision, String beforeHash,
                                        Long afterRevision, String afterHash,
                                        boolean changed, Long operationId, Object undoExpiresAt,
                                        Integer wordCount, int replacementsApplied) {
        Map<String, Object> receipt = new LinkedHashMap<>();
        receipt.put("chapterId", String.valueOf(chapterId));
        receipt.put("beforeRevision", beforeRevision);
        receipt.put("beforeContentHash", beforeHash);
        receipt.put("afterRevision", afterRevision);
        receipt.put("afterContentHash", afterHash);
        receipt.put("changed", changed);
        receipt.put("operationId", operationId == null ? null : String.valueOf(operationId));
        receipt.put("undoExpiresAt", undoExpiresAt);
        receipt.put("wordCount", wordCount);
        receipt.put("replacementsApplied", replacementsApplied);
        return receipt;
    }

    protected record Mutation(String content, boolean changed, int replacementsApplied) {
    }
}

@Component
class ChapterReplaceToolHandler extends AbstractChapterMutationToolHandler {
    ChapterReplaceToolHandler(NovelApplicationService novels, JsonCodec jsonCodec, AgentRunEventPublisher events) {
        super(novels, jsonCodec, events);
    }

    @Override
    public String toolCode() {
        return "chapter_replace";
    }

    @Override
    protected void validateMutation(Map<String, Object> arguments) {
        ChapterToolSupport.rejectUnexpected(arguments,
                Set.of("chapterId", "expectedRevision", "expectedContentHash", "content"), toolCode());
        if (!(arguments.get("content") instanceof String)) {
            throw new IllegalArgumentException("content must be a string");
        }
    }

    @Override
    protected Mutation buildMutation(String currentContent, Map<String, Object> arguments) {
        String content = (String) arguments.get("content");
        return new Mutation(content, !currentContent.equals(content), 0);
    }

}

@Component
class ChapterPatchToolHandler extends AbstractChapterMutationToolHandler {
    ChapterPatchToolHandler(NovelApplicationService novels, JsonCodec jsonCodec, AgentRunEventPublisher events) {
        super(novels, jsonCodec, events);
    }

    @Override
    public String toolCode() {
        return "chapter_patch";
    }

    @Override
    protected void validateMutation(Map<String, Object> arguments) {
        ChapterToolSupport.rejectUnexpected(arguments,
                Set.of("chapterId", "expectedRevision", "expectedContentHash", "replacements"), toolCode());
        parseReplacements(arguments);
    }

    @Override
    protected Mutation buildMutation(String currentContent, Map<String, Object> arguments) {
        String content = currentContent;
        int replacementsApplied = 0;
        List<Replacement> replacements = parseReplacements(arguments);
        for (int index = 0; index < replacements.size(); index++) {
            Replacement replacement = replacements.get(index);
            int actualOccurrences = countOccurrences(content, replacement.oldText());
            if (actualOccurrences != replacement.expectedOccurrences()) {
                throw new ChapterPatchRejectedException("Replacement " + (index + 1) + " expected "
                        + replacement.expectedOccurrences() + " occurrence(s) but found " + actualOccurrences);
            }
            content = content.replace(replacement.oldText(), replacement.newText());
            replacementsApplied += actualOccurrences;
        }
        return new Mutation(content, !currentContent.equals(content), replacementsApplied);
    }

    private List<Replacement> parseReplacements(Map<String, Object> arguments) {
        Object raw = arguments.get("replacements");
        if (!(raw instanceof List<?> values) || values.isEmpty()) {
            throw new IllegalArgumentException("replacements must be a non-empty array");
        }
        List<Replacement> replacements = new ArrayList<>();
        for (int index = 0; index < values.size(); index++) {
            if (!(values.get(index) instanceof Map<?, ?> rawReplacement)) {
                throw new IllegalArgumentException("replacement " + (index + 1) + " must be an object");
            }
            Map<String, Object> replacement = new LinkedHashMap<>();
            rawReplacement.forEach((key, value) -> replacement.put(String.valueOf(key), value));
            ChapterToolSupport.rejectUnexpected(replacement,
                    Set.of("oldText", "newText", "expectedOccurrences"), "replacement " + (index + 1));
            Object oldText = replacement.get("oldText");
            Object newText = replacement.get("newText");
            Long expectedOccurrences = JsonValues.longValue(replacement, "expectedOccurrences");
            if (!(oldText instanceof String oldValue) || oldValue.isEmpty()) {
                throw new IllegalArgumentException("replacement " + (index + 1) + " oldText must not be empty");
            }
            if (!(newText instanceof String newValue)) {
                throw new IllegalArgumentException("replacement " + (index + 1) + " newText must be a string");
            }
            if (expectedOccurrences == null || expectedOccurrences < 1 || expectedOccurrences > Integer.MAX_VALUE) {
                throw new IllegalArgumentException("replacement " + (index + 1)
                        + " expectedOccurrences must be a positive integer");
            }
            replacements.add(new Replacement(oldValue, newValue, expectedOccurrences.intValue()));
        }
        return List.copyOf(replacements);
    }

    private int countOccurrences(String content, String needle) {
        int count = 0;
        int offset = 0;
        while ((offset = content.indexOf(needle, offset)) >= 0) {
            count++;
            offset += needle.length();
        }
        return count;
    }

    private record Replacement(String oldText, String newText, int expectedOccurrences) {
    }
}

final class ChapterToolSupport {
    private ChapterToolSupport() {
    }

    static void requireContext(AuthorizedAgentRunContext context, ToolCallRequest request) {
        if (context == null || request == null) {
            throw new IllegalArgumentException("chapter tool requires project, run and operator context");
        }
    }

    static Long requireChapterId(Map<String, Object> arguments) {
        Long chapterId = JsonValues.longValue(arguments, "chapterId");
        if (chapterId == null || chapterId < 1) {
            throw new IllegalArgumentException("chapterId must be a positive integer");
        }
        return chapterId;
    }

    static void requireExpectedState(Map<String, Object> arguments) {
        Long revision = JsonValues.longValue(arguments, "expectedRevision");
        if (revision == null || revision < 1) {
            throw new IllegalArgumentException("expectedRevision must be a positive integer");
        }
        String hash = JsonValues.string(arguments, "expectedContentHash");
        if (hash == null || !hash.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("expectedContentHash must be a lowercase SHA-256 hash");
        }
    }

    static void rejectUnexpected(Map<String, Object> arguments, Set<String> allowed, String scope) {
        arguments.keySet().stream().filter(field -> !allowed.contains(field)).findFirst().ifPresent(field -> {
            throw new IllegalArgumentException("Unexpected " + scope + " field: " + field);
        });
    }

    static Map<String, Object> readOutput(NovelChapter chapter) {
        String content = value(chapter.getContent());
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("chapterId", String.valueOf(chapter.getChapterId()));
        output.put("title", chapter.getTitle());
        output.put("content", content);
        output.put("contentRevision", chapter.getContentRevision());
        output.put("contentHash", sha256(content));
        output.put("wordCount", chapter.getWordCount() == null ? countWords(content) : chapter.getWordCount());
        return output;
    }

    static String sha256(String content) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value(content).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    static int countWords(String content) {
        return value(content).codePoints().filter(codePoint -> !Character.isWhitespace(codePoint)).toArray().length;
    }

    static String value(String content) {
        return content == null ? "" : content;
    }

    static String rootMessage(Throwable failure) {
        Throwable current = failure;
        while (current.getCause() != null) current = current.getCause();
        return current.getMessage() == null || current.getMessage().isBlank()
                ? current.getClass().getSimpleName()
                : current.getMessage();
    }
}

final class ChapterPatchRejectedException extends RuntimeException {
    ChapterPatchRejectedException(String message) {
        super(message);
    }
}

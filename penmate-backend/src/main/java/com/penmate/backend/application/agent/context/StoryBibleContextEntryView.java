package com.penmate.backend.application.agent.context;

/**
 * Story Bible 运行时上下文条目视图。
 * <p>这是 context builder 的结构化输入，而不是最终 prompt 大文本。</p>
 */
public record StoryBibleContextEntryView(
        String source,
        String entryKey,
        String title,
        String content,
        String entryType,
        String canonicalStatus,
        Integer riskLevel,
        Integer versionNo,
        Long validFromChapterId,
        Long validToChapterId
) {

    public StoryBibleContextEntryView {
        source = normalize(source);
        entryKey = normalize(entryKey);
        title = normalize(title);
        content = normalize(content);
        entryType = normalize(entryType);
        canonicalStatus = normalize(canonicalStatus);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}

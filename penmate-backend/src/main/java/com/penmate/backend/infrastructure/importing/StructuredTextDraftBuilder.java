package com.penmate.backend.infrastructure.importing;

import com.penmate.backend.domain.novel.importing.NovelImportDraft;
import com.penmate.backend.domain.novel.importing.NovelImportFormat;

import java.util.ArrayList;
import java.util.List;

final class StructuredTextDraftBuilder {
    private final NovelImportFormat format;
    private final List<MutableVolume> volumes = new ArrayList<>();
    private MutableVolume currentVolume;
    private MutableChapter currentChapter;

    StructuredTextDraftBuilder(NovelImportFormat format) {
        this.format = format;
    }

    void volume(String title) {
        currentVolume = new MutableVolume(clean(title, "第一卷"));
        volumes.add(currentVolume);
        currentChapter = null;
    }

    void chapter(String title) {
        ensureVolume();
        currentChapter = new MutableChapter(clean(title, "第一章"));
        currentVolume.chapters.add(currentChapter);
    }

    void content(String line) {
        if (currentChapter == null && (line == null || line.isBlank())) return;
        ensureChapter();
        currentChapter.lines.add(line == null ? "" : line);
    }

    boolean hasStructure() {
        return !volumes.isEmpty();
    }

    NovelImportDraft build(String projectTitle) {
        if (volumes.isEmpty()) volume("第一卷");
        if (volumes.stream().allMatch(volume -> volume.chapters.isEmpty())) chapter("第一章");
        List<NovelImportDraft.Volume> result = volumes.stream()
                .filter(volume -> !volume.chapters.isEmpty())
                .map(volume -> new NovelImportDraft.Volume(volume.title, volume.chapters.stream()
                        .map(chapter -> new NovelImportDraft.Chapter(chapter.title, trimBlankLines(chapter.lines)))
                        .toList()))
                .toList();
        return new NovelImportDraft(projectTitle, format, result, List.of()).withDiagnostics();
    }

    private void ensureVolume() {
        if (currentVolume == null) volume("第一卷");
    }

    private void ensureChapter() {
        ensureVolume();
        if (currentChapter == null) chapter("第一章");
    }

    private static String trimBlankLines(List<String> lines) {
        int start = 0;
        int end = lines.size();
        while (start < end && lines.get(start).isBlank()) start++;
        while (end > start && lines.get(end - 1).isBlank()) end--;
        return String.join("\n", lines.subList(start, end));
    }

    private static String clean(String value, String fallback) {
        String normalized = value == null ? "" : value.strip();
        return normalized.isEmpty() ? fallback : normalized;
    }

    private static final class MutableVolume {
        private final String title;
        private final List<MutableChapter> chapters = new ArrayList<>();
        private MutableVolume(String title) { this.title = title; }
    }

    private static final class MutableChapter {
        private final String title;
        private final List<String> lines = new ArrayList<>();
        private MutableChapter(String title) { this.title = title; }
    }
}

package com.penmate.backend.application.agent.tool;

import com.penmate.backend.application.common.exception.BusinessException;
import com.penmate.backend.domain.novel.model.NovelChapter;
import com.penmate.backend.domain.novel.model.NovelProject;
import com.penmate.backend.domain.novel.model.NovelVolume;
import com.penmate.backend.domain.novel.repository.NovelGateway;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ManuscriptReadApplicationService {
    public static final int MAX_SELECTIONS = 50;
    public static final int MAX_RETURNED_CHARACTERS = 20_000;
    public static final int MAX_MANIFEST_PAGE = 200;
    private final NovelGateway novels;

    @Transactional(readOnly = true)
    public Manifest manifest(Long projectId, int cursor, int limit) {
        NovelProject project = requireProject(projectId);
        if (cursor < 0) throw BusinessException.badRequest("cursor must be greater than or equal to 0");
        if (limit < 1 || limit > MAX_MANIFEST_PAGE) {
            throw BusinessException.badRequest("limit must be between 1 and " + MAX_MANIFEST_PAGE);
        }
        List<NovelVolume> volumes = novels.findVolumesByProjectId(projectId).stream()
                .sorted(Comparator.comparing(NovelVolume::getSortOrder, Comparator.nullsLast(Integer::compareTo))
                        .thenComparing(NovelVolume::getVolumeId)).toList();
        Map<Long, NovelVolume> volumeById = volumes.stream()
                .collect(Collectors.toMap(NovelVolume::getVolumeId, Function.identity()));
        List<NovelChapter> chapters = novels.findChaptersByProjectId(projectId).stream()
                .sorted(Comparator.comparing((NovelChapter chapter) -> {
                    NovelVolume volume = volumeById.get(chapter.getVolumeId());
                    return volume == null ? Integer.MAX_VALUE : value(volume.getSortOrder(), Integer.MAX_VALUE);
                }).thenComparing(chapter -> value(chapter.getSortOrder(), Integer.MAX_VALUE))
                        .thenComparing(NovelChapter::getChapterId)).toList();
        int from = Math.min(cursor, chapters.size());
        int to = Math.min(chapters.size(), from + limit);
        List<ManifestChapter> page = chapters.subList(from, to).stream().map(chapter -> {
            NovelVolume volume = volumeById.get(chapter.getVolumeId());
            String content = text(chapter.getContent());
            return new ManifestChapter(chapter.getChapterId(), chapter.getTitle(), chapter.getVolumeId(),
                    volume == null ? null : volume.getTitle(), chapter.getSortOrder(), chapter.getDisplayNo(),
                    value(chapter.getContentRevision(), 0L), codePoints(content), sha256(content));
        }).toList();
        List<ManifestVolume> volumeItems = volumes.stream().map(volume -> new ManifestVolume(
                volume.getVolumeId(), volume.getTitle(), volume.getSortOrder(),
                chapters.stream().filter(chapter -> java.util.Objects.equals(chapter.getVolumeId(), volume.getVolumeId())).count()
        )).toList();
        return new Manifest(projectId, value(project.getStructureRevision(), 0L), volumeItems, page,
                chapters.size(), to == chapters.size() ? null : to);
    }

    @Transactional(readOnly = true)
    public ChapterRead read(Long projectId, List<Selection> selections) {
        requireProject(projectId);
        List<Selection> requested = List.copyOf(selections == null ? List.of() : selections);
        if (requested.isEmpty() || requested.size() > MAX_SELECTIONS) {
            throw BusinessException.badRequest("selections must contain between 1 and " + MAX_SELECTIONS + " items");
        }
        long totalRequested = 0;
        java.util.ArrayList<ResolvedSelection> resolved = new java.util.ArrayList<>();
        for (Selection selection : requested) {
            if (selection == null || selection.chapterId() == null) {
                throw BusinessException.badRequest("Every selection requires chapterId");
            }
            NovelChapter chapter = novels.findChapterByIdAndProjectId(projectId, selection.chapterId());
            if (chapter == null) throw BusinessException.notFound("Chapter not found: " + selection.chapterId());
            String content = text(chapter.getContent());
            int characters = codePoints(content);
            int start = selection.start() == null ? 0 : selection.start();
            int end = selection.end() == null ? characters : selection.end();
            if (start < 0 || end < start || end > characters) {
                throw BusinessException.badRequest("Invalid range for chapter " + selection.chapterId());
            }
            totalRequested += end - start;
            resolved.add(new ResolvedSelection(chapter, content, characters, start, end));
        }
        int totalReturned = 0;
        java.util.ArrayList<ChapterSlice> results = new java.util.ArrayList<>();
        java.util.ArrayList<Selection> nextSelections = new java.util.ArrayList<>();
        for (int index = 0; index < resolved.size(); index++) {
            ResolvedSelection selection = resolved.get(index);
            int available = MAX_RETURNED_CHARACTERS - totalReturned;
            if (available <= 0) {
                appendRemainingSelections(resolved, index, nextSelections);
                break;
            }
            int sliceEnd = Math.min(selection.end(), selection.start() + available);
            NovelChapter chapter = selection.chapter();
            results.add(new ChapterSlice(chapter.getChapterId(), chapter.getVolumeId(), chapter.getTitle(),
                    value(chapter.getContentRevision(), 0L), sha256(selection.content()), selection.characters(),
                    selection.start(), sliceEnd, slice(selection.content(), selection.start(), sliceEnd),
                    selection.start() == 0 && sliceEnd == selection.characters()));
            totalReturned += sliceEnd - selection.start();
            if (sliceEnd < selection.end()) {
                nextSelections.add(new Selection(chapter.getChapterId(), sliceEnd, selection.end()));
                appendRemainingSelections(resolved, index + 1, nextSelections);
                break;
            }
        }
        return new ChapterRead(List.copyOf(results), totalReturned, totalRequested,
                !nextSelections.isEmpty(), List.copyOf(nextSelections));
    }

    private void appendRemainingSelections(List<ResolvedSelection> resolved, int startIndex,
                                           List<Selection> target) {
        for (int index = startIndex; index < resolved.size(); index++) {
            ResolvedSelection remaining = resolved.get(index);
            target.add(new Selection(remaining.chapter().getChapterId(), remaining.start(), remaining.end()));
        }
    }

    private NovelProject requireProject(Long projectId) {
        NovelProject project = novels.findProjectById(projectId);
        if (project == null) throw BusinessException.notFound("Novel project not found");
        return project;
    }
    private int codePoints(String value) { return value.codePointCount(0, value.length()); }
    private String slice(String value, int start, int end) {
        return value.substring(value.offsetByCodePoints(0, start), value.offsetByCodePoints(0, end));
    }
    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
    private String text(String value) { return value == null ? "" : value; }
    private <T> T value(T value, T fallback) { return value == null ? fallback : value; }

    public record Manifest(Long projectId, Long structureRevision, List<ManifestVolume> volumes,
                           List<ManifestChapter> chapters, int totalChapters, Integer nextCursor) {}
    public record ManifestVolume(Long volumeId, String title, Integer sortOrder, long chapterCount) {}
    public record ManifestChapter(Long chapterId, String title, Long volumeId, String volumeTitle,
                                  Integer sortOrder, Integer displayNo, Long contentRevision,
                                  int characterCount, String contentHash) {}
    public record Selection(Long chapterId, Integer start, Integer end) {}
    public record ChapterRead(List<ChapterSlice> chapters, int totalCharacters,
                              long totalRequestedCharacters, boolean truncated,
                              List<Selection> nextSelections) {}
    public record ChapterSlice(Long chapterId, Long volumeId, String title, Long contentRevision,
                               String contentHash, int characterCount, int start, int end,
                               String content, boolean isComplete) {}
    private record ResolvedSelection(NovelChapter chapter, String content, int characters,
                                     int start, int end) {}
}

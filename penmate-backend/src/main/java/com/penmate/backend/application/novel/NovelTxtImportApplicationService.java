package com.penmate.backend.application.novel;

import com.penmate.backend.application.common.exception.BusinessException;
import com.penmate.backend.application.novel.command.NovelCommands.CreateProjectCommand;
import com.penmate.backend.application.novel.command.NovelCommands.ImportChapterCommand;
import com.penmate.backend.application.novel.command.NovelCommands.ImportProjectCommand;
import com.penmate.backend.application.novel.command.NovelCommands.ImportVolumeCommand;
import com.penmate.backend.domain.novel.model.NovelProject;
import org.springframework.stereotype.Service;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Service
public class NovelTxtImportApplicationService {

    private static final int MAX_FILE_BYTES = 10 * 1024 * 1024;
    private static final int MAX_VOLUMES = 100;
    private static final int MAX_CHAPTERS = 2000;
    private static final Pattern VOLUME_HEADING = Pattern.compile(
            "^第[0-9零〇一二两三四五六七八九十百千万]+卷(?:\\s+.*|[：:].*)?$", Pattern.CASE_INSENSITIVE);
    private static final Pattern CHAPTER_HEADING = Pattern.compile(
            "^第[0-9零〇一二两三四五六七八九十百千万]+章(?:\\s+.*|[：:].*)?$", Pattern.CASE_INSENSITIVE);

    private final NovelApplicationService novelApplicationService;

    public NovelTxtImportApplicationService(NovelApplicationService novelApplicationService) {
        this.novelApplicationService = novelApplicationService;
    }

    public ImportPreview preview(String filename, byte[] bytes) {
        if (bytes == null || bytes.length == 0) throw BusinessException.badRequest("TXT file is empty");
        if (bytes.length > MAX_FILE_BYTES) throw BusinessException.badRequest("TXT file exceeds 10 MB");
        String text = decodeUtf8(bytes).replace("\r\n", "\n").replace('\r', '\n');
        if (text.startsWith("\uFEFF")) text = text.substring(1);
        if (text.isBlank()) throw BusinessException.badRequest("TXT file contains no text");
        return new ImportPreview(projectTitle(filename), parse(text));
    }

    public NovelProject importProject(Long ownerUserId, ImportPreview preview, String traceId) {
        validatePreview(preview);
        List<ImportVolumeCommand> volumes = preview.volumes().stream()
                .map(volume -> new ImportVolumeCommand(volume.title(), volume.chapters().stream()
                        .map(chapter -> new ImportChapterCommand(chapter.title(), chapter.content()))
                        .toList()))
                .toList();
        return novelApplicationService.createImportedProject(new ImportProjectCommand(
                new CreateProjectCommand(ownerUserId, preview.projectTitle(), null, "其他", null, List.of(), 1),
                volumes
        ), traceId);
    }

    private List<ImportVolumePreview> parse(String text) {
        List<MutableVolume> volumes = new ArrayList<>();
        MutableVolume currentVolume = null;
        MutableChapter currentChapter = null;
        int chapterCount = 0;
        for (String line : text.split("\\n", -1)) {
            String heading = line.trim();
            if (VOLUME_HEADING.matcher(heading).matches()) {
                currentVolume = new MutableVolume(heading);
                volumes.add(currentVolume);
                currentChapter = null;
                continue;
            }
            if (CHAPTER_HEADING.matcher(heading).matches()) {
                if (currentVolume == null) {
                    currentVolume = new MutableVolume("第一卷");
                    volumes.add(currentVolume);
                }
                currentChapter = new MutableChapter(heading);
                currentVolume.chapters.add(currentChapter);
                chapterCount++;
                continue;
            }
            if (currentVolume == null) {
                currentVolume = new MutableVolume("第一卷");
                volumes.add(currentVolume);
            }
            if (currentChapter == null) {
                currentChapter = new MutableChapter("第一章");
                currentVolume.chapters.add(currentChapter);
                chapterCount++;
            }
            currentChapter.lines.add(line);
        }
        if (volumes.size() > MAX_VOLUMES || chapterCount > MAX_CHAPTERS) {
            throw BusinessException.badRequest("TXT file contains too many volumes or chapters");
        }
        return volumes.stream()
                .filter(volume -> !volume.chapters.isEmpty())
                .map(volume -> new ImportVolumePreview(volume.title, volume.chapters.stream()
                        .map(chapter -> new ImportChapterPreview(chapter.title, trimBlankLines(chapter.lines)))
                        .toList()))
                .toList();
    }

    private void validatePreview(ImportPreview preview) {
        if (preview == null || invalidTitle(preview.projectTitle())) {
            throw BusinessException.badRequest("Project title must contain 1 to 200 characters");
        }
        if (preview.volumes() == null || preview.volumes().isEmpty() || preview.volumes().size() > MAX_VOLUMES) {
            throw BusinessException.badRequest("Import requires 1 to 100 volumes");
        }
        int chapterCount = 0;
        for (ImportVolumePreview volume : preview.volumes()) {
            if (volume == null || invalidTitle(volume.title()) || volume.chapters() == null || volume.chapters().isEmpty()) {
                throw BusinessException.badRequest("Every imported volume requires a title and chapter");
            }
            for (ImportChapterPreview chapter : volume.chapters()) {
                if (chapter == null || invalidTitle(chapter.title())) {
                    throw BusinessException.badRequest("Every imported chapter requires a title");
                }
                chapterCount++;
            }
        }
        if (chapterCount > MAX_CHAPTERS) throw BusinessException.badRequest("Import has too many chapters");
    }

    private boolean invalidTitle(String title) {
        return title == null || title.isBlank() || title.trim().length() > 200;
    }

    private String decodeUtf8(byte[] bytes) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException exception) {
            throw BusinessException.badRequest("TXT file must use UTF-8 encoding");
        }
    }

    private String projectTitle(String filename) {
        String value = filename == null ? "" : filename.trim().replaceAll("(?i)\\.txt$", "");
        value = value.isBlank() ? "导入作品" : value;
        return value.length() > 200 ? value.substring(0, 200) : value;
    }

    private String trimBlankLines(List<String> lines) {
        int start = 0;
        int end = lines.size();
        while (start < end && lines.get(start).isBlank()) start++;
        while (end > start && lines.get(end - 1).isBlank()) end--;
        return String.join("\n", lines.subList(start, end));
    }

    public record ImportPreview(String projectTitle, List<ImportVolumePreview> volumes) {}

    public record ImportVolumePreview(String title, List<ImportChapterPreview> chapters) {}

    public record ImportChapterPreview(String title, String content) {}

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

package com.penmate.backend.application.novel.export;

import com.penmate.backend.application.common.exception.BusinessException;
import com.penmate.backend.domain.novel.model.NovelChapter;
import com.penmate.backend.domain.novel.model.NovelProject;
import com.penmate.backend.domain.novel.model.NovelVolume;
import com.penmate.backend.domain.novel.repository.NovelGateway;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@Service
public class NovelExportService {

    private static final Comparator<NovelVolume> VOLUME_ORDER = Comparator
            .comparing(NovelVolume::getSortOrder, Comparator.nullsLast(Integer::compareTo))
            .thenComparing(NovelVolume::getVolumeId, Comparator.nullsLast(Long::compareTo));
    private static final Comparator<NovelChapter> CHAPTER_ORDER = Comparator
            .comparing(NovelChapter::getSortOrder, Comparator.nullsLast(Integer::compareTo))
            .thenComparing(NovelChapter::getChapterId, Comparator.nullsLast(Long::compareTo));

    private final NovelGateway novelGateway;
    private final NovelDocumentRenderer documentRenderer;

    public NovelExportService(NovelGateway novelGateway, NovelDocumentRenderer documentRenderer) {
        this.novelGateway = novelGateway;
        this.documentRenderer = documentRenderer;
    }

    public ExportedNovel export(Long projectId, Long actorUserId, String rawFormat) {
        Objects.requireNonNull(projectId, "projectId must not be null");
        Objects.requireNonNull(actorUserId, "actorUserId must not be null");
        NovelProject project = novelGateway.findProjectById(projectId);
        if (project == null || !Objects.equals(project.getOwnerUserId(), actorUserId)) {
            throw BusinessException.notFound("Novel project not found");
        }

        NovelExportFormat format = NovelExportFormat.parse(rawFormat);
        NovelManuscript manuscript = assemble(project);
        byte[] content = documentRenderer.render(format, manuscript);
        return new ExportedNovel(fileName(project.getTitle(), format), format.contentType(), content);
    }

    private NovelManuscript assemble(NovelProject project) {
        List<NovelVolume> volumes = new ArrayList<>(novelGateway.findVolumesByProjectId(project.getProjectId()));
        List<NovelChapter> chapters = new ArrayList<>(novelGateway.findChaptersByProjectId(project.getProjectId()));
        volumes.sort(VOLUME_ORDER);
        chapters.sort(CHAPTER_ORDER);

        List<NovelManuscript.Volume> manuscriptVolumes = new ArrayList<>();
        for (NovelVolume volume : volumes) {
            List<NovelManuscript.Chapter> volumeChapters = chapters.stream()
                    .filter(chapter -> Objects.equals(chapter.getVolumeId(), volume.getVolumeId()))
                    .map(chapter -> new NovelManuscript.Chapter(chapter.getTitle(), chapter.getContent()))
                    .toList();
            manuscriptVolumes.add(new NovelManuscript.Volume(volume.getTitle(), volumeChapters));
        }
        return new NovelManuscript(project.getTitle(), manuscriptVolumes);
    }

    private String fileName(String title, NovelExportFormat format) {
        String baseName = title == null ? "" : title.trim()
                .replaceAll("[\\\\/:*?\"<>|]", "_")
                .replaceAll("[. ]+$", "");
        if (baseName.isBlank()) {
            baseName = "novel";
        }
        return baseName + "." + format.extension();
    }

    public record ExportedNovel(String fileName, String contentType, byte[] content) {
        public ExportedNovel {
            content = content == null ? new byte[0] : content.clone();
        }

        @Override
        public byte[] content() {
            return content.clone();
        }
    }
}

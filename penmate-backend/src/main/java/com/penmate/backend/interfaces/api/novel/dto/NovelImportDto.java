package com.penmate.backend.interfaces.api.novel.dto;

import com.penmate.backend.domain.novel.importing.NovelImportDraft;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class NovelImportDto {
    @NotBlank @Size(max = 200)
    private String projectTitle;
    @NotEmpty @Size(max = 100)
    private List<@Valid VolumeDto> volumes;

    public NovelImportDraft toDraft() {
        return new NovelImportDraft(projectTitle, null, volumes.stream()
                .map(volume -> new NovelImportDraft.Volume(volume.title, volume.chapters.stream()
                        .map(chapter -> new NovelImportDraft.Chapter(chapter.title, chapter.content)).toList()))
                .toList(), List.of());
    }

    @Data
    public static class VolumeDto {
        @NotBlank @Size(max = 200)
        private String title;
        @NotEmpty
        private List<@Valid ChapterDto> chapters;
    }

    @Data
    public static class ChapterDto {
        @NotBlank @Size(max = 200)
        private String title;
        @NotNull
        private String content;
    }
}

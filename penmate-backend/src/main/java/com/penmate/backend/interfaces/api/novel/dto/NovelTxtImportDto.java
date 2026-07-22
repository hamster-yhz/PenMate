package com.penmate.backend.interfaces.api.novel.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class NovelTxtImportDto {

    @NotBlank
    @Size(max = 200)
    private String projectTitle;

    @NotEmpty
    @Size(max = 100)
    private List<@Valid VolumeDto> volumes;

    @Data
    public static class VolumeDto {
        @NotBlank
        @Size(max = 200)
        private String title;

        @NotEmpty
        private List<@Valid ChapterDto> chapters;
    }

    @Data
    public static class ChapterDto {
        @NotBlank
        @Size(max = 200)
        private String title;

        @NotNull
        private String content;
    }
}

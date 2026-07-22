package com.penmate.backend.interfaces.api.novel.dto;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

@Data

public class CreateNovelProjectDto {

    @NotBlank
    private String title;

    private String summary;

    @Size(max = 40)
    private String genre;

    @Size(max = 40)
    private String customGenre;

    @Size(max = 10)
    private List<@Size(max = 12) String> tags;

    private Integer status;

}


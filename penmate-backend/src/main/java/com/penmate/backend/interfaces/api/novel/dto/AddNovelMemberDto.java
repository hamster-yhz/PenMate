package com.penmate.backend.interfaces.api.novel.dto;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Data

public class AddNovelMemberDto {

    @NotNull
    private Long userId;

    @NotBlank
    private String memberRole;

}


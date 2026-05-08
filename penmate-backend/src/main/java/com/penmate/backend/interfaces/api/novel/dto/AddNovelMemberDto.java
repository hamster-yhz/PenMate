package com.penmate.backend.interfaces.api.novel.dto;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;

@Data

public class AddNovelMemberDto {

    @NotBlank
    private String userId;

    @NotBlank
    private String memberRole;

}


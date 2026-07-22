package com.penmate.backend.interfaces.api.novel.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class PermanentlyDeleteNovelProjectDto {

    @NotBlank(message = "confirmationTitle must not be blank")
    private String confirmationTitle;
}

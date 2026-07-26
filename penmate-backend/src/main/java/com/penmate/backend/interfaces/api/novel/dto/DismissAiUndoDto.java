package com.penmate.backend.interfaces.api.novel.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public record DismissAiUndoDto(
        @NotEmpty @Size(max = 100)
        List<@Pattern(regexp = "^[1-9][0-9]*$") String> operationIds
) {
}

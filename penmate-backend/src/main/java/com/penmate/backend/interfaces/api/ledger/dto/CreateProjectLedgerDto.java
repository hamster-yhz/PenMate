package com.penmate.backend.interfaces.api.ledger.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateProjectLedgerDto(
        @NotBlank @Size(max = 120) String title,
        @Size(max = 20_000) String content
) {
}

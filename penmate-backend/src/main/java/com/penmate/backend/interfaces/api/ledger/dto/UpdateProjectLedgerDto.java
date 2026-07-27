package com.penmate.backend.interfaces.api.ledger.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateProjectLedgerDto(
        @Pattern(regexp = "^[1-9]\\d*$")
        String expectedRevision,
        @Size(max = 120) String title,
        @Min(0) Integer start,
        @Min(0) Integer end,
        @Size(max = 20_000) String replacement
) {
}

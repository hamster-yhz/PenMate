package com.penmate.backend.interfaces.api.ledger.dto;

public record ProjectLedgerDto(
        String ledgerId,
        String projectId,
        String title,
        String content,
        String contentRevision,
        String leaseOwnerType,
        String leaseOwnerId,
        String leaseExpiresAt,
        String createdAt,
        String updatedAt,
        Integer offset,
        Integer end,
        Integer totalCharacters,
        Boolean complete
) {
}

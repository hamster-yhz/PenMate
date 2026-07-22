package com.penmate.backend.interfaces.api.rbac.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class ReplaceRbacAssignmentsDto {
    @NotNull
    private Long expectedRevision;

    @NotNull
    private List<String> assignmentIds;
}

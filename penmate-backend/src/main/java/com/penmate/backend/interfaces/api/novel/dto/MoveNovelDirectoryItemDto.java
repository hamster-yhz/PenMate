package com.penmate.backend.interfaces.api.novel.dto;

import com.penmate.backend.application.novel.command.NovelCommands.DirectoryNodeType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class MoveNovelDirectoryItemDto {

    @NotNull
    private DirectoryNodeType nodeType;

    @NotBlank
    private String nodeId;

    private String targetVolumeId;

    @NotNull
    @Min(1)
    private Integer sortOrder;

    @NotNull
    @Min(1)
    private Long expectedStructureRevision;
}

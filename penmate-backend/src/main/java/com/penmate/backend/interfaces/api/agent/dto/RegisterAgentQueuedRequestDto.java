package com.penmate.backend.interfaces.api.agent.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class RegisterAgentQueuedRequestDto {
    @NotBlank
    private String type;
    private String userMessage;
    private List<String> activeSkills;
    @Valid
    private TaskRequest taskRequest;

    @Data
    public static class TaskRequest {
        private String chapterId;
        @NotNull(message = "chapterIds must not be null")
        private List<String> chapterIds;
        private String modelConfigId;
        private String selectedText;
    }
}

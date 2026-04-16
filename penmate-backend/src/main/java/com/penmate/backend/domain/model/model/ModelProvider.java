package com.penmate.backend.domain.model.model;

import lombok.Data;
import java.time.LocalDateTime;

@Data

public class ModelProvider {
    private Long id;
    private String code;
    private String name;
    private String baseUrl;
    private String authType;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

}


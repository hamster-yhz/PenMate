package com.penmate.backend.interfaces.api.rag.dto;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;

@Data

public class CreateRagDocumentDto {

    @NotBlank
    private String docType;

    @NotBlank
    private String title;

    private String sourceRef;

    @NotBlank
    private String originObjectKey;

    private String originEtag;

    private String mimeType;

}


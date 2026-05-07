package com.penmate.backend.interfaces.api.model.dto;

import lombok.Data;

@Data
public class UpdateOfficialModelKeyDto {

    private String keyName;

    private String apiKey;

    private Boolean isDefault;

    private String status;
}

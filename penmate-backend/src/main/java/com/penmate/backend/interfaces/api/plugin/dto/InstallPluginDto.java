package com.penmate.backend.interfaces.api.plugin.dto;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;

@Data

public class InstallPluginDto {

    @NotBlank
    private String pluginCode;
    private String version;
    private String configJson;

}


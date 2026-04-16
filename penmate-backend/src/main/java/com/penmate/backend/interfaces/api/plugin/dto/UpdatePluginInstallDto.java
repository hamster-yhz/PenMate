package com.penmate.backend.interfaces.api.plugin.dto;

import lombok.Data;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "插件安装更新请求参数")
@Data
public class UpdatePluginInstallDto {

    @Schema(description = "是否启用插件", example = "true")
    private Boolean enabled;

    @Schema(description = "插件运行配置（JSON 字符串）", example = "{\"timeout\":3000,\"retries\":2}")
    private String configJson;

}


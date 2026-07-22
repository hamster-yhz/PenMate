package com.penmate.backend.interfaces.api.novel.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class NovelCoverCropDto {
    @NotNull @DecimalMin("0") @DecimalMax("1")
    private Double x;
    @NotNull @DecimalMin("0") @DecimalMax("1")
    private Double y;
    @NotNull @DecimalMin(value = "0", inclusive = false) @DecimalMax("1")
    private Double width;
    @NotNull @DecimalMin(value = "0", inclusive = false) @DecimalMax("1")
    private Double height;
}

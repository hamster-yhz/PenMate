package com.penmate.backend.interfaces.api.auth.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class UpdateUserUiPreferencesDto {
    @NotNull
    @Pattern(regexp = "SYSTEM|LIGHT|DARK")
    private String themeMode;

    @NotNull
    @Pattern(regexp = "SERIF|SANS|SYSTEM")
    private String editorFontFamily;

    @NotNull
    @Min(14)
    @Max(24)
    private Integer editorFontSize;

    @NotNull
    @DecimalMin("1.50")
    @DecimalMax("2.40")
    private BigDecimal editorLineHeight;

    @NotNull
    @DecimalMin("0.00")
    @DecimalMax("2.00")
    private BigDecimal editorParagraphSpacing;

    @NotNull
    @Min(560)
    @Max(1000)
    private Integer editorContentWidth;

    @NotNull
    private Boolean typewriterMode;

    @NotNull
    private Boolean highlightCurrentParagraph;
}

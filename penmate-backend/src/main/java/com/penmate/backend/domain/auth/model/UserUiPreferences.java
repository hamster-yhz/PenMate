package com.penmate.backend.domain.auth.model;

import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

@Data
public class UserUiPreferences {
    private Long id;
    private Long userId;
    private String themeMode;
    private String editorFontFamily;
    private Integer editorFontSize;
    private BigDecimal editorLineHeight;
    private BigDecimal editorParagraphSpacing;
    private Integer editorContentWidth;
    private Boolean typewriterMode;
    private Boolean highlightCurrentParagraph;
    private Instant createdAt;
    private Instant updatedAt;
}

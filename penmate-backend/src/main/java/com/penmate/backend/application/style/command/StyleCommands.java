package com.penmate.backend.application.style.command;

public final class StyleCommands {

    private StyleCommands() {
    }

    public record CreateStyleCommand(String name,
                                     Boolean isDefault,
                                     String pace,
                                     String tone,
                                     String narrativeFocus,
                                     String promptTemplate,
                                     String sampleText,
                                     Long operatorId) {}

    public record UpdateStyleCommand(String name,
                                     String pace,
                                     String tone,
                                     String narrativeFocus,
                                     String promptTemplate,
                                     String sampleText,
                                     Long operatorId) {}

    public record SwitchStyleCommand(Long toStyleId,
                                     Boolean warningConfirmed,
                                     String reason,
                                     Long operatorId) {}

    public record AnalyzeStyleCommand(String sampleText,
                                      Long operatorId) {}
}


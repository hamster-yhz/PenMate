package com.penmate.backend.application.style.command;

public final class StyleCommands {

    private StyleCommands() {
    }

    /**
     * CreateStyleCommand。
     * <p>业务层：负责业务流程编排、领域对象协作与审计事件触发。</p>
     */
    public record CreateStyleCommand(String name,
                                     Boolean isDefault,
                                     String pace,
                                     String tone,
                                     String narrativeFocus,
                                     String promptTemplate,
                                     String sampleText,
                                     Long operatorId) {}

    /**
     * UpdateStyleCommand。
     * <p>业务层：负责业务流程编排、领域对象协作与审计事件触发。</p>
     */
    public record UpdateStyleCommand(String name,
                                     String pace,
                                     String tone,
                                     String narrativeFocus,
                                     String promptTemplate,
                                     String sampleText,
                                     Long operatorId) {}

    /**
     * SwitchStyleCommand。
     * <p>业务层：负责业务流程编排、领域对象协作与审计事件触发。</p>
     */
    public record SwitchStyleCommand(Long toStyleId,
                                     Boolean warningConfirmed,
                                     String reason,
                                     Long operatorId) {}

    /**
     * AnalyzeStyleCommand。
     * <p>业务层：负责业务流程编排、领域对象协作与审计事件触发。</p>
     */
    public record AnalyzeStyleCommand(String sampleText,
                                      Long operatorId) {}
}


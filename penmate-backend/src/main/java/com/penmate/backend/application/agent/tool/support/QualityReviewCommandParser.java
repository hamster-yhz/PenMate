package com.penmate.backend.application.agent.tool.support;

import com.penmate.backend.application.common.serialization.JsonCodec;
import com.penmate.backend.application.common.serialization.JsonValues;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class QualityReviewCommandParser {

    private final JsonCodec jsonCodec;

    public QualityReviewCommandParser(JsonCodec jsonCodec) {
        this.jsonCodec = jsonCodec;
    }

    public QualityReviewCommand parseAndValidate(String toolArgsJson) {
        QualityReviewCommand command = parse(toolArgsJson);
        validate(command);
        return command;
    }

    public QualityReviewCommand parse(String toolArgsJson) {
        try {
            Map<String, Object> args = jsonCodec.readObject(toolArgsJson);
            return new QualityReviewCommand(
                    JsonValues.string(args, "draftText"),
                    toStringList(JsonValues.list(args, "userRequirements")),
                    toStringList(JsonValues.list(args, "personaProfile")),
                    toStringList(JsonValues.list(args, "storyOutline")),
                    toStringList(JsonValues.list(args, "timelineConstraints")),
                    toStringList(JsonValues.list(args, "worldRules")),
                    toStringList(JsonValues.list(args, "characterKnowledgeBoundaries")),
                    integerOrZero(args, "currentRevisionRound"),
                    integerOrZero(args, "maxRevisionRounds")
            );
        } catch (Exception ex) {
            throw new IllegalArgumentException("toolArgsJson must be valid JSON", ex);
        }
    }

    public void validate(QualityReviewCommand command) {
        if (command.draftText().isBlank()) {
            throw new IllegalArgumentException("draftText must not be blank");
        }
        requireNonEmptyList(command.userRequirements(), "userRequirements");
        requireNonEmptyList(command.personaProfile(), "personaProfile");
        requireNonEmptyList(command.storyOutline(), "storyOutline");
        requireNonEmptyList(command.timelineConstraints(), "timelineConstraints");
        requireNonEmptyList(command.worldRules(), "worldRules");
        requireNonEmptyList(command.characterKnowledgeBoundaries(), "characterKnowledgeBoundaries");
        if (command.maxRevisionRounds() < 0) {
            throw new IllegalArgumentException("maxRevisionRounds must be greater than or equal to 0");
        }
        if (command.currentRevisionRound() < 0 || command.currentRevisionRound() > command.maxRevisionRounds()) {
            throw new IllegalArgumentException("currentRevisionRound must be between 0 and maxRevisionRounds");
        }
    }

    private List<String> toStringList(List<?> values) {
        return values.stream()
                .filter(value -> value != null)
                .map(String::valueOf)
                .toList();
    }

    private int integerOrZero(Map<String, Object> values, String key) {
        Integer value = JsonValues.integerValue(values, key);
        return value == null ? 0 : value;
    }

    private void requireNonEmptyList(List<String> values, String fieldName) {
        if (values == null || values.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be empty");
        }
        boolean hasNonBlank = values.stream().anyMatch(value -> value != null && !value.isBlank());
        if (!hasNonBlank) {
            throw new IllegalArgumentException(fieldName + " must contain at least one non-blank item");
        }
    }
}

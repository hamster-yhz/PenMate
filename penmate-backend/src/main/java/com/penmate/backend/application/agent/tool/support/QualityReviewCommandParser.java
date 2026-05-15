package com.penmate.backend.application.agent.tool.support;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import com.penmate.backend.infrastructure.agent.codec.AgentJsonCodec;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class QualityReviewCommandParser {

    public QualityReviewCommand parseAndValidate(String toolArgsJson) {
        QualityReviewCommand command = parse(toolArgsJson);
        validate(command);
        return command;
    }

    public QualityReviewCommand parse(String toolArgsJson) {
        try {
            JSONObject args = AgentJsonCodec.parseObj(toolArgsJson);
            return new QualityReviewCommand(
                    AgentJsonCodec.getString(args, "draftText"),
                    toStringList(args.getJSONArray("userRequirements")),
                    toStringList(args.getJSONArray("personaProfile")),
                    toStringList(args.getJSONArray("storyOutline")),
                    toStringList(args.getJSONArray("timelineConstraints")),
                    toStringList(args.getJSONArray("worldRules")),
                    toStringList(args.getJSONArray("characterKnowledgeBoundaries")),
                    args.getInt("currentRevisionRound", 0),
                    args.getInt("maxRevisionRounds", 0)
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

    private List<String> toStringList(JSONArray array) {
        return array == null ? List.of() : array.toList(String.class);
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

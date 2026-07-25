package com.penmate.backend.application.agent.tool.runtime;

import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.penmate.backend.infrastructure.serialization.JacksonJsonCodec;

import static org.assertj.core.api.Assertions.assertThat;

class ToolApprovalPreviewTest {

    @Test
    void extracts_the_first_affected_story_bible_node_without_copying_node_content() {
        var preview = new ToolApprovalPreview(new JacksonJsonCodec(new ObjectMapper()),
                java.util.List.of(new StoryBibleUpdateApprovalPreviewProvider()))
                .from("story_bible_update", """
                {"operation":"batch","operations":[
                  {"kind":"update_node","nodeId":71,"bodyMarkdown":"private body"},
                  {"kind":"update_progression","nodeId":72}
                ]}
                """);

        assertThat(preview).containsEntry("operation", "batch")
                .containsEntry("kind", "update_node")
                .containsEntry("nodeId", "71")
                .containsEntry("operationCount", "2")
                .doesNotContainValue("private body");
    }
}

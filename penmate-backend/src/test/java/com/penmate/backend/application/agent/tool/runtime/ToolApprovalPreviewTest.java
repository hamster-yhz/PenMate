package com.penmate.backend.application.agent.tool.runtime;

import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.penmate.backend.infrastructure.serialization.JacksonJsonCodec;

import static org.assertj.core.api.Assertions.assertThat;

class ToolApprovalPreviewTest {

    @Test
    void previews_a_node_write_without_copying_node_content() {
        var preview = new ToolApprovalPreview(new JacksonJsonCodec(new ObjectMapper()),
                java.util.List.of(new StoryBibleV2ApprovalPreviewConfiguration()
                        .storyBibleNodeWriteApprovalPreview()))
                .from("story_bible_node_write", """
                {"operation":"update","nodeId":71,"expectedRevision":4,"bodyMarkdown":"private body"}
                """);

        assertThat(preview).containsEntry("operation", "update")
                .containsEntry("nodeId", "71")
                .containsEntry("expectedRevision", "4")
                .doesNotContainValue("private body");
    }
}

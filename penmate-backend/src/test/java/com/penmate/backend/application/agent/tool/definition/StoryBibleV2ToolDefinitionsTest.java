package com.penmate.backend.application.agent.tool.definition;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.penmate.backend.infrastructure.agent.tool.NetworkntAgentToolSchemaValidator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StoryBibleV2ToolDefinitionsTest {

    @Test
    void exposes_narrow_story_bible_tools() {
        assertThat(new StoryBibleInspectToolDefinition().descriptor().exposure().lifecycleStatus())
                .isEqualTo(ToolLifecycleStatus.ACTIVE);
        assertThat(new StoryBibleNodeWriteToolDefinition().descriptor().toolCode())
                .isEqualTo("story_bible_node_write");
        assertThat(new StoryBibleRelationWriteToolDefinition().descriptor().toolCode())
                .isEqualTo("story_bible_relation_write");
        assertThat(new StoryBibleProgressionWriteToolDefinition().descriptor().toolCode())
                .isEqualTo("story_bible_progression_write");
        assertThat(new StoryBibleStructureWriteToolDefinition().descriptor().exposure().executionProfiles())
                .containsExactly("world-build");
    }

    @Test
    void node_tool_schema_accepts_structured_attributes_and_rejects_unknown_arguments() {
        AgentToolDescriptor descriptor = new StoryBibleNodeWriteToolDefinition().descriptor();
        NetworkntAgentToolSchemaValidator validator = new NetworkntAgentToolSchemaValidator(new ObjectMapper());
        validator.register(descriptor.toolCode(), descriptor.exposure().parametersJsonSchema());

        validator.validate(descriptor.toolCode(), """
                {"operation":"update","nodeId":71,"expectedRevision":3,
                 "attributes":{"pointOfView":true,"traits":["patient"]}}
                """);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> validator.validate(
                        descriptor.toolCode(), "{\"operation\":\"update\",\"rawJson\":\"{}\"}"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

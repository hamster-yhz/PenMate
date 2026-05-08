package com.penmate.backend.interfaces.config;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class OpenApiArtifactContractTest {

    @Test
    void should_keep_agent_business_ids_as_string_in_generated_openapi_artifact() throws Exception {
        String openApi = Files.readString(Path.of("..", "docs", "tmp", "openapi.json"), StandardCharsets.UTF_8);

        assertThat(openApi).contains("/api/v1/novels/{projectId}/agent/sessions");
        assertThat(openApi).contains("/api/v1/novels/{projectId}/agent/sessions/{sessionId}/recovery");
        assertThat(openApi).contains("/api/v1/novels/{projectId}/agent/sessions/{sessionId}/resume");
        assertThat(openApi).contains("/api/v1/novels/{projectId}/agent/sessions/{sessionId}/turns");
        assertThat(openApi).contains("\"userId\":{\"type\":\"string\"");
        assertThat(openApi).contains("\"operatorId\":{\"type\":\"string\"");
        assertThat(openApi).contains("\"chapterId\":{\"type\":\"string\"");
        assertThat(openApi).contains("\"name\":\"projectId\",\"in\":\"path\"");
        assertThat(openApi).contains("\"name\":\"sessionId\",\"in\":\"path\"");
        assertThat(openApi).contains("\"schema\":{\"type\":\"string\"");
    }
}

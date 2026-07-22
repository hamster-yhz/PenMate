package com.penmate.backend.infrastructure.serialization;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JacksonJsonCodecTest {

    private final JacksonJsonCodec codec = new JacksonJsonCodec(new ObjectMapper());

    @Test
    void reads_objects_and_writes_structured_values() {
        Map<String, Object> payload = codec.readObject("{\"projectId\":42,\"operation\":\"DELETE\"}");

        assertThat(payload).containsEntry("projectId", 42).containsEntry("operation", "DELETE");
        assertThat(codec.readObject(codec.write(Map.of("status", "DONE", "documentId", 7L))))
                .containsEntry("documentId", 7)
                .containsEntry("status", "DONE");
        Sample sample = codec.read("{\"name\":\"Mira\",\"rank\":7}", Sample.class);
        assertThat(sample).isEqualTo(new Sample("Mira", 7));
        assertThat(codec.read("[1,2,3]")).isEqualTo(java.util.List.of(1, 2, 3));
    }

    @Test
    void rejects_invalid_json_objects_at_the_adapter_boundary() {
        assertThatThrownBy(() -> codec.readObject("not-json"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid JSON object");
    }

    private record Sample(String name, int rank) {
    }
}

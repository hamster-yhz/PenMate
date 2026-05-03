package com.penmate.backend.infrastructure.agent.codec;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentJsonCodecTest {

    @Test
    void should_parse_object_array_and_keep_stable_json_string() {
        JSONObject object = AgentJsonCodec.parseObj("{\"tool\":\"context_enhancer\",\"enabled\":true}");
        JSONArray array = AgentJsonCodec.parseArray("[{\"id\":\"call_1\"}]");

        assertEquals("context_enhancer", AgentJsonCodec.getString(object, "tool"));
        assertTrue(AgentJsonCodec.getBool(object, "enabled"));
        assertEquals("call_1", array.getJSONObject(0).getStr("id"));
        assertEquals("{\"tool\":\"context_enhancer\",\"enabled\":true}", AgentJsonCodec.toJson(object));
    }

    @Test
    void should_return_empty_structures_for_blank_json() {
        assertTrue(AgentJsonCodec.parseObj(null).isEmpty());
        assertTrue(AgentJsonCodec.parseObj(" ").isEmpty());
        assertTrue(AgentJsonCodec.parseArray(null).isEmpty());
        assertTrue(AgentJsonCodec.parseArray(" ").isEmpty());
    }

    @Test
    void should_keep_null_fields_when_serializing_with_unified_config() {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("tool", "context_enhancer");
        value.put("enabled", null);

        assertEquals("{\"tool\":\"context_enhancer\",\"enabled\":null}", AgentJsonCodec.toJson(value));
    }

    @Test
    void should_keep_raw_string_input_unchanged() {
        assertEquals("style", AgentJsonCodec.toJson("style"));
    }

    @Test
    void should_return_safe_defaults_for_missing_or_null_json_values() {
        JSONObject object = AgentJsonCodec.parseObj("{}");

        assertEquals("", AgentJsonCodec.getString(null, "tool"));
        assertEquals("", AgentJsonCodec.getString(object, "tool"));
        assertFalse(AgentJsonCodec.getBool(null, "enabled"));
        assertFalse(AgentJsonCodec.getBool(object, "enabled"));
    }
}

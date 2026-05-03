package com.penmate.backend.application.agent.json;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentJsonsTest {

    @Test
    void should_parse_object_array_and_keep_stable_json_string() {
        JSONObject object = AgentJsons.parseObj("{\"tool\":\"context_enhancer\",\"enabled\":true}");
        JSONArray array = AgentJsons.parseArray("[{\"id\":\"call_1\"}]");

        assertEquals("context_enhancer", AgentJsons.getString(object, "tool"));
        assertTrue(AgentJsons.getBool(object, "enabled"));
        assertEquals("call_1", array.getJSONObject(0).getStr("id"));
        assertEquals("{\"tool\":\"context_enhancer\",\"enabled\":true}", AgentJsons.toJson(object));
    }

    @Test
    void should_return_empty_structures_for_blank_json() {
        assertTrue(AgentJsons.parseObj(null).isEmpty());
        assertTrue(AgentJsons.parseObj(" ").isEmpty());
        assertTrue(AgentJsons.parseArray(null).isEmpty());
        assertTrue(AgentJsons.parseArray(" ").isEmpty());
    }

    @Test
    void should_keep_null_fields_when_serializing_with_unified_config() {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("tool", "context_enhancer");
        value.put("enabled", null);

        assertEquals("{\"tool\":\"context_enhancer\",\"enabled\":null}", AgentJsons.toJson(value));
    }

    @Test
    void should_keep_raw_string_input_unchanged() {
        assertEquals("style", AgentJsons.toJson("style"));
    }

    @Test
    void should_return_safe_defaults_for_missing_or_null_json_values() {
        JSONObject object = AgentJsons.parseObj("{}");

        assertEquals("", AgentJsons.getString(null, "tool"));
        assertEquals("", AgentJsons.getString(object, "tool"));
        assertFalse(AgentJsons.getBool(null, "enabled"));
        assertFalse(AgentJsons.getBool(object, "enabled"));
    }
}

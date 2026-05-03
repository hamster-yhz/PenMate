package com.penmate.backend.application.agent.json;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONConfig;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;

public final class AgentJsons {

    private static final JSONConfig CONFIG = JSONConfig.create().setIgnoreNullValue(false);

    private AgentJsons() {
    }

    public static JSONObject parseObj(String raw) {
        if (raw == null || raw.isBlank()) {
            return JSONUtil.parseObj("{}", CONFIG);
        }
        return JSONUtil.parseObj(raw, CONFIG);
    }

    public static JSONArray parseArray(String raw) {
        if (raw == null || raw.isBlank()) {
            return JSONUtil.parseArray("[]", CONFIG);
        }
        return JSONUtil.parseArray(raw, CONFIG);
    }

    public static String toJson(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof String str) {
            return str;
        }
        return JSONUtil.parse(value, CONFIG).toString();
    }

    public static String getString(JSONObject object, String key) {
        return object == null ? "" : object.getStr(key, "");
    }

    public static boolean getBool(JSONObject object, String key) {
        return object != null && Boolean.TRUE.equals(object.getBool(key, false));
    }
}

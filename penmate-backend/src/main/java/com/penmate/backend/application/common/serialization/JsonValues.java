package com.penmate.backend.application.common.serialization;

import java.util.List;
import java.util.Map;

public final class JsonValues {

    private JsonValues() {
    }

    public static String string(Map<?, ?> values, String key) {
        Object value = values == null ? null : values.get(key);
        if (value == null) return "";
        if (value instanceof String text) return text;
        if (value instanceof Number || value instanceof Boolean) return String.valueOf(value);
        return "";
    }

    public static String nullableString(Map<?, ?> values, String key) {
        if (values == null || !values.containsKey(key) || values.get(key) == null) return null;
        return string(values, key);
    }

    public static boolean booleanValue(Map<?, ?> values, String key) {
        Object value = values == null ? null : values.get(key);
        if (value instanceof Boolean bool) return bool;
        return value instanceof String text && Boolean.parseBoolean(text);
    }

    public static Long longValue(Map<?, ?> values, String key) {
        Object value = values == null ? null : values.get(key);
        if (value == null) return null;
        if (value instanceof Number number) return number.longValue();
        if (value instanceof String text) {
            try {
                return Long.parseLong(text.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    public static Integer integerValue(Map<?, ?> values, String key) {
        Long value = longValue(values, key);
        if (value == null || value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) return null;
        return value.intValue();
    }

    public static List<?> list(Map<?, ?> values, String key) {
        Object value = values == null ? null : values.get(key);
        return value instanceof List<?> list ? list : List.of();
    }
}

package com.penmate.backend.application.common.serialization;

import java.util.Map;
import java.util.List;

public interface JsonCodec {

    Object read(String json);

    <T> T read(String json, Class<T> type);

    <T> List<T> readList(String json, Class<T> elementType);

    Map<String, Object> readObject(String json);

    String write(Object value);

    String writeCanonical(Object value);
}

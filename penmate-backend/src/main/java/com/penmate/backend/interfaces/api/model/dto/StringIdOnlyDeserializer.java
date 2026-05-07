package com.penmate.backend.interfaces.api.model.dto;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;

import java.io.IOException;

/**
 * 仅接受 JSON 字符串类型的业务 ID 反序列化器。
 */
public class StringIdOnlyDeserializer extends JsonDeserializer<String> {

    @Override
    public String deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        if (parser.currentToken() != JsonToken.VALUE_STRING) {
            throw InvalidFormatException.from(parser, "ID must be provided as a JSON string", parser.getText(), String.class);
        }
        return parser.getValueAsString();
    }
}

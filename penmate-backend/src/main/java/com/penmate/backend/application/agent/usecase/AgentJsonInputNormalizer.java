package com.penmate.backend.application.agent.usecase;

import cn.hutool.json.JSONUtil;
import com.penmate.backend.infrastructure.agent.codec.AgentJsonCodec;
import org.springframework.stereotype.Component;

@Component
public class AgentJsonInputNormalizer {

    public String normalize(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return trimmed;
        }
        try {
            if (trimmed.startsWith("{")) {
                return AgentJsonCodec.toJson(AgentJsonCodec.parseObj(trimmed));
            }
            if (trimmed.startsWith("[")) {
                return AgentJsonCodec.toJson(AgentJsonCodec.parseArray(trimmed));
            }
            if (JSONUtil.isTypeJSON(trimmed)) {
                return trimmed;
            }
            return trimmed;
        } catch (Exception ex) {
            return trimmed;
        }
    }
}

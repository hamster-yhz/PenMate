package com.penmate.backend.application.agent.usecase;

import com.penmate.backend.application.common.serialization.JsonCodec;
import org.springframework.stereotype.Component;

/**
 * Agent JSON 输入规范化器。
 * <p>用于把接口层传入的 JSON 字符串压缩为稳定格式，减少等价 JSON 因空白或格式差异带来的快照噪声。</p>
 * <p>若输入不是合法 JSON，则保持原始文本返回，避免因过度校验阻断用例。</p>
 */
@Component
public class AgentJsonInputNormalizer {

    private final JsonCodec jsonCodec;

    public AgentJsonInputNormalizer(JsonCodec jsonCodec) {
        this.jsonCodec = jsonCodec;
    }

    public String normalize(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return trimmed;
        }
        try {
            if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
                return jsonCodec.write(jsonCodec.read(trimmed));
            }
            return trimmed;
        } catch (Exception ex) {
            return trimmed;
        }
    }
}

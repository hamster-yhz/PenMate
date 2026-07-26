package com.penmate.backend.infrastructure.model;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.penmate.backend.application.common.exception.BusinessException;
import com.penmate.backend.domain.model.service.ModelCatalogGateway;
import com.penmate.backend.domain.model.service.ModelEndpointPolicy;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Component
public class HttpModelCatalogGateway implements ModelCatalogGateway {
    private static final int MAX_MODELS = 500;
    private static final int MAX_RESPONSE_BYTES = 2 * 1024 * 1024;

    private final ObjectMapper objectMapper;
    private final ModelEndpointPolicy endpointPolicy;
    private final HttpClient httpClient;
    private final Duration responseTimeout;

    public HttpModelCatalogGateway(ObjectMapper objectMapper, ModelEndpointPolicy endpointPolicy,
                                   @Qualifier("modelCatalogHttpClient") HttpClient httpClient,
                                   @Value("${penmate.model.catalog-response-timeout:15s}") Duration responseTimeout) {
        this.objectMapper = objectMapper;
        this.endpointPolicy = endpointPolicy;
        this.httpClient = httpClient;
        this.responseTimeout = responseTimeout;
    }

    @Override
    public List<String> discover(DiscoveryRequest request) {
        String baseUrl = endpointPolicy.validate(request.baseUrl(), request.systemScope());
        URI endpoint = catalogEndpoint(baseUrl, request.providerCode());
        HttpRequest.Builder builder = HttpRequest.newBuilder(endpoint).timeout(responseTimeout)
                .header("Accept", "application/json").GET();
        applyAuthentication(builder, request);
        try {
            HttpResponse<InputStream> response = httpClient.send(
                    builder.build(), HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream body = response.body()) {
                if (response.statusCode() >= 300 && response.statusCode() < 400) {
                    throw BusinessException.of("模型站点返回了重定向，请检查 Base URL");
                }
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    throw catalogFailure(response.statusCode());
                }
                byte[] bytes = body.readNBytes(MAX_RESPONSE_BYTES + 1);
                if (bytes.length > MAX_RESPONSE_BYTES) {
                    throw BusinessException.of("站点返回的模型列表过大");
                }
                return parse(new String(bytes, StandardCharsets.UTF_8));
            }
        } catch (IOException exception) {
            throw BusinessException.of("无法连接模型站点，请检查 Base URL 和网络");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw BusinessException.of("模型探测请求已中断");
        }
    }

    private BusinessException catalogFailure(int statusCode) {
        return switch (statusCode) {
            case 401, 403 -> BusinessException.of("站点鉴权失败，请检查 API Key");
            case 404 -> BusinessException.of("站点不支持模型列表接口，请检查 Base URL");
            case 429 -> BusinessException.of("站点请求过于频繁，请稍后重试");
            default -> BusinessException.of("模型站点返回 HTTP " + statusCode);
        };
    }

    private void applyAuthentication(HttpRequest.Builder builder, DiscoveryRequest request) {
        if (request.apiKey() == null || request.apiKey().isBlank()
                || "NONE".equalsIgnoreCase(request.authType())) return;
        String providerCode = request.providerCode() == null
                ? "" : request.providerCode().toLowerCase(Locale.ROOT);
        if (providerCode.contains("anthropic") || providerCode.contains("claude")) {
            builder.header("x-api-key", request.apiKey()).header("anthropic-version", "2023-06-01");
        } else if ((providerCode.contains("gemini") || providerCode.contains("google"))
                && !request.baseUrl().toLowerCase(Locale.ROOT).contains("/openai")) {
            builder.header("x-goog-api-key", request.apiKey());
        } else {
            builder.header("Authorization", "Bearer " + request.apiKey());
        }
    }

    private URI catalogEndpoint(String baseUrl, String providerCode) {
        URI base = URI.create(baseUrl);
        String path = base.getPath() == null ? "" : base.getPath();
        path = path.replaceFirst("(?i)/(chat/completions|responses|embeddings|models)/?$", "");
        String code = providerCode == null ? "" : providerCode.toLowerCase(Locale.ROOT);
        if ((code.contains("anthropic") || code.contains("claude"))
                && !path.matches("(?i).*/v1$")) path = path.replaceAll("/+$", "") + "/v1";
        path = path.replaceAll("/+$", "") + "/models";
        try {
            return new URI(base.getScheme(), null, base.getHost(), base.getPort(), path, null, null);
        } catch (Exception exception) {
            throw BusinessException.badRequest("Model Base URL is invalid");
        }
    }

    private List<String> parse(String body) {
        JsonNode root;
        try {
            root = objectMapper.readTree(body);
        } catch (JsonProcessingException exception) {
            throw BusinessException.of("站点返回的模型列表不是有效 JSON");
        }
        JsonNode rows = root.isArray() ? root : root.path("data");
        if (!rows.isArray()) rows = root.path("models");
        if (!rows.isArray()) throw BusinessException.of("站点响应中没有可识别的模型列表");

        Set<String> unique = new LinkedHashSet<>();
        for (JsonNode row : rows) {
            String id = row.isTextual() ? row.asText() : text(row, "id", "name", "model");
            if (id != null && id.startsWith("models/")) id = id.substring("models/".length());
            if (id != null && !id.isBlank() && id.length() <= 200) unique.add(id.trim());
            if (unique.size() >= MAX_MODELS) break;
        }
        List<String> result = new ArrayList<>(unique);
        result.sort(Comparator.comparing(value -> value.toLowerCase(Locale.ROOT)));
        return List.copyOf(result);
    }

    private String text(JsonNode node, String... fields) {
        for (String field : fields) {
            JsonNode value = node.get(field);
            if (value != null && value.isTextual()) return value.asText();
        }
        return null;
    }
}

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
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
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

    @Override
    public Optional<ModelCapacity> probeCapacity(DiscoveryRequest request, String modelName) {
        if (modelName == null || modelName.isBlank()) return Optional.empty();
        String baseUrl = endpointPolicy.validate(request.baseUrl(), request.systemScope());
        String providerCode = request.providerCode() == null
                ? "" : request.providerCode().toLowerCase(Locale.ROOT);
        try {
            if (providerCode.contains("ollama")) {
                URI endpoint = ollamaShowEndpoint(baseUrl);
                String body = objectMapper.writeValueAsString(Map.of("model", modelName.trim()));
                return parseOllamaCapacity(sendJson(request, endpoint, "POST", body), endpoint);
            }
            URI endpoint = catalogEndpoint(baseUrl, request.providerCode());
            return parseCatalogCapacity(sendJson(request, endpoint, "GET", null), modelName, endpoint);
        } catch (IOException exception) {
            return Optional.empty();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }

    private JsonNode sendJson(DiscoveryRequest request, URI endpoint, String method, String body)
            throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder(endpoint).timeout(responseTimeout)
                .header("Accept", "application/json");
        applyAuthentication(builder, request);
        if ("POST".equals(method)) {
            builder.header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body == null ? "{}" : body));
        } else {
            builder.GET();
        }
        HttpResponse<InputStream> response = httpClient.send(
                builder.build(), HttpResponse.BodyHandlers.ofInputStream());
        try (InputStream responseBody = response.body()) {
            if (response.statusCode() < 200 || response.statusCode() >= 300) return null;
            byte[] bytes = responseBody.readNBytes(MAX_RESPONSE_BYTES + 1);
            if (bytes.length > MAX_RESPONSE_BYTES) return null;
            return objectMapper.readTree(bytes);
        }
    }

    private Optional<ModelCapacity> parseCatalogCapacity(JsonNode root, String modelName, URI endpoint) {
        if (root == null) return Optional.empty();
        JsonNode rows = root.isArray() ? root : root.path("data");
        if (!rows.isArray()) rows = root.path("models");
        if (!rows.isArray()) return Optional.empty();
        String expected = normalizeModelName(modelName);
        for (JsonNode row : rows) {
            String id = row.isTextual() ? row.asText() : text(row, "id", "name", "model");
            if (!expected.equals(normalizeModelName(id))) continue;
            int context = firstPositive(row,
                    "context_length", "max_context_tokens", "max_input_tokens",
                    "inputTokenLimit", "input_token_limit", "max_model_len");
            if (context <= 0) context = firstPositive(row.path("top_provider"), "context_length");
            if (context <= 0) return Optional.empty();
            int output = firstPositive(row,
                    "max_completion_tokens", "max_output_tokens", "outputTokenLimit", "output_token_limit");
            if (output <= 0) output = firstPositive(row.path("top_provider"), "max_completion_tokens");
            return Optional.of(new ModelCapacity(context, output > 0 ? output : null,
                    endpoint.toString(), Instant.now()));
        }
        return Optional.empty();
    }

    private Optional<ModelCapacity> parseOllamaCapacity(JsonNode root, URI endpoint) {
        if (root == null) return Optional.empty();
        JsonNode modelInfo = root.path("model_info");
        if (!modelInfo.isObject()) return Optional.empty();
        var fields = modelInfo.fields();
        while (fields.hasNext()) {
            var field = fields.next();
            if (field.getKey().toLowerCase(Locale.ROOT).endsWith(".context_length")) {
                int context = positiveInt(field.getValue());
                if (context > 0) {
                    return Optional.of(new ModelCapacity(context, null, endpoint.toString(), Instant.now()));
                }
            }
        }
        return Optional.empty();
    }

    private int firstPositive(JsonNode node, String... fields) {
        if (node == null || !node.isObject()) return 0;
        for (String field : fields) {
            int value = positiveInt(node.get(field));
            if (value > 0) return value;
        }
        return 0;
    }

    private int positiveInt(JsonNode value) {
        if (value == null) return 0;
        long parsed;
        if (value.isIntegralNumber()) parsed = value.asLong();
        else if (value.isTextual()) {
            try {
                parsed = Long.parseLong(value.asText().trim());
            } catch (NumberFormatException exception) {
                return 0;
            }
        } else return 0;
        return parsed > 0 && parsed <= Integer.MAX_VALUE ? (int) parsed : 0;
    }

    private String normalizeModelName(String value) {
        String model = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        return model.startsWith("models/") ? model.substring("models/".length()) : model;
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

    private URI ollamaShowEndpoint(String baseUrl) {
        URI base = URI.create(baseUrl);
        String path = base.getPath() == null ? "" : base.getPath();
        path = path.replaceFirst("(?i)/(v1|api/(show|tags)|chat/completions|embeddings|models)/?$", "");
        path = path.replaceAll("/+$", "") + "/api/show";
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

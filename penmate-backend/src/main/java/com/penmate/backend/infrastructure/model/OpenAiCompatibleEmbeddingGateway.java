package com.penmate.backend.infrastructure.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.penmate.backend.application.common.exception.BusinessException;
import com.penmate.backend.domain.model.service.ModelEndpointPolicy;
import com.penmate.backend.domain.rag.service.EmbeddingGateway;
import com.penmate.backend.domain.rag.service.EmbeddingDimensionProbeGateway;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

@Component
public class OpenAiCompatibleEmbeddingGateway implements EmbeddingGateway, EmbeddingDimensionProbeGateway {
    private final ObjectMapper objectMapper;
    private final ModelEndpointPolicy endpointPolicy;
    private final HttpClient client;
    private final HttpClient probeClient;
    private final Duration responseTimeout;
    private final Duration probeResponseTimeout;

    public OpenAiCompatibleEmbeddingGateway(ObjectMapper objectMapper, ModelEndpointPolicy endpointPolicy,
                                            @Value("${penmate.indexing.embedding-connect-timeout:10s}") Duration connectTimeout,
                                            @Value("${penmate.indexing.embedding-response-timeout:60s}") Duration responseTimeout,
                                            @Value("${penmate.model.embedding-probe-connect-timeout:5s}") Duration probeConnectTimeout,
                                            @Value("${penmate.model.embedding-probe-response-timeout:15s}") Duration probeResponseTimeout) {
        this.objectMapper = objectMapper;
        this.endpointPolicy = endpointPolicy;
        this.client = HttpClient.newBuilder().connectTimeout(connectTimeout)
                .followRedirects(HttpClient.Redirect.NEVER).build();
        this.probeClient = HttpClient.newBuilder().connectTimeout(probeConnectTimeout)
                .followRedirects(HttpClient.Redirect.NEVER).build();
        this.responseTimeout = responseTimeout;
        this.probeResponseTimeout = probeResponseTimeout;
    }

    @Override
    public List<float[]> embed(EmbeddingRequest request) {
        if (request.inputs() == null || request.inputs().isEmpty()) return List.of();
        RuntimeException lastFailure = null;
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                return execute(request, client, responseTimeout);
            } catch (RetryableEmbeddingException exception) {
                lastFailure = exception;
                if (attempt < 3) pause(attempt);
            }
        }
        throw lastFailure == null ? BusinessException.of("Embedding request failed") : lastFailure;
    }

    @Override
    public int probe(ProbeRequest request) {
        EmbeddingRequest embeddingRequest = new EmbeddingRequest(request.baseUrl(), request.apiKey(),
                request.modelName(), request.systemScope(), List.of("PenMate dimension probe"), request.dimensions());
        List<float[]> result = execute(embeddingRequest, probeClient, probeResponseTimeout);
        if (result.size() != 1) throw BusinessException.of("Embedding probe returned an invalid response count");
        return result.getFirst().length;
    }

    private List<float[]> execute(EmbeddingRequest request, HttpClient httpClient, Duration timeout) {
        String baseUrl = endpointPolicy.validate(request.baseUrl(), request.systemScope());
        URI endpoint = URI.create(baseUrl + (baseUrl.endsWith("/embeddings") ? "" : "/embeddings"));
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", request.modelName());
            body.put("input", request.inputs());
            if (request.dimensions() != null) body.put("dimensions", request.dimensions());
            String payload = objectMapper.writeValueAsString(body);
            HttpRequest.Builder builder = HttpRequest.newBuilder(endpoint).timeout(timeout)
                    .header("Content-Type", "application/json").header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload));
            if (request.apiKey() != null && !request.apiKey().isBlank()) builder.header("Authorization", "Bearer " + request.apiKey());
            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 300 && response.statusCode() < 400) {
                throw BusinessException.of("Embedding endpoint redirects are not allowed");
            }
            if (response.statusCode() == 429 || response.statusCode() >= 500) {
                throw new RetryableEmbeddingException("Embedding endpoint returned HTTP " + response.statusCode());
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                if (request.dimensions() != null && (response.statusCode() == 400 || response.statusCode() == 422)) {
                    throw BusinessException.of(com.penmate.backend.application.common.exception.BusinessErrorType.BUSINESS_RULE,
                            "EMBEDDING_DIMENSIONS_UNSUPPORTED",
                            "Embedding endpoint does not support the requested dimensions", null);
                }
                throw BusinessException.of("Embedding endpoint returned HTTP " + response.statusCode());
            }
            return parse(response.body(), request.inputs().size(), request.dimensions());
        } catch (IOException exception) {
            throw new RetryableEmbeddingException("Embedding endpoint connection failed", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw BusinessException.of("Embedding request was interrupted");
        }
    }

    private List<float[]> parse(String body, int expectedCount, Integer expectedDimension) {
        JsonNode root;
        try {
            root = objectMapper.readTree(body);
        } catch (JsonProcessingException exception) {
            throw BusinessException.of("Embedding response is not valid JSON");
        }
        JsonNode data = root.path("data");
        if (!data.isArray() || data.size() != expectedCount) throw BusinessException.of("Embedding response count does not match input count");
        List<JsonNode> ordered = new ArrayList<>();
        data.forEach(ordered::add);
        ordered.sort(Comparator.comparingInt(node -> node.path("index").asInt(Integer.MAX_VALUE)));
        List<float[]> result = new ArrayList<>(expectedCount);
        Integer dimension = null;
        for (JsonNode item : ordered) {
            JsonNode embedding = item.path("embedding");
            if (!embedding.isArray() || embedding.isEmpty()) throw BusinessException.of("Embedding response contains an empty vector");
            if (dimension == null) dimension = embedding.size();
            if (embedding.size() != dimension || dimension > 4000) throw BusinessException.of("Embedding response dimension is invalid or inconsistent");
            if (expectedDimension != null && embedding.size() != expectedDimension) {
                throw BusinessException.of(com.penmate.backend.application.common.exception.BusinessErrorType.BUSINESS_RULE,
                        "EMBEDDING_DIMENSION_MISMATCH",
                        "Embedding endpoint returned " + embedding.size() + " dimensions; expected " + expectedDimension,
                        Map.of("expectedDimensions", expectedDimension, "actualDimensions", embedding.size()));
            }
            float[] vector = new float[dimension];
            for (int i = 0; i < dimension; i++) {
                if (!embedding.get(i).isNumber()) throw BusinessException.of("Embedding response contains a non-numeric value");
                vector[i] = embedding.get(i).floatValue();
                if (!Float.isFinite(vector[i])) throw BusinessException.of("Embedding response contains a non-finite value");
            }
            result.add(vector);
        }
        return List.copyOf(result);
    }

    private void pause(int attempt) {
        long base = switch (attempt) { case 1 -> 1000L; case 2 -> 2000L; default -> 4000L; };
        try {
            Thread.sleep(base + ThreadLocalRandom.current().nextLong(100, 401));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw BusinessException.of("Embedding retry was interrupted");
        }
    }

    private static final class RetryableEmbeddingException extends RuntimeException {
        private RetryableEmbeddingException(String message) { super(message); }
        private RetryableEmbeddingException(String message, Throwable cause) { super(message, cause); }
    }
}

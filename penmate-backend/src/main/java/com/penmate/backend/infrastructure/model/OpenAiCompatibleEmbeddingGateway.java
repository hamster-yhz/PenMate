package com.penmate.backend.infrastructure.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.penmate.backend.application.common.exception.BusinessException;
import com.penmate.backend.domain.model.service.ModelEndpointPolicy;
import com.penmate.backend.domain.rag.service.EmbeddingGateway;
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
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

@Component
public class OpenAiCompatibleEmbeddingGateway implements EmbeddingGateway {
    private final ObjectMapper objectMapper;
    private final ModelEndpointPolicy endpointPolicy;
    private final HttpClient client;
    private final Duration responseTimeout;

    public OpenAiCompatibleEmbeddingGateway(ObjectMapper objectMapper, ModelEndpointPolicy endpointPolicy,
                                            @Value("${penmate.indexing.embedding-connect-timeout:10s}") Duration connectTimeout,
                                            @Value("${penmate.indexing.embedding-response-timeout:60s}") Duration responseTimeout) {
        this.objectMapper = objectMapper;
        this.endpointPolicy = endpointPolicy;
        this.client = HttpClient.newBuilder().connectTimeout(connectTimeout)
                .followRedirects(HttpClient.Redirect.NEVER).build();
        this.responseTimeout = responseTimeout;
    }

    @Override
    public List<float[]> embed(EmbeddingRequest request) {
        if (request.inputs() == null || request.inputs().isEmpty()) return List.of();
        RuntimeException lastFailure = null;
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                return execute(request);
            } catch (RetryableEmbeddingException exception) {
                lastFailure = exception;
                if (attempt < 3) pause(attempt);
            }
        }
        throw lastFailure == null ? BusinessException.of("Embedding request failed") : lastFailure;
    }

    private List<float[]> execute(EmbeddingRequest request) {
        String baseUrl = endpointPolicy.validate(request.baseUrl(), request.systemScope());
        URI endpoint = URI.create(baseUrl + (baseUrl.endsWith("/embeddings") ? "" : "/embeddings"));
        try {
            String payload = objectMapper.writeValueAsString(Map.of("model", request.modelName(), "input", request.inputs()));
            HttpRequest.Builder builder = HttpRequest.newBuilder(endpoint).timeout(responseTimeout)
                    .header("Content-Type", "application/json").header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload));
            if (request.apiKey() != null && !request.apiKey().isBlank()) builder.header("Authorization", "Bearer " + request.apiKey());
            HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 300 && response.statusCode() < 400) {
                throw BusinessException.of("Embedding endpoint redirects are not allowed");
            }
            if (response.statusCode() == 429 || response.statusCode() >= 500) {
                throw new RetryableEmbeddingException("Embedding endpoint returned HTTP " + response.statusCode());
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw BusinessException.of("Embedding endpoint returned HTTP " + response.statusCode());
            }
            return parse(response.body(), request.inputs().size());
        } catch (IOException exception) {
            throw new RetryableEmbeddingException("Embedding endpoint connection failed", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw BusinessException.of("Embedding request was interrupted");
        }
    }

    private List<float[]> parse(String body, int expectedCount) {
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

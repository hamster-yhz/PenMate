package com.penmate.backend.infrastructure.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.penmate.backend.application.common.exception.BusinessException;
import com.penmate.backend.domain.model.service.ModelEndpointPolicy;
import com.penmate.backend.domain.rag.service.EmbeddingDimensionProbeGateway;
import com.penmate.backend.domain.rag.service.EmbeddingGateway;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OpenAiCompatibleEmbeddingGatewayTest {
    private HttpServer server;
    private OpenAiCompatibleEmbeddingGateway gateway;
    private String baseUrl;

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/v1";
        ModelEndpointPolicy policy = mock(ModelEndpointPolicy.class);
        when(policy.validate(anyString(), anyBoolean())).thenReturn(baseUrl);
        gateway = new OpenAiCompatibleEmbeddingGateway(new ObjectMapper(), policy,
                Duration.ofSeconds(1), Duration.ofSeconds(2), Duration.ofSeconds(1), Duration.ofSeconds(2));
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void sends_custom_dimensions_and_accepts_matching_response() {
        AtomicReference<String> requestBody = new AtomicReference<>();
        server.createContext("/v1/embeddings", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            respond(exchange, 200, "{\"data\":[{\"index\":0,\"embedding\":[0.1,0.2]}]}");
        });
        server.start();

        List<float[]> result = gateway.embed(new EmbeddingGateway.EmbeddingRequest(
                baseUrl, "key", "embedding-model", false, List.of("hello"), 2));

        assertThat(result.getFirst()).hasSize(2);
        assertThat(requestBody.get()).contains("\"dimensions\":2");
    }

    @Test
    void reports_stable_error_when_returned_dimension_does_not_match() {
        server.createContext("/v1/embeddings", exchange ->
                respond(exchange, 200, "{\"data\":[{\"index\":0,\"embedding\":[0.1,0.2,0.3]}]}"));
        server.start();

        assertThatThrownBy(() -> gateway.embed(new EmbeddingGateway.EmbeddingRequest(
                baseUrl, "key", "embedding-model", false, List.of("hello"), 2)))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo("EMBEDDING_DIMENSION_MISMATCH"));
    }

    @Test
    void probe_does_not_retry() {
        AtomicInteger requests = new AtomicInteger();
        server.createContext("/v1/embeddings", exchange -> {
            requests.incrementAndGet();
            respond(exchange, 500, "{}");
        });
        server.start();

        assertThatThrownBy(() -> gateway.probe(new EmbeddingDimensionProbeGateway.ProbeRequest(
                baseUrl, "key", "embedding-model", false, null))).isInstanceOf(RuntimeException.class);
        assertThat(requests).hasValue(1);
    }

    private void respond(com.sun.net.httpserver.HttpExchange exchange, int status, String body) throws java.io.IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}

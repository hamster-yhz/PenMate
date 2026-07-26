package com.penmate.backend.infrastructure.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.penmate.backend.domain.model.service.ModelCatalogGateway;
import com.penmate.backend.domain.model.service.ModelEndpointPolicy;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.time.Duration;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HttpModelCatalogGatewayTest {
    private HttpServer server;
    private String baseUrl;

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/v1";
        server.createContext("/v1/models", exchange -> {
            String bearer = exchange.getRequestHeaders().getFirst("Authorization");
            String apiKey = exchange.getRequestHeaders().getFirst("x-api-key");
            assertThat("Bearer secret".equals(bearer) || "secret".equals(apiKey)).isTrue();
            if (apiKey != null) assertThat(exchange.getRequestHeaders().getFirst("anthropic-version")).isEqualTo("2023-06-01");
            byte[] body = "{\"data\":[{\"id\":\"gpt-5\"},{\"id\":\"gpt-5\"},{\"id\":\"gpt-4.1\"}]}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.createContext("/denied/models", exchange -> {
            exchange.sendResponseHeaders(401, -1);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void tearDown() { if (server != null) server.stop(0); }

    @Test
    void validatesEndpointAndReturnsSortedDistinctModelIds() {
        ModelEndpointPolicy policy = (url, system) -> url;
        HttpModelCatalogGateway gateway = new HttpModelCatalogGateway(new ObjectMapper(), policy,
                HttpClient.newHttpClient(), Duration.ofSeconds(3));

        List<String> models = gateway.discover(new ModelCatalogGateway.DiscoveryRequest(
                baseUrl + "/chat/completions", "secret", "openai", "API_KEY", false));

        assertThat(models).containsExactly("gpt-4.1", "gpt-5");
    }

    @Test
    void usesTheAnthropicCatalogPathAndHeaders() {
        ModelEndpointPolicy policy = (url, system) -> url;
        HttpModelCatalogGateway gateway = new HttpModelCatalogGateway(new ObjectMapper(), policy,
                HttpClient.newHttpClient(), Duration.ofSeconds(3));

        List<String> models = gateway.discover(new ModelCatalogGateway.DiscoveryRequest(
                baseUrl.replace("/v1", ""), "secret", "claude", "API_KEY", true));

        assertThat(models).contains("gpt-5");
    }

    @Test
    void returnsAnActionableAuthenticationFailure() {
        ModelEndpointPolicy policy = (url, system) -> url;
        HttpModelCatalogGateway gateway = new HttpModelCatalogGateway(new ObjectMapper(), policy,
                HttpClient.newHttpClient(), Duration.ofSeconds(3));

        assertThatThrownBy(() -> gateway.discover(new ModelCatalogGateway.DiscoveryRequest(
                baseUrl.replace("/v1", "/denied"), "bad-secret", "openai", "API_KEY", false)))
                .hasMessage("站点鉴权失败，请检查 API Key");
    }
}

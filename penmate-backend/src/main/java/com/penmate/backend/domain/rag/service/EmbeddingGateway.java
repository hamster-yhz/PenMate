package com.penmate.backend.domain.rag.service;

import java.util.List;

public interface EmbeddingGateway {
    List<float[]> embed(EmbeddingRequest request);

    record EmbeddingRequest(String baseUrl, String apiKey, String modelName, boolean systemScope,
                            List<String> inputs, Integer dimensions) {
        public EmbeddingRequest(String baseUrl, String apiKey, String modelName, boolean systemScope,
                                List<String> inputs) {
            this(baseUrl, apiKey, modelName, systemScope, inputs, null);
        }
    }
}

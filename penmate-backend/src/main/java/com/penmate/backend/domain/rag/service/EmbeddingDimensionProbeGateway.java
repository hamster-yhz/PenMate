package com.penmate.backend.domain.rag.service;

public interface EmbeddingDimensionProbeGateway {

    int probe(ProbeRequest request);

    record ProbeRequest(String baseUrl, String apiKey, String modelName, boolean systemScope,
                        Integer dimensions) {
    }
}

package com.penmate.backend.domain.model.service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ModelCatalogGateway {

    List<String> discover(DiscoveryRequest request);

    Optional<ModelCapacity> probeCapacity(DiscoveryRequest request, String modelName);

    record DiscoveryRequest(String baseUrl, String apiKey, String providerCode,
                            String authType, boolean systemScope) {
    }

    record ModelCapacity(int maxContextTokens, Integer maxOutputTokens,
                         String sourceUrl, Instant verifiedAt) {
    }
}

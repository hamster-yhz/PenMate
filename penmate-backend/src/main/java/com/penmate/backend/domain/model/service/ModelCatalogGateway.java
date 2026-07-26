package com.penmate.backend.domain.model.service;

import java.util.List;

public interface ModelCatalogGateway {

    List<String> discover(DiscoveryRequest request);

    record DiscoveryRequest(String baseUrl, String apiKey, String providerCode,
                            String authType, boolean systemScope) {
    }
}

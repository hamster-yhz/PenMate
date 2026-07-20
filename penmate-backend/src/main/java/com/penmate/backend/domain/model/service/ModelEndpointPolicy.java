package com.penmate.backend.domain.model.service;

public interface ModelEndpointPolicy {
    String validate(String baseUrl, boolean systemScope);
}

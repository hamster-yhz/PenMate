package com.penmate.backend.infrastructure.model;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration(proxyBeanMethods = false)
public class ModelHttpClientConfiguration {

    @Bean
    HttpClient modelCatalogHttpClient(
            @Value("${penmate.model.catalog-connect-timeout:5s}") Duration connectTimeout) {
        return HttpClient.newBuilder()
                .connectTimeout(connectTimeout)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }
}

package com.penmate.backend.infrastructure.bootstrap;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "penmate.bootstrap")
public class SystemBootstrapProperties {

    private boolean reconcile;
    private Admin admin = new Admin();
    private Model model = new Model();

    @Data
    public static class Admin {
        private String email;
        private String password;
    }

    @Data
    public static class Model {
        private String provider;
        private String baseUrl;
        private String apiKey;
        private String modelName;
    }
}

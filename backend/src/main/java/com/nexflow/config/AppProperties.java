package com.nexflow.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@Data
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private boolean seedDemoData = false;
    private String uploadDir = "./uploads";
    private Ai ai = new Ai();
    private Cors cors = new Cors();
    private Jwt jwt = new Jwt();

    @Data
    public static class Ai {
        private boolean enabled = false;
    }

    @Data
    public static class Cors {
        private List<String> allowedOrigins = List.of("http://localhost:5173", "http://127.0.0.1:5173");
    }

    @Data
    public static class Jwt {
        private String secret = "nexflow-dev-secret-change-me-in-production-0123456789abcdef";
        private long expirationMs = 86400000L;
    }
}

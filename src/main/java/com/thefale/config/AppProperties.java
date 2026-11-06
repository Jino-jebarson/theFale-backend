package com.thefale.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Data
@Configuration
@ConfigurationProperties(prefix = "fale")
public class AppProperties {

    private Security security = new Security();
    private Cors cors = new Cors();
    private Storage storage = new Storage();
    private Auth auth = new Auth();

    @Data
    public static class Security {
        private Jwt jwt = new Jwt();

        @Data
        public static class Jwt {
            private String secret;
            private long accessTokenExpirationMs = 900000; // 15 mins
            private long refreshTokenExpirationMs = 604800000; // 7 days
            private String cookieName = "fale_refresh_token";
        }
    }

    @Data
    public static class Cors {
        private List<String> allowedOrigins = List.of("http://localhost:5173");
    }

    @Data
    public static class Storage {
        private String type = "local";
        private String localDir = "uploads";
    }

    @Data
    public static class Auth {
        private boolean devOtpEnabled = true;
    }
}

package org.example.apigateway.config;

import java.util.List;
import org.example.common.properties.FrontendProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.cors.CorsConfiguration;

import static org.assertj.core.api.Assertions.assertThat;

class CorsConfigUnitTest {

    @Test
    @DisplayName("브라우저 Client에 Rate Limit 응답 Header를 노출한다")
    void corsConfiguration_shouldExposeRateLimitHeaders() {
        FrontendProperties frontendProperties = new FrontendProperties(
                "https://frontend.example.com",
                new FrontendProperties.OAuth2("/success", "/failure")
        );

        CorsConfiguration configuration = new CorsConfig(frontendProperties).corsConfiguration();

        assertThat(configuration.getExposedHeaders()).containsAll(List.of(
                "X-RateLimit-Remaining",
                "X-RateLimit-Replenish-Rate",
                "X-RateLimit-Burst-Capacity",
                "X-RateLimit-Requested-Tokens"
        ));
    }
}

package org.example.apigateway.endpoint;

import org.example.common.test.config.TestBootApplication;
import org.example.apigateway.config.TestGatewayCorsConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        classes = {
                TestBootApplication.class,
                TestGatewayCorsConfig.class
        }
)
class GatewayCorsConfigTest {

    @Autowired
    private CorsConfigurationSource corsConfigurationSource;

    @Test
    @DisplayName("테스트 CORS 설정은 localhost:3000 Origin을 허용한다")
    void corsConfigurationSource_shouldAllowLocalhost3000() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/user/me")
                        .header(HttpHeaders.ORIGIN, "http://localhost:3000")
        );

        CorsConfiguration config = corsConfigurationSource.getCorsConfiguration(exchange);

        assertThat(config).isNotNull();
        assertThat(config.checkOrigin("http://localhost:3000"))
                .isEqualTo("http://localhost:3000");
        assertThat(config.getAllowCredentials()).isTrue();
    }
}
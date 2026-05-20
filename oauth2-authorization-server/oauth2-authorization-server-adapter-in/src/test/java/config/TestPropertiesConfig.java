package config;

import org.example.common.properties.JwtProperties;
import org.example.oauth2.properties.OAuth2RegisteredClientProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration
public class TestPropertiesConfig {

    @Bean
    @Primary
    public JwtProperties jwtProperties() {
        return new JwtProperties(
                "test-key",
                1,
                "http://localhost:9000",
                "http://localhost:9000/oauth2/jwks",
                "http://localhost:9000/sign",
                3_600_000L,
                604_800_000L,
                1_000,
                1_000
        );
    }

    @Bean
    @Primary
    public OAuth2RegisteredClientProperties oAuth2RegisteredClientProperties() {
        return new OAuth2RegisteredClientProperties(
                "my-client-id",
                "my-authorization-server",
                "my-client-secret"
        );
    }
}
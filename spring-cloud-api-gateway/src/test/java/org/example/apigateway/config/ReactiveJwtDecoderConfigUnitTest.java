package org.example.apigateway.config;

import java.time.Instant;
import java.util.List;
import org.example.apigateway.oauth2.validator.ReactiveBlacklistTokenValidator;
import org.example.common.properties.JwtProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ReactiveJwtDecoderConfigUnitTest {

    private static final String EXPECTED_ISSUER = "https://issuer.example";

    private ReactiveJwtDecoderConfig sut;

    @BeforeEach
    void setUp() {
        sut = new ReactiveJwtDecoderConfig(mock(ReactiveBlacklistTokenValidator.class));
    }

    @Test
    @DisplayName("설정된 issuer와 다른 JWT는 invalid_token으로 거부한다")
    void jwtValidator_shouldRejectJwt_whenIssuerDoesNotMatch() {
        JwtProperties properties = new JwtProperties(
                "test-key",
                1,
                EXPECTED_ISSUER,
                "https://issuer.example/jwks",
                "https://issuer.example/sign",
                3_600_000L,
                604_800_000L,
                1_000,
                1_000
        );
        OAuth2TokenValidator<Jwt> validator = ReflectionTestUtils.invokeMethod(
                sut,
                "jwtValidator",
                properties
        );

        assertThat(validator).isNotNull();
        OAuth2TokenValidatorResult result = validator.validate(jwt("https://another-issuer.example"));
        assertThat(result.hasErrors()).isTrue();
        assertThat(result.getErrors())
                .extracting("errorCode")
                .contains("invalid_token");
    }

    private Jwt jwt(String issuer) {
        Instant issuedAt = Instant.now();

        return Jwt.withTokenValue("access-token")
                .header("alg", "none")
                .issuer(issuer)
                .issuedAt(issuedAt)
                .expiresAt(issuedAt.plusSeconds(3600))
                .subject("user-1")
                .claim("id", "user-1")
                .claim("roles", List.of("ROLE_USER"))
                .build();
    }
}

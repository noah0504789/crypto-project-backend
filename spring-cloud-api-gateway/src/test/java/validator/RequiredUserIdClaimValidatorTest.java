package validator;

import org.example.gateway.oauth2.validator.RequiredUserIdClaimValidator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RequiredUserIdClaimValidatorTest {

    private final RequiredUserIdClaimValidator sut = new RequiredUserIdClaimValidator();

    @Test
    @DisplayName("id 클레임이 있으면 검증에 성공한다")
    void validate_shouldSuccess_whenIdClaimExists() {
        // given
        Jwt jwt = jwt(Map.of(
                "sub", "user@test.com",
                "id", "user-1",
                "roles", "ROLE_USER"
        ));

        // when
        OAuth2TokenValidatorResult result = sut.validate(jwt);

        // then
        assertThat(result.hasErrors()).isFalse();
    }

    @Test
    @DisplayName("id 클레임이 없으면 검증에 실패한다")
    void validate_shouldFail_whenIdClaimMissing() {
        // given
        Jwt jwt = jwt(Map.of(
                "sub", "user@test.com",
                "roles", "ROLE_USER"
        ));

        // when
        OAuth2TokenValidatorResult result = sut.validate(jwt);

        // then
        assertThat(result.hasErrors()).isTrue();
        assertThat(result.getErrors())
                .extracting("errorCode")
                .contains("invalid_token");

        assertThat(result.getErrors())
                .extracting("description")
                .contains("Required claim 'id' is missing");
    }

    @Test
    @DisplayName("id 클레임이 빈 문자열이면 검증에 실패한다")
    void validate_shouldFail_whenIdClaimIsBlank() {
        // given
        Jwt jwt = jwt(Map.of(
                "sub", "user@test.com",
                "id", "   ",
                "roles", "ROLE_USER"
        ));

        // when
        OAuth2TokenValidatorResult result = sut.validate(jwt);

        // then
        assertThat(result.hasErrors()).isTrue();
        assertThat(result.getErrors())
                .extracting("errorCode")
                .contains("invalid_token");

        assertThat(result.getErrors())
                .extracting("description")
                .contains("Required claim 'id' is missing");
    }

    @Test
    @DisplayName("id 클레임이 null이면 검증에 실패한다")
    void validate_shouldFail_whenIdClaimIsNull() {
        // given
        Jwt jwt = jwtWithNullableClaim();

        // when
        OAuth2TokenValidatorResult result = sut.validate(jwt);

        // then
        assertThat(result.hasErrors()).isTrue();
        assertThat(result.getErrors())
                .extracting("errorCode")
                .contains("invalid_token");

        assertThat(result.getErrors())
                .extracting("description")
                .contains("Required claim 'id' is missing");
    }

    private Jwt jwt(Map<String, Object> claims) {
        Instant now = Instant.now();

        return new Jwt(
                "access-token",
                now,
                now.plusSeconds(3600),
                Map.of(
                        "alg", "none",
                        "typ", "JWT"
                ),
                claims
        );
    }

    private Jwt jwtWithNullableClaim() {
        Instant now = Instant.now();

        return Jwt.withTokenValue("access-token")
                .header("alg", "none")
                .header("typ", "JWT")
                .claim("sub", "user@test.com")
                .claim("id", null)
                .issuedAt(now)
                .expiresAt(now.plusSeconds(3600))
                .build();
    }
}
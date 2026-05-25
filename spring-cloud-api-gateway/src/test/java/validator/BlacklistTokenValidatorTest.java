package validator;

import org.example.gateway.oauth2.application.service.BlacklistTokenService;
import org.example.gateway.oauth2.validator.BlacklistTokenValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class BlacklistTokenValidatorTest {

    private static final String ACCESS_TOKEN = "access-token";

    @Mock
    private BlacklistTokenService blacklistTokenService;

    private BlacklistTokenValidator sut;

    @BeforeEach
    void setup() {
        sut = new BlacklistTokenValidator(blacklistTokenService);
    }

    @Test
    @DisplayName("access token이 블랙리스트에 없으면 검증 성공을 반환한다")
    void validate_shouldReturnSuccess_whenAccessTokenIsNotBlacklisted() {
        // given
        Jwt jwt = jwt();

        given(blacklistTokenService.existsByAccessToken(ACCESS_TOKEN))
                .willReturn(false);

        // when
        OAuth2TokenValidatorResult result = sut.validate(jwt);

        // then
        assertThat(result.hasErrors()).isFalse();

        then(blacklistTokenService)
                .should()
                .existsByAccessToken(ACCESS_TOKEN);
    }

    @Test
    @DisplayName("access token이 블랙리스트에 있으면 invalid_token 오류를 반환한다")
    void validate_shouldReturnFailure_whenAccessTokenIsBlacklisted() {
        // given
        Jwt jwt = jwt();

        given(blacklistTokenService.existsByAccessToken(ACCESS_TOKEN))
                .willReturn(true);

        // when
        OAuth2TokenValidatorResult result = sut.validate(jwt);

        // then
        assertThat(result.hasErrors()).isTrue();
        assertThat(result.getErrors())
                .hasSize(1)
                .first()
                .satisfies(error -> {
                    assertThat(error.getErrorCode()).isEqualTo("invalid_token");
                    assertThat(error.getDescription()).isEqualTo("blacklist token");
                    assertThat(error.getUri()).isNull();
                });

        then(blacklistTokenService)
                .should()
                .existsByAccessToken(ACCESS_TOKEN);
    }

    private Jwt jwt() {
        Instant issuedAt = Instant.now();

        return Jwt.withTokenValue(BlacklistTokenValidatorTest.ACCESS_TOKEN)
                .header("alg", "none")
                .issuedAt(issuedAt)
                .expiresAt(issuedAt.plusSeconds(3600))
                .subject("user-1")
                .claim("id", "user-1")
                .claim("roles", java.util.List.of("ROLE_USER"))
                .build();
    }
}
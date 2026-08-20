package org.example.apigateway.validator;

import java.time.Instant;
import java.util.List;
import org.example.apigateway.oauth2.application.service.BlacklistTokenService;
import org.example.apigateway.oauth2.validator.ReactiveBlacklistTokenValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class ReactiveBlacklistTokenValidatorUnitTest {

    private static final String ACCESS_TOKEN = "access-token";

    @Mock
    private BlacklistTokenService blacklistTokenService;

    private ReactiveBlacklistTokenValidator sut;

    @BeforeEach
    void setup() {
        sut = new ReactiveBlacklistTokenValidator(blacklistTokenService);
    }

    @Test
    @DisplayName("access token이 블랙리스트에 없으면 원본 JWT를 반환한다")
    void validate_shouldReturnJwt_whenAccessTokenIsNotBlacklisted() {
        // given
        Jwt jwt = jwt();

        given(blacklistTokenService.existsByAccessToken(ACCESS_TOKEN))
                .willReturn(Mono.just(false));

        // when & then
        StepVerifier.create(sut.validate(jwt))
                .expectNext(jwt)
                .verifyComplete();

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
                .willReturn(Mono.just(true));

        // when & then
        StepVerifier.create(sut.validate(jwt))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(JwtValidationException.class);

                    JwtValidationException exception = (JwtValidationException) error;

                    assertThat(exception.getErrors())
                            .hasSize(1)
                            .first()
                            .satisfies(oauth2Error -> {
                                assertThat(oauth2Error.getErrorCode()).isEqualTo("invalid_token");
                                assertThat(oauth2Error.getDescription()).isEqualTo("blacklist token");
                                assertThat(oauth2Error.getUri()).isNull();
                            });
                })
                .verify();

        then(blacklistTokenService)
                .should()
                .existsByAccessToken(ACCESS_TOKEN);
    }

    @Test
    @DisplayName("blacklist 조회 오류를 인증 체인에 전달한다")
    void validate_shouldPropagateError_whenBlacklistLookupFails() {
        // given
        Jwt jwt = jwt();
        RuntimeException error = new RuntimeException("gRPC unavailable");

        given(blacklistTokenService.existsByAccessToken(ACCESS_TOKEN))
                .willReturn(Mono.error(error));

        // when & then
        StepVerifier.create(sut.validate(jwt))
                .expectErrorSatisfies(actual -> assertThat(actual).isSameAs(error))
                .verify();
    }

    private Jwt jwt() {
        Instant issuedAt = Instant.now();

        return Jwt.withTokenValue(ACCESS_TOKEN)
                .header("alg", "none")
                .issuedAt(issuedAt)
                .expiresAt(issuedAt.plusSeconds(3600))
                .subject("user-1")
                .claim("id", "user-1")
                .claim("roles", List.of("ROLE_USER"))
                .build();
    }
}

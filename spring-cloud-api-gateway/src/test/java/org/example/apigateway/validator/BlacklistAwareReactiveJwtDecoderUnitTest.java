package org.example.apigateway.validator;

import java.time.Instant;
import java.util.List;
import org.example.apigateway.oauth2.validator.BlacklistAwareReactiveJwtDecoder;
import org.example.apigateway.oauth2.validator.ReactiveBlacklistTokenValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class BlacklistAwareReactiveJwtDecoderUnitTest {

    private static final String ACCESS_TOKEN = "access-token";

    @Mock
    private ReactiveBlacklistTokenValidator blacklistTokenValidator;

    @Mock
    private ReactiveJwtDecoder delegate;

    private BlacklistAwareReactiveJwtDecoder sut;

    @BeforeEach
    void setUp() {
        sut = new BlacklistAwareReactiveJwtDecoder(delegate, blacklistTokenValidator);
    }

    @Test
    @DisplayName("기본 JWT 검증이 성공하면 reactive blacklist 검증을 이어서 수행한다")
    void decode_shouldValidateBlacklist_afterDelegateSucceeds() {
        // given
        Jwt jwt = jwt();

        given(delegate.decode(ACCESS_TOKEN))
                .willReturn(Mono.just(jwt));
        given(blacklistTokenValidator.validate(jwt))
                .willReturn(Mono.just(jwt));

        // when & then
        StepVerifier.create(sut.decode(ACCESS_TOKEN))
                .expectNext(jwt)
                .verifyComplete();

        then(blacklistTokenValidator)
                .should()
                .validate(jwt);
    }

    @Test
    @DisplayName("기본 JWT 검증이 실패하면 blacklist를 조회하지 않는다")
    void decode_shouldNotValidateBlacklist_whenDelegateFails() {
        // given
        RuntimeException error = new RuntimeException("invalid signature");

        given(delegate.decode(ACCESS_TOKEN))
                .willReturn(Mono.error(error));

        // when & then
        StepVerifier.create(sut.decode(ACCESS_TOKEN))
                .expectErrorSatisfies(actual -> assertThat(actual).isSameAs(error))
                .verify();

        then(blacklistTokenValidator)
                .shouldHaveNoInteractions();
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

package oauth2.token;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import java.time.Instant;
import java.util.Set;

import org.example.oauth2.authorizationserver.token.application.policy.RotatingRefreshTokenPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.core.OAuth2Token;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AccessTokenAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenGenerator;

@ExtendWith(MockitoExtension.class)
class RotatingRefreshTokenPolicyUnitTest {

    private final String CLIENT_ID = "my-client-id";
    private final String CLIENT_SECRET = "my-client-secret";
    private final String EMAIL = "user@test.com";

    private final String ACCESS_TOKEN_VALUE = "access-token-value";
    private final String REFRESH_TOKEN_VALUE = "refresh-token-value";

    private final Instant ISSUED_AT = Instant.parse("2026-05-14T04:00:00Z");
    private final Instant EXPIRES_AT = Instant.parse("2026-05-14T05:00:00Z");

    @Mock
    private OAuth2TokenGenerator<OAuth2Token> tokenGenerator;

    private RotatingRefreshTokenPolicy sut;

    @BeforeEach
    void setUp() {
        sut = new RotatingRefreshTokenPolicy(tokenGenerator);
    }

    @Test
    @DisplayName("token_exchange 요청이면 TOKEN_EXCHANGE grant type으로 refresh token을 생성한다")
    void resolve_shouldGenerateRefreshToken_withTokenExchangeGrantType() {
        // given
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter(
                OAuth2ParameterNames.GRANT_TYPE,
                AuthorizationGrantType.TOKEN_EXCHANGE.getValue()
        );

        OAuth2AccessTokenAuthenticationToken authentication =
                accessTokenAuthentication();

        Authentication principal =
                (Authentication) authentication.getPrincipal();

        RegisteredClient registeredClient =
                authentication.getRegisteredClient();

        OAuth2RefreshToken generatedRefreshToken =
                new OAuth2RefreshToken(REFRESH_TOKEN_VALUE, ISSUED_AT, EXPIRES_AT);

        given(tokenGenerator.generate(any(OAuth2TokenContext.class)))
                .willReturn(generatedRefreshToken);

        ArgumentCaptor<OAuth2TokenContext> contextCaptor =
                ArgumentCaptor.forClass(OAuth2TokenContext.class);

        // when
        OAuth2RefreshToken result =
                sut.resolve(request, authentication, principal, registeredClient);

        // then
        assertThat(result).isSameAs(generatedRefreshToken);
        assertThat(result.getTokenValue()).isEqualTo(REFRESH_TOKEN_VALUE);

        then(tokenGenerator)
                .should()
                .generate(contextCaptor.capture());

        OAuth2TokenContext context = contextCaptor.getValue();

        assertThat(context.getTokenType())
                .isEqualTo(OAuth2TokenType.REFRESH_TOKEN);

        assertThat(context.getAuthorizationGrantType())
                .isEqualTo(AuthorizationGrantType.TOKEN_EXCHANGE);

        assertThat((Authentication) context.getPrincipal())
                .isSameAs(principal);

        assertThat(context.getRegisteredClient())
                .isSameAs(registeredClient);

        assertThat(context.getAuthorizedScopes())
                .containsExactlyInAnyOrder("read", "write");
    }

    @Test
    @DisplayName("refresh_token 요청이면 REFRESH_TOKEN grant type으로 refresh token을 생성한다")
    void resolve_shouldGenerateRefreshToken_withRefreshTokenGrantType() {
        // given
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter(
                OAuth2ParameterNames.GRANT_TYPE,
                AuthorizationGrantType.REFRESH_TOKEN.getValue()
        );

        OAuth2AccessTokenAuthenticationToken authentication =
                accessTokenAuthentication();

        Authentication principal =
                (Authentication) authentication.getPrincipal();

        RegisteredClient registeredClient =
                authentication.getRegisteredClient();

        OAuth2RefreshToken generatedRefreshToken =
                new OAuth2RefreshToken(REFRESH_TOKEN_VALUE, ISSUED_AT, EXPIRES_AT);

        given(tokenGenerator.generate(any(OAuth2TokenContext.class)))
                .willReturn(generatedRefreshToken);

        ArgumentCaptor<OAuth2TokenContext> contextCaptor =
                ArgumentCaptor.forClass(OAuth2TokenContext.class);

        // when
        OAuth2RefreshToken result =
                sut.resolve(request, authentication, principal, registeredClient);

        // then
        assertThat(result).isSameAs(generatedRefreshToken);

        then(tokenGenerator)
                .should()
                .generate(contextCaptor.capture());

        OAuth2TokenContext context = contextCaptor.getValue();

        assertThat(context.getTokenType())
                .isEqualTo(OAuth2TokenType.REFRESH_TOKEN);

        assertThat(context.getAuthorizationGrantType())
                .isEqualTo(AuthorizationGrantType.REFRESH_TOKEN);
    }

    @Test
    @DisplayName("grant_type이 없으면 TOKEN_EXCHANGE grant type으로 refresh token을 생성한다")
    void resolve_shouldGenerateRefreshToken_withTokenExchangeGrantType_whenGrantTypeMissing() {
        // given
        MockHttpServletRequest request = new MockHttpServletRequest();

        OAuth2AccessTokenAuthenticationToken authentication =
                accessTokenAuthentication();

        Authentication principal =
                (Authentication) authentication.getPrincipal();

        RegisteredClient registeredClient =
                authentication.getRegisteredClient();

        OAuth2RefreshToken generatedRefreshToken =
                new OAuth2RefreshToken(REFRESH_TOKEN_VALUE, ISSUED_AT, EXPIRES_AT);

        given(tokenGenerator.generate(any(OAuth2TokenContext.class)))
                .willReturn(generatedRefreshToken);

        ArgumentCaptor<OAuth2TokenContext> contextCaptor =
                ArgumentCaptor.forClass(OAuth2TokenContext.class);

        // when
        OAuth2RefreshToken result =
                sut.resolve(request, authentication, principal, registeredClient);

        // then
        assertThat(result).isSameAs(generatedRefreshToken);

        then(tokenGenerator)
                .should()
                .generate(contextCaptor.capture());

        assertThat(contextCaptor.getValue().getAuthorizationGrantType())
                .isEqualTo(AuthorizationGrantType.TOKEN_EXCHANGE);
    }

    @Test
    @DisplayName("tokenGenerator가 refresh token을 생성하지 못하면 예외를 던진다")
    void resolve_shouldThrowException_whenGeneratedTokenIsNotRefreshToken() {
        // given
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter(
                OAuth2ParameterNames.GRANT_TYPE,
                AuthorizationGrantType.REFRESH_TOKEN.getValue()
        );

        OAuth2AccessTokenAuthenticationToken authentication =
                accessTokenAuthentication();

        Authentication principal =
                (Authentication) authentication.getPrincipal();

        RegisteredClient registeredClient =
                authentication.getRegisteredClient();

        OAuth2AccessToken wrongToken =
                new OAuth2AccessToken(
                        OAuth2AccessToken.TokenType.BEARER,
                        "wrong-access-token",
                        ISSUED_AT,
                        EXPIRES_AT,
                        Set.of("read", "write")
                );

        given(tokenGenerator.generate(any(OAuth2TokenContext.class)))
                .willReturn(wrongToken);

        // when & then
        assertThatThrownBy(() ->
                sut.resolve(request, authentication, principal, registeredClient)
        )
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to generate refresh token");
    }

    @Test
    @DisplayName("shouldSave는 항상 true를 반환한다")
    void shouldSave_shouldAlwaysReturnTrue() {
        // given
        MockHttpServletRequest request = new MockHttpServletRequest();

        OAuth2AccessTokenAuthenticationToken authentication =
                accessTokenAuthentication();

        // when
        boolean result = sut.shouldSave(request, authentication);

        // then
        assertThat(result).isTrue();
    }

    private OAuth2AccessTokenAuthenticationToken accessTokenAuthentication() {
        RegisteredClient registeredClient =
                registeredClient();

        Authentication principal =
                new UsernamePasswordAuthenticationToken(EMAIL, null);

        OAuth2AccessToken accessToken =
                new OAuth2AccessToken(
                        OAuth2AccessToken.TokenType.BEARER,
                        ACCESS_TOKEN_VALUE,
                        ISSUED_AT,
                        EXPIRES_AT,
                        Set.of("read", "write")
                );

        OAuth2RefreshToken refreshToken =
                new OAuth2RefreshToken("existing-refresh-token", ISSUED_AT, EXPIRES_AT);

        return new OAuth2AccessTokenAuthenticationToken(
                registeredClient,
                principal,
                accessToken,
                refreshToken
        );
    }

    private RegisteredClient registeredClient() {
        return RegisteredClient.withId(CLIENT_ID)
                .clientId(CLIENT_ID)
                .clientSecret(CLIENT_SECRET)
                .authorizationGrantType(AuthorizationGrantType.TOKEN_EXCHANGE)
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                .scope("read")
                .scope("write")
                .tokenSettings(TokenSettings.builder().build())
                .clientSettings(ClientSettings.builder().build())
                .build();
    }
}
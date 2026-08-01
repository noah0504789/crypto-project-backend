package oauth2.token;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Map;
import java.util.Set;

import org.example.oauth2.authorizationserver.authorization.application.CustomAuthenticationSuccessHandler;
import org.example.oauth2.authorizationserver.token.application.policy.RefreshTokenPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.core.endpoint.OAuth2AccessTokenResponse;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AccessTokenAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;

@ExtendWith(MockitoExtension.class)
class CustomAuthenticationSuccessHandlerUnitTest {

    private final String CLIENT_ID = "my-client-id";
    private final String CLIENT_SECRET = "my-client-secret";

    private final String EMAIL = "user@test.com";
    private final String EXISTING_REFRESH_TOKEN_VALUE = "existing-refresh-token-value";
    private final String GENERATED_REFRESH_TOKEN_VALUE = "generated-refresh-token-value";

    private final Instant ISSUED_AT = Instant.parse("2026-05-14T04:00:00Z");
    private final Instant EXPIRES_AT = Instant.parse("2026-05-14T05:00:00Z");

    @Mock
    private HttpMessageConverter<OAuth2AccessTokenResponse> accessTokenResponseConverter;

    @Mock
    private OAuth2AuthorizationService authorizationService;

    @Mock
    private RefreshTokenPolicy refreshTokenPolicy;

    private ObjectMapper objectMapper;

    private CustomAuthenticationSuccessHandler sut;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();

        sut = new CustomAuthenticationSuccessHandler(
                accessTokenResponseConverter,
                authorizationService,
                refreshTokenPolicy,
                objectMapper
        );
    }

    @Test
    @DisplayName("정책이 기존 refresh token을 반환하고 저장이 불필요하면 응답만 작성한다")
    void onAuthenticationSuccess_shouldWriteResponseOnly_whenRefreshTokenDoesNotNeedSave() throws Exception {
        // given
        OAuth2AccessToken accessToken = accessToken(jwtWithClaims(Map.of(
                "sub", EMAIL,
                "email", EMAIL,
                "iat", String.valueOf(ISSUED_AT.getEpochSecond()),
                "exp", String.valueOf(EXPIRES_AT.getEpochSecond())
        )));

        OAuth2RefreshToken existingRefreshToken = new OAuth2RefreshToken(EXISTING_REFRESH_TOKEN_VALUE, ISSUED_AT, EXPIRES_AT);

        OAuth2AccessTokenAuthenticationToken authentication = accessTokenAuthentication(accessToken, existingRefreshToken);

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        Authentication principal = (Authentication) authentication.getPrincipal();

        RegisteredClient registeredClient = authentication.getRegisteredClient();

        given(refreshTokenPolicy.resolve(
                request,
                authentication,
                principal,
                registeredClient
        )).willReturn(existingRefreshToken);

        given(refreshTokenPolicy.shouldSave(request, authentication))
                .willReturn(false);

        ArgumentCaptor<OAuth2AccessTokenResponse> responseCaptor =
                ArgumentCaptor.forClass(OAuth2AccessTokenResponse.class);

        // when
        sut.onAuthenticationSuccess(request, response, authentication);

        // then
        then(refreshTokenPolicy)
                .should()
                .resolve(request, authentication, principal, registeredClient);

        then(refreshTokenPolicy)
                .should()
                .shouldSave(request, authentication);

        then(authorizationService)
                .shouldHaveNoInteractions();

        then(accessTokenResponseConverter)
                .should()
                .write(
                        responseCaptor.capture(),
                        isNull(),
                        any(ServletServerHttpResponse.class)
                );

        OAuth2AccessTokenResponse tokenResponse =
                responseCaptor.getValue();

        assertThat(tokenResponse.getAccessToken().getTokenValue())
                .isEqualTo(accessToken.getTokenValue());

        assertThat(tokenResponse.getRefreshToken().getTokenValue())
                .isEqualTo(EXISTING_REFRESH_TOKEN_VALUE);

        assertThat(tokenResponse.getAccessToken().getScopes())
                .containsExactlyInAnyOrderElementsOf(accessToken.getScopes());

        assertThat(tokenResponse.getAccessToken().getIssuedAt())
                .isNotNull();

        assertThat(tokenResponse.getAccessToken().getExpiresAt())
                .isNotNull();

        assertThat(ChronoUnit.SECONDS.between(
                tokenResponse.getAccessToken().getIssuedAt(),
                tokenResponse.getAccessToken().getExpiresAt()
        )).isEqualTo(ChronoUnit.SECONDS.between(ISSUED_AT, EXPIRES_AT));
    }

    @Test
    @DisplayName("정책이 새 refresh token을 반환하고 저장이 필요하면 OAuth2Authorization을 저장하고 응답을 작성한다")
    void onAuthenticationSuccess_shouldSaveAuthorizationAndWriteResponse_whenRefreshTokenNeedsSave() throws Exception {
        // given
        OAuth2AccessToken accessToken = accessToken(jwtWithClaims(Map.of(
                "sub", EMAIL,
                "email", EMAIL,
                "iat", String.valueOf(ISSUED_AT.getEpochSecond()),
                "exp", String.valueOf(EXPIRES_AT.getEpochSecond())
        )));

        OAuth2RefreshToken generatedRefreshToken =
                new OAuth2RefreshToken(GENERATED_REFRESH_TOKEN_VALUE, ISSUED_AT, EXPIRES_AT);

        OAuth2AccessTokenAuthenticationToken authentication =
                accessTokenAuthentication(accessToken, null);

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        Authentication principal =
                (Authentication) authentication.getPrincipal();

        RegisteredClient registeredClient =
                authentication.getRegisteredClient();

        given(refreshTokenPolicy.resolve(
                request,
                authentication,
                principal,
                registeredClient
        )).willReturn(generatedRefreshToken);

        given(refreshTokenPolicy.shouldSave(request, authentication))
                .willReturn(true);

        ArgumentCaptor<OAuth2Authorization> authorizationCaptor =
                ArgumentCaptor.forClass(OAuth2Authorization.class);

        ArgumentCaptor<OAuth2AccessTokenResponse> responseCaptor =
                ArgumentCaptor.forClass(OAuth2AccessTokenResponse.class);

        // when
        sut.onAuthenticationSuccess(request, response, authentication);

        // then
        then(authorizationService)
                .should()
                .save(authorizationCaptor.capture());

        OAuth2Authorization savedAuthorization =
                authorizationCaptor.getValue();

        assertThat(savedAuthorization.getRegisteredClientId())
                .isEqualTo(CLIENT_ID);

        assertThat(savedAuthorization.getPrincipalName())
                .isEqualTo(EMAIL);

        assertThat(savedAuthorization.getAuthorizationGrantType())
                .isEqualTo(AuthorizationGrantType.TOKEN_EXCHANGE);

        assertThat(savedAuthorization.getRefreshToken())
                .isNotNull();

        assertThat(savedAuthorization.getRefreshToken().getToken().getTokenValue())
                .isEqualTo(GENERATED_REFRESH_TOKEN_VALUE);

        then(accessTokenResponseConverter)
                .should()
                .write(
                        responseCaptor.capture(),
                        isNull(),
                        any(ServletServerHttpResponse.class)
                );

        OAuth2AccessTokenResponse tokenResponse =
                responseCaptor.getValue();

        assertThat(tokenResponse.getAccessToken().getTokenValue())
                .isEqualTo(accessToken.getTokenValue());

        assertThat(tokenResponse.getRefreshToken().getTokenValue())
                .isEqualTo(GENERATED_REFRESH_TOKEN_VALUE);
    }

    @Test
    @DisplayName("access token이 JWT 형식이 아니면 예외를 던지고 저장/응답을 수행하지 않는다")
    void onAuthenticationSuccess_shouldThrowException_whenAccessTokenIsInvalidJwt() {
        // given
        OAuth2AccessToken accessToken =
                new OAuth2AccessToken(
                        OAuth2AccessToken.TokenType.BEARER,
                        "invalid-jwt",
                        ISSUED_AT,
                        EXPIRES_AT,
                        Set.of("read", "write")
                );

        OAuth2RefreshToken refreshToken =
                new OAuth2RefreshToken(EXISTING_REFRESH_TOKEN_VALUE, ISSUED_AT, EXPIRES_AT);

        OAuth2AccessTokenAuthenticationToken authentication =
                accessTokenAuthentication(accessToken, refreshToken);

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        Authentication principal =
                (Authentication) authentication.getPrincipal();

        RegisteredClient registeredClient =
                authentication.getRegisteredClient();

        given(refreshTokenPolicy.resolve(
                request,
                authentication,
                principal,
                registeredClient
        )).willReturn(refreshToken);

        // when & then
        assertThatThrownBy(() ->
                sut.onAuthenticationSuccess(request, response, authentication)
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid JWT format");

        then(authorizationService)
                .shouldHaveNoInteractions();

        then(accessTokenResponseConverter)
                .shouldHaveNoInteractions();

        then(refreshTokenPolicy)
                .should(never())
                .shouldSave(any(), any());
    }

    @Test
    @DisplayName("저장이 필요한 상황에서 JWT claim sub가 없으면 예외를 던지고 저장/응답을 수행하지 않는다")
    void onAuthenticationSuccess_shouldThrowException_whenSubClaimIsMissingAndSaveRequired() {
        // given
        OAuth2AccessToken accessToken = accessToken(jwtWithClaims(Map.of(
                "email", EMAIL,
                "iat", String.valueOf(ISSUED_AT.getEpochSecond()),
                "exp", String.valueOf(EXPIRES_AT.getEpochSecond())
        )));

        OAuth2RefreshToken generatedRefreshToken =
                new OAuth2RefreshToken(GENERATED_REFRESH_TOKEN_VALUE, ISSUED_AT, EXPIRES_AT);

        OAuth2AccessTokenAuthenticationToken authentication =
                accessTokenAuthentication(accessToken, null);

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        Authentication principal =
                (Authentication) authentication.getPrincipal();

        RegisteredClient registeredClient =
                authentication.getRegisteredClient();

        given(refreshTokenPolicy.resolve(
                request,
                authentication,
                principal,
                registeredClient
        )).willReturn(generatedRefreshToken);

        given(refreshTokenPolicy.shouldSave(request, authentication))
                .willReturn(true);

        // when & then
        assertThatThrownBy(() ->
                sut.onAuthenticationSuccess(request, response, authentication)
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("JWT claim 'sub' is missing");

        then(authorizationService)
                .shouldHaveNoInteractions();

        then(accessTokenResponseConverter)
                .shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("저장이 불필요한 상황에서는 sub claim이 없어도 응답을 작성한다")
    void onAuthenticationSuccess_shouldWriteResponse_whenSubClaimMissingButSaveNotRequired() throws Exception {
        // given
        OAuth2AccessToken accessToken = accessToken(jwtWithClaims(Map.of(
                "email", EMAIL,
                "iat", String.valueOf(ISSUED_AT.getEpochSecond()),
                "exp", String.valueOf(EXPIRES_AT.getEpochSecond())
        )));

        OAuth2RefreshToken existingRefreshToken =
                new OAuth2RefreshToken(EXISTING_REFRESH_TOKEN_VALUE, ISSUED_AT, EXPIRES_AT);

        OAuth2AccessTokenAuthenticationToken authentication =
                accessTokenAuthentication(accessToken, existingRefreshToken);

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        Authentication principal =
                (Authentication) authentication.getPrincipal();

        RegisteredClient registeredClient =
                authentication.getRegisteredClient();

        given(refreshTokenPolicy.resolve(
                request,
                authentication,
                principal,
                registeredClient
        )).willReturn(existingRefreshToken);

        given(refreshTokenPolicy.shouldSave(request, authentication))
                .willReturn(false);

        // when
        sut.onAuthenticationSuccess(request, response, authentication);

        // then
        then(authorizationService)
                .shouldHaveNoInteractions();

        then(accessTokenResponseConverter)
                .should()
                .write(
                        any(OAuth2AccessTokenResponse.class),
                        isNull(),
                        any(ServletServerHttpResponse.class)
                );
    }

    @Test
    @DisplayName("지원하지 않는 Authentication 타입이면 예외를 던진다")
    void onAuthenticationSuccess_shouldThrowException_whenAuthenticationTypeUnsupported() {
        // given
        Authentication authentication = new UsernamePasswordAuthenticationToken(EMAIL, null);
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        // when & then
        assertThatThrownBy(() ->
                sut.onAuthenticationSuccess(request, response, authentication)
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported authentication type");

        then(refreshTokenPolicy)
                .shouldHaveNoInteractions();

        then(authorizationService)
                .shouldHaveNoInteractions();

        then(accessTokenResponseConverter)
                .shouldHaveNoInteractions();
    }

    private OAuth2AccessTokenAuthenticationToken accessTokenAuthentication(
            OAuth2AccessToken accessToken,
            OAuth2RefreshToken refreshToken
    ) {
        RegisteredClient registeredClient = registeredClient();
        Authentication principal = new UsernamePasswordAuthenticationToken(EMAIL, null);

        return new OAuth2AccessTokenAuthenticationToken(
                registeredClient,
                principal,
                accessToken,
                refreshToken
        );
    }

    private OAuth2AccessToken accessToken(String tokenValue) {
        return new OAuth2AccessToken(
                OAuth2AccessToken.TokenType.BEARER,
                tokenValue,
                ISSUED_AT,
                EXPIRES_AT,
                Set.of("read", "write")
        );
    }

    private String jwtWithClaims(Map<String, ?> claims) {
        String headerJson = """
                {"alg":"RS256","typ":"JWT"}
                """;

        try {
            String header =
                    Base64.getUrlEncoder()
                            .withoutPadding()
                            .encodeToString(headerJson.getBytes(StandardCharsets.UTF_8));

            String payload =
                    Base64.getUrlEncoder()
                            .withoutPadding()
                            .encodeToString(objectMapper.writeValueAsBytes(claims));

            return header + "." + payload + ".signature";
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
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
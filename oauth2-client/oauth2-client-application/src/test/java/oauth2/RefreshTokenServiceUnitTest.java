package oauth2;

import jakarta.servlet.http.Cookie;
import org.example.oauth2.client.token.application.port.out.AuthServerTokenPort;
import org.example.oauth2.client.properties.InternalAuthServerProperties;
import org.example.common.properties.JwtProperties;
import org.example.oauth2.client.exception.OAuth2ClientInfrastructureException;
import org.example.oauth2.client.token.application.service.RefreshTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseCookie;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.oauth2.client.endpoint.OAuth2AccessTokenResponseClient;
import org.springframework.security.oauth2.client.endpoint.OAuth2RefreshTokenGrantRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.core.endpoint.OAuth2AccessTokenResponse;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceUnitTest {

    private static final String CLIENT_REGISTRATION_ID = "google";
    private static final String USERNAME = "user@test.com";
    private static final String REFRESH_TOKEN = "refresh-token";
    private static final String NEW_ACCESS_TOKEN = "new-access-token";
    private static final String NEW_REFRESH_TOKEN = "new-refresh-token";
    private static final String INTERNAL_AUTH_SERVER_CLIENT_REGISTRATION_ID = "my-authorization-server";

    @Mock
    private AuthServerTokenPort grpcClient;

    @Mock
    private JwtProperties jwtProperties;

    @Mock
    private ClientRegistrationRepository clientRegistrationRepository;

    @Mock
    private OAuth2AccessTokenResponseClient<OAuth2RefreshTokenGrantRequest> refreshTokenResponseClient;

    @Mock
    private InternalAuthServerProperties internalAuthServerProperties;

    private RefreshTokenService sut;

    @BeforeEach
    void setUp() {
        sut = new RefreshTokenService(
                grpcClient,
                jwtProperties,
                clientRegistrationRepository,
                refreshTokenResponseClient,
                internalAuthServerProperties
        );
    }

    @Test
    @DisplayName("clientRegistrationId와 username으로 저장된 refresh token을 조회한다")
    void findValue_shouldReturnRefreshToken() {
        // given
        given(grpcClient.findRefreshToken(anyString(), anyString()))
                .willReturn(REFRESH_TOKEN);

        // when
        String result = sut.findValue(CLIENT_REGISTRATION_ID, USERNAME);

        // then
        assertThat(result).isEqualTo(REFRESH_TOKEN);

        then(grpcClient)
                .should()
                .findRefreshToken(CLIENT_REGISTRATION_ID, USERNAME);
    }

    @Test
    @DisplayName("request가 null이면 refresh token 추출 결과는 null이다")
    void extractRefreshToken_shouldReturnNull_whenRequestIsNull() {
        // when
        String result = sut.extractRefreshToken(null);

        // then
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("쿠키가 없으면 refresh token 추출 결과는 null이다")
    void extractRefreshToken_shouldReturnNull_whenCookiesAreNull() {
        // given
        MockHttpServletRequest request = new MockHttpServletRequest();

        // when
        String result = sut.extractRefreshToken(request);

        // then
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("refreshToken 쿠키가 있으면 값을 추출한다")
    void extractRefreshToken_shouldReturnRefreshToken_whenRefreshTokenCookieExists() {
        // given
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(
                new Cookie("other", "other-value"),
                new Cookie("refreshToken", REFRESH_TOKEN)
        );

        // when
        String result = sut.extractRefreshToken(request);

        // then
        assertThat(result).isEqualTo(REFRESH_TOKEN);
    }

    @Test
    @DisplayName("refreshToken 쿠키 값이 비어 있으면 null을 반환한다")
    void extractRefreshToken_shouldReturnNull_whenRefreshTokenCookieValueIsBlank() {
        // given
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("refreshToken", " "));

        // when
        String result = sut.extractRefreshToken(request);

        // then
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("refresh token으로 내부 auth-server에 토큰 재발급을 요청한다")
    void reissue_shouldRequestRefreshTokenGrant() {
        // given
        ClientRegistration clientRegistration = internalClientRegistration();

        OAuth2AccessTokenResponse tokenResponse =
                OAuth2AccessTokenResponse.withToken(NEW_ACCESS_TOKEN)
                        .tokenType(OAuth2AccessToken.TokenType.BEARER)
                        .refreshToken(NEW_REFRESH_TOKEN)
                        .expiresIn(3600)
                        .build();

        given(internalAuthServerProperties.clientRegistrationId())
                .willReturn(INTERNAL_AUTH_SERVER_CLIENT_REGISTRATION_ID);

        given(clientRegistrationRepository.findByRegistrationId(
                INTERNAL_AUTH_SERVER_CLIENT_REGISTRATION_ID
        )).willReturn(clientRegistration);

        ArgumentCaptor<OAuth2RefreshTokenGrantRequest> captor =
                ArgumentCaptor.forClass(OAuth2RefreshTokenGrantRequest.class);

        given(refreshTokenResponseClient.getTokenResponse(captor.capture()))
                .willReturn(tokenResponse);

        // when
        OAuth2AccessTokenResponse result = sut.reissue(REFRESH_TOKEN);

        // then
        assertThat(result).isSameAs(tokenResponse);

        OAuth2RefreshTokenGrantRequest grantRequest = captor.getValue();

        assertThat(grantRequest.getClientRegistration())
                .isSameAs(clientRegistration);

        assertThat(grantRequest.getRefreshToken().getTokenValue())
                .isEqualTo(REFRESH_TOKEN);

        assertThat(grantRequest.getAccessToken().getTokenValue())
                .isEqualTo("not-used");
    }

    @Test
    @DisplayName("내부 auth-server ClientRegistration이 없으면 예외를 던진다")
    void reissue_shouldThrowException_whenClientRegistrationMissing() {
        // given
        given(internalAuthServerProperties.clientRegistrationId())
                .willReturn(INTERNAL_AUTH_SERVER_CLIENT_REGISTRATION_ID);

        given(clientRegistrationRepository.findByRegistrationId(
                INTERNAL_AUTH_SERVER_CLIENT_REGISTRATION_ID
        )).willReturn(null);

        // when & then
        assertThatThrownBy(() -> sut.reissue(REFRESH_TOKEN))
                .isInstanceOf(OAuth2ClientInfrastructureException.class)
                .hasMessageContaining("ClientRegistration not found")
                .hasMessageContaining(INTERNAL_AUTH_SERVER_CLIENT_REGISTRATION_ID);

        then(refreshTokenResponseClient)
                .shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("refresh token 응답 쿠키를 생성한다")
    void getResponseCookie_shouldCreateRefreshTokenCookie() {
        // given
        given(jwtProperties.refreshTokenExpirationMs())
                .willReturn(604_800_000L);

        // when
        ResponseCookie cookie = sut.getResponseCookie(REFRESH_TOKEN);

        // then
        assertThat(cookie.getName()).isEqualTo("refreshToken");
        assertThat(cookie.getValue()).isEqualTo(REFRESH_TOKEN);
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.isSecure()).isTrue();
        assertThat(cookie.getSameSite()).isEqualTo("None");
        assertThat(cookie.getPath()).isEqualTo("/");
        assertThat(cookie.getMaxAge()).isEqualTo(Duration.ofMillis(604_800_000L));
    }

    @Test
    @DisplayName("refresh token 삭제 쿠키를 생성한다")
    void deleteResponseCookie_shouldCreateExpiredRefreshTokenCookie() {
        // when
        ResponseCookie cookie = sut.deleteResponseCookie(REFRESH_TOKEN);

        // then
        assertThat(cookie.getName()).isEqualTo("refreshToken");
        assertThat(cookie.getValue()).isEqualTo(REFRESH_TOKEN);
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.isSecure()).isTrue();
        assertThat(cookie.getSameSite()).isEqualTo("None");
        assertThat(cookie.getPath()).isEqualTo("/");
        assertThat(cookie.getMaxAge()).isEqualTo(Duration.ZERO);
    }

    @Test
    @DisplayName("삭제 쿠키 생성 시 refresh token이 null이면 빈 값으로 생성한다")
    void deleteResponseCookie_shouldUseEmptyValue_whenRefreshTokenIsNull() {
        // when
        ResponseCookie cookie = sut.deleteResponseCookie(null);

        // then
        assertThat(cookie.getName()).isEqualTo("refreshToken");
        assertThat(cookie.getValue()).isEqualTo("");
        assertThat(cookie.getMaxAge()).isEqualTo(Duration.ZERO);
    }

    private ClientRegistration internalClientRegistration() {
        return ClientRegistration.withRegistrationId(INTERNAL_AUTH_SERVER_CLIENT_REGISTRATION_ID)
                .clientId("my-client-id")
                .clientSecret("my-client-secret")
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                .tokenUri("http://oauth2-authorization-server:9000/oauth2/token")
                .build();
    }
}

package oauth2;

import jakarta.servlet.http.HttpServletResponse;
import org.example.common.properties.FrontendProperties;
import org.example.oauth2.client.properties.InternalAuthServerProperties;
import org.example.oauth2.client.handler.CustomOAuth2LoginSuccessHandler;
import org.example.oauth2.client.token.application.service.RefreshTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.endpoint.OAuth2AccessTokenResponseClient;
import org.springframework.security.oauth2.client.endpoint.TokenExchangeGrantRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.endpoint.OAuth2AccessTokenResponse;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class CustomOAuth2LoginSuccessHandlerTest {

    private static final String PROVIDER_REGISTRATION_ID = "google";
    private static final String INTERNAL_CLIENT_REGISTRATION_ID = "my-authorization-server";
    private static final String PRINCIPAL_NAME = "user@test.com";

    private static final String PROVIDER_ACCESS_TOKEN = "provider-access-token";
    private static final String INTERNAL_ACCESS_TOKEN = "internal-access-token";
    private static final String INTERNAL_REFRESH_TOKEN = "internal-refresh-token";

    private static final String FRONTEND_ORIGIN = "http://localhost:5500";
    private static final String SUCCESS_REDIRECT_PATH = "/login-success.html";
    private static final String FAILURE_REDIRECT_PATH = "/login-failure.html";
    private static final String REDIRECT_URI = FRONTEND_ORIGIN + SUCCESS_REDIRECT_PATH;

    @Mock
    private OAuth2AccessTokenResponseClient<TokenExchangeGrantRequest> tokenExchangeResponseClient;

    @Mock
    private ClientRegistrationRepository clientRegistrationRepository;

    @Mock
    private OAuth2AuthorizedClientService oAuth2AuthorizedClientService;

    @Mock
    private RefreshTokenService refreshTokenService;

    private CustomOAuth2LoginSuccessHandler sut;

    @BeforeEach
    void setUp() {
        InternalAuthServerProperties internalAuthServerProperties = new InternalAuthServerProperties(INTERNAL_CLIENT_REGISTRATION_ID);

        FrontendProperties frontendProperties = new FrontendProperties(
                FRONTEND_ORIGIN,
                new FrontendProperties.OAuth2(
                        SUCCESS_REDIRECT_PATH,
                        FAILURE_REDIRECT_PATH
                )
        );

        sut = new CustomOAuth2LoginSuccessHandler(
                tokenExchangeResponseClient,
                clientRegistrationRepository,
                oAuth2AuthorizedClientService,
                refreshTokenService,
                internalAuthServerProperties,
                frontendProperties
        );
    }

    @Test
    @DisplayName("OAuth2 로그인 성공 시 token_exchange 후 refresh token 쿠키를 설정하고 SPA로 redirect한다")
    void onAuthenticationSuccess_shouldExchangeTokenAndRedirect() throws Exception {
        // given
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        OAuth2AuthenticationToken authentication = oauth2AuthenticationToken();

        OAuth2AuthorizedClient providerAuthorizedClient = providerAuthorizedClient();
        ClientRegistration internalClientRegistration = internalClientRegistration();

        OAuth2AccessTokenResponse tokenResponse =
                OAuth2AccessTokenResponse.withToken(INTERNAL_ACCESS_TOKEN)
                        .tokenType(OAuth2AccessToken.TokenType.BEARER)
                        .refreshToken(INTERNAL_REFRESH_TOKEN)
                        .expiresIn(3600)
                        .build();

        ResponseCookie refreshTokenCookie =
                ResponseCookie.from("refreshToken", INTERNAL_REFRESH_TOKEN)
                        .httpOnly(true)
                        .secure(true)
                        .sameSite("None")
                        .path("/")
                        .build();

        given(oAuth2AuthorizedClientService.loadAuthorizedClient(
                PROVIDER_REGISTRATION_ID,
                PRINCIPAL_NAME
        )).willReturn(providerAuthorizedClient);

        given(clientRegistrationRepository.findByRegistrationId(INTERNAL_CLIENT_REGISTRATION_ID))
                .willReturn(internalClientRegistration);

        ArgumentCaptor<TokenExchangeGrantRequest> captor =
                ArgumentCaptor.forClass(TokenExchangeGrantRequest.class);

        given(tokenExchangeResponseClient.getTokenResponse(captor.capture()))
                .willReturn(tokenResponse);

        given(refreshTokenService.getResponseCookie(INTERNAL_REFRESH_TOKEN))
                .willReturn(refreshTokenCookie);

        // when
        sut.onAuthenticationSuccess(request, response, authentication);

        // then
        TokenExchangeGrantRequest grantRequest = captor.getValue();

        assertThat(grantRequest.getClientRegistration())
                .isSameAs(internalClientRegistration);

        assertThat(grantRequest.getSubjectToken().getTokenValue())
                .isEqualTo(PROVIDER_ACCESS_TOKEN);

        assertThat(response.getStatus())
                .isEqualTo(HttpServletResponse.SC_FOUND);

        assertThat(response.getHeader(HttpHeaders.SET_COOKIE))
                .isEqualTo(refreshTokenCookie.toString());

        assertThat(response.getRedirectedUrl())
                .isEqualTo(REDIRECT_URI + "?accessToken=" + INTERNAL_ACCESS_TOKEN);

        then(refreshTokenService)
                .should()
                .getResponseCookie(INTERNAL_REFRESH_TOKEN);
    }

    @Test
    @DisplayName("provider authorized client가 없으면 401을 반환한다")
    void onAuthenticationSuccess_shouldReturnUnauthorized_whenAuthorizedClientMissing() throws Exception {
        // given
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        OAuth2AuthenticationToken authentication = oauth2AuthenticationToken();

        given(oAuth2AuthorizedClientService.loadAuthorizedClient(
                PROVIDER_REGISTRATION_ID,
                PRINCIPAL_NAME
        )).willReturn(null);

        // when
        sut.onAuthenticationSuccess(request, response, authentication);

        // then
        assertThat(response.getStatus())
                .isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);

        assertThat(response.getContentAsString())
                .isEqualTo("authorized client not found");

        then(tokenExchangeResponseClient)
                .shouldHaveNoInteractions();

        then(refreshTokenService)
                .shouldHaveNoInteractions();

        then(clientRegistrationRepository)
                .shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("내부 auth-server ClientRegistration이 없으면 500을 반환한다")
    void onAuthenticationSuccess_shouldReturnServerError_whenInternalClientRegistrationMissing() throws Exception {
        // given
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        OAuth2AuthenticationToken authentication = oauth2AuthenticationToken();

        given(oAuth2AuthorizedClientService.loadAuthorizedClient(
                PROVIDER_REGISTRATION_ID,
                PRINCIPAL_NAME
        )).willReturn(providerAuthorizedClient());

        given(clientRegistrationRepository.findByRegistrationId(INTERNAL_CLIENT_REGISTRATION_ID))
                .willReturn(null);

        // when
        sut.onAuthenticationSuccess(request, response, authentication);

        // then
        assertThat(response.getStatus())
                .isEqualTo(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);

        assertThat(response.getContentAsString())
                .isEqualTo("internal client registration not found");

        then(tokenExchangeResponseClient)
                .shouldHaveNoInteractions();

        then(refreshTokenService)
                .shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("token_exchange 응답에 refresh token이 없으면 502를 반환한다")
    void onAuthenticationSuccess_shouldReturnBadGateway_whenRefreshTokenNotIssued() throws Exception {
        // given
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        OAuth2AuthenticationToken authentication = oauth2AuthenticationToken();

        OAuth2AccessTokenResponse tokenResponse =
                OAuth2AccessTokenResponse.withToken(INTERNAL_ACCESS_TOKEN)
                        .tokenType(OAuth2AccessToken.TokenType.BEARER)
                        .expiresIn(3600)
                        .build();

        given(oAuth2AuthorizedClientService.loadAuthorizedClient(
                PROVIDER_REGISTRATION_ID,
                PRINCIPAL_NAME
        )).willReturn(providerAuthorizedClient());

        given(clientRegistrationRepository.findByRegistrationId(INTERNAL_CLIENT_REGISTRATION_ID))
                .willReturn(internalClientRegistration());

        given(tokenExchangeResponseClient.getTokenResponse(any(TokenExchangeGrantRequest.class)))
                .willReturn(tokenResponse);

        // when
        sut.onAuthenticationSuccess(request, response, authentication);

        // then
        assertThat(response.getStatus())
                .isEqualTo(HttpServletResponse.SC_BAD_GATEWAY);

        assertThat(response.getContentAsString())
                .isEqualTo("refresh token not issued");

        then(refreshTokenService)
                .shouldHaveNoInteractions();
    }

    private OAuth2AuthenticationToken oauth2AuthenticationToken() {
        OAuth2User principal = mock(OAuth2User.class);

        Collection<? extends GrantedAuthority> authorities =
                List.of(new SimpleGrantedAuthority("ROLE_USER"));

        given(principal.getName())
                .willReturn(PRINCIPAL_NAME);

        return new OAuth2AuthenticationToken(
                principal,
                authorities,
                PROVIDER_REGISTRATION_ID
        );
    }

    private OAuth2AuthorizedClient providerAuthorizedClient() {
        OAuth2AccessToken accessToken =
                new OAuth2AccessToken(
                        OAuth2AccessToken.TokenType.BEARER,
                        PROVIDER_ACCESS_TOKEN,
                        Instant.now(),
                        Instant.now().plusSeconds(3600)
                );

        return new OAuth2AuthorizedClient(
                providerClientRegistration(),
                PRINCIPAL_NAME,
                accessToken
        );
    }

    private ClientRegistration providerClientRegistration() {
        return ClientRegistration.withRegistrationId(PROVIDER_REGISTRATION_ID)
                .clientId("google-client-id")
                .clientSecret("google-client-secret")
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .authorizationUri("https://accounts.google.com/o/oauth2/v2/auth")
                .tokenUri("https://oauth2.googleapis.com/token")
                .jwkSetUri("https://www.googleapis.com/oauth2/v3/certs")
                .userInfoUri("https://openidconnect.googleapis.com/v1/userinfo")
                .userNameAttributeName("sub")
                .scope("openid", "profile", "email")
                .clientName("Google")
                .build();
    }

    private ClientRegistration internalClientRegistration() {
        return ClientRegistration.withRegistrationId(INTERNAL_CLIENT_REGISTRATION_ID)
                .clientId("my-client-id")
                .clientSecret("my-client-secret")
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(new AuthorizationGrantType("urn:ietf:params:oauth:grant-type:token-exchange"))
                .tokenUri("http://oauth2-authorization-server:9000/oauth2/token")
                .build();
    }
}
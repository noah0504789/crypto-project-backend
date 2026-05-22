package endpoint;

import org.example.common.test.config.TestBootApplication;
import config.TestExternalDependencyConfig;
import config.TestPropertiesConfig;
import config.TestSecurityDependencyConfig;
import jakarta.servlet.http.Cookie;
import org.example.AuthController;
import org.example.common.config.MessageConverterConfig;
import org.example.oauth2.adapter.in.config.SecurityFilterChainConfig;
import org.example.oauth2.port.AuthServerTokenPort;
import org.example.oauth2.service.token.RefreshTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mockito;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.client.endpoint.OAuth2AccessTokenResponseClient;
import org.springframework.security.oauth2.client.endpoint.OAuth2RefreshTokenGrantRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.core.endpoint.OAuth2AccessTokenResponse;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = {
        TestBootApplication.class,

        AuthController.class,
        RefreshTokenService.class,

        // 실제 운영 설정
        MessageConverterConfig.class,
        SecurityFilterChainConfig.class,

        // 테스트 설정
        TestPropertiesConfig.class,
        TestSecurityDependencyConfig.class,
        TestExternalDependencyConfig.class
})
@AutoConfigureMockMvc
class AuthRefreshE2ETest {

    private static final String OLD_REFRESH_TOKEN = "old-refresh-token";
    private static final String NEW_ACCESS_TOKEN = "new-access-token";
    private static final String NEW_REFRESH_TOKEN = "new-refresh-token";
    private static final String INTERNAL_CLIENT_REGISTRATION_ID = "my-authorization-server";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ClientRegistrationRepository clientRegistrationRepository;

    @Autowired
    private OAuth2AccessTokenResponseClient<OAuth2RefreshTokenGrantRequest> refreshTokenResponseClient;

    @Autowired
    private AuthServerTokenPort grpcClient;

    @BeforeEach
    void resetMocks() {
        Mockito.reset(
                refreshTokenResponseClient,
                clientRegistrationRepository,
                grpcClient
        );
    }

    @Test
    @DisplayName("/auth/refresh - refreshToken 쿠키가 있으면 새 access token과 refresh token cookie를 반환한다")
    void refresh_shouldIssueNewTokens_whenRefreshTokenCookieExists() throws Exception {
        // given
        given(clientRegistrationRepository.findByRegistrationId(INTERNAL_CLIENT_REGISTRATION_ID))
                .willReturn(internalClientRegistration());

        OAuth2AccessTokenResponse tokenResponse =
                OAuth2AccessTokenResponse.withToken(NEW_ACCESS_TOKEN)
                        .tokenType(OAuth2AccessToken.TokenType.BEARER)
                        .refreshToken(NEW_REFRESH_TOKEN)
                        .expiresIn(3600)
                        .build();

        given(refreshTokenResponseClient.getTokenResponse(any(OAuth2RefreshTokenGrantRequest.class)))
                .willReturn(tokenResponse);

        // when & then
        mockMvc.perform(post("/auth/refresh")
                        .cookie(new Cookie("refreshToken", OLD_REFRESH_TOKEN)))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, "Location, Authorization"))
                .andExpect(header().string(HttpHeaders.AUTHORIZATION, "Bearer " + NEW_ACCESS_TOKEN))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("refreshToken=" + NEW_REFRESH_TOKEN)))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Path=/")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Secure")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("HttpOnly")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("SameSite=None")))
                .andExpect(content().string("Access Token/Refresh Token Issue Success"));
    }

    @Test
    @DisplayName("/auth/refresh - refreshToken 쿠키가 없으면 401을 반환한다")
    void refresh_shouldReturnUnauthorized_whenRefreshTokenCookieMissing() throws Exception {
        mockMvc.perform(post("/auth/refresh"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.LOCATION, "/login"))
                .andExpect(content().string("Refresh Token not exists"));

        then(refreshTokenResponseClient).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("/auth/refresh - refresh token grant 실패 시 401을 반환한다")
    void refresh_shouldReturnUnauthorized_whenRefreshTokenGrantFails() throws Exception {
        // given
        given(clientRegistrationRepository.findByRegistrationId(INTERNAL_CLIENT_REGISTRATION_ID))
                .willReturn(internalClientRegistration());

        given(refreshTokenResponseClient.getTokenResponse(any(OAuth2RefreshTokenGrantRequest.class)))
                .willThrow(new OAuth2AuthorizationException(new OAuth2Error("invalid_grant")));

        // when & then
        mockMvc.perform(post("/auth/refresh")
                        .cookie(new Cookie("refreshToken", OLD_REFRESH_TOKEN)))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, "Location, Authorization"))
                .andExpect(header().string(HttpHeaders.LOCATION, "/login"))
                .andExpect(content().string("invalid token"));
    }

    @Test
    @DisplayName("/auth/refresh - 재발급 응답에 refresh token이 없으면 401을 반환한다")
    void refresh_shouldReturnUnauthorized_whenRefreshTokenNotIssued() throws Exception {
        // given
        given(clientRegistrationRepository.findByRegistrationId(INTERNAL_CLIENT_REGISTRATION_ID))
                .willReturn(internalClientRegistration());

        OAuth2AccessTokenResponse tokenResponse =
                OAuth2AccessTokenResponse.withToken(NEW_ACCESS_TOKEN)
                        .tokenType(OAuth2AccessToken.TokenType.BEARER)
                        .expiresIn(3600)
                        .build();

        given(refreshTokenResponseClient.getTokenResponse(any(OAuth2RefreshTokenGrantRequest.class)))
                .willReturn(tokenResponse);

        // when & then
        mockMvc.perform(post("/auth/refresh")
                        .cookie(new Cookie("refreshToken", OLD_REFRESH_TOKEN)))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, "Location, Authorization"))
                .andExpect(header().string(HttpHeaders.LOCATION, "/login"))
                .andExpect(content().string("Refresh Token not issued"));
    }

    private ClientRegistration internalClientRegistration() {
        return ClientRegistration.withRegistrationId(INTERNAL_CLIENT_REGISTRATION_ID)
                .clientId("my-client-id")
                .clientSecret("my-client-secret")
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(new AuthorizationGrantType("refresh_token"))
                .tokenUri("http://oauth2-authorization-server:9000/oauth2/token")
                .build();
    }
}
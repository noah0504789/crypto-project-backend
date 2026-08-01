package oauth2;

import java.time.Instant;
import java.util.Map;

import org.example.oauth2.client.token.application.service.AuthorizedClientTokenService;
import org.example.oauth2.client.authorizedclient.CustomOAuth2AuthorizedClientService;
import org.example.oauth2.client.token.application.service.AccessTokenService;
import org.example.oauth2.client.token.application.service.RefreshTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.core.user.OAuth2User;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class CustomOAuth2AuthorizedClientTokenServiceUnitTest {

    private static final String REGISTRATION_ID = "google";
    private static final String CLIENT_ID = "google-client-id";
    private static final String PRINCIPAL_NAME = "user@test.com";
    private static final String ACCESS_TOKEN = "access-token-value";
    private static final String REFRESH_TOKEN = "refresh-token-value";

    @Mock
    private AccessTokenService accessTokenService;

    @Mock
    private RefreshTokenService refreshTokenService;

    @Mock
    private AuthorizedClientTokenService authorizedClientTokenService;

    @Mock
    private ClientRegistrationRepository clientRegistrationRepository;

    private CustomOAuth2AuthorizedClientService sut;

    @BeforeEach
    void setUp() {
        sut = new CustomOAuth2AuthorizedClientService(
                accessTokenService,
                refreshTokenService,
                authorizedClientTokenService,
                clientRegistrationRepository
        );
    }

    @Test
    @DisplayName("저장된 access token과 refresh token으로 OAuth2AuthorizedClient를 복구한다")
    void loadAuthorizedClient_shouldReturnAuthorizedClient_whenTokensExist() {
        // given
        ClientRegistration clientRegistration = clientRegistration();

        given(clientRegistrationRepository.findByRegistrationId(REGISTRATION_ID))
                .willReturn(clientRegistration);

        given(accessTokenService.findValue(REGISTRATION_ID, PRINCIPAL_NAME))
                .willReturn(ACCESS_TOKEN);

        given(refreshTokenService.findValue(REGISTRATION_ID, PRINCIPAL_NAME))
                .willReturn(REFRESH_TOKEN);

        // when
        OAuth2AuthorizedClient result =
                sut.loadAuthorizedClient(REGISTRATION_ID, PRINCIPAL_NAME);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getClientRegistration()).isSameAs(clientRegistration);
        assertThat(result.getPrincipalName()).isEqualTo(PRINCIPAL_NAME);
        assertThat(result.getAccessToken().getTokenValue()).isEqualTo(ACCESS_TOKEN);
        assertThat(result.getRefreshToken()).isNotNull();
        assertThat(result.getRefreshToken().getTokenValue()).isEqualTo(REFRESH_TOKEN);
    }

    @Test
    @DisplayName("access token이 없으면 null을 반환한다")
    void loadAuthorizedClient_shouldReturnNull_whenAccessTokenMissing() {
        // given
        given(clientRegistrationRepository.findByRegistrationId(REGISTRATION_ID))
                .willReturn(clientRegistration());

        given(accessTokenService.findValue(REGISTRATION_ID, PRINCIPAL_NAME))
                .willReturn(null);

        // when
        OAuth2AuthorizedClient result =
                sut.loadAuthorizedClient(REGISTRATION_ID, PRINCIPAL_NAME);

        // then
        assertThat(result).isNull();

        then(refreshTokenService)
                .shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("refresh token이 없으면 access token만 가진 OAuth2AuthorizedClient를 반환한다")
    void loadAuthorizedClient_shouldReturnAuthorizedClientWithoutRefreshToken_whenRefreshTokenMissing() {
        // given
        given(clientRegistrationRepository.findByRegistrationId(REGISTRATION_ID))
                .willReturn(clientRegistration());

        given(accessTokenService.findValue(REGISTRATION_ID, PRINCIPAL_NAME))
                .willReturn(ACCESS_TOKEN);

        given(refreshTokenService.findValue(REGISTRATION_ID, PRINCIPAL_NAME))
                .willReturn(null);

        // when
        OAuth2AuthorizedClient result =
                sut.loadAuthorizedClient(REGISTRATION_ID, PRINCIPAL_NAME);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getAccessToken().getTokenValue()).isEqualTo(ACCESS_TOKEN);
        assertThat(result.getRefreshToken()).isNull();
    }

    @Test
    @DisplayName("ClientRegistration이 없으면 null을 반환한다")
    void loadAuthorizedClient_shouldReturnNull_whenClientRegistrationMissing() {
        // given
        given(clientRegistrationRepository.findByRegistrationId(REGISTRATION_ID))
                .willReturn(null);

        // when
        OAuth2AuthorizedClient result =
                sut.loadAuthorizedClient(REGISTRATION_ID, PRINCIPAL_NAME);

        // then
        assertThat(result).isNull();

        then(accessTokenService).shouldHaveNoInteractions();
        then(refreshTokenService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("OAuth2User principal이면 attributes와 token 값을 저장소에 위임한다")
    void saveAuthorizedClient_shouldSaveWithOAuth2UserAttributes() {
        // given
        OAuth2AuthorizedClient authorizedClient =
                authorizedClient(REFRESH_TOKEN);

        Map<String, Object> attributes = Map.of(
                "id", "user-id",
                "email", PRINCIPAL_NAME
        );

        OAuth2User principal = mock(OAuth2User.class);

//        given(principal.getName()).willReturn(PRINCIPAL_NAME);
        given(principal.getAttributes()).willReturn(attributes);

        Authentication authentication = mock(Authentication.class);

        given(authentication.getName()).willReturn(PRINCIPAL_NAME);
        given(authentication.getPrincipal()).willReturn(principal);

        // when
        sut.saveAuthorizedClient(authorizedClient, authentication);

        // then
        then(authorizedClientTokenService)
                .should()
                .save(
                        REGISTRATION_ID,
                        PRINCIPAL_NAME,
                        attributes,
                        ACCESS_TOKEN,
                        REFRESH_TOKEN
                );
    }

    @Test
    @DisplayName("refresh token이 없어도 access token 저장을 위임한다")
    void saveAuthorizedClient_shouldSaveWithoutRefreshToken_whenRefreshTokenMissing() {
        // given
        OAuth2AuthorizedClient authorizedClient =
                authorizedClient(null);

        Authentication authentication = mock(Authentication.class);

        given(authentication.getName()).willReturn(PRINCIPAL_NAME);
        given(authentication.getPrincipal()).willReturn("principal");

        // when
        sut.saveAuthorizedClient(authorizedClient, authentication);

        // then
        then(authorizedClientTokenService)
                .should()
                .save(
                        REGISTRATION_ID,
                        PRINCIPAL_NAME,
                        Map.of(),
                        ACCESS_TOKEN,
                        null
                );
    }

    @Test
    @DisplayName("clientRegistrationId와 principalName으로 AuthorizedClient 삭제를 위임한다")
    void removeAuthorizedClient_shouldDelegateRemoveAllByEmail() {
        // when
        sut.removeAuthorizedClient(REGISTRATION_ID, PRINCIPAL_NAME);

        // then
        then(authorizedClientTokenService)
                .should()
                .removeAllByEmail(PRINCIPAL_NAME);
    }

    private OAuth2AuthorizedClient authorizedClient(String refreshTokenValue) {
        OAuth2AccessToken accessToken =
                new OAuth2AccessToken(
                        OAuth2AccessToken.TokenType.BEARER,
                        ACCESS_TOKEN,
                        Instant.now(),
                        Instant.now().plusSeconds(3600)
                );

        OAuth2RefreshToken refreshToken = null;

        if (refreshTokenValue != null) {
            refreshToken = new OAuth2RefreshToken(
                    refreshTokenValue,
                    Instant.now()
            );
        }

        return new OAuth2AuthorizedClient(
                clientRegistration(),
                PRINCIPAL_NAME,
                accessToken,
                refreshToken
        );
    }

    private ClientRegistration clientRegistration() {
        return ClientRegistration.withRegistrationId(REGISTRATION_ID)
                .clientId(CLIENT_ID)
                .clientSecret("client-secret")
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .authorizationUri("https://example.com/oauth2/authorize")
                .tokenUri("https://example.com/oauth2/token")
                .userInfoUri("https://example.com/userinfo")
                .userNameAttributeName("sub")
                .scope("openid", "profile", "email")
                .clientName("Google")
                .build();
    }
}
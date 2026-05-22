package oauth2;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.example.common.enums.RedisKey;
import org.example.oauth2.properties.OAuth2RegisteredClientProperties;
import org.example.oauth2.CustomOAuth2AuthorizationService;
import org.example.oauth2.token.port.out.AccessTokenPort;
import org.example.oauth2.token.port.out.RefreshTokenPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.OAuth2TokenFormat;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.security.oauth2.core.AuthorizationGrantType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class CustomOAuth2AuthorizationServiceTest {

    private final String CLIENT_ID = "my-client-id";
    private final String CLIENT_SECRET = "my-client-secret";
    private final String REGISTRATION_ID = "my-authorization-server";

    private final String EMAIL = "user@test.com";
    private final String ACCESS_TOKEN = "access-token";
    private final String REFRESH_TOKEN = "refresh-token";

    private final Instant ISSUED_AT = Instant.parse("2026-05-14T04:00:00Z");
    private final Instant EXPIRES_AT = Instant.parse("2026-05-14T05:00:00Z");

    @Mock
    private AccessTokenPort redisAccessTokenAdapter;

    @Mock
    private RefreshTokenPort redisRefreshTokenAdapter;

    @Mock
    private RegisteredClientRepository registeredClientRepository;

    private CustomOAuth2AuthorizationService sut;

    @BeforeEach
    void setUp() {
        OAuth2RegisteredClientProperties registeredClientProperties = new OAuth2RegisteredClientProperties(
                CLIENT_ID,
                REGISTRATION_ID,
                CLIENT_SECRET
        );

        sut = new CustomOAuth2AuthorizationService(
                redisAccessTokenAdapter,
                redisRefreshTokenAdapter,
                registeredClientRepository,
                registeredClientProperties
        );
    }

    @Test
    @DisplayName("save는 refresh token이 없으면 Redis에 저장하지 않는다")
    void save_shouldDoNothing_whenRefreshTokenDoesNotExist() {
        // given
        OAuth2Authorization authorization = OAuth2Authorization
                .withRegisteredClient(registeredClient())
                .authorizationGrantType(AuthorizationGrantType.TOKEN_EXCHANGE)
                .principalName(EMAIL)
                .build();

        // when
        sut.save(authorization);

        // then
        then(redisRefreshTokenAdapter)
                .shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("save는 refresh token이 있으면 RedisRefreshTokenAdapter에 저장을 위임한다")
    void save_shouldCacheRefreshToken_whenRefreshTokenExists() {
        // given
        OAuth2RefreshToken refreshToken =
                new OAuth2RefreshToken(REFRESH_TOKEN, ISSUED_AT, EXPIRES_AT);

        OAuth2Authorization authorization = OAuth2Authorization
                .withRegisteredClient(registeredClient())
                .authorizationGrantType(AuthorizationGrantType.TOKEN_EXCHANGE)
                .principalName(EMAIL)
                .refreshToken(refreshToken)
                .build();

        // when
        sut.save(authorization);

        // then
        then(redisRefreshTokenAdapter)
                .should()
                .cache(REFRESH_TOKEN, REGISTRATION_ID, EMAIL);
    }

    @Test
    @DisplayName("findByToken은 access token claims가 존재하면 OAuth2Authorization을 재구성한다")
    void findByToken_shouldReturnAuthorization_whenAccessTokenExists() {
        // given
        String claimKey = RedisKey.ACCESS_CLAIMS.keyFor(ACCESS_TOKEN);

        Map<String, String> claims = new LinkedHashMap<>();
        claims.put("sub", EMAIL);
        claims.put("email", EMAIL);
        claims.put("iat", String.valueOf(ISSUED_AT.getEpochSecond()));
        claims.put("exp", String.valueOf(EXPIRES_AT.getEpochSecond()));

        given(redisAccessTokenAdapter.existsByClaimKey(claimKey))
                .willReturn(true);

        given(redisAccessTokenAdapter.findClaims(ACCESS_TOKEN))
                .willReturn(claims);

        given(registeredClientRepository.findById(CLIENT_ID))
                .willReturn(registeredClient());

        // when
        OAuth2Authorization result =
                sut.findByToken(ACCESS_TOKEN, OAuth2TokenType.ACCESS_TOKEN);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getPrincipalName()).isEqualTo(EMAIL);
        assertThat(result.getAuthorizationGrantType())
                .isEqualTo(AuthorizationGrantType.TOKEN_EXCHANGE);

        OAuth2Authorization.Token<OAuth2AccessToken> token =
                result.getAccessToken();

        assertThat(token).isNotNull();
        assertThat(token.getToken().getTokenValue()).isEqualTo(ACCESS_TOKEN);
        assertThat(token.getToken().getIssuedAt()).isEqualTo(ISSUED_AT);
        assertThat(token.getToken().getExpiresAt()).isEqualTo(EXPIRES_AT);

        assertThat(token.getMetadata())
                .containsEntry(
                        OAuth2TokenFormat.class.getName(),
                        OAuth2TokenFormat.SELF_CONTAINED.getValue()
                );

        assertThat(token.getClaims())
                .containsEntry("email", EMAIL)
                .containsEntry("sub", EMAIL);
    }

    @Test
    @DisplayName("findByToken은 access token claims가 없으면 null을 반환한다")
    void findByToken_shouldReturnNull_whenAccessClaimKeyDoesNotExist() {
        // given
        String claimKey = RedisKey.ACCESS_CLAIMS.keyFor(ACCESS_TOKEN);

        given(redisAccessTokenAdapter.existsByClaimKey(claimKey))
                .willReturn(false);

        // when
        OAuth2Authorization result =
                sut.findByToken(ACCESS_TOKEN, OAuth2TokenType.ACCESS_TOKEN);

        // then
        assertThat(result).isNull();

        then(redisAccessTokenAdapter)
                .should(never())
                .findClaims(anyString());
    }

    @Test
    @DisplayName("findByToken은 access token claims가 비어 있으면 null을 반환한다")
    void findByToken_shouldReturnNull_whenAccessClaimsAreEmpty() {
        // given
        String claimKey = RedisKey.ACCESS_CLAIMS.keyFor(ACCESS_TOKEN);

        given(redisAccessTokenAdapter.existsByClaimKey(claimKey))
                .willReturn(true);

        given(redisAccessTokenAdapter.findClaims(ACCESS_TOKEN))
                .willReturn(Collections.emptyMap());

        // when
        OAuth2Authorization result =
                sut.findByToken(ACCESS_TOKEN, OAuth2TokenType.ACCESS_TOKEN);

        // then
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("findByToken은 access token이 있어도 RegisteredClient가 없으면 null을 반환한다")
    void findByToken_shouldReturnNull_whenRegisteredClientDoesNotExistForAccessToken() {
        // given
        String claimKey = RedisKey.ACCESS_CLAIMS.keyFor(ACCESS_TOKEN);

        Map<String, String> claims = new LinkedHashMap<>();
        claims.put("email", EMAIL);
        claims.put("iat", String.valueOf(ISSUED_AT.getEpochSecond()));
        claims.put("exp", String.valueOf(EXPIRES_AT.getEpochSecond()));

        given(redisAccessTokenAdapter.existsByClaimKey(claimKey))
                .willReturn(true);

        given(redisAccessTokenAdapter.findClaims(ACCESS_TOKEN))
                .willReturn(claims);

        given(registeredClientRepository.findById(CLIENT_ID))
                .willReturn(null);

        // when
        OAuth2Authorization result =
                sut.findByToken(ACCESS_TOKEN, OAuth2TokenType.ACCESS_TOKEN);

        // then
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("findByToken은 refresh token이 존재하면 OAuth2Authorization을 재구성한다")
    void findByToken_shouldReturnAuthorization_whenRefreshTokenExists() {
        // given
        String refreshEmailKey =
                RedisKey.REFRESH_TOKEN.keyFor(REGISTRATION_ID, REFRESH_TOKEN);

        given(redisRefreshTokenAdapter.existsByEmailKey(refreshEmailKey))
                .willReturn(true);

        given(redisRefreshTokenAdapter.findEmail(REGISTRATION_ID, REFRESH_TOKEN))
                .willReturn(EMAIL);

        given(registeredClientRepository.findById(CLIENT_ID))
                .willReturn(registeredClient());

        // when
        OAuth2Authorization result =
                sut.findByToken(REFRESH_TOKEN, OAuth2TokenType.REFRESH_TOKEN);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getPrincipalName()).isEqualTo(EMAIL);
        assertThat(result.getAuthorizationGrantType())
                .isEqualTo(AuthorizationGrantType.REFRESH_TOKEN);

        OAuth2Authorization.Token<OAuth2RefreshToken> token =
                result.getRefreshToken();

        assertThat(token).isNotNull();
        assertThat(token.getToken().getTokenValue()).isEqualTo(REFRESH_TOKEN);
    }

    @Test
    @DisplayName("findByToken은 refresh token 역조회 key가 없으면 null을 반환한다")
    void findByToken_shouldReturnNull_whenRefreshEmailKeyDoesNotExist() {
        // given
        String refreshEmailKey =
                RedisKey.REFRESH_TOKEN.keyFor(REGISTRATION_ID, REFRESH_TOKEN);

        given(redisRefreshTokenAdapter.existsByEmailKey(refreshEmailKey))
                .willReturn(false);

        // when
        OAuth2Authorization result =
                sut.findByToken(REFRESH_TOKEN, OAuth2TokenType.REFRESH_TOKEN);

        // then
        assertThat(result).isNull();

        then(redisRefreshTokenAdapter)
                .should(never())
                .findEmail(anyString(), anyString());
    }

    @Test
    @DisplayName("findByToken은 refresh token으로 email을 찾지 못하면 null을 반환한다")
    void findByToken_shouldReturnNull_whenRefreshEmailIsNull() {
        // given
        String refreshEmailKey =
                RedisKey.REFRESH_TOKEN.keyFor(REGISTRATION_ID, REFRESH_TOKEN);

        given(redisRefreshTokenAdapter.existsByEmailKey(refreshEmailKey))
                .willReturn(true);

        given(redisRefreshTokenAdapter.findEmail(REGISTRATION_ID, REFRESH_TOKEN))
                .willReturn(null);

        // when
        OAuth2Authorization result =
                sut.findByToken(REFRESH_TOKEN, OAuth2TokenType.REFRESH_TOKEN);

        // then
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("findByToken은 refresh token이 있어도 RegisteredClient가 없으면 null을 반환한다")
    void findByToken_shouldReturnNull_whenRegisteredClientDoesNotExistForRefreshToken() {
        // given
        String refreshEmailKey =
                RedisKey.REFRESH_TOKEN.keyFor(REGISTRATION_ID, REFRESH_TOKEN);

        given(redisRefreshTokenAdapter.existsByEmailKey(refreshEmailKey))
                .willReturn(true);

        given(redisRefreshTokenAdapter.findEmail(REGISTRATION_ID, REFRESH_TOKEN))
                .willReturn(EMAIL);

        given(registeredClientRepository.findById(CLIENT_ID))
                .willReturn(null);

        // when
        OAuth2Authorization result =
                sut.findByToken(REFRESH_TOKEN, OAuth2TokenType.REFRESH_TOKEN);

        // then
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("findByToken은 token이 비어 있으면 예외를 던진다")
    void findByToken_shouldThrowException_whenTokenIsBlank() {
        // when & then
        assertThatThrownBy(() -> sut.findByToken(" ", OAuth2TokenType.ACCESS_TOKEN))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("token cannot be empty");
    }

    @Test
    @DisplayName("findById는 사용하지 않으므로 null을 반환한다")
    void findById_shouldReturnNull() {
        // when
        OAuth2Authorization result = sut.findById("authorization-id");

        // then
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("remove는 no-op으로 동작한다")
    void remove_shouldDoNothing() {
        // given
        OAuth2Authorization authorization = OAuth2Authorization
                .withRegisteredClient(registeredClient())
                .authorizationGrantType(AuthorizationGrantType.TOKEN_EXCHANGE)
                .principalName(EMAIL)
                .build();

        // when
        sut.remove(authorization);

        // then
        then(redisAccessTokenAdapter).shouldHaveNoInteractions();
        then(redisRefreshTokenAdapter).shouldHaveNoInteractions();
    }

    private RegisteredClient registeredClient() {
        return RegisteredClient.withId(CLIENT_ID)
                .clientId(CLIENT_ID)
                .clientSecret(CLIENT_SECRET)
                .authorizationGrantType(AuthorizationGrantType.TOKEN_EXCHANGE)
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                .tokenSettings(TokenSettings.builder().build())
                .clientSettings(ClientSettings.builder().build())
                .build();
    }
}

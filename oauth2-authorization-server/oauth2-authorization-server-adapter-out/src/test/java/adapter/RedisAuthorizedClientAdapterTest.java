package adapter;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.example.common.enums.RedisKey;
import org.example.oauth2.authorizationserver.token.adapter.out.redis.RedisAccessTokenAdapter;
import org.example.oauth2.authorizationserver.token.adapter.out.redis.RedisAuthorizedClientAdapter;
import org.example.oauth2.authorizationserver.token.adapter.out.redis.RedisRefreshTokenAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class RedisAuthorizedClientAdapterTest {

    @Mock
    private RedisAccessTokenAdapter redisAccessTokenAdapter;

    @Mock
    private RedisRefreshTokenAdapter redisRefreshTokenAdapter;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private RedisScript<Boolean> storeTokensLua;

    @Mock
    private RedisScript<Boolean> deleteTokensLua;

    private RedisAuthorizedClientAdapter sut;

    @BeforeEach
    void setUp() {
        sut = new RedisAuthorizedClientAdapter(
                redisAccessTokenAdapter,
                redisRefreshTokenAdapter,
                stringRedisTemplate,
                storeTokensLua,
                deleteTokensLua
        );
    }

    @Test
    @DisplayName("access token key가 이미 존재하면 기존 토큰을 삭제한 뒤 새 토큰을 저장한다")
    @SuppressWarnings({"unchecked", "rawtypes"})
    void save_shouldRemoveExistingTokensAndStoreNewTokens_whenAccessTokenAlreadyExists() {
        // given
        String clientRegistrationId = "my-authorization-server";
        String email = "user@test.com";
        String accessToken = "new-access-token";
        String refreshToken = "new-refresh-token";

        String accessTokenKey = RedisKey.ACCESS_TOKEN.keyFor(clientRegistrationId, email);

        Map<String, String> claims = new LinkedHashMap<>();
        claims.put("sub", email);
        claims.put("email", email);
        claims.put("iat", "1778736600");
        claims.put("exp", "1778740200");

        List<String> claimsFlat = List.of(
                "sub", email,
                "email", email,
                "iat", "1778736600",
                "exp", "1778740200"
        );

        given(redisAccessTokenAdapter.existsByTokenKey(accessTokenKey))
                .willReturn(true);

        given(redisAccessTokenAdapter.getTTL())
                .willReturn(Duration.ofSeconds(3600));

        given(redisRefreshTokenAdapter.getTTL())
                .willReturn(Duration.ofSeconds(604800));

        given(redisAccessTokenAdapter.getClaimFlattenMap(claims))
                .willReturn(claimsFlat);

        ArgumentCaptor<List<String>> deleteKeysCaptor =
                ArgumentCaptor.forClass(List.class);

        ArgumentCaptor<List<String>> storeKeysCaptor =
                ArgumentCaptor.forClass(List.class);

        ArgumentCaptor<Object[]> storeArgsCaptor =
                ArgumentCaptor.forClass(Object[].class);

        // when
        boolean result = sut.save(
                clientRegistrationId,
                email,
                accessToken,
                refreshToken,
                claims
        );

        // then
        assertThat(result).isTrue();

        then(stringRedisTemplate)
                .should()
                .execute(
                        eq(deleteTokensLua),
                        deleteKeysCaptor.capture()
                );

        assertThat(deleteKeysCaptor.getValue())
                .containsExactly(RedisKey.TOKENS_SET.keyFor(email));

        then(stringRedisTemplate)
                .should()
                .execute(
                        eq(storeTokensLua),
                        storeKeysCaptor.capture(),
                        storeArgsCaptor.capture()
                );

        assertThat(storeKeysCaptor.getValue())
                .containsExactly(
                        RedisKey.ACCESS_CLAIMS.keyFor(accessToken),
                        RedisKey.ACCESS_TOKEN.keyFor(clientRegistrationId, email),
                        RedisKey.REFRESH_TOKEN.keyFor(clientRegistrationId, refreshToken),
                        RedisKey.REFRESH_TOKEN.keyFor(clientRegistrationId, email),
                        RedisKey.TOKENS_SET.keyFor(email)
                );

        assertThat(storeArgsCaptor.getValue())
                .containsExactly(
                        "3600",
                        "604800",
                        "4",
                        "sub", email,
                        "email", email,
                        "iat", "1778736600",
                        "exp", "1778740200",
                        accessToken,
                        refreshToken,
                        email
                );
    }

    @Test
    @DisplayName("토큰 저장 시 storeTokens Lua에 정해진 순서의 keys와 args를 전달한다")
    @SuppressWarnings({"unchecked", "rawtypes"})
    void save_shouldExecuteStoreTokensLua_withExpectedKeysAndArgs() {
        // given
        String clientRegistrationId = "my-authorization-server";
        String email = "user@test.com";
        String accessToken = "access-token";
        String refreshToken = "refresh-token";

        String accessTokenKey = RedisKey.ACCESS_TOKEN.keyFor(clientRegistrationId, email);

        Map<String, String> claims = new LinkedHashMap<>();
        claims.put("sub", email);
        claims.put("email", email);
        claims.put("iat", "1778736600");
        claims.put("exp", "1778740200");

        List<String> claimsFlat = List.of(
                "sub", email,
                "email", email,
                "iat", "1778736600",
                "exp", "1778740200"
        );

        given(redisAccessTokenAdapter.existsByTokenKey(accessTokenKey))
                .willReturn(false);

        given(redisAccessTokenAdapter.getTTL())
                .willReturn(Duration.ofSeconds(3600));

        given(redisRefreshTokenAdapter.getTTL())
                .willReturn(Duration.ofSeconds(604800));

        given(redisAccessTokenAdapter.getClaimFlattenMap(claims))
                .willReturn(claimsFlat);

        ArgumentCaptor<List<String>> keysCaptor =
                ArgumentCaptor.forClass(List.class);

        ArgumentCaptor<Object[]> argsCaptor =
                ArgumentCaptor.forClass(Object[].class);

        // when
        boolean result = sut.save(
                clientRegistrationId,
                email,
                accessToken,
                refreshToken,
                claims
        );

        // then
        assertThat(result).isTrue();

        then(stringRedisTemplate)
                .should()
                .execute(
                        eq(storeTokensLua),
                        keysCaptor.capture(),
                        argsCaptor.capture()
                );

        assertThat(keysCaptor.getValue())
                .containsExactly(
                        RedisKey.ACCESS_CLAIMS.keyFor(accessToken),
                        RedisKey.ACCESS_TOKEN.keyFor(clientRegistrationId, email),
                        RedisKey.REFRESH_TOKEN.keyFor(clientRegistrationId, refreshToken),
                        RedisKey.REFRESH_TOKEN.keyFor(clientRegistrationId, email),
                        RedisKey.TOKENS_SET.keyFor(email)
                );

        assertThat(argsCaptor.getValue())
                .containsExactly(
                        "3600",
                        "604800",
                        "4",
                        "sub", email,
                        "email", email,
                        "iat", "1778736600",
                        "exp", "1778740200",
                        accessToken,
                        refreshToken,
                        email
                );
    }

    @Test
    @DisplayName("토큰 삭제 시 deleteTokens Lua에 tokens set key를 전달한다")
    @SuppressWarnings({"unchecked", "rawtypes"})
    void remove_shouldExecuteDeleteTokensLua_withTokensSetKey() {
        // given
        String email = "user@test.com";

        ArgumentCaptor<List<String>> keysCaptor =
                ArgumentCaptor.forClass(List.class);

        // when
        boolean result = sut.remove(email);

        // then
        assertThat(result).isTrue();

        then(stringRedisTemplate)
                .should()
                .execute(
                        eq(deleteTokensLua),
                        keysCaptor.capture()
                );

        assertThat(keysCaptor.getValue())
                .containsExactly(RedisKey.TOKENS_SET.keyFor(email));
    }
}
package adapter;

import config.TestPropertiesConfig;
import org.example.common.testcontainer.RedisTestContainerInitializer;
import org.example.common.test.config.TestBootApplication;
import config.TestRedisConfig;
import org.example.common.enums.RedisKey;
import org.example.common.redis.StringRedisHashOperations;
import org.example.oauth2.token.adapter.RedisAccessTokenAdapter;
import org.example.oauth2.token.adapter.RedisAuthorizedClientAdapter;
import org.example.oauth2.token.adapter.RedisRefreshTokenAdapter;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.redis.DataRedisTest;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ContextConfiguration;

import static org.assertj.core.api.Assertions.assertThat;

@DataRedisTest(properties = {"spring.data.redis.repositories.enabled=false"})
@ContextConfiguration(
        classes = {
                TestBootApplication.class,
                TestRedisConfig.class,
                TestPropertiesConfig.class,
                RedisAuthorizedClientAdapter.class,
                RedisAccessTokenAdapter.class,
                RedisRefreshTokenAdapter.class,
                StringRedisHashOperations.class
        },
        initializers = RedisTestContainerInitializer.class
)
class RedisAuthorizedClientAdapterIntegrationTest {

    @Autowired
    private RedisAuthorizedClientAdapter redisAuthorizedClientAdapter;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @BeforeEach
    void setUp() {
        Assertions.assertNotNull(stringRedisTemplate.getConnectionFactory());

        stringRedisTemplate.getConnectionFactory()
                .getConnection()
                .serverCommands()
                .flushDb();
    }

    @Test
    @DisplayName("토큰 저장 시 Lua를 실행하여 access token, refresh token, claims, index set을 Redis에 저장한다")
    void save_shouldStoreTokensClaimsAndIndexSet() {
        // given
        String clientRegistrationId = "my-authorization-server";
        String email = "user@test.com";
        String accessToken = "access-token-value";
        String refreshToken = "refresh-token-value";

        Map<String, String> claims = new LinkedHashMap<>();
        claims.put("sub", email);
        claims.put("email", email);
        claims.put("iat", "1778736600");
        claims.put("exp", "1778740200");

        String claimKey = RedisKey.ACCESS_CLAIMS.keyFor(accessToken);
        String accessTokenKey = RedisKey.ACCESS_TOKEN.keyFor(clientRegistrationId, email);
        String refreshEmailKey = RedisKey.REFRESH_TOKEN.keyFor(clientRegistrationId, refreshToken);
        String refreshTokenKey = RedisKey.REFRESH_TOKEN.keyFor(clientRegistrationId, email);
        String tokensSetKey = RedisKey.TOKENS_SET.keyFor(email);

        // when
        boolean result = redisAuthorizedClientAdapter.save(
                clientRegistrationId,
                email,
                accessToken,
                refreshToken,
                claims
        );

        // then
        assertThat(result).isTrue();

        assertThat(stringRedisTemplate.opsForHash().entries(claimKey))
                .containsEntry("sub", email)
                .containsEntry("email", email)
                .containsEntry("iat", "1778736600")
                .containsEntry("exp", "1778740200");

        assertThat(stringRedisTemplate.opsForValue().get(accessTokenKey))
                .isEqualTo(accessToken);

        assertThat(stringRedisTemplate.opsForValue().get(refreshEmailKey))
                .isEqualTo(email);

        assertThat(stringRedisTemplate.opsForValue().get(refreshTokenKey))
                .isEqualTo(refreshToken);

        assertThat(stringRedisTemplate.opsForSet().members(tokensSetKey))
                .containsExactlyInAnyOrder(
                        claimKey,
                        accessTokenKey,
                        refreshEmailKey,
                        refreshTokenKey
                );

        assertThat(stringRedisTemplate.getExpire(claimKey)).isGreaterThan(0);
        assertThat(stringRedisTemplate.getExpire(accessTokenKey)).isGreaterThan(0);
        assertThat(stringRedisTemplate.getExpire(refreshEmailKey)).isGreaterThan(0);
        assertThat(stringRedisTemplate.getExpire(refreshTokenKey)).isGreaterThan(0);
        assertThat(stringRedisTemplate.getExpire(tokensSetKey)).isGreaterThan(0);
    }

    @Test
    @DisplayName("이미 access token key가 존재하면 저장하지 않고 false를 반환한다")
    void save_shouldReturnFalse_whenAccessTokenAlreadyExists() {
        // given
        String clientRegistrationId = "my-authorization-server";
        String email = "user@test.com";

        Map<String, String> claims = new LinkedHashMap<>();
        claims.put("sub", email);
        claims.put("email", email);
        claims.put("iat", "1778736600");
        claims.put("exp", "1778740200");

        boolean firstResult = redisAuthorizedClientAdapter.save(
                clientRegistrationId,
                email,
                "access-token-value",
                "refresh-token-value",
                claims
        );

        // when
        boolean secondResult = redisAuthorizedClientAdapter.save(
                clientRegistrationId,
                email,
                "another-access-token-value",
                "another-refresh-token-value",
                claims
        );

        // then
        assertThat(firstResult).isTrue();
        assertThat(secondResult).isFalse();
    }

    @Test
    @DisplayName("토큰 삭제 시 index set에 저장된 관련 Redis key를 모두 삭제한다")
    void remove_shouldDeleteAllIndexedTokenKeys() {
        // given
        String clientRegistrationId = "my-authorization-server";
        String email = "user@test.com";
        String accessToken = "access-token-value";
        String refreshToken = "refresh-token-value";

        Map<String, String> claims = new LinkedHashMap<>();
        claims.put("sub", email);
        claims.put("email", email);
        claims.put("iat", "1778736600");
        claims.put("exp", "1778740200");

        String claimKey = RedisKey.ACCESS_CLAIMS.keyFor(accessToken);
        String accessTokenKey = RedisKey.ACCESS_TOKEN.keyFor(clientRegistrationId, email);
        String refreshEmailKey = RedisKey.REFRESH_TOKEN.keyFor(clientRegistrationId, refreshToken);
        String refreshTokenKey = RedisKey.REFRESH_TOKEN.keyFor(clientRegistrationId, email);
        String tokensSetKey = RedisKey.TOKENS_SET.keyFor(email);

        redisAuthorizedClientAdapter.save(
                clientRegistrationId,
                email,
                accessToken,
                refreshToken,
                claims
        );

        assertThat(stringRedisTemplate.hasKey(tokensSetKey))
                .isTrue();

        // when
        boolean result = redisAuthorizedClientAdapter.remove(email);

        // then
        assertThat(result).isTrue();

        assertThat(stringRedisTemplate.hasKey(claimKey)).isFalse();
        assertThat(stringRedisTemplate.hasKey(accessTokenKey)).isFalse();
        assertThat(stringRedisTemplate.hasKey(refreshEmailKey)).isFalse();
        assertThat(stringRedisTemplate.hasKey(refreshTokenKey)).isFalse();
        assertThat(stringRedisTemplate.hasKey(tokensSetKey)).isFalse();
    }
}
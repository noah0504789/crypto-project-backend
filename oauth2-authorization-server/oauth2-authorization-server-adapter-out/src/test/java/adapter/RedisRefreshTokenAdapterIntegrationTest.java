package adapter;

import config.TestPropertiesConfig;
import org.example.test.testcontainer.RedisTestContainerInitializer;
import org.example.test.config.TestBootApplication;
import config.TestRedisConfig;
import org.example.common.enums.RedisKey;
import org.example.oauth2.token.adapter.out.redis.RedisRefreshTokenAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.redis.DataRedisTest;

import org.junit.jupiter.api.DisplayName;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ContextConfiguration;

import static org.assertj.core.api.Assertions.assertThat;

@DataRedisTest(properties = {
        "spring.data.redis.repositories.enabled=false"
})
@ContextConfiguration(
        classes = {
                TestBootApplication.class,
                TestRedisConfig.class,
                TestPropertiesConfig.class,
                RedisRefreshTokenAdapter.class
        },
        initializers = RedisTestContainerInitializer.class
)
class RedisRefreshTokenAdapterIntegrationTest {

    @Autowired
    private RedisRefreshTokenAdapter sut;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @BeforeEach
    void setUp() {
        stringRedisTemplate.getConnectionFactory()
                .getConnection()
                .serverCommands()
                .flushDb();
    }

    @Test
    @DisplayName("refresh token 저장 시 token key, email 역조회 key, index set을 Redis에 저장한다")
    void cache_shouldStoreRefreshTokenEmailReverseKeyAndIndexSet() {
        // given
        String clientRegistrationId = "my-authorization-server";
        String email = "user@test.com";
        String refreshToken = "refresh-token";

        String refreshTokenKey =
                RedisKey.REFRESH_TOKEN.keyFor(clientRegistrationId, email);

        String refreshEmailKey =
                RedisKey.REFRESH_TOKEN.keyFor(clientRegistrationId, refreshToken);

        String tokensSetKey =
                RedisKey.TOKENS_SET.keyFor(email);

        // when
        sut.cache(refreshToken, clientRegistrationId, email);

        // then
        assertThat(stringRedisTemplate.opsForValue().get(refreshTokenKey))
                .isEqualTo(refreshToken);

        assertThat(stringRedisTemplate.opsForValue().get(refreshEmailKey))
                .isEqualTo(email);

        assertThat(stringRedisTemplate.opsForSet().members(tokensSetKey))
                .containsExactlyInAnyOrder(
                        refreshTokenKey,
                        refreshEmailKey
                );

        assertThat(stringRedisTemplate.getExpire(refreshTokenKey))
                .isGreaterThan(0);

        assertThat(stringRedisTemplate.getExpire(refreshEmailKey))
                .isGreaterThan(0);

        assertThat(stringRedisTemplate.getExpire(tokensSetKey))
                .isGreaterThan(0);
    }

    @Test
    @DisplayName("findValue는 Redis에 저장된 email 기준 refresh token을 조회한다")
    void findValue_shouldReturnRefreshTokenFromRedis() {
        // given
        String clientRegistrationId = "my-authorization-server";
        String email = "user@test.com";
        String refreshToken = "refresh-token";

        sut.cache(refreshToken, clientRegistrationId, email);

        // when
        String result = sut.findValue(clientRegistrationId, email);

        // then
        assertThat(result).isEqualTo(refreshToken);
    }

    @Test
    @DisplayName("findEmail은 Redis에 저장된 refresh token 기준 email을 조회한다")
    void findEmail_shouldReturnEmailFromRedis() {
        // given
        String clientRegistrationId = "my-authorization-server";
        String email = "user@test.com";
        String refreshToken = "refresh-token";

        sut.cache(refreshToken, clientRegistrationId, email);

        // when
        String result = sut.findEmail(clientRegistrationId, refreshToken);

        // then
        assertThat(result).isEqualTo(email);
    }

    @Test
    @DisplayName("existsByEmailKey는 refresh token 역조회 key가 존재하면 true를 반환한다")
    void existsByEmailKey_shouldReturnTrue_whenRefreshEmailKeyExists() {
        // given
        String clientRegistrationId = "my-authorization-server";
        String email = "user@test.com";
        String refreshToken = "refresh-token";

        sut.cache(refreshToken, clientRegistrationId, email);

        String refreshEmailKey =
                RedisKey.REFRESH_TOKEN.keyFor(clientRegistrationId, refreshToken);

        // when
        boolean result = sut.existsByEmailKey(refreshEmailKey);

        // then
        assertThat(result).isTrue();
    }
}
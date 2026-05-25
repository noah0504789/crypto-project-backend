package adapter;

import java.time.Duration;
import java.util.List;

import org.example.common.enums.RedisKey;
import org.example.common.properties.JwtProperties;
import org.example.oauth2.authorizationserver.token.adapter.out.redis.RedisRefreshTokenAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class RedisRefreshTokenAdapterTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private RedisScript<Boolean> storeRefreshTokenLua;

    @Mock
    private JwtProperties jwtProperties;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private RedisRefreshTokenAdapter sut;

    @BeforeEach
    void setUp() {
        sut = new RedisRefreshTokenAdapter(
                stringRedisTemplate,
                storeRefreshTokenLua,
                jwtProperties
        );
    }

    @Test
    @DisplayName("refresh token 저장 시 storeRefreshToken Lua에 정해진 순서의 keys와 args를 전달한다")
    @SuppressWarnings({"unchecked", "rawtypes"})
    void cache_shouldExecuteStoreRefreshTokenLua_withExpectedKeysAndArgs() {
        // given
        String clientRegistrationId = "my-authorization-server";
        String email = "user@test.com";
        String refreshToken = "refresh-token";

        given(jwtProperties.refreshTokenExpirationMs())
                .willReturn(604_800_000L);

        ArgumentCaptor<List<String>> keysCaptor =
                ArgumentCaptor.forClass(List.class);

        ArgumentCaptor<Object[]> argsCaptor =
                ArgumentCaptor.forClass(Object[].class);

        // when
        sut.cache(refreshToken, clientRegistrationId, email);

        // then
        then(stringRedisTemplate)
                .should()
                .execute(
                        eq(storeRefreshTokenLua),
                        keysCaptor.capture(),
                        argsCaptor.capture()
                );

        assertThat(keysCaptor.getValue())
                .containsExactly(
                        RedisKey.REFRESH_TOKEN.keyFor(clientRegistrationId, email),
                        RedisKey.REFRESH_EMAIL_PREFIX.keyFor(clientRegistrationId),
                        RedisKey.TOKENS_SET.keyFor(email)
                );

        assertThat(argsCaptor.getValue())
                .containsExactly(
                        "604800",
                        refreshToken,
                        email
                );
    }

    @Test
    @DisplayName("getTTL은 refresh token 만료 시간을 Duration으로 반환한다")
    void getTTL_shouldReturnRefreshTokenExpirationDuration() {
        // given
        given(jwtProperties.refreshTokenExpirationMs())
                .willReturn(604_800_000L);

        // when
        Duration result = sut.getTTL();

        // then
        assertThat(result).isEqualTo(Duration.ofDays(7));
    }

    @Test
    @DisplayName("findValue는 clientRegistrationId와 username으로 refresh token을 조회한다")
    void findValue_shouldReturnRefreshToken() {
        // given
        String clientRegistrationId = "my-authorization-server";
        String username = "user@test.com";
        String key = RedisKey.REFRESH_TOKEN.keyFor(clientRegistrationId, username);

        given(stringRedisTemplate.opsForValue())
                .willReturn(valueOperations);

        given(valueOperations.get(key))
                .willReturn("refresh-token");

        // when
        String result = sut.findValue(clientRegistrationId, username);

        // then
        assertThat(result).isEqualTo("refresh-token");
        then(valueOperations).should().get(key);
    }

    @Test
    @DisplayName("findEmail은 clientRegistrationId와 refresh token으로 email을 조회한다")
    void findEmail_shouldReturnEmail() {
        // given
        String clientRegistrationId = "my-authorization-server";
        String refreshToken = "refresh-token";
        String key = RedisKey.REFRESH_TOKEN.keyFor(clientRegistrationId, refreshToken);

        given(stringRedisTemplate.opsForValue())
                .willReturn(valueOperations);

        given(valueOperations.get(key))
                .willReturn("user@test.com");

        // when
        String result = sut.findEmail(clientRegistrationId, refreshToken);

        // then
        assertThat(result).isEqualTo("user@test.com");
        then(valueOperations).should().get(key);
    }

    @Test
    @DisplayName("existsByEmailKey는 Redis key가 존재하면 true를 반환한다")
    void existsByEmailKey_shouldReturnTrue_whenKeyExists() {
        // given
        String emailKey = RedisKey.REFRESH_TOKEN.keyFor(
                "my-authorization-server",
                "refresh-token"
        );

        given(stringRedisTemplate.hasKey(emailKey))
                .willReturn(true);

        // when
        boolean result = sut.existsByEmailKey(emailKey);

        // then
        assertThat(result).isTrue();
    }
}
package org.example.oauth2.token.adapter.out.redis;

import lombok.extern.slf4j.Slf4j;
import org.example.common.enums.RedisKey;
import org.example.common.properties.JwtProperties;
import org.example.oauth2.token.port.out.RefreshTokenPort;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Repository
public class RedisRefreshTokenAdapter implements RefreshTokenPort {

    private final StringRedisTemplate stringRedisTemplate;
    private final RedisScript<Boolean> storeTokensLua;
    private final JwtProperties jwtProperties;

    public RedisRefreshTokenAdapter(
            StringRedisTemplate stringRedisTemplate,
            @Qualifier("storeRefreshToken_lua") RedisScript<Boolean> storeRefreshTokenLua,
            JwtProperties jwtProperties
    ) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.storeTokensLua = storeRefreshTokenLua;
        this.jwtProperties = jwtProperties;
    }

    public void cache(String refreshToken, String clientRegistrationId, String email) {
        List<String> keys = new ArrayList<>();
        keys.add(RedisKey.REFRESH_TOKEN.keyFor(clientRegistrationId, email));
        keys.add(RedisKey.REFRESH_EMAIL_PREFIX.keyFor(clientRegistrationId));
        keys.add(RedisKey.TOKENS_SET.keyFor(email));

        List<String> args = new ArrayList<>();
        args.add(String.valueOf(getTTL().getSeconds()));
        args.add(refreshToken);
        args.add(email);

        stringRedisTemplate.execute(storeTokensLua, keys, args.toArray(new String[0]));
    }

    public Duration getTTL() {
        return Duration.ofMillis(jwtProperties.refreshTokenExpirationMs());
    }

    public String findValue(String clientRegistrationId, String username) {
        String key = RedisKey.REFRESH_TOKEN.keyFor(clientRegistrationId, username);

        return stringRedisTemplate.opsForValue().get(key);
    }

    public String findEmail(String clientRegistrationId, String token) {
        String key = RedisKey.REFRESH_TOKEN.keyFor(clientRegistrationId, token);

        return stringRedisTemplate.opsForValue().get(key);
    }

    public boolean existsByEmailKey(String emailKey) {
        return stringRedisTemplate.hasKey(emailKey);
    }
}

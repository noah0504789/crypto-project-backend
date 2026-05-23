package org.example.oauth2.token.adapter.out.redis;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.common.enums.RedisKey;
import org.example.common.properties.JwtProperties;
import org.example.common.redis.operation.StringRedisHashOperations;
import org.example.oauth2.token.port.out.AccessTokenPort;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

@Slf4j
@Repository
@RequiredArgsConstructor
public class RedisAccessTokenAdapter implements AccessTokenPort {

    private final StringRedisHashOperations hash;
    private final StringRedisTemplate stringRedisTemplate;
    private final JwtProperties jwtProperties;

    public Duration getTTL() {
        return Duration.ofMillis(jwtProperties.accessTokenExpirationMs());
    }

    public String findValue(String clientRegistrationId, String username) {
        String key = RedisKey.ACCESS_TOKEN.keyFor(clientRegistrationId, username);

        return stringRedisTemplate.opsForValue().get(key);
    }

    public Map<String, String> findClaims(String accessToken) {
        String claimKey = RedisKey.ACCESS_CLAIMS.keyFor(accessToken);

        return hash.find(claimKey);
    }

    public boolean existsByTokenKey(String tokenKey) {
        return stringRedisTemplate.hasKey(tokenKey);
    }

    public boolean existsByClaimKey(String claimKey) {
        return hash.hasEntries(claimKey);
    }

    public List<String> getClaimFlattenMap(Map<String, String> claims) {
        return claims.entrySet()
                .stream()
                .flatMap(e -> Stream.of(e.getKey(), e.getValue()))
                .toList();
    }
}

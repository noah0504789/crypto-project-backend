package org.example.oauth2.authorizationserver.token.adapter.out.redis;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.common.enums.RedisKey;
import org.example.oauth2.authorizationserver.infra.redis.RedisSetRegistry;
import org.example.oauth2.authorizationserver.token.application.port.out.BlacklistTokenPort;
import org.springframework.data.redis.support.collections.RedisSet;
import org.springframework.stereotype.Repository;

@Slf4j
@Repository
@RequiredArgsConstructor
public class RedisBlacklistTokenAdapter implements BlacklistTokenPort {

    private final RedisSetRegistry registry;

    private RedisSet<String> blacklistTokenCache;

    @PostConstruct
    public void init() {
        blacklistTokenCache = registry.getSet(RedisKey.BLACKLIST_TOKEN_SET.keyFor());
    }

    public void register(String accessToken) {
        blacklistTokenCache.add(accessToken);
    }

    public boolean existsByAccessToken(String accessToken) {
        return blacklistTokenCache.contains(accessToken);
    }
}

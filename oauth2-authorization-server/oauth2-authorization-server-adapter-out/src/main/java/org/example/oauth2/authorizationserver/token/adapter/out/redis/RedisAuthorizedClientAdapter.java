package org.example.oauth2.authorizationserver.token.adapter.out.redis;

import lombok.extern.slf4j.Slf4j;
import org.example.common.enums.RedisKey;
import org.example.oauth2.authorizationserver.token.application.port.out.AuthorizedClientPort;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Repository
public class RedisAuthorizedClientAdapter implements AuthorizedClientPort {

    private final RedisAccessTokenAdapter redisAccessTokenAdapter;
    private final RedisRefreshTokenAdapter redisRefreshTokenAdapter;
    private final StringRedisTemplate stringRedisTemplate;
    private final RedisScript<Boolean> storeTokensLua;
    private final RedisScript<Boolean> deleteTokensLua;

    public RedisAuthorizedClientAdapter(
            RedisAccessTokenAdapter redisAccessTokenAdapter,
            RedisRefreshTokenAdapter redisRefreshTokenAdapter,
            StringRedisTemplate stringRedisTemplate,
            @Qualifier("storeTokens_lua") RedisScript<Boolean> storeTokensLua,
            @Qualifier("deleteTokens_lua") RedisScript<Boolean> deleteTokensLua
    ) {
        this.redisAccessTokenAdapter = redisAccessTokenAdapter;
        this.redisRefreshTokenAdapter = redisRefreshTokenAdapter;
        this.stringRedisTemplate = stringRedisTemplate;
        this.storeTokensLua = storeTokensLua;
        this.deleteTokensLua = deleteTokensLua;
    }

    public boolean save(
            String clientRegistrationId,
            String email,
            String accessToken,
            String refreshToken,
            Map<String, String> claims
    ) {
        String accessTokenKey = RedisKey.ACCESS_TOKEN.keyFor(clientRegistrationId, email);

        if (redisAccessTokenAdapter.existsByTokenKey(accessTokenKey)) {
            return false;
        }

        List<String> keys = new ArrayList<>();
        keys.add(RedisKey.ACCESS_CLAIMS.keyFor(accessToken));
        keys.add(accessTokenKey);
        keys.add(RedisKey.REFRESH_TOKEN.keyFor(clientRegistrationId, refreshToken));
        keys.add(RedisKey.REFRESH_TOKEN.keyFor(clientRegistrationId, email));
        keys.add(RedisKey.TOKENS_SET.keyFor(email));

        Duration accessTTL = redisAccessTokenAdapter.getTTL();
        Duration refreshTTL = redisRefreshTokenAdapter.getTTL();

        List<String> args = new ArrayList<>();
        args.add(String.valueOf(accessTTL.getSeconds()));
        args.add(String.valueOf(refreshTTL.getSeconds()));

        List<String> claimsFlat = redisAccessTokenAdapter.getClaimFlattenMap(claims);
        args.add(String.valueOf(claimsFlat.size() / 2));
        args.addAll(claimsFlat);

        args.add(accessToken);
        args.add(refreshToken);
        args.add(email);

        stringRedisTemplate.execute(storeTokensLua, keys, args.toArray(new String[0]));

        return true;
    }

    public boolean remove(String email) {
        List<String> keys = List.of(RedisKey.TOKENS_SET.keyFor(email));

        stringRedisTemplate.execute(deleteTokensLua, keys);

        return true;
    }
}

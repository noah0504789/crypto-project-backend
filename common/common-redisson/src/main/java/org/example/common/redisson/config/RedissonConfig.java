package org.example.common.redisson.config;

import lombok.RequiredArgsConstructor;
import org.example.common.properties.AppRedisProperties;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.ClusterServersConfig;
import org.redisson.config.Config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;

import java.util.List;

@Configuration
@RequiredArgsConstructor
public class RedissonConfig {

    private static final String REDIS_PREFIX = "redis://";

    @Bean(destroyMethod = "shutdown")
    public RedissonClient redissonClient(RedisProperties redisProperties, AppRedisProperties appRedisProperties) {
        Config config = new Config();

        int timeoutMs = (int) redisProperties.getTimeout().toMillis();
        int connectTimeoutMs = (int) redisProperties.getConnectTimeout().toMillis();

        ClusterServersConfig cluster = config.useClusterServers()
                .setScanInterval((int) appRedisProperties.cluster().refresh().period().toMillis())
                .setTimeout(timeoutMs)
                .setConnectTimeout(connectTimeoutMs)
                .setRetryAttempts(3)
                .setRetryInterval(100)
                .setCheckSlotsCoverage(false);

        List<String> nodes = redisProperties.getCluster().getNodes();

        cluster.addNodeAddress(
                nodes.stream()
                        .map(node -> REDIS_PREFIX + node)
                        .toArray(String[]::new)
        );

        if (redisProperties.getPassword() != null && !redisProperties.getPassword().isBlank()) {
            cluster.setPassword(redisProperties.getPassword());
        }

        return Redisson.create(config);
    }
}

package org.example.common.redis.support;

import io.lettuce.core.ReadFrom;
import io.lettuce.core.SocketOptions;
import io.lettuce.core.cluster.ClusterClientOptions;
import io.lettuce.core.cluster.ClusterTopologyRefreshOptions;
import io.lettuce.core.resource.ClientResources;
import org.example.common.properties.AppRedisProperties;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.data.redis.connection.RedisClusterConfiguration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

public final class RedisConnectionFactorySupport {

    private RedisConnectionFactorySupport() {
    }

    public static RedisConnectionFactory createClusterConnectionFactory(
            ClientResources clientResources,
            RedisProperties redisProperties,
            AppRedisProperties appRedisProperties
    ) {
        return createClusterConnectionFactory(clientResources, redisProperties, appRedisProperties, null);
    }

    public static RedisConnectionFactory createClusterConnectionFactory(
            ClientResources clientResources,
            RedisProperties redisProperties,
            AppRedisProperties appRedisProperties,
            ReadFrom readFrom
    ) {
        RedisClusterConfiguration clusterConfig =
                new RedisClusterConfiguration(redisProperties.getCluster().getNodes());

        clusterConfig.setMaxRedirects(redisProperties.getCluster().getMaxRedirects());

        SocketOptions socketOptions = SocketOptions.builder()
                .connectTimeout(redisProperties.getConnectTimeout())
                .keepAlive(appRedisProperties.socket().keepAlive())
                .build();

        ClusterTopologyRefreshOptions.Builder refreshBuilder =
                ClusterTopologyRefreshOptions.builder()
                        .enablePeriodicRefresh(appRedisProperties.cluster().refresh().period())
                        .dynamicRefreshSources(appRedisProperties.cluster().refresh().dynamicRefreshSources());

        if (appRedisProperties.cluster().refresh().adaptive()) {
            refreshBuilder.enableAllAdaptiveRefreshTriggers();
        }

        ClusterClientOptions clusterClientOptions = ClusterClientOptions.builder()
                .pingBeforeActivateConnection(true)
                .autoReconnect(true)
                .socketOptions(socketOptions)
                .topologyRefreshOptions(refreshBuilder.build())
                .maxRedirects(redisProperties.getCluster().getMaxRedirects())
                .build();

        LettuceClientConfiguration.LettuceClientConfigurationBuilder clientConfigBuilder =
                LettuceClientConfiguration.builder()
                        .commandTimeout(redisProperties.getTimeout())
                        .clientResources(clientResources)
                        .clientOptions(clusterClientOptions);

        if (readFrom != null) {
            clientConfigBuilder.readFrom(readFrom);
        }

        LettuceConnectionFactory lettuceConnectionFactory =
                new LettuceConnectionFactory(clusterConfig, clientConfigBuilder.build());

        lettuceConnectionFactory.setValidateConnection(appRedisProperties.connection().validate());
        lettuceConnectionFactory.afterPropertiesSet();

        return lettuceConnectionFactory;
    }

    public static RedisTemplate<String, String> createStringRedisTemplate(
            RedisConnectionFactory redisConnectionFactory
    ) {
        RedisTemplate<String, String> template = new RedisTemplate<>();
        template.setConnectionFactory(redisConnectionFactory);

        StringRedisSerializer stringRedisSerializer = new StringRedisSerializer();

        template.setKeySerializer(stringRedisSerializer);
        template.setValueSerializer(stringRedisSerializer);
        template.setHashKeySerializer(stringRedisSerializer);
        template.setHashValueSerializer(stringRedisSerializer);

        template.afterPropertiesSet();
        return template;
    }
}

package org.example.market.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.example.market.application.cache.MarketCacheNames;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@EnableCaching
@Configuration
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager(MarketCacheNames.MARKETS);
        cacheManager.setCaffeine(
                Caffeine.newBuilder()
                        .maximumSize(200)
                        .expireAfterWrite(Duration.ofDays(30))
        );
        return cacheManager;
    }
}
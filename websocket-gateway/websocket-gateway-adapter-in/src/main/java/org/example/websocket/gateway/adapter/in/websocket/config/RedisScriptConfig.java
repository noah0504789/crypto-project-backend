package org.example.websocket.gateway.adapter.in.websocket.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.RedisScript;

@Configuration
public class RedisScriptConfig {

    @Bean("chatMessageRateLimit_lua")
    public RedisScript<Long> chatMessageRateLimit() {
        return RedisScript.of(new ClassPathResource("META-INF/scripts/chatMessageRateLimit.lua"), Long.class);
    }
}

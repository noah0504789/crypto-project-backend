package org.example.apigateway.config;

import org.example.apigateway.ratelimit.GatewayRateLimitProperties;
import org.example.common.properties.ApiPathProperties;
import org.example.common.properties.JwtProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.TestConfiguration;

@TestConfiguration
@EnableConfigurationProperties({
        JwtProperties.class,
        ApiPathProperties.class,
        GatewayRateLimitProperties.class
})
public class TestPropertiesConfig {
}

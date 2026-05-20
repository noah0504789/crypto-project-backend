package org.example.infra.config;

import org.example.common.properties.JwtProperties;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Configuration
public class HttpClientConfig {

    @Bean
    public RestTemplate jwtRestTemplate(RestTemplateBuilder builder, JwtProperties jwtProperties) {
        return builder
                .connectTimeout(Duration.ofMillis(jwtProperties.connectTimeoutMs()))
                .readTimeout(Duration.ofMillis(jwtProperties.readTimeoutMs()))
                .build();
    }
}

package org.example.apigateway.config;

import lombok.RequiredArgsConstructor;
import org.example.common.properties.FrontendProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

@Configuration
@RequiredArgsConstructor
public class CorsConfig {

    private final FrontendProperties frontendProperties;

    @Bean
    public CorsConfiguration corsConfiguration() {
        CorsConfiguration config = new CorsConfiguration();
        config.addAllowedOrigin(frontendProperties.origin());
        config.addAllowedMethod(HttpMethod.OPTIONS);
        config.addAllowedMethod(HttpMethod.GET);
        config.addAllowedMethod(HttpMethod.POST);
        config.addAllowedMethod(HttpMethod.PUT);
        config.addAllowedMethod(HttpMethod.PATCH);
        config.addAllowedMethod(HttpMethod.DELETE);
        config.addAllowedHeader("*");
        config.setAllowCredentials(true);
        config.addExposedHeader("Authorization");
        config.addExposedHeader("Set-Cookie");
        config.addExposedHeader("X-RateLimit-Remaining");
        config.addExposedHeader("X-RateLimit-Replenish-Rate");
        config.addExposedHeader("X-RateLimit-Burst-Capacity");
        config.addExposedHeader("X-RateLimit-Requested-Tokens");
        config.setMaxAge(3600L);
        return config;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(CorsConfiguration corsConfiguration) {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", corsConfiguration);
        return source;
    }

    @Bean
    public CorsWebFilter corsWebFilter(CorsConfigurationSource corsConfigurationSource) {
        return new CorsWebFilter(corsConfigurationSource);
    }
}

package org.example.common.config;

import org.example.common.properties.JwtProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

@Configuration
public class JwtDecoderConfig {

    @Bean("authServerJwtDecoder")
    public JwtDecoder authServerJwtDecoder(JwtProperties jwtProperties) {
        return NimbusJwtDecoder
                .withJwkSetUri(jwtProperties.jwksUri())
                .build();
    }
}
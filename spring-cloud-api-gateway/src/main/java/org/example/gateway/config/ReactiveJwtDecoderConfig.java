package org.example.gateway.config;

import lombok.RequiredArgsConstructor;
import org.example.common.properties.JwtProperties;
import org.example.oauth2.service.BlacklistTokenService;
import org.example.oauth2.validator.BlacklistTokenValidator;
import org.example.oauth2.validator.RequiredUserIdClaimValidator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;

@Configuration
@RequiredArgsConstructor
public class ReactiveJwtDecoderConfig {

    private final BlacklistTokenService blacklistTokenService;

    @Bean
    public ReactiveJwtDecoder reactiveJwtDecoder(JwtProperties jwtProperties) {
        NimbusReactiveJwtDecoder jwtDecoder = NimbusReactiveJwtDecoder.withJwkSetUri(jwtProperties.jwksUri()).build();

        OAuth2TokenValidator<Jwt> defaultValidator = JwtValidators.createDefaultWithIssuer(jwtProperties.issuerUri());
        OAuth2TokenValidator<Jwt> blacklistValidator = new BlacklistTokenValidator(blacklistTokenService);
        OAuth2TokenValidator<Jwt> requiredUserIdValidator = new RequiredUserIdClaimValidator();

        jwtDecoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                defaultValidator,
                blacklistValidator,
                requiredUserIdValidator
        ));

        return jwtDecoder;
    }
}
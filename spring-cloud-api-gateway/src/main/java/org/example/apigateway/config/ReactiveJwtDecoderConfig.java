package org.example.apigateway.config;

import lombok.RequiredArgsConstructor;
import org.example.common.properties.JwtProperties;
import org.example.apigateway.oauth2.validator.BlacklistAwareReactiveJwtDecoder;
import org.example.apigateway.oauth2.validator.ReactiveBlacklistTokenValidator;
import org.example.apigateway.oauth2.validator.RequiredUserIdClaimValidator;
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

    private final ReactiveBlacklistTokenValidator blacklistTokenValidator;

    @Bean
    public ReactiveJwtDecoder reactiveJwtDecoder(JwtProperties jwtProperties) {
        NimbusReactiveJwtDecoder jwtDecoder = NimbusReactiveJwtDecoder.withJwkSetUri(jwtProperties.jwksUri()).build();
        jwtDecoder.setJwtValidator(jwtValidator(jwtProperties));

        return new BlacklistAwareReactiveJwtDecoder(jwtDecoder, blacklistTokenValidator);
    }

    private OAuth2TokenValidator<Jwt> jwtValidator(JwtProperties jwtProperties) {
        OAuth2TokenValidator<Jwt> defaultValidator = JwtValidators.createDefaultWithIssuer(jwtProperties.issuerUri());
        OAuth2TokenValidator<Jwt> requiredUserIdValidator = new RequiredUserIdClaimValidator();

        return new DelegatingOAuth2TokenValidator<>(defaultValidator, requiredUserIdValidator);
    }
}

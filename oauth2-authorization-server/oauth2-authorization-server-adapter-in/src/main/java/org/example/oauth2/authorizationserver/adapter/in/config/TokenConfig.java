package org.example.oauth2.authorizationserver.adapter.in.config;

import org.example.common.enums.JwtClaimKey;
import org.example.oauth2.authorizationserver.user.application.service.UserQueryService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;

import org.springframework.security.oauth2.server.authorization.token.DelegatingOAuth2TokenGenerator;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.JwtGenerator;
import org.springframework.security.oauth2.server.authorization.token.OAuth2AccessTokenGenerator;
import org.springframework.security.oauth2.server.authorization.token.OAuth2RefreshTokenGenerator;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenGenerator;

@Configuration
public class TokenConfig {

    @Bean
    public OAuth2TokenGenerator<?> tokenGenerator(
            @Qualifier("rs256JwtEncoder") JwtEncoder rs256JwtEncoder,
            ObjectProvider<OAuth2TokenCustomizer<JwtEncodingContext>> customizers) {
        JwtGenerator jwtGenerator = new JwtGenerator(rs256JwtEncoder);
        jwtGenerator.setJwtCustomizer(context ->
                customizers.orderedStream()
                        .forEach(customizer -> customizer.customize(context))
        );

        return new DelegatingOAuth2TokenGenerator(
                jwtGenerator,
                new OAuth2AccessTokenGenerator(),
                new OAuth2RefreshTokenGenerator()
        );
    }

    @Bean
    public OAuth2TokenCustomizer<JwtEncodingContext> jwtCustomizer(UserQueryService userQueryService) {
        return context -> {
            if (!OAuth2TokenType.ACCESS_TOKEN.equals(context.getTokenType())) {
                return;
            }

            Authentication principal = context.getPrincipal();
            if (principal == null || principal.getName() == null) {
                return;
            }

            String name = principal.getName();

            userQueryService.findByEmail(name)
                    .ifPresent(user -> context.getClaims()
                            .claim(JwtClaimKey.ROLES.value(), user.roles())
                            .claim(JwtClaimKey.USER_ID.value(), user.id()));
        };
    }
}

package org.example.oauth2.authorizationserver.adapter.in.config;

import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.example.oauth2.authorizationserver.authorization.application.CustomAuthenticationSuccessHandler;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configurers.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenGenerator;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.util.matcher.RequestMatcher;

@Slf4j
@Configuration
public class SecurityFilterChainConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            @Qualifier("customOAuth2AuthorizationService") OAuth2AuthorizationService authorizationService,
            RegisteredClientRepository registeredClientRepository,
            OAuth2TokenGenerator<?> tokenGenerator,
            CustomAuthenticationSuccessHandler authenticationSuccessHandler
    ) throws Exception {
        OAuth2AuthorizationServerConfigurer configurer = new OAuth2AuthorizationServerConfigurer();

        RequestMatcher endpointsMatcher = configurer.getEndpointsMatcher();

        return http
                .securityMatcher(endpointsMatcher)
                .csrf(AbstractHttpConfigurer::disable)
                .with(configurer, auth -> auth
                        .authorizationService(authorizationService)
                        .registeredClientRepository(registeredClientRepository)
                        .clientAuthentication(clientAuth -> clientAuth
                                .errorResponseHandler(clientAuthenticationFailureHandler()))
                        .tokenGenerator(tokenGenerator)
                        .tokenEndpoint(tokenEndpoint -> tokenEndpoint
                                .accessTokenResponseHandler(authenticationSuccessHandler))
                )
                .build();
    }

    private AuthenticationFailureHandler clientAuthenticationFailureHandler() {
        return (request, response, exception) -> {
            log.error("client 인증 실패 ex={}", exception.getMessage());
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        };
    }
}
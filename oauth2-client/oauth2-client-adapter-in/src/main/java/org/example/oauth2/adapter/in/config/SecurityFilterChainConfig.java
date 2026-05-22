package org.example.oauth2.adapter.in.config;

import org.example.common.properties.ApiPathProperties;
import org.example.oauth2.user.CustomOidcUserService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.logout.HeaderWriterLogoutHandler;
import org.springframework.security.web.authentication.logout.LogoutHandler;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import org.springframework.security.web.header.writers.ClearSiteDataHeaderWriter;

@Configuration
public class SecurityFilterChainConfig {

    @Bean
    public SecurityFilterChain oauth2FilterChain(
            HttpSecurity http,
            ApiPathProperties apiPathProperties,
            OAuth2AuthorizationRequestResolver requestResolver,
            CustomOidcUserService customOidcUserService,
            OAuth2AuthorizedClientService redisOAuth2AuthorizedClientService,
            OAuth2AuthorizedClientRepository statelessOAuth2AuthorizedClientRepository,
            AuthenticationSuccessHandler customOAuth2LoginSuccessHandler,
            AuthenticationFailureHandler customOAuth2LoginFailureHandler,
            LogoutSuccessHandler customLogoutSuccessHandler
    ) throws Exception {
        return http
                .securityMatcher(
                        apiPathProperties.oauth2().authorizationPattern(),
                        apiPathProperties.oauth2().loginCallbackPattern(),
                        apiPathProperties.auth().pattern()
                )
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                .authorizeHttpRequests(auth ->
                        auth.anyRequest().permitAll()
                )
                .oauth2Login(oauth2 -> oauth2
                        .authorizedClientService(redisOAuth2AuthorizedClientService)
                        .authorizedClientRepository(statelessOAuth2AuthorizedClientRepository)
                        .authorizationEndpoint(authorization -> authorization
                                .authorizationRequestResolver(requestResolver)
                        )
                        .redirectionEndpoint(redirection -> redirection
                                .baseUri(apiPathProperties.oauth2().loginCallbackBaseUri())
                        )
                        .userInfoEndpoint(userInfo -> userInfo
                                .oidcUserService(customOidcUserService)
                        )
                        .successHandler(customOAuth2LoginSuccessHandler)
                        .failureHandler(customOAuth2LoginFailureHandler)
                )
                .logout(logout -> logout
                        .logoutUrl(apiPathProperties.auth().logout())
                        .addLogoutHandler(clearSiteDataLogoutHandler())
                        .logoutSuccessHandler(customLogoutSuccessHandler)
                )
                .build();
    }

    private LogoutHandler clearSiteDataLogoutHandler() {
        return new HeaderWriterLogoutHandler(new ClearSiteDataHeaderWriter(ClearSiteDataHeaderWriter.Directive.STORAGE));
    }
}

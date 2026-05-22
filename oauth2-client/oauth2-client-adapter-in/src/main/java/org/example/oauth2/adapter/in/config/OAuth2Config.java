package org.example.oauth2.adapter.in.config;

import org.example.common.properties.ApiPathProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.endpoint.*;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;

@Configuration
public class OAuth2Config {

    @Bean
    public OAuth2AuthorizationRequestResolver oauth2AuthorizationRequestResolver(
            ClientRegistrationRepository clientRegistrationRepository,
            ApiPathProperties apiPathProperties
    ) {
        DefaultOAuth2AuthorizationRequestResolver resolver =
                new DefaultOAuth2AuthorizationRequestResolver(
                        clientRegistrationRepository,
                        apiPathProperties.oauth2().authorizationBaseUri()
                );

        resolver.setAuthorizationRequestCustomizer(customizer ->
                customizer.attributes(attributes -> {
                    String registrationId = String.valueOf(
                            attributes.get("registration_id")
                    );

                    if ("google".equals(registrationId)) {
                        customizer.additionalParameters(parameters -> {
                            parameters.put("access_type", "offline");
                            parameters.put("prompt", "consent");
                        });
                    }
                })
        );

        return resolver;
    }

    @Bean
    public OidcUserService oidcUserService() {
        return new OidcUserService();
    }

    @Bean
    public OAuth2AccessTokenResponseClient<OAuth2RefreshTokenGrantRequest> refreshTokenResponseClient() {
        return new RestClientRefreshTokenTokenResponseClient();
    }

    @Bean
    public OAuth2AccessTokenResponseClient<TokenExchangeGrantRequest> tokenExchangeResponseClient() {
        return new RestClientTokenExchangeTokenResponseClient();
    }
}

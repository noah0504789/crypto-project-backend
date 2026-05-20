package org.example.common.config;

import lombok.RequiredArgsConstructor;
import org.example.common.properties.JwtProperties;
import org.example.oauth2.properties.OAuth2RegisteredClientProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.endpoint.OAuth2AccessTokenResponse;
import org.springframework.security.oauth2.core.http.converter.OAuth2AccessTokenResponseHttpMessageConverter;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.InMemoryRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.OAuth2TokenFormat;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;

import java.time.Duration;

@Configuration
@RequiredArgsConstructor
public class AuthorizationServerConfig {

    private final JwtProperties jwtProperties;
    private final OAuth2RegisteredClientProperties oAuth2RegisteredClientProperties;

    @Bean
    public AuthorizationServerSettings authorizationServerSettings() {
        return AuthorizationServerSettings.builder()
                .issuer(jwtProperties.issuerUri())
                .build();
    }

    @Bean
    public RegisteredClientRepository registeredClientRepository() {
        return new InMemoryRegisteredClientRepository(registeredClient());
    }

    @Bean
    public HttpMessageConverter<OAuth2AccessTokenResponse> accessTokenResponseConverter() {
        return new OAuth2AccessTokenResponseHttpMessageConverter();
    }

    private RegisteredClient registeredClient() {
        return RegisteredClient.withId(oAuth2RegisteredClientProperties.id())
                .clientId(oAuth2RegisteredClientProperties.id())
                .clientSecret("{noop}"+oAuth2RegisteredClientProperties.secret())
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.TOKEN_EXCHANGE)
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                .clientSettings(ClientSettings.builder()
                        .requireAuthorizationConsent(false)
                        .build())
                .tokenSettings(TokenSettings.builder()
                        .accessTokenTimeToLive(Duration.ofMillis(jwtProperties.accessTokenExpirationMs()))
                        .refreshTokenTimeToLive(Duration.ofMillis(jwtProperties.refreshTokenExpirationMs()))
                        .accessTokenFormat(OAuth2TokenFormat.SELF_CONTAINED)
                        .reuseRefreshTokens(false)
                        .build())
                .build();
    }
}

package config;

import org.example.oauth2.client.token.application.port.out.AuthServerTokenClientPort;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.client.endpoint.OAuth2AccessTokenResponseClient;
import org.springframework.security.oauth2.client.endpoint.OAuth2RefreshTokenGrantRequest;
import org.springframework.security.oauth2.client.endpoint.TokenExchangeGrantRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;

import static org.mockito.Mockito.mock;

@TestConfiguration
public class TestExternalDependencyConfig {

    @Bean
    public AuthServerTokenClientPort testAuthServerTokenPort() {
        return mock(AuthServerTokenClientPort.class);
    }

    @Bean
    public ClientRegistrationRepository testClientRegistrationRepository() {
        return mock(ClientRegistrationRepository.class);
    }

    @Bean
    public OAuth2AccessTokenResponseClient<OAuth2RefreshTokenGrantRequest> testRefreshTokenResponseClient() {
        return mock(OAuth2AccessTokenResponseClient.class);
    }

    @Bean
    public OAuth2AccessTokenResponseClient<TokenExchangeGrantRequest> testTokenExchangeResponseClient() {
        return mock(OAuth2AccessTokenResponseClient.class);
    }
}
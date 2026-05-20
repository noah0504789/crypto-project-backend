package config;

import org.example.oauth2.port.AuthServerTokenPort;
import org.example.oauth2.service.token.BlacklistTokenService;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.endpoint.OAuth2AccessTokenResponseClient;
import org.springframework.security.oauth2.client.endpoint.OAuth2RefreshTokenGrantRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import static org.mockito.Mockito.mock;

@TestConfiguration
public class TestLogoutExternalDependencyConfig {

    @Bean
    public AuthServerTokenPort testAuthServerTokenPort() {
        return mock(AuthServerTokenPort.class);
    }

    @Bean
    public OAuth2AuthorizedClientService testOAuth2AuthorizedClientService() {
        return mock(OAuth2AuthorizedClientService.class);
    }

    @Bean
    public ClientRegistrationRepository testClientRegistrationRepository() {
        return mock(ClientRegistrationRepository.class);
    }

    @Bean
    @SuppressWarnings("unchecked")
    public OAuth2AccessTokenResponseClient<OAuth2RefreshTokenGrantRequest> testRefreshTokenResponseClient() {
        return mock(OAuth2AccessTokenResponseClient.class);
    }

    @Bean
    public JwtDecoder testAuthServerJwtDecoder() {
        return mock(JwtDecoder.class);
    }

    @Bean
    public BlacklistTokenService testBlacklistTokenService() {
        return mock(BlacklistTokenService.class);
    }
}
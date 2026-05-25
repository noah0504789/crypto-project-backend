package config;

import org.example.oauth2.client.oidc.CustomOidcUserService;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.web.servlet.handler.HandlerMappingIntrospector;

import static org.mockito.Mockito.mock;

@TestConfiguration
public class TestLogoutSecurityDependencyConfig {

    @Bean(name = "mvcHandlerMappingIntrospector")
    public HandlerMappingIntrospector mvcHandlerMappingIntrospector() {
        return new HandlerMappingIntrospector();
    }

    @Bean
    public OAuth2AuthorizationRequestResolver testOAuth2AuthorizationRequestResolver() {
        return mock(OAuth2AuthorizationRequestResolver.class);
    }

    @Bean
    public CustomOidcUserService testCustomOidcUserService() {
        return mock(CustomOidcUserService.class);
    }

    @Bean
    public OAuth2AuthorizedClientRepository testOAuth2AuthorizedClientRepository() {
        return mock(OAuth2AuthorizedClientRepository.class);
    }

    @Bean
    public AuthenticationSuccessHandler testCustomOAuth2LoginSuccessHandler() {
        return mock(AuthenticationSuccessHandler.class);
    }

    @Bean
    public AuthenticationFailureHandler testCustomOAuth2LoginFailureHandler() {
        return mock(AuthenticationFailureHandler.class);
    }
}
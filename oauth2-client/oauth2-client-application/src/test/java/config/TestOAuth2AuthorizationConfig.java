package config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;

@TestConfiguration
public class TestOAuth2AuthorizationConfig {

    private static final String AUTHORIZATION_BASE_URI = "/oauth2/authorization";
    private static final String GOOGLE_REGISTRATION_ID = "google";
    private static final String KAKAO_REGISTRATION_ID = "kakao";

    @Bean
    public ClientRegistrationRepository testClientRegistrationRepository() {
        return new InMemoryClientRegistrationRepository(
                googleClientRegistration(),
                kakaoClientRegistration()
        );
    }

    @Bean
    public OAuth2AuthorizationRequestResolver testOAuth2AuthorizationRequestResolver(ClientRegistrationRepository clientRegistrationRepository) {
        DefaultOAuth2AuthorizationRequestResolver resolver =
                new DefaultOAuth2AuthorizationRequestResolver(
                        clientRegistrationRepository,
                        AUTHORIZATION_BASE_URI
                );

        resolver.setAuthorizationRequestCustomizer(customizer ->
                customizer.attributes(attributes -> {
                    String registrationId = String.valueOf(
                            attributes.get("registration_id")
                    );

                    if (GOOGLE_REGISTRATION_ID.equals(registrationId)) {
                        customizer.additionalParameters(parameters -> {
                            parameters.put("access_type", "offline");
                            parameters.put("prompt", "consent");
                        });
                    }
                })
        );

        return resolver;
    }

    private ClientRegistration googleClientRegistration() {
        return ClientRegistration.withRegistrationId(GOOGLE_REGISTRATION_ID)
                .clientId("test-google-client-id")
                .clientSecret("test-google-client-secret")
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .scope("openid", "profile", "email")
                .authorizationUri("https://accounts.google.com/o/oauth2/v2/auth")
                .tokenUri("https://oauth2.googleapis.com/token")
                .jwkSetUri("https://www.googleapis.com/oauth2/v3/certs")
                .userInfoUri("https://openidconnect.googleapis.com/v1/userinfo")
                .userNameAttributeName("sub")
                .clientName("Google")
                .build();
    }

    private ClientRegistration kakaoClientRegistration() {
        return ClientRegistration.withRegistrationId(KAKAO_REGISTRATION_ID)
                .clientId("test-kakao-client-id")
                .clientSecret("test-kakao-client-secret")
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .scope("openid", "profile_nickname", "account_email")
                .authorizationUri("https://kauth.kakao.com/oauth/authorize")
                .tokenUri("https://kauth.kakao.com/oauth/token")
                .jwkSetUri("https://kauth.kakao.com/.well-known/jwks.json")
                .userInfoUri("https://kapi.kakao.com/v1/oidc/userinfo")
                .userNameAttributeName("email")
                .clientName("Kakao")
                .build();
    }
}
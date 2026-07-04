package endpoint;

import org.example.common.test.config.TestBootApplication;
import config.TestLogoutExternalDependencyConfig;
import config.TestLogoutSecurityDependencyConfig;
import config.TestPropertiesConfig;
import org.example.common.config.MessageConverterConfig;
import org.example.oauth2.client.adapter.in.config.SecurityFilterChainConfig;
import org.example.oauth2.client.token.application.port.out.AuthServerTokenPort;
import org.example.oauth2.client.handler.CustomLogoutSuccessHandler;
import org.example.oauth2.client.token.application.service.BlacklistTokenService;
import org.example.oauth2.client.token.application.service.RefreshTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.reset;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = {
        TestBootApplication.class,

        CustomLogoutSuccessHandler.class,
        RefreshTokenService.class,

        SecurityFilterChainConfig.class,
        MessageConverterConfig.class,

        TestPropertiesConfig.class,
        TestLogoutSecurityDependencyConfig.class,
        TestLogoutExternalDependencyConfig.class
})
@AutoConfigureMockMvc
class AuthLogoutE2ETest {

    private static final String CLIENT_REGISTRATION_ID = "my-authorization-server";
    private static final String EMAIL = "user@test.com";
    private static final String ACCESS_TOKEN = "access-token";
    private static final String REFRESH_TOKEN = "refresh-token";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtDecoder authServerJwtDecoder;

    @Autowired
    private AuthServerTokenPort grpcClient;

    @Autowired
    private BlacklistTokenService blacklistTokenService;

    @Autowired
    private OAuth2AuthorizedClientService authorizedClientService;

    @BeforeEach
    void resetMocks() {
        reset(
                authServerJwtDecoder,
                grpcClient,
                blacklistTokenService,
                authorizedClientService
        );
    }

    @Test
    @DisplayName("/auth/logout - Bearer access token이 있으면 로그아웃 성공")
    void logout_shouldSucceed_whenBearerTokenExists() throws Exception {
        // given
        Jwt jwt = Jwt.withTokenValue(ACCESS_TOKEN)
                .header("alg", "RS256")
                .subject(EMAIL)
                .claim("sub", EMAIL)
                .build();

        given(authServerJwtDecoder.decode(ACCESS_TOKEN))
                .willReturn(jwt);

        given(grpcClient.findRefreshToken(anyString(), anyString()))
                .willReturn(REFRESH_TOKEN);

        // when & then
        mockMvc.perform(post("/auth/logout")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ACCESS_TOKEN))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("refreshToken=")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Path=/")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Secure")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("HttpOnly")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("SameSite=None")))
                .andExpect(content().string("logout success!"));

        then(blacklistTokenService)
                .should()
                .register(ACCESS_TOKEN);

        then(authorizedClientService)
                .should()
                .removeAuthorizedClient(CLIENT_REGISTRATION_ID, EMAIL);
    }

    @Test
    @DisplayName("/auth/logout - Authorization 헤더가 없으면 401을 반환한다")
    void logout_shouldReturnUnauthorized_whenAuthorizationHeaderMissing() throws Exception {
        mockMvc.perform(post("/auth/logout"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().string("invalid token"));

        then(blacklistTokenService).shouldHaveNoInteractions();
        then(authorizedClientService).shouldHaveNoInteractions();
        then(grpcClient).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("/auth/logout - Authorization 헤더가 Bearer 형식이 아니면 401을 반환한다")
    void logout_shouldReturnUnauthorized_whenAuthorizationHeaderIsNotBearer() throws Exception {
        mockMvc.perform(post("/auth/logout")
                        .header(HttpHeaders.AUTHORIZATION, "Basic abc"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().string("invalid token"));

        then(blacklistTokenService).shouldHaveNoInteractions();
        then(authorizedClientService).shouldHaveNoInteractions();
        then(grpcClient).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("/auth/logout - access token 검증 실패 후 subject 추출도 실패하면 401을 반환한다")
    void logout_shouldReturnUnauthorized_whenJwtInvalidAndCannotParseSubject() throws Exception {
        // given
        String invalidToken = "not.jwt";

        given(authServerJwtDecoder.decode(invalidToken))
                .willThrow(new JwtValidationException(
                        "invalid",
                        List.of(new OAuth2Error("invalid_token"))
                ));

        // when & then
        mockMvc.perform(post("/auth/logout")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + invalidToken))
                .andExpect(status().isUnauthorized())
                .andExpect(content().string("invalid token"));

        then(blacklistTokenService).shouldHaveNoInteractions();
        then(authorizedClientService).shouldHaveNoInteractions();
        then(grpcClient).shouldHaveNoInteractions();
    }
}
package endpoint;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.Optional;

import org.example.common.test.config.TestBootApplication;
import config.TestPropertiesConfig;
import config.TestRedisConfig;
import org.example.common.test.testcontainer.RedisTestContainerInitializer;
import org.example.oauth2.authorizationserver.adapter.in.config.AuthorizationServerConfig;
import org.example.oauth2.authorizationserver.adapter.in.config.PasswordEncoderConfig;
import org.example.common.config.MessageConverterConfig;
import org.example.oauth2.authorizationserver.adapter.in.config.SecurityFilterChainConfig;
import org.example.oauth2.authorizationserver.adapter.in.config.TokenConfig;
import org.example.common.enums.RedisKey;
import org.example.common.properties.JwtProperties;
import org.example.common.redis.operation.StringRedisHashOperations;
import org.example.oauth2.authorizationserver.authorization.application.CustomAuthenticationSuccessHandler;
import org.example.oauth2.authorizationserver.authorization.application.CustomOAuth2AuthorizationService;
import org.example.oauth2.authorizationserver.token.adapter.out.vault.Rs256JwtEncoder;
import org.example.oauth2.authorizationserver.token.adapter.out.redis.RedisAccessTokenAdapter;
import org.example.oauth2.authorizationserver.token.adapter.out.redis.RedisRefreshTokenAdapter;
import org.example.oauth2.authorizationserver.token.adapter.out.vault.dto.SignRequest;
import org.example.oauth2.authorizationserver.token.adapter.out.vault.dto.SignResponse;
import org.example.oauth2.authorizationserver.token.application.policy.RotatingRefreshTokenPolicy;
import org.example.oauth2.authorizationserver.user.application.service.UserQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.client.RestTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = {
        TestBootApplication.class,

        // 실제 운영 설정
        AuthorizationServerConfig.class,
        PasswordEncoderConfig.class,
        SecurityFilterChainConfig.class,
        TokenConfig.class,
        MessageConverterConfig.class,

        // 실제 컴포넌트
        CustomOAuth2AuthorizationService.class,
        RedisRefreshTokenAdapter.class,
        RedisAccessTokenAdapter.class,
        StringRedisHashOperations.class,
        RotatingRefreshTokenPolicy.class,
        CustomAuthenticationSuccessHandler.class,
        Rs256JwtEncoder.class,

        // 테스트 대체 설정
        TestRedisConfig.class,
        TestPropertiesConfig.class
})
@AutoConfigureMockMvc
@ContextConfiguration(initializers = RedisTestContainerInitializer.class)
class OAuth2TokenEndpointE2ETest {

    private static final String CLIENT_ID = "my-client-id";
    private static final String CLIENT_SECRET = "my-client-secret";
    private static final String WRONG_CLIENT_SECRET = "wrong-client-secret";

    private static final String REGISTRATION_ID = "my-authorization-server";

    private static final String EMAIL = "user@test.com";
    private static final String OLD_REFRESH_TOKEN = "old-refresh-token";
    private static final String UNKNOWN_REFRESH_TOKEN = "unknown-refresh-token";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private RedisRefreshTokenAdapter redisRefreshTokenAdapter;

    @Autowired
    private JwtProperties jwtProperties;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private RestTemplate jwtRestTemplate;

    @MockitoBean
    private UserQueryService userQueryService;

    @BeforeEach
    void setUp() {
        stringRedisTemplate.getConnectionFactory()
                .getConnection()
                .serverCommands()
                .flushDb();

        given(userQueryService.findByEmail(anyString()))
                .willReturn(Optional.empty());

        given(jwtRestTemplate.postForObject(
                eq(jwtProperties.signUri()),
                any(SignRequest.class),
                eq(SignResponse.class)
        )).willAnswer(invocation -> {
            SignRequest request = invocation.getArgument(1);

            return new SignResponse(
                    request.keyName() + ":" + request.keyVersion(),
                    "RS256",
                    "test-signature-b64u"
            );
        });
    }

    @Test
    @DisplayName("refresh_token grant 요청 시 기존 RT를 폐기하고 새 RT를 Redis에 저장한다")
    void refreshTokenGrant_shouldRotateRefreshToken() throws Exception {
        // given
        saveOldRefreshToken();

        String emailTokenKey =
                RedisKey.REFRESH_TOKEN.keyFor(REGISTRATION_ID, EMAIL);

        String oldRefreshEmailKey =
                RedisKey.REFRESH_TOKEN.keyFor(REGISTRATION_ID, OLD_REFRESH_TOKEN);

        assertThat(stringRedisTemplate.opsForValue().get(emailTokenKey))
                .isEqualTo(OLD_REFRESH_TOKEN);

        assertThat(stringRedisTemplate.opsForValue().get(oldRefreshEmailKey))
                .isEqualTo(EMAIL);

        // when
        MvcResult result =
                performRefreshTokenGrant(OLD_REFRESH_TOKEN)
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.access_token").exists())
                        .andExpect(jsonPath("$.refresh_token").exists())
                        .andReturn();

        // then
        String newRefreshToken =
                extractRefreshToken(result);

        assertThat(newRefreshToken)
                .isNotBlank()
                .isNotEqualTo(OLD_REFRESH_TOKEN);

        String newRefreshEmailKey =
                RedisKey.REFRESH_TOKEN.keyFor(REGISTRATION_ID, newRefreshToken);

        assertThat(stringRedisTemplate.opsForValue().get(emailTokenKey))
                .isEqualTo(newRefreshToken);

        assertThat(stringRedisTemplate.opsForValue().get(oldRefreshEmailKey))
                .isNull();

        assertThat(stringRedisTemplate.opsForValue().get(newRefreshEmailKey))
                .isEqualTo(EMAIL);

        assertThat(stringRedisTemplate.getExpire(emailTokenKey))
                .isGreaterThan(0);

        assertThat(stringRedisTemplate.getExpire(newRefreshEmailKey))
                .isGreaterThan(0);
    }

    @Test
    @DisplayName("rotation 이후 기존 RT로 다시 요청하면 실패한다")
    void refreshTokenGrant_shouldFail_whenUsingOldRefreshTokenAfterRotation() throws Exception {
        // given
        saveOldRefreshToken();

        MvcResult firstResult =
                performRefreshTokenGrant(OLD_REFRESH_TOKEN)
                        .andExpect(status().isOk())
                        .andReturn();

        String rotatedRefreshToken =
                extractRefreshToken(firstResult);

        assertThat(rotatedRefreshToken)
                .isNotBlank()
                .isNotEqualTo(OLD_REFRESH_TOKEN);

        String oldRefreshEmailKey =
                RedisKey.REFRESH_TOKEN.keyFor(REGISTRATION_ID, OLD_REFRESH_TOKEN);

        assertThat(stringRedisTemplate.opsForValue().get(oldRefreshEmailKey))
                .isNull();

        // when & then
        performRefreshTokenGrant(OLD_REFRESH_TOKEN)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    @DisplayName("rotation 이후 새 RT로 요청하면 성공하고 다시 새로운 RT로 교체된다")
    void refreshTokenGrant_shouldSucceedAndRotateAgain_whenUsingRotatedRefreshToken() throws Exception {
        // given
        saveOldRefreshToken();

        MvcResult firstResult =
                performRefreshTokenGrant(OLD_REFRESH_TOKEN)
                        .andExpect(status().isOk())
                        .andReturn();

        String firstRotatedRefreshToken =
                extractRefreshToken(firstResult);

        assertThat(firstRotatedRefreshToken)
                .isNotBlank()
                .isNotEqualTo(OLD_REFRESH_TOKEN);

        // when
        MvcResult secondResult =
                performRefreshTokenGrant(firstRotatedRefreshToken)
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.access_token").exists())
                        .andExpect(jsonPath("$.refresh_token").exists())
                        .andReturn();

        // then
        String secondRotatedRefreshToken =
                extractRefreshToken(secondResult);

        assertThat(secondRotatedRefreshToken)
                .isNotBlank()
                .isNotEqualTo(OLD_REFRESH_TOKEN)
                .isNotEqualTo(firstRotatedRefreshToken);

        String emailTokenKey =
                RedisKey.REFRESH_TOKEN.keyFor(REGISTRATION_ID, EMAIL);

        String oldRefreshEmailKey =
                RedisKey.REFRESH_TOKEN.keyFor(REGISTRATION_ID, OLD_REFRESH_TOKEN);

        String firstRotatedEmailKey =
                RedisKey.REFRESH_TOKEN.keyFor(REGISTRATION_ID, firstRotatedRefreshToken);

        String secondRotatedEmailKey =
                RedisKey.REFRESH_TOKEN.keyFor(REGISTRATION_ID, secondRotatedRefreshToken);

        assertThat(stringRedisTemplate.opsForValue().get(emailTokenKey))
                .isEqualTo(secondRotatedRefreshToken);

        assertThat(stringRedisTemplate.opsForValue().get(oldRefreshEmailKey))
                .isNull();

        assertThat(stringRedisTemplate.opsForValue().get(firstRotatedEmailKey))
                .isNull();

        assertThat(stringRedisTemplate.opsForValue().get(secondRotatedEmailKey))
                .isEqualTo(EMAIL);
    }

    @Test
    @DisplayName("존재하지 않는 RT로 요청하면 실패한다")
    void refreshTokenGrant_shouldFail_whenRefreshTokenDoesNotExist() throws Exception {
        performRefreshTokenGrant(UNKNOWN_REFRESH_TOKEN)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    @DisplayName("client secret이 틀리면 실패한다")
    void refreshTokenGrant_shouldFail_whenClientSecretInvalid() throws Exception {
        // given
        saveOldRefreshToken();

        // when & then
        mockMvc.perform(post("/oauth2/token")
                        .with(httpBasic(CLIENT_ID, WRONG_CLIENT_SECRET))
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param(OAuth2ParameterNames.GRANT_TYPE, AuthorizationGrantType.REFRESH_TOKEN.getValue())
                        .param(OAuth2ParameterNames.REFRESH_TOKEN, OLD_REFRESH_TOKEN)
                )
                .andExpect(status().isUnauthorized());
    }

    private void saveOldRefreshToken() {
        redisRefreshTokenAdapter.cache(
                OLD_REFRESH_TOKEN,
                REGISTRATION_ID,
                EMAIL
        );
    }

    private ResultActions performRefreshTokenGrant(String refreshToken) throws Exception {
        return mockMvc.perform(post("/oauth2/token")
                .with(httpBasic(CLIENT_ID, CLIENT_SECRET))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param(OAuth2ParameterNames.GRANT_TYPE, AuthorizationGrantType.REFRESH_TOKEN.getValue())
                .param(OAuth2ParameterNames.REFRESH_TOKEN, refreshToken)
        );
    }

    private String extractRefreshToken(MvcResult result) throws Exception {
        String responseBody =
                result.getResponse().getContentAsString();

        Map<String, Object> response =
                objectMapper.readValue(responseBody, new TypeReference<>() {});

        return String.valueOf(response.get("refresh_token"));
    }
}
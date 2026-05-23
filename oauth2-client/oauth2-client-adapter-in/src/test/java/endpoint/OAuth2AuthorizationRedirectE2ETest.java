package endpoint;

import config.*;
import org.example.config.MessageConverterConfig;
import org.example.oauth2.adapter.in.config.SecurityFilterChainConfig;
import org.example.test.config.TestBootApplication;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.util.MultiValueMap;
import org.springframework.web.util.UriComponentsBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = {
        TestBootApplication.class,

        // 실제 운영 설정
        SecurityFilterChainConfig.class,
        MessageConverterConfig.class,

        // 테스트 설정
        TestOAuth2AuthorizationConfig.class,
        TestOAuth2AuthorizationSecurityDependencyConfig.class,
        TestPropertiesConfig.class
})
@AutoConfigureMockMvc
class OAuth2AuthorizationRedirectE2ETest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("/oauth2/authorization/google - Google 인증 서버로 redirect하고 offline/consent 파라미터를 포함한다")
    void authorizationGoogle_shouldRedirectToGoogleAuthorizationEndpoint() throws Exception {
        // when
        MvcResult result = mockMvc.perform(get("/oauth2/authorization/google"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().exists(HttpHeaders.LOCATION))
                .andReturn();

        // then
        String location = result.getResponse().getHeader(HttpHeaders.LOCATION);

        assertThat(location).isNotNull();
        assertThat(location).startsWith("https://accounts.google.com/o/oauth2/v2/auth");

        MultiValueMap<String, String> queryParams =
                UriComponentsBuilder.fromUriString(location)
                        .build()
                        .getQueryParams();

        assertThat(queryParams.getFirst("response_type"))
                .isEqualTo("code");

        assertThat(queryParams.getFirst("client_id"))
                .isEqualTo("test-google-client-id");

        assertThat(queryParams.getFirst("redirect_uri"))
                .contains("/login/oauth2/code/google");

        assertThat(queryParams.getFirst("scope"))
                .contains("openid")
                .contains("profile")
                .contains("email");

        assertThat(queryParams.getFirst("state"))
                .isNotBlank();

        assertThat(queryParams.getFirst("nonce"))
                .isNotBlank();

        assertThat(queryParams.getFirst("access_type"))
                .isEqualTo("offline");

        assertThat(queryParams.getFirst("prompt"))
                .isEqualTo("consent");
    }

    @Test
    @DisplayName("/oauth2/authorization/kakao - Kakao 인증 서버로 redirect하고 Google 전용 파라미터는 포함하지 않는다")
    void authorizationKakao_shouldRedirectToKakaoAuthorizationEndpoint() throws Exception {
        // when
        MvcResult result = mockMvc.perform(get("/oauth2/authorization/kakao"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().exists(HttpHeaders.LOCATION))
                .andReturn();

        // then
        String location = result.getResponse().getHeader(HttpHeaders.LOCATION);

        assertThat(location).isNotNull();
        assertThat(location).startsWith("https://kauth.kakao.com/oauth/authorize");

        MultiValueMap<String, String> queryParams =
                UriComponentsBuilder.fromUriString(location)
                        .build()
                        .getQueryParams();

        assertThat(queryParams.getFirst("response_type"))
                .isEqualTo("code");

        assertThat(queryParams.getFirst("client_id"))
                .isEqualTo("test-kakao-client-id");

        assertThat(queryParams.getFirst("redirect_uri"))
                .contains("/login/oauth2/code/kakao");

        assertThat(queryParams.getFirst("scope"))
                .contains("openid")
                .contains("profile_nickname")
                .contains("account_email");

        assertThat(queryParams.getFirst("state"))
                .isNotBlank();

        assertThat(queryParams.getFirst("nonce"))
                .isNotBlank();

        assertThat(queryParams)
                .doesNotContainKey("access_type");

        assertThat(queryParams)
                .doesNotContainKey("prompt");
    }
}

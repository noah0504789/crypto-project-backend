package oauth2;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import jakarta.servlet.http.HttpServletResponse;
import org.example.oauth2.properties.InternalAuthServerProperties;
import org.example.oauth2.handler.CustomLogoutSuccessHandler;
import org.example.oauth2.service.token.BlacklistTokenService;
import org.example.oauth2.service.token.RefreshTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidationException;

import java.time.Instant;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class CustomLogoutSuccessHandlerTest {

    private static final String CLIENT_REGISTRATION_ID = "my-authorization-server";
    private static final String EMAIL = "user@test.com";
    private static final String ACCESS_TOKEN = "access-token";
    private static final String REFRESH_TOKEN = "refresh-token";

    @Mock
    private OAuth2AuthorizedClientService authorizedClientService;

    @Mock
    private RefreshTokenService refreshTokenService;

    @Mock
    private BlacklistTokenService blacklistTokenService;

    @Mock
    private JwtDecoder authServerJwtDecoder;

    @Mock
    private InternalAuthServerProperties internalAuthServerProperties;

    private CustomLogoutSuccessHandler sut;

    @BeforeEach
    void setUp() {
        sut = new CustomLogoutSuccessHandler(
                authorizedClientService,
                refreshTokenService,
                blacklistTokenService,
                authServerJwtDecoder,
                internalAuthServerProperties
        );
    }

    @Test
    @DisplayName("Authorization 헤더가 없으면 401과 invalid token을 반환한다")
    void onLogoutSuccess_shouldReturnUnauthorized_whenAuthorizationHeaderMissing() throws Exception {
        // given
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        // when
        sut.onLogoutSuccess(request, response, null);

        // then
        assertThat(response.getStatus())
                .isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);

        assertThat(response.getContentAsString())
                .isEqualTo("invalid token");

        then(blacklistTokenService).shouldHaveNoInteractions();
        then(refreshTokenService).shouldHaveNoInteractions();
        then(authorizedClientService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("Authorization 헤더가 Bearer 형식이 아니면 401과 invalid token을 반환한다")
    void onLogoutSuccess_shouldReturnUnauthorized_whenAuthorizationHeaderIsNotBearer() throws Exception {
        // given
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.AUTHORIZATION, "Basic abc");

        MockHttpServletResponse response = new MockHttpServletResponse();

        // when
        sut.onLogoutSuccess(request, response, null);

        // then
        assertThat(response.getStatus())
                .isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);

        assertThat(response.getContentAsString())
                .isEqualTo("invalid token");

        then(blacklistTokenService).shouldHaveNoInteractions();
        then(refreshTokenService).shouldHaveNoInteractions();
        then(authorizedClientService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("정상 JWT면 blacklist 등록, refresh cookie 삭제, authorized client 삭제 후 200을 반환한다")
    void onLogoutSuccess_shouldLogoutSuccessfully_whenAccessTokenIsValid() throws Exception {
        // given
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + ACCESS_TOKEN);

        MockHttpServletResponse response = new MockHttpServletResponse();

        Jwt jwt = Jwt.withTokenValue(ACCESS_TOKEN)
                .header("alg", "RS256")
                .subject(EMAIL)
                .claim("sub", EMAIL)
                .build();

        ResponseCookie deleteCookie = ResponseCookie.from("refreshToken", "")
                .httpOnly(true)
                .secure(true)
                .sameSite("None")
                .path("/")
                .maxAge(0)
                .build();

        given(authServerJwtDecoder.decode(ACCESS_TOKEN))
                .willReturn(jwt);

        given(internalAuthServerProperties.clientRegistrationId())
                .willReturn(CLIENT_REGISTRATION_ID);

        given(refreshTokenService.findValue(CLIENT_REGISTRATION_ID, EMAIL))
                .willReturn(REFRESH_TOKEN);

        given(refreshTokenService.deleteResponseCookie(REFRESH_TOKEN))
                .willReturn(deleteCookie);

        // when
        sut.onLogoutSuccess(request, response, null);

        // then
        assertThat(response.getStatus())
                .isEqualTo(HttpServletResponse.SC_OK);

        assertThat(response.getContentAsString())
                .isEqualTo("logout success!");

        String setCookie = response.getHeader(HttpHeaders.SET_COOKIE);

        assertThat(setCookie)
                .contains("refreshToken=")
                .contains("Path=/")
                .contains("Max-Age=0")
                .contains("Secure")
                .contains("HttpOnly")
                .contains("SameSite=None");

        then(blacklistTokenService)
                .should()
                .register(ACCESS_TOKEN);

        then(refreshTokenService)
                .should()
                .findValue(CLIENT_REGISTRATION_ID, EMAIL);

        then(refreshTokenService)
                .should()
                .deleteResponseCookie(REFRESH_TOKEN);

        then(authorizedClientService)
                .should()
                .removeAuthorizedClient(CLIENT_REGISTRATION_ID, EMAIL);
    }

    @Test
    @DisplayName("JWT가 만료되어도 payload에서 subject를 읽을 수 있으면 로그아웃 처리한다")
    void onLogoutSuccess_shouldLogoutSuccessfully_whenJwtValidationFailsButSubjectCanBeParsed() throws Exception {
        // given
        String expiredAccessToken = signedJwtWithSubject(EMAIL);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + expiredAccessToken);

        MockHttpServletResponse response = new MockHttpServletResponse();

        OAuth2Error error = new OAuth2Error("invalid_token");

        ResponseCookie deleteCookie = ResponseCookie.from("refreshToken", "")
                .httpOnly(true)
                .secure(true)
                .sameSite("None")
                .path("/")
                .maxAge(0)
                .build();

        given(authServerJwtDecoder.decode(expiredAccessToken))
                .willThrow(new JwtValidationException("expired", List.of(error)));

        given(internalAuthServerProperties.clientRegistrationId())
                .willReturn(CLIENT_REGISTRATION_ID);

        given(refreshTokenService.findValue(CLIENT_REGISTRATION_ID, EMAIL))
                .willReturn(REFRESH_TOKEN);

        given(refreshTokenService.deleteResponseCookie(REFRESH_TOKEN))
                .willReturn(deleteCookie);

        // when
        sut.onLogoutSuccess(request, response, null);

        // then
        assertThat(response.getStatus())
                .isEqualTo(HttpServletResponse.SC_OK);

        assertThat(response.getContentAsString())
                .isEqualTo("logout success!");

        String setCookie = response.getHeader(HttpHeaders.SET_COOKIE);

        assertThat(setCookie)
                .contains("refreshToken=")
                .contains("Path=/")
                .contains("Max-Age=0")
                .contains("Secure")
                .contains("HttpOnly")
                .contains("SameSite=None");

        then(blacklistTokenService)
                .should()
                .register(expiredAccessToken);

        then(refreshTokenService)
                .should()
                .findValue(CLIENT_REGISTRATION_ID, EMAIL);

        then(refreshTokenService)
                .should()
                .deleteResponseCookie(REFRESH_TOKEN);

        then(authorizedClientService)
                .should()
                .removeAuthorizedClient(CLIENT_REGISTRATION_ID, EMAIL);
    }

    @Test
    @DisplayName("JWT 검증 실패 후 payload 파싱도 실패하면 401을 반환한다")
    void onLogoutSuccess_shouldReturnUnauthorized_whenJwtCannotBeParsed() throws Exception {
        // given
        String invalidAccessToken = "not.jwt";

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + invalidAccessToken);

        MockHttpServletResponse response = new MockHttpServletResponse();

        OAuth2Error error = new OAuth2Error("invalid_token");

        given(authServerJwtDecoder.decode(invalidAccessToken))
                .willThrow(new JwtValidationException("invalid", List.of(error)));

        // when
        sut.onLogoutSuccess(request, response, null);

        // then
        assertThat(response.getStatus())
                .isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);

        assertThat(response.getContentAsString())
                .isEqualTo("invalid token");

        then(blacklistTokenService).shouldHaveNoInteractions();
        then(refreshTokenService).shouldHaveNoInteractions();
        then(authorizedClientService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("JWT subject가 없으면 401을 반환한다")
    void onLogoutSuccess_shouldReturnUnauthorized_whenSubjectMissing() throws Exception {
        // given
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + ACCESS_TOKEN);

        MockHttpServletResponse response = new MockHttpServletResponse();

        Jwt jwt = Jwt.withTokenValue(ACCESS_TOKEN)
                .header("alg", "RS256")
                .claim("some", "claim")
                .build();

        given(authServerJwtDecoder.decode(ACCESS_TOKEN))
                .willReturn(jwt);

        // when
        sut.onLogoutSuccess(request, response, null);

        // then
        assertThat(response.getStatus())
                .isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);

        assertThat(response.getContentAsString())
                .isEqualTo("invalid token");

        then(blacklistTokenService).shouldHaveNoInteractions();
        then(refreshTokenService).shouldHaveNoInteractions();
        then(authorizedClientService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("저장된 refresh token이 없어도 삭제 쿠키를 내려주고 로그아웃 처리한다")
    void onLogoutSuccess_shouldLogoutSuccessfully_whenStoredRefreshTokenMissing() throws Exception {
        // given
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + ACCESS_TOKEN);

        MockHttpServletResponse response = new MockHttpServletResponse();

        Jwt jwt = Jwt.withTokenValue(ACCESS_TOKEN)
                .header("alg", "RS256")
                .subject(EMAIL)
                .claim("sub", EMAIL)
                .build();

        ResponseCookie deleteCookie = ResponseCookie.from("refreshToken", "")
                .httpOnly(true)
                .secure(true)
                .sameSite("None")
                .path("/")
                .maxAge(0)
                .build();

        given(authServerJwtDecoder.decode(ACCESS_TOKEN))
                .willReturn(jwt);

        given(internalAuthServerProperties.clientRegistrationId())
                .willReturn(CLIENT_REGISTRATION_ID);

        given(refreshTokenService.findValue(CLIENT_REGISTRATION_ID, EMAIL))
                .willReturn(null);

        given(refreshTokenService.deleteResponseCookie(null))
                .willReturn(deleteCookie);

        // when
        sut.onLogoutSuccess(request, response, null);

        // then
        assertThat(response.getStatus())
                .isEqualTo(HttpServletResponse.SC_OK);

        String setCookie = response.getHeader(HttpHeaders.SET_COOKIE);

        assertThat(setCookie)
                .contains("refreshToken=")
                .contains("Path=/")
                .contains("Max-Age=0")
                .contains("Secure")
                .contains("HttpOnly")
                .contains("SameSite=None");

        assertThat(response.getContentAsString())
                .isEqualTo("logout success!");

        then(blacklistTokenService)
                .should()
                .register(ACCESS_TOKEN);

        then(authorizedClientService)
                .should()
                .removeAuthorizedClient(CLIENT_REGISTRATION_ID, EMAIL);
    }

    private String signedJwtWithSubject(String subject) throws JOSEException {
        JWTClaimsSet claimsSet = new JWTClaimsSet.Builder()
                .subject(subject)
                .issueTime(Date.from(Instant.now().minusSeconds(3600)))
                .expirationTime(Date.from(Instant.now().minusSeconds(60)))
                .build();

        SignedJWT signedJWT = new SignedJWT(
                new JWSHeader(JWSAlgorithm.HS256),
                claimsSet
        );

        signedJWT.sign(new MACSigner("01234567890123456789012345678901"));

        return signedJWT.serialize();
    }
}
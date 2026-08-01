package controller;

import org.example.oauth2.client.adapter.in.web.AuthController;
import org.example.oauth2.client.token.application.service.RefreshTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthorizationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.endpoint.OAuth2AccessTokenResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class AuthControllerUnitTest {

    private static final String OLD_REFRESH_TOKEN = "old-refresh-token";
    private static final String NEW_ACCESS_TOKEN = "new-access-token";
    private static final String NEW_REFRESH_TOKEN = "new-refresh-token";

    @Mock
    private RefreshTokenService refreshTokenService;

    private AuthController sut;

    @BeforeEach
    void setUp() {
        sut = new AuthController(refreshTokenService);
    }

    @Test
    @DisplayName("refresh token이 없으면 401과 로그인 Location을 반환한다")
    void myAuthRefresh_shouldReturnUnauthorized_whenRefreshTokenMissing() {
        // given
        MockHttpServletRequest request = new MockHttpServletRequest();

        given(refreshTokenService.extractRefreshToken(request))
                .willReturn(null);

        // when
        ResponseEntity<String> response = sut.myAuthRefresh(request);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getHeaders().getFirst(HttpHeaders.LOCATION))
                .isEqualTo("/login");
        assertThat(response.getBody())
                .isEqualTo("Refresh Token not exists");

        then(refreshTokenService)
                .should()
                .extractRefreshToken(request);

        then(refreshTokenService)
                .shouldHaveNoMoreInteractions();
    }

    @Test
    @DisplayName("refresh token 재발급 성공 시 새 access token과 refresh token 쿠키를 반환한다")
    void myAuthRefresh_shouldReturnNewTokens_whenRefreshTokenReissueSucceeds() {
        // given
        MockHttpServletRequest request = new MockHttpServletRequest();

        OAuth2AccessTokenResponse tokenResponse =
                OAuth2AccessTokenResponse.withToken(NEW_ACCESS_TOKEN)
                        .tokenType(OAuth2AccessToken.TokenType.BEARER)
                        .refreshToken(NEW_REFRESH_TOKEN)
                        .expiresIn(3600)
                        .build();

        ResponseCookie refreshTokenCookie =
                ResponseCookie.from("refreshToken", NEW_REFRESH_TOKEN)
                        .httpOnly(true)
                        .secure(true)
                        .sameSite("None")
                        .path("/")
                        .build();

        given(refreshTokenService.extractRefreshToken(request))
                .willReturn(OLD_REFRESH_TOKEN);

        given(refreshTokenService.reissue(OLD_REFRESH_TOKEN))
                .willReturn(tokenResponse);

        given(refreshTokenService.getResponseCookie(NEW_REFRESH_TOKEN))
                .willReturn(refreshTokenCookie);

        // when
        ResponseEntity<String> response = sut.myAuthRefresh(request);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        assertThat(response.getHeaders().getFirst(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS))
                .isEqualTo("Location, Authorization");

        assertThat(response.getHeaders().getFirst(HttpHeaders.AUTHORIZATION))
                .isEqualTo("Bearer " + NEW_ACCESS_TOKEN);

        assertThat(response.getHeaders().getFirst(HttpHeaders.SET_COOKIE))
                .isEqualTo(refreshTokenCookie.toString());

        assertThat(response.getBody())
                .isEqualTo("Access Token/Refresh Token Issue Success");

        then(refreshTokenService)
                .should()
                .extractRefreshToken(request);

        then(refreshTokenService)
                .should()
                .reissue(OLD_REFRESH_TOKEN);

        then(refreshTokenService)
                .should()
                .getResponseCookie(NEW_REFRESH_TOKEN);
    }

    @Test
    @DisplayName("refresh token 재발급 실패 시 401과 로그인 Location을 반환한다")
    void myAuthRefresh_shouldReturnUnauthorized_whenRefreshTokenReissueFails() {
        // given
        MockHttpServletRequest request = new MockHttpServletRequest();

        OAuth2Error error = new OAuth2Error("invalid_grant");

        given(refreshTokenService.extractRefreshToken(request))
                .willReturn(OLD_REFRESH_TOKEN);

        given(refreshTokenService.reissue(OLD_REFRESH_TOKEN))
                .willThrow(new OAuth2AuthorizationException(error));

        // when
        ResponseEntity<String> response = sut.myAuthRefresh(request);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        assertThat(response.getHeaders().getFirst(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS))
                .isEqualTo("Location, Authorization");

        assertThat(response.getHeaders().getFirst(HttpHeaders.LOCATION))
                .isEqualTo("/login");

        assertThat(response.getBody())
                .isEqualTo("invalid token");

        then(refreshTokenService)
                .should()
                .extractRefreshToken(request);

        then(refreshTokenService)
                .should()
                .reissue(OLD_REFRESH_TOKEN);

        then(refreshTokenService)
                .shouldHaveNoMoreInteractions();
    }

    @Test
    @DisplayName("재발급 응답에 refresh token이 없으면 401을 반환한다")
    void myAuthRefresh_shouldReturnUnauthorized_whenRefreshTokenNotIssued() {
        // given
        MockHttpServletRequest request = new MockHttpServletRequest();

        OAuth2AccessTokenResponse tokenResponse =
                OAuth2AccessTokenResponse.withToken(NEW_ACCESS_TOKEN)
                        .tokenType(OAuth2AccessToken.TokenType.BEARER)
                        .expiresIn(3600)
                        .build();

        given(refreshTokenService.extractRefreshToken(request))
                .willReturn(OLD_REFRESH_TOKEN);

        given(refreshTokenService.reissue(OLD_REFRESH_TOKEN))
                .willReturn(tokenResponse);

        // when
        ResponseEntity<String> response = sut.myAuthRefresh(request);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        assertThat(response.getHeaders().getFirst(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS))
                .isEqualTo("Location, Authorization");

        assertThat(response.getHeaders().getFirst(HttpHeaders.LOCATION))
                .isEqualTo("/login");

        assertThat(response.getBody())
                .isEqualTo("Refresh Token not issued");

        then(refreshTokenService)
                .should()
                .extractRefreshToken(request);

        then(refreshTokenService)
                .should()
                .reissue(OLD_REFRESH_TOKEN);

        then(refreshTokenService)
                .shouldHaveNoMoreInteractions();
    }
}
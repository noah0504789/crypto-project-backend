package org.example.oauth2.client.adapter.in.web;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.common.enums.AuthTokenKey;
import org.example.oauth2.client.token.application.service.RefreshTokenService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.core.endpoint.OAuth2AccessTokenResponse;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequiredArgsConstructor
public class AuthController {

    private final RefreshTokenService refreshTokenService;

    @PostMapping("${api-path.auth.refresh:/auth/refresh}")
    public ResponseEntity<String> myAuthRefresh(HttpServletRequest request) {
        String refreshToken = refreshTokenService.extractRefreshToken(request);

        if (!StringUtils.hasText(refreshToken)) {
            log.warn("[auth] refresh token missing");
            return unauthorized("Refresh Token not exists");
        }

        OAuth2AccessTokenResponse tokenResponse;

        try {
            tokenResponse = refreshTokenService.reissue(refreshToken);
        } catch (Exception e) {
            log.warn("[auth] Token reissue failed: {}", e.getMessage());
            return unauthorized("invalid token");
        }

        if (tokenResponse.getRefreshToken() == null) {
            return unauthorized("Refresh Token not issued");
        }

        String newAccessToken = tokenResponse.getAccessToken().getTokenValue();
        String newRefreshToken = tokenResponse.getRefreshToken().getTokenValue();

        ResponseCookie newRefreshTokenCookie =
                refreshTokenService.getResponseCookie(newRefreshToken);

        return ResponseEntity.status(HttpStatus.CREATED)
                .header(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, "Location, Authorization")
                .header(HttpHeaders.AUTHORIZATION, AuthTokenKey.BEARER_PREFIX.value() + newAccessToken)
                .header(HttpHeaders.SET_COOKIE, newRefreshTokenCookie.toString())
                .body("Access Token/Refresh Token Issue Success");
    }

    private ResponseEntity<String> unauthorized(String message) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .header(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, "Location, Authorization")
                .header(HttpHeaders.LOCATION, "/login")
                .body(message);
    }
}

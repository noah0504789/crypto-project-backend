package org.example.oauth2.client.handler;

import com.nimbusds.jwt.SignedJWT;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.example.common.enums.AuthTokenKey;
import org.example.oauth2.client.properties.InternalAuthServerProperties;
import org.example.oauth2.client.token.application.service.BlacklistTokenService;
import org.example.oauth2.client.token.application.service.RefreshTokenService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.text.ParseException;

@Component
@RequiredArgsConstructor
public class CustomLogoutSuccessHandler implements LogoutSuccessHandler {

    private static final String INVALID_TOKEN_MESSAGE = "invalid token";
    private static final String LOGOUT_SUCCESS_MESSAGE = "logout success!";

    private final OAuth2AuthorizedClientService authorizedClientService;
    private final RefreshTokenService refreshTokenService;
    private final BlacklistTokenService blacklistTokenService;
    private final JwtDecoder authServerJwtDecoder;
    private final InternalAuthServerProperties internalAuthServerProperties;

    @Override
    public void onLogoutSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException {
        String accessToken = extractBearerToken(request);

        if (!StringUtils.hasText(accessToken)) {
            writeUnauthorized(response);
            return;
        }

        String email;

        try {
            email = resolveSubject(accessToken);
        } catch (RuntimeException e) {
            writeUnauthorized(response);
            return;
        }

        blacklistTokenService.register(accessToken);

        String clientRegistrationId = internalAuthServerProperties.clientRegistrationId();
        String refreshToken = refreshTokenService.findValue(clientRegistrationId, email);
        ResponseCookie refreshTokenDeleteCookie = refreshTokenService.deleteResponseCookie(refreshToken);

        authorizedClientService.removeAuthorizedClient(clientRegistrationId, email);

        response.setHeader(HttpHeaders.SET_COOKIE, refreshTokenDeleteCookie.toString());
        response.setStatus(HttpServletResponse.SC_OK);
        response.getWriter().write(LOGOUT_SUCCESS_MESSAGE);
    }

    private String extractBearerToken(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);

        if (!StringUtils.hasText(header) || !header.startsWith(AuthTokenKey.BEARER_PREFIX.value())) {
            return null;
        }

        return header.substring(AuthTokenKey.BEARER_PREFIX.value().length());
    }

    private String resolveSubject(String accessToken) {
        try {
            Jwt jwt = authServerJwtDecoder.decode(accessToken);

            if (!StringUtils.hasText(jwt.getSubject())) {
                throw new IllegalArgumentException("JWT subject is missing");
            }

            return jwt.getSubject();
        } catch (JwtValidationException e) {
            return parseSubjectWithoutValidation(accessToken);
        }
    }

    private String parseSubjectWithoutValidation(String accessToken) {
        try {
            SignedJWT signedJwt = SignedJWT.parse(accessToken);

            String subject = signedJwt.getJWTClaimsSet().getSubject();

            if (!StringUtils.hasText(subject)) {
                throw new IllegalArgumentException("JWT subject is missing");
            }

            return subject;
        } catch (ParseException e) {
            throw new IllegalArgumentException("Invalid JWT", e);
        }
    }

    private void writeUnauthorized(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.getWriter().write(INVALID_TOKEN_MESSAGE);
    }
}

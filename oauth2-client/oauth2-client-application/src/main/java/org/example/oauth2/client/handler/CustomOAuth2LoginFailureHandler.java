package org.example.oauth2.client.handler;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.common.properties.ApiPathProperties;
import org.example.common.properties.FrontendProperties;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class CustomOAuth2LoginFailureHandler implements AuthenticationFailureHandler {

    private static final String ERROR_PARAM_NAME = "error";
    private static final String ERROR_PARAM_VALUE = "oauth2_login_failed";

    private final FrontendProperties frontendProperties;

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response, AuthenticationException exception) throws IOException {
        log.warn("[auth] OAuth2 login failed. message={}", exception.getMessage());

        String redirectUri = UriComponentsBuilder
                .fromUriString(frontendProperties.failureRedirectUri())
                .queryParam(ERROR_PARAM_NAME, ERROR_PARAM_VALUE)
                .build()
                .toUriString();

        response.sendRedirect(redirectUri);
    }
}

package org.example.oauth2.client.handler;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.example.common.properties.FrontendProperties;
import org.example.oauth2.client.properties.InternalAuthServerProperties;
import org.example.oauth2.client.token.application.service.RefreshTokenService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.endpoint.OAuth2AccessTokenResponseClient;
import org.springframework.security.oauth2.client.endpoint.TokenExchangeGrantRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.core.endpoint.OAuth2AccessTokenResponse;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class CustomOAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {

    private final OAuth2AccessTokenResponseClient<TokenExchangeGrantRequest> tokenExchangeResponseClient;
    private final ClientRegistrationRepository clientRegistrationRepository;
    private final OAuth2AuthorizedClientService oAuth2AuthorizedClientService;
    private final RefreshTokenService refreshTokenService;
    private final InternalAuthServerProperties internalAuthServerProperties;
    private final FrontendProperties frontendProperties;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException {
        OAuth2AuthenticationToken authenticationToken = (OAuth2AuthenticationToken) authentication;

        String principalName = authenticationToken.getName();
        String authorizedClientRegistrationId = authenticationToken.getAuthorizedClientRegistrationId();
        OAuth2AuthorizedClient authorizedClient = oAuth2AuthorizedClientService.loadAuthorizedClient(authorizedClientRegistrationId, principalName);
        if (authorizedClient == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("authorized client not found");
            return;
        }

        ClientRegistration internalClientRegistration = clientRegistrationRepository.findByRegistrationId(internalAuthServerProperties.clientRegistrationId());
        if (internalClientRegistration == null) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().write("internal client registration not found");
            return;
        }

        TokenExchangeGrantRequest tokenExchangeRequest = new TokenExchangeGrantRequest(internalClientRegistration, authorizedClient.getAccessToken(), null);
        OAuth2AccessTokenResponse tokenResponse = tokenExchangeResponseClient.getTokenResponse(tokenExchangeRequest);
        OAuth2AccessToken accessToken = tokenResponse.getAccessToken();
        OAuth2RefreshToken refreshToken = tokenResponse.getRefreshToken();
        if (refreshToken == null) {
            response.setStatus(HttpServletResponse.SC_BAD_GATEWAY);
            response.getWriter().write("refresh token not issued");
            return;
        }

        String spaRedirectUri =
                UriComponentsBuilder
                        .fromUriString(frontendProperties.successRedirectUri())
                        .queryParam("accessToken", accessToken.getTokenValue())
                        .build()
                        .toUriString();

        ResponseCookie refreshTokenCookie = refreshTokenService.getResponseCookie(refreshToken.getTokenValue());
        response.setHeader(HttpHeaders.SET_COOKIE, refreshTokenCookie.toString());
        response.sendRedirect(spaRedirectUri);
    }
}
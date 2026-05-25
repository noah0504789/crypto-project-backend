package org.example.oauth2.client.authorizedclient;

import lombok.RequiredArgsConstructor;
import org.example.oauth2.client.token.application.service.AuthorizedClientTokenService;
import org.example.oauth2.client.token.application.service.AccessTokenService;
import org.example.oauth2.client.token.application.service.RefreshTokenService;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class CustomOAuth2AuthorizedClientService implements OAuth2AuthorizedClientService {

    private final AccessTokenService accessTokenService;
    private final RefreshTokenService refreshTokenService;
    private final AuthorizedClientTokenService authorizedClientTokenService;
    private final ClientRegistrationRepository clientRegistrationRepository;

    @Override
    @SuppressWarnings("unchecked")
    public <T extends OAuth2AuthorizedClient> T loadAuthorizedClient(String clientRegistrationId, String principalName) {
        ClientRegistration clientRegistration = clientRegistrationRepository.findByRegistrationId(clientRegistrationId);
        if (clientRegistration == null) {
            return null;
        }

        String accessTokenValue = accessTokenService.findValue(clientRegistrationId, principalName);
        if (!StringUtils.hasText(accessTokenValue)) {
            return null;
        }

        OAuth2AccessToken accessToken =
                new OAuth2AccessToken(
                        OAuth2AccessToken.TokenType.BEARER,
                        accessTokenValue,
                        null,
                        null,
                        null
                );

        String refreshTokenValue = refreshTokenService.findValue(clientRegistrationId, principalName);

        OAuth2RefreshToken refreshToken = null;
        if (StringUtils.hasText(refreshTokenValue)) {
            refreshToken = new OAuth2RefreshToken(refreshTokenValue, null);
        }

        return (T) new OAuth2AuthorizedClient(
                clientRegistration,
                principalName,
                accessToken,
                refreshToken
        );
    }

    @Override
    public void saveAuthorizedClient(OAuth2AuthorizedClient authorizedClient, Authentication authentication) {
        String clientRegistrationId = authorizedClient.getClientRegistration().getRegistrationId();
        String principalName = authentication.getName();
        String accessTokenValue = authorizedClient.getAccessToken().getTokenValue();
        OAuth2RefreshToken refreshToken = authorizedClient.getRefreshToken();
        String refreshTokenValue = refreshToken == null ? null : refreshToken.getTokenValue();

        Map<String, Object> attributes = resolveAttributes(authentication);

        authorizedClientTokenService.save(
                clientRegistrationId,
                principalName,
                attributes,
                accessTokenValue,
                refreshTokenValue
        );
    }

    @Override
    public void removeAuthorizedClient(String clientRegistrationId, String principalName) {
        authorizedClientTokenService.removeAllByEmail(principalName);
    }

    private Map<String, Object> resolveAttributes(Authentication authentication) {
        Object principal = authentication.getPrincipal();

        if (principal instanceof OAuth2User oauth2User) {
            return oauth2User.getAttributes();
        }

        return Map.of();
    }
}
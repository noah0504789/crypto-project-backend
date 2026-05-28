package org.example.oauth2.client.oidc.profile;

import lombok.RequiredArgsConstructor;
import org.example.oauth2.client.oidc.profile.extractor.OidcProviderProfileExtractor;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class OidcProviderProfileResolver {

    private final List<OidcProviderProfileExtractor> extractors;

    public OidcProviderProfile resolve(String clientRegistrationId, OidcIdToken idToken, OidcUser oidcUser) {
        return extractors.stream()
                .filter(extractor -> extractor.supports(clientRegistrationId))
                .findFirst()
                .orElseThrow(() -> new OAuth2AuthenticationException(
                        new OAuth2Error("unsupported_provider"),
                        "Unsupported OIDC provider: " + clientRegistrationId
                ))
                .extract(idToken, oidcUser);
    }
}
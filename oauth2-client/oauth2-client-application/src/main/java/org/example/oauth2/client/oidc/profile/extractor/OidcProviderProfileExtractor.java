package org.example.oauth2.client.oidc.profile.extractor;

import org.example.oauth2.client.oidc.profile.OidcProviderProfile;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

public interface OidcProviderProfileExtractor {

    boolean supports(String clientRegistrationId);

    OidcProviderProfile extract(OidcIdToken idToken, OidcUser oidcUser);
}
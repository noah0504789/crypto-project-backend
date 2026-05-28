package org.example.oauth2.client.oidc.profile.extractor;

import org.example.oauth2.client.oidc.profile.OidcProviderProfile;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class GoogleOidcProviderProfileExtractor implements OidcProviderProfileExtractor {

    @Override
    public boolean supports(String clientRegistrationId) {
        return "google".equals(clientRegistrationId);
    }

    @Override
    public OidcProviderProfile extract(OidcIdToken idToken, OidcUser oidcUser) {
        String providerSub = resolveSub(idToken);
        String email = resolveEmail(idToken, oidcUser);
        String nickname = firstText(
                oidcUser.getFullName(),
                oidcUser.getClaimAsString("name"),
                email,
                oidcUser.getName()
        );

        return new OidcProviderProfile(providerSub, email, nickname);
    }

    private String resolveSub(OidcIdToken idToken) {
        String sub = idToken.getSubject();

        if (!StringUtils.hasText(sub)) {
            throw new OAuth2AuthenticationException(
                    new OAuth2Error("missing_sub"),
                    "OIDC subject claim is missing"
            );
        }

        return sub;
    }

    private String resolveEmail(OidcIdToken idToken, OidcUser oidcUser) {
        String email = firstText(idToken.getEmail(), oidcUser.getEmail());

        if (!StringUtils.hasText(email)) {
            throw new OAuth2AuthenticationException(
                    new OAuth2Error("missing_email"),
                    "OIDC email claim is missing"
            );
        }

        return email;
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }

        return null;
    }
}
package org.example.oauth2.client.oidc.profile;

public record OidcProviderProfile(
        String providerSub,
        String email,
        String nickname
) {
}
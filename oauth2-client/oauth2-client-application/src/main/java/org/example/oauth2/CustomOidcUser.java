package org.example.oauth2;

import org.example.user.UserResponse;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import java.util.*;

public record CustomOidcUser(
        OidcUser delegate,
        UserResponse userResponse,
        String clientRegistrationId,
        Collection<? extends GrantedAuthority> authorities,
        Map<String, Object> attributes
) implements OidcUser {

    public CustomOidcUser(
            OidcUser delegate,
            UserResponse userResponse,
            String clientRegistrationId,
            Collection<? extends GrantedAuthority> authorities
    ) {
        this(
                delegate,
                userResponse,
                clientRegistrationId,
                authorities,
                mergeAttributes(delegate, userResponse, clientRegistrationId)
        );
    }

    public CustomOidcUser {
        Objects.requireNonNull(delegate, "delegate must not be null");
        Objects.requireNonNull(userResponse, "userResponse must not be null");
        Objects.requireNonNull(clientRegistrationId, "clientRegistrationId must not be null");
        Objects.requireNonNull(authorities, "authorities must not be null");
        Objects.requireNonNull(attributes, "attributes must not be null");

        authorities = List.copyOf(authorities);
        attributes = Map.copyOf(attributes);
    }

    @Override
    public Map<String, Object> getClaims() {
        return attributes;
    }

    @Override
    public OidcUserInfo getUserInfo() {
        return delegate.getUserInfo();
    }

    @Override
    public OidcIdToken getIdToken() {
        return delegate.getIdToken();
    }

    @Override
    public Map<String, Object> getAttributes() {
        return attributes;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getName() {
        return userResponse.id();
    }

    private static Map<String, Object> mergeAttributes(OidcUser delegate, UserResponse userResponse, String clientRegistrationId) {
        Map<String, Object> merged = new HashMap<>(delegate.getAttributes());
        merged.putAll(userResponse.getAttributes(clientRegistrationId));

        return Map.copyOf(merged);
    }
}
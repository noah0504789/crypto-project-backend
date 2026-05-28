package org.example.oauth2.client.oidc;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record CustomOidcUser(
        OidcUser delegate,
        String userId,
        String sub,
        String email,
        String nickname,
        String clientRegistrationId,
        Object createdAt,
        Collection<? extends GrantedAuthority> authorities,
        Map<String, Object> attributes
) implements OidcUser {

    public CustomOidcUser(
            OidcUser delegate,
            String userId,
            String sub,
            String email,
            String nickname,
            String clientRegistrationId,
            Object createdAt,
            Collection<? extends GrantedAuthority> authorities
    ) {
        this(
                delegate,
                userId,
                sub,
                email,
                nickname,
                clientRegistrationId,
                createdAt,
                authorities,
                mergeAttributes(
                        delegate,
                        userId,
                        sub,
                        email,
                        nickname,
                        clientRegistrationId,
                        createdAt
                )
        );
    }

    public CustomOidcUser {
        Objects.requireNonNull(delegate, "delegate must not be null");
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(sub, "sub must not be null");
        Objects.requireNonNull(email, "email must not be null");
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

    /**
     * Spring Security principalName.
     *
     * OAuth2AuthorizedClientService 저장/삭제 기준으로 사용될 수 있으므로
     * 로그아웃 기준과 동일하게 email을 반환한다.
     */
    @Override
    public String getName() {
        return email;
    }

    private static Map<String, Object> mergeAttributes(
            OidcUser delegate,
            String userId,
            String sub,
            String email,
            String nickname,
            String clientRegistrationId,
            Object createdAt
    ) {
        Map<String, Object> merged = new HashMap<>(delegate.getAttributes());

        merged.put("id", userId);
        merged.put("userId", userId);
        merged.put("sub", sub);
        merged.put("email", email);
        merged.put("nickname", nickname);
        merged.put("clientRegistrationId", clientRegistrationId);
        merged.put("createdAt", createdAt);

        return Map.copyOf(merged);
    }
}
package org.example.oauth2.user;

import lombok.RequiredArgsConstructor;
import org.example.user.UserCommandService;
import org.example.user.UserQueryService;
import org.example.contract.user.UserResponse;
import org.example.user.UserRoleAuthorityMapper;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Collection;

@Service
@RequiredArgsConstructor
public class CustomOidcUserService implements OAuth2UserService<OidcUserRequest, OidcUser> {

    private final OidcUserService delegate;
    private final UserQueryService userQueryService;
    private final UserCommandService userCommandService;
    private final UserRoleAuthorityMapper userRoleAuthorityMapper;

    @Override
    public OidcUser loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {
        OidcUser oidcUser = delegate.loadUser(userRequest);

        ClientRegistration clientRegistration = userRequest.getClientRegistration();
        String clientRegistrationId = clientRegistration.getRegistrationId();

        OidcIdToken idToken = userRequest.getIdToken();
        String providerSub = resolveSub(idToken);
        String email = resolveEmail(idToken, oidcUser);
        String nickname = resolveNickname(oidcUser, clientRegistrationId);

        UserResponse userResponse = userQueryService.findByEmail(email)
                .orElseGet(() -> userCommandService.signUpOauth2(providerSub, email, nickname));

        Collection<? extends GrantedAuthority> authorities = userRoleAuthorityMapper.toAuthorities(userResponse.roles());

        return new CustomOidcUser(
                oidcUser,
                String.valueOf(userResponse.id()),
                userResponse.sub(),
                userResponse.email(),
                userResponse.nickname(),
                clientRegistrationId,
                userResponse.createdAt(),
                authorities
        );
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
        String email = idToken.getEmail();

        if (!StringUtils.hasText(email)) {
            email = oidcUser.getEmail();
        }

        if (!StringUtils.hasText(email)) {
            throw new OAuth2AuthenticationException(
                    new OAuth2Error("missing_email"),
                    "OIDC email claim is missing"
            );
        }

        return email;
    }

    private String resolveNickname(OidcUser oidcUser, String clientRegistrationId) {
        if ("kakao".equals(clientRegistrationId)) {
            String nickname = oidcUser.getClaimAsString("nickname");

            if (StringUtils.hasText(nickname)) {
                return nickname;
            }
        }

        if ("google".equals(clientRegistrationId)) {
            String fullName = oidcUser.getFullName();

            if (StringUtils.hasText(fullName)) {
                return fullName;
            }

            String name = oidcUser.getClaimAsString("name");

            if (StringUtils.hasText(name)) {
                return name;
            }
        }

        return oidcUser.getName();
    }
}
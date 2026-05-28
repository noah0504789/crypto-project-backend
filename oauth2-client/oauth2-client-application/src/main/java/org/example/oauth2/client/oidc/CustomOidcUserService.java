package org.example.oauth2.client.oidc;

import lombok.RequiredArgsConstructor;
import org.example.oauth2.client.oidc.profile.OidcProviderProfile;
import org.example.oauth2.client.oidc.profile.OidcProviderProfileResolver;
import org.example.oauth2.client.user.application.service.UserCommandService;
import org.example.oauth2.client.user.application.service.UserQueryService;
import org.example.contract.user.UserResponse;
import org.example.oauth2.client.user.application.service.UserRoleAuthorityMapper;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;

import java.util.Collection;

@Service
@RequiredArgsConstructor
public class CustomOidcUserService implements OAuth2UserService<OidcUserRequest, OidcUser> {

    private final OidcUserService delegate;
    private final OidcProviderProfileResolver profileResolver;
    private final UserQueryService userQueryService;
    private final UserCommandService userCommandService;
    private final UserRoleAuthorityMapper userRoleAuthorityMapper;

    @Override
    public OidcUser loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {
        OidcUser oidcUser = delegate.loadUser(userRequest);

        ClientRegistration clientRegistration = userRequest.getClientRegistration();
        String clientRegistrationId = clientRegistration.getRegistrationId();

        OidcProviderProfile profile = profileResolver.resolve(clientRegistrationId, userRequest.getIdToken(), oidcUser);
        UserResponse userResponse = userQueryService.findByEmail(profile.email())
                .orElseGet(() -> userCommandService.signUpOauth2(profile.providerSub(), profile.email(), profile.nickname()));

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
}
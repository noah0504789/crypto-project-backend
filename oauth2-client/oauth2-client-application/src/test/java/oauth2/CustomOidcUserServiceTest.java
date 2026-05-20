package oauth2;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.example.oauth2.CustomOidcUser;
import org.example.oauth2.service.CustomOidcUserService;
import org.example.user.UserCommandService;
import org.example.user.UserQueryService;
import org.example.user.UserResponse;
import org.example.user.UserRoleAuthorityMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.core.oidc.IdTokenClaimNames;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class CustomOidcUserServiceTest {

    private static final String REGISTRATION_ID = "google";
    private static final String PROVIDER_SUB = "provider-sub";
    private static final String EMAIL = "user@test.com";
    private static final String NICKNAME = "테스트유저";

    @Mock
    private OidcUserService delegate;

    @Mock
    private UserQueryService userQueryService;

    @Mock
    private UserCommandService userCommandService;

    private final UserRoleAuthorityMapper userRoleAuthorityMapper = new UserRoleAuthorityMapper();

    private CustomOidcUserService sut;

    @BeforeEach
    void setUp() {
        sut = new CustomOidcUserService(
                delegate,
                userQueryService,
                userCommandService,
                userRoleAuthorityMapper
        );
    }

    @Test
    @DisplayName("기존 회원이면 회원가입하지 않고 CustomOidcUser를 반환한다")
    void loadUser_shouldReturnCustomOidcUser_whenUserExists() {
        // given
        OidcUserRequest userRequest = oidcUserRequest(REGISTRATION_ID, claims(
                PROVIDER_SUB,
                EMAIL,
                NICKNAME
        ));

        OidcUser oidcUser = oidcUser(claims(
                PROVIDER_SUB,
                EMAIL,
                NICKNAME
        ));

        UserResponse userResponse = userResponse();

        given(delegate.loadUser(userRequest))
                .willReturn(oidcUser);

        given(userQueryService.findByEmail(EMAIL))
                .willReturn(Optional.of(userResponse));

        // when
        OidcUser result = sut.loadUser(userRequest);

        // then
        assertThat(result).isInstanceOf(CustomOidcUser.class);
        assertThat(result.getName()).isEqualTo(userResponse.id());
        assertThat((String) result.getAttribute("email")).isEqualTo(EMAIL);
        assertThat((String) result.getAttribute("nickname")).isEqualTo(NICKNAME);
        assertThat((String) result.getAttribute("clientRegistrationId")).isEqualTo(REGISTRATION_ID);

        assertThat(result.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_USER");

        then(userCommandService)
                .shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("신규 회원이면 OAuth2 회원가입 후 CustomOidcUser를 반환한다")
    void loadUser_shouldSignUpAndReturnCustomOidcUser_whenUserDoesNotExist() {
        // given
        OidcUserRequest userRequest = oidcUserRequest(REGISTRATION_ID, claims(
                PROVIDER_SUB,
                EMAIL,
                NICKNAME
        ));

        OidcUser oidcUser = oidcUser(claims(
                PROVIDER_SUB,
                EMAIL,
                NICKNAME
        ));

        UserResponse userResponse = userResponse();

        given(delegate.loadUser(userRequest))
                .willReturn(oidcUser);

        given(userQueryService.findByEmail(EMAIL))
                .willReturn(Optional.empty());

        given(userCommandService.signUpOauth2(PROVIDER_SUB, EMAIL, NICKNAME))
                .willReturn(userResponse);

        // when
        OidcUser result = sut.loadUser(userRequest);

        // then
        assertThat(result).isInstanceOf(CustomOidcUser.class);
        assertThat(result.getName()).isEqualTo(userResponse.id());
        assertThat((String) result.getAttribute("email")).isEqualTo(EMAIL);

        assertThat(result.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_USER");

        then(userCommandService)
                .should()
                .signUpOauth2(PROVIDER_SUB, EMAIL, NICKNAME);
    }

    @Test
    @DisplayName("idToken에 email이 없으면 oidcUser의 email을 사용한다")
    void loadUser_shouldUseOidcUserEmail_whenIdTokenEmailMissing() {
        // given
        Map<String, Object> idTokenClaims = new HashMap<>();
        idTokenClaims.put(IdTokenClaimNames.SUB, PROVIDER_SUB);

        Map<String, Object> userClaims = claims(
                PROVIDER_SUB,
                EMAIL,
                NICKNAME
        );

        OidcUserRequest userRequest =
                oidcUserRequest(REGISTRATION_ID, idTokenClaims);

        OidcUser oidcUser =
                oidcUser(userClaims);

        given(delegate.loadUser(userRequest))
                .willReturn(oidcUser);

        given(userQueryService.findByEmail(EMAIL))
                .willReturn(Optional.of(userResponse()));

        // when
        OidcUser result = sut.loadUser(userRequest);

        // then
        assertThat((String) result.getAttribute("email")).isEqualTo(EMAIL);
    }

    @Test
    @DisplayName("email이 없으면 예외를 던진다")
    void loadUser_shouldThrowException_whenEmailMissing() {
        // given
        Map<String, Object> idTokenClaims = new HashMap<>();
        idTokenClaims.put(IdTokenClaimNames.SUB, PROVIDER_SUB);

        OidcUserRequest userRequest =
                oidcUserRequest(REGISTRATION_ID, idTokenClaims);

        OidcUser oidcUser =
                oidcUser(idTokenClaims);

        given(delegate.loadUser(userRequest))
                .willReturn(oidcUser);

        // when & then
        assertThatThrownBy(() -> sut.loadUser(userRequest))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .hasMessageContaining("OIDC email claim is missing");

        then(userQueryService)
                .shouldHaveNoInteractions();

        then(userCommandService)
                .shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("sub가 없으면 예외를 던진다")
    void loadUser_shouldThrowException_whenSubMissing() {
        // given
        Map<String, Object> claims = new HashMap<>();
        claims.put("email", EMAIL);

        OidcUserRequest userRequest =
                oidcUserRequest(REGISTRATION_ID, claims);

        OidcUser oidcUser =
                oidcUser(claims);

        given(delegate.loadUser(userRequest))
                .willReturn(oidcUser);

        // when & then
        assertThatThrownBy(() -> sut.loadUser(userRequest))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .hasMessageContaining("OIDC subject claim is missing");

        then(userQueryService)
                .shouldHaveNoInteractions();

        then(userCommandService)
                .shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("roles에 ROLE_ prefix가 없으면 ROLE_을 붙여 권한을 생성한다")
    void loadUser_shouldAddRolePrefix_whenRoleDoesNotHavePrefix() {
        // given
        OidcUserRequest userRequest = oidcUserRequest(REGISTRATION_ID, claims(
                PROVIDER_SUB,
                EMAIL,
                NICKNAME
        ));

        OidcUser oidcUser = oidcUser(claims(
                PROVIDER_SUB,
                EMAIL,
                NICKNAME
        ));

        UserResponse userResponse = userResponse(List.of("USER", "ADMIN"));

        given(delegate.loadUser(userRequest))
                .willReturn(oidcUser);

        given(userQueryService.findByEmail(EMAIL))
                .willReturn(Optional.of(userResponse));

        // when
        OidcUser result = sut.loadUser(userRequest);

        // then
        assertThat(result.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_USER", "ROLE_ADMIN");
    }

    @Test
    @DisplayName("roles가 비어 있으면 기본 권한 ROLE_USER를 사용한다")
    void loadUser_shouldUseDefaultRole_whenRolesEmpty() {
        // given
        OidcUserRequest userRequest = oidcUserRequest(REGISTRATION_ID, claims(
                PROVIDER_SUB,
                EMAIL,
                NICKNAME
        ));

        OidcUser oidcUser = oidcUser(claims(
                PROVIDER_SUB,
                EMAIL,
                NICKNAME
        ));

        UserResponse userResponse = userResponse(List.of());

        given(delegate.loadUser(userRequest))
                .willReturn(oidcUser);

        given(userQueryService.findByEmail(EMAIL))
                .willReturn(Optional.of(userResponse));

        // when
        OidcUser result = sut.loadUser(userRequest);

        // then
        assertThat(result.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_USER");
    }

    private OidcUserRequest oidcUserRequest(String registrationId, Map<String, Object> claims) {
        ClientRegistration clientRegistration =
                clientRegistration(registrationId);

        OidcIdToken idToken =
                new OidcIdToken(
                        "id-token",
                        Instant.now(),
                        Instant.now().plusSeconds(3600),
                        claims
                );

        OAuth2AccessToken accessToken =
                new OAuth2AccessToken(
                        OAuth2AccessToken.TokenType.BEARER,
                        "access-token",
                        Instant.now(),
                        Instant.now().plusSeconds(3600)
                );

        return new OidcUserRequest(
                clientRegistration,
                accessToken,
                idToken
        );
    }

    private OidcUser oidcUser(Map<String, Object> claims) {
        OidcIdToken idToken =
                new OidcIdToken(
                        "id-token",
                        Instant.now(),
                        Instant.now().plusSeconds(3600),
                        claims
                );

        OidcUserInfo userInfo = new OidcUserInfo(claims);

        String userNameAttributeName =
                claims.containsKey(IdTokenClaimNames.SUB)
                        ? IdTokenClaimNames.SUB
                        : "email";

        return new DefaultOidcUser(
                List.of(new SimpleGrantedAuthority("SCOPE_openid")),
                idToken,
                userInfo,
                userNameAttributeName
        );
    }

    private ClientRegistration clientRegistration(String registrationId) {
        return ClientRegistration.withRegistrationId(registrationId)
                .clientId(registrationId + "-client-id")
                .clientSecret("client-secret")
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .authorizationUri("https://example.com/oauth2/authorize")
                .tokenUri("https://example.com/oauth2/token")
                .jwkSetUri("https://example.com/oauth2/jwks")
                .userInfoUri("https://example.com/userinfo")
                .userNameAttributeName(IdTokenClaimNames.SUB)
                .scope("openid", "profile", "email")
                .clientName(registrationId)
                .build();
    }

    private Map<String, Object> claims(String sub, String email, String nickname) {
        Map<String, Object> claims = new HashMap<>();
        claims.put(IdTokenClaimNames.SUB, sub);
        claims.put("email", email);
        claims.put("name", nickname);
        claims.put("nickname", nickname);
        return claims;
    }

    private UserResponse userResponse() {
        return userResponse(List.of("ROLE_USER"));
    }

    private UserResponse userResponse(List<String> roles) {
        return UserResponse.builder()
                .id("internal-user-id")
                .sub(PROVIDER_SUB)
                .nickname(NICKNAME)
                .email(EMAIL)
                .roles(roles)
                .createdAt(LocalDateTime.now())
                .build();
    }
}
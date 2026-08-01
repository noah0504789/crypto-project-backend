package oauth2;

import org.example.oauth2.client.oidc.profile.OidcProviderProfile;
import org.example.oauth2.client.oidc.profile.extractor.GoogleOidcProviderProfileExtractor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.IdTokenClaimNames;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
class GoogleOidcProviderProfileExtractorUnitTest {

    private static final String REGISTRATION_ID = "google";
    private static final String PROVIDER_SUB = "google-sub";
    private static final String EMAIL = "user@test.com";
    private static final String NAME = "구글유저";

    private final GoogleOidcProviderProfileExtractor sut = new GoogleOidcProviderProfileExtractor();

    @Test
    @DisplayName("registrationId가 google이면 지원한다")
    void supports_shouldReturnTrue_whenGoogle() {
        assertThat(sut.supports(REGISTRATION_ID)).isTrue();
    }

    @Test
    @DisplayName("registrationId가 google이 아니면 지원하지 않는다")
    void supports_shouldReturnFalse_whenNotGoogle() {
        assertThat(sut.supports("kakao")).isFalse();
    }

    @Test
    @DisplayName("Google 프로필에서 sub, email, nickname을 추출한다")
    void extract_shouldReturnProfile() {
        // given
        Map<String, Object> claims = claims();

        OidcIdToken idToken = idToken(claims);
        OidcUser oidcUser = oidcUser(claims);

        // when
        OidcProviderProfile result = sut.extract(idToken, oidcUser);

        // then
        assertThat(result.providerSub()).isEqualTo(PROVIDER_SUB);
        assertThat(result.email()).isEqualTo(EMAIL);
        assertThat(result.nickname()).isEqualTo(NAME);
    }

    @Test
    @DisplayName("idToken에 email이 없으면 oidcUser의 email을 사용한다")
    void extract_shouldUseOidcUserEmail_whenIdTokenEmailMissing() {
        // given
        Map<String, Object> idTokenClaims = new HashMap<>();
        idTokenClaims.put(IdTokenClaimNames.SUB, PROVIDER_SUB);

        Map<String, Object> userClaims = claims();

        OidcIdToken idToken = idToken(idTokenClaims);
        OidcUser oidcUser = oidcUser(userClaims);

        // when
        OidcProviderProfile result = sut.extract(idToken, oidcUser);

        // then
        assertThat(result.email()).isEqualTo(EMAIL);
    }

    @Test
    @DisplayName("name이 없으면 email을 nickname으로 사용한다")
    void extract_shouldUseEmailAsNickname_whenNameMissing() {
        // given
        Map<String, Object> claims = new HashMap<>();
        claims.put(IdTokenClaimNames.SUB, PROVIDER_SUB);
        claims.put("email", EMAIL);

        OidcIdToken idToken = idToken(claims);
        OidcUser oidcUser = oidcUser(claims);

        // when
        OidcProviderProfile result = sut.extract(idToken, oidcUser);

        // then
        assertThat(result.nickname()).isEqualTo(EMAIL);
    }

    @Test
    @DisplayName("sub가 없으면 예외를 던진다")
    void extract_shouldThrowException_whenSubMissing() {
        // given
        Map<String, Object> claims = new HashMap<>();
        claims.put("email", EMAIL);
        claims.put("name", NAME);

        OidcIdToken idToken = idToken(claims);
        OidcUser oidcUser = oidcUser(claims);

        // when & then
        assertThatThrownBy(() -> sut.extract(idToken, oidcUser))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .hasMessageContaining("OIDC subject claim is missing");
    }

    @Test
    @DisplayName("email이 없으면 예외를 던진다")
    void extract_shouldThrowException_whenEmailMissing() {
        // given
        Map<String, Object> claims = new HashMap<>();
        claims.put(IdTokenClaimNames.SUB, PROVIDER_SUB);
        claims.put("name", NAME);

        OidcIdToken idToken = idToken(claims);
        OidcUser oidcUser = oidcUser(claims);

        // when & then
        assertThatThrownBy(() -> sut.extract(idToken, oidcUser))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .hasMessageContaining("OIDC email claim is missing");
    }

    private OidcIdToken idToken(Map<String, Object> claims) {
        return new OidcIdToken(
                "id-token",
                Instant.now(),
                Instant.now().plusSeconds(3600),
                claims
        );
    }

    private OidcUser oidcUser(Map<String, Object> claims) {
        OidcIdToken idToken = idToken(claims);
        OidcUserInfo userInfo = new OidcUserInfo(claims);

        String userNameAttributeName = claims.containsKey(IdTokenClaimNames.SUB)
                ? IdTokenClaimNames.SUB
                : "email";

        return new DefaultOidcUser(
                List.of(new SimpleGrantedAuthority("SCOPE_openid")),
                idToken,
                userInfo,
                userNameAttributeName
        );
    }

    private Map<String, Object> claims() {
        Map<String, Object> claims = new HashMap<>();
        claims.put(IdTokenClaimNames.SUB, GoogleOidcProviderProfileExtractorUnitTest.PROVIDER_SUB);
        claims.put("email", GoogleOidcProviderProfileExtractorUnitTest.EMAIL);
        claims.put("name", GoogleOidcProviderProfileExtractorUnitTest.NAME);
        return claims;
    }
}
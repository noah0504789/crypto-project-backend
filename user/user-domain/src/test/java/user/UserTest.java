package user;

import org.example.user.domain.model.RoleEnum;
import org.example.user.domain.model.Role;
import org.example.user.domain.model.User;
import org.example.user.domain.model.UserRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserTest {

    @Test
    @DisplayName("ofLocal은 로컬 회원가입 User를 생성한다")
    void ofLocal_createsLocalUser() {
        // given
        String email = "noah@test.com";
        String nickname = "noah";
        String encodedPassword = "encoded-password";
        Role role = Role.ofName(RoleEnum.USER);

        // when
        User user = User.ofLocal(
                email,
                nickname,
                encodedPassword,
                role
        );

        // then
        assertThat(user.getSub()).isNull();
        assertThat(user.getEmail()).isEqualTo(email);
        assertThat(user.getNickname()).isEqualTo(nickname);
        assertThat(user.getPassword()).isEqualTo(encodedPassword);

        assertThat(user.getRoleNames())
                .containsExactly(RoleEnum.USER.getName());
    }

    @Test
    @DisplayName("ofOAuth2는 OAuth2 회원가입 User를 생성한다")
    void ofOAuth2_createsOAuth2User() {
        // given
        String sub = "oidc-sub";
        String email = "noah@test.com";
        String nickname = "noah";
        Role role = Role.ofName(RoleEnum.USER);

        // when
        User user = User.ofOAuth2(
                sub,
                email,
                nickname,
                role
        );

        // then
        assertThat(user.getSub()).isEqualTo(sub);
        assertThat(user.getEmail()).isEqualTo(email);
        assertThat(user.getNickname()).isEqualTo(nickname);
        assertThat(user.getPassword()).isNull();

        assertThat(user.getRoleNames())
                .containsExactly(RoleEnum.USER.getName());
    }

    @Test
    @DisplayName("ofLocal은 roles 컬렉션을 빈 Set이 아닌 상태로 초기화한다")
    void ofLocal_initializesRoles() {
        // given
        Role role = Role.ofName(RoleEnum.USER);

        // when
        User user = User.ofLocal(
                "noah@test.com",
                "noah",
                "encoded-password",
                role
        );

        // then
        assertThat(user.getRoles()).isNotNull();
        assertThat(user.getRoles()).hasSize(1);
    }

    @Test
    @DisplayName("ofOAuth2는 roles 컬렉션을 빈 Set이 아닌 상태로 초기화한다")
    void ofOAuth2_initializesRoles() {
        // given
        Role role = Role.ofName(RoleEnum.USER);

        // when
        User user = User.ofOAuth2(
                "oidc-sub",
                "noah@test.com",
                "noah",
                role
        );

        // then
        assertThat(user.getRoles()).isNotNull();
        assertThat(user.getRoles()).hasSize(1);
    }

    @Test
    @DisplayName("생성된 UserRole은 생성된 User 자신과 전달받은 Role을 참조한다")
    void staticFactory_linksUserAndRole() {
        // given
        Role role = Role.ofName(RoleEnum.USER);

        // when
        User user = User.ofOAuth2(
                "oidc-sub",
                "noah@test.com",
                "noah",
                role
        );

        UserRole userRole = user.getRoles()
                .stream()
                .findFirst()
                .orElseThrow();

        // then
        assertThat(userRole.getUser()).isSameAs(user);
        assertThat(userRole.getRole()).isSameAs(role);
        assertThat(userRole.getRoleName()).isEqualTo(RoleEnum.USER.getName());
    }

    @Test
    @DisplayName("기본 Role은 USER다")
    void getDefaultRole_returnsUser() {
        // when
        RoleEnum defaultRole = User.getDefaultRole();

        // then
        assertThat(defaultRole).isEqualTo(RoleEnum.USER);
    }
}
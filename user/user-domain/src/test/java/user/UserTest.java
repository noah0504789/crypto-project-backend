package user;

import org.example.role.domain.model.RoleEnum;
import org.example.role.domain.model.Role;
import org.example.user.domain.model.User;
import org.example.user.domain.model.UserRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserTest {

    @Test
    @DisplayName("ofLocal은 Role 없이 로컬 회원가입 User를 생성한다")
    void ofLocal_createsLocalUser() {
        // given
        String email = "noah@test.com";
        String nickname = "noah";
        String encodedPassword = "encoded-password";

        // when
        User user = User.ofLocal(
                email,
                nickname,
                encodedPassword
        );

        // then
        assertThat(user.getSub()).isNull();
        assertThat(user.getEmail()).isEqualTo(email);
        assertThat(user.getNickname()).isEqualTo(nickname);
        assertThat(user.getPassword()).isEqualTo(encodedPassword);

        assertThat(user.getRoles()).isNotNull();
        assertThat(user.getRoles()).isEmpty();
        assertThat(user.getRoleNames()).isEmpty();
    }

    @Test
    @DisplayName("ofOAuth2는 Role 없이 OAuth2 회원가입 User를 생성한다")
    void ofOAuth2_createsOAuth2User() {
        // given
        String sub = "oidc-sub";
        String email = "noah@test.com";
        String nickname = "noah";

        // when
        User user = User.ofOAuth2(
                sub,
                email,
                nickname
        );

        // then
        assertThat(user.getSub()).isEqualTo(sub);
        assertThat(user.getEmail()).isEqualTo(email);
        assertThat(user.getNickname()).isEqualTo(nickname);
        assertThat(user.getPassword()).isNull();

        assertThat(user.getRoles()).isNotNull();
        assertThat(user.getRoles()).isEmpty();
        assertThat(user.getRoleNames()).isEmpty();
    }

    @Test
    @DisplayName("ofLocal은 roles 컬렉션을 빈 Set으로 초기화한다")
    void ofLocal_initializesRolesAsEmptySet() {
        // when
        User user = User.ofLocal(
                "noah@test.com",
                "noah",
                "encoded-password"
        );

        // then
        assertThat(user.getRoles()).isNotNull();
        assertThat(user.getRoles()).isEmpty();
    }

    @Test
    @DisplayName("ofOAuth2는 roles 컬렉션을 빈 Set으로 초기화한다")
    void ofOAuth2_initializesRolesAsEmptySet() {
        // when
        User user = User.ofOAuth2(
                "oidc-sub",
                "noah@test.com",
                "noah"
        );

        // then
        assertThat(user.getRoles()).isNotNull();
        assertThat(user.getRoles()).isEmpty();
    }

    @Test
    @DisplayName("addRole은 UserRole을 생성하고 User와 Role을 연결한다")
    void addRole_linksUserAndRole() {
        // given
        Role role = Role.ofName(RoleEnum.USER);

        User user = User.ofOAuth2(
                "oidc-sub",
                "noah@test.com",
                "noah"
        );

        // when
        user.addRole(role);

        // then
        assertThat(user.getRoles()).hasSize(1);
        assertThat(user.getRoleNames())
                .containsExactly(RoleEnum.USER.getName());

        UserRole userRole = user.getRoles()
                .stream()
                .findFirst()
                .orElseThrow();

        assertThat(userRole.getUser()).isSameAs(user);
        assertThat(userRole.getRole()).isSameAs(role);
        assertThat(userRole.getRoleName()).isEqualTo(RoleEnum.USER.getName());
    }

    @Test
    @DisplayName("hasRole은 User가 해당 Role을 가지고 있으면 true를 반환한다")
    void hasRole_whenUserHasRole_returnsTrue() {
        // given
        Role role = Role.ofName(RoleEnum.USER);

        User user = User.ofLocal(
                "noah@test.com",
                "noah",
                "encoded-password"
        );

        user.addRole(role);

        // when
        boolean result = user.hasRole(role);

        // then
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("hasRole은 User가 해당 Role을 가지고 있지 않으면 false를 반환한다")
    void hasRole_whenUserDoesNotHaveRole_returnsFalse() {
        // given
        Role userRole = Role.ofName(RoleEnum.USER);
        Role adminRole = Role.ofName(RoleEnum.ADMIN);

        User user = User.ofLocal(
                "noah@test.com",
                "noah",
                "encoded-password"
        );

        user.addRole(userRole);

        // when
        boolean result = user.hasRole(adminRole);

        // then
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("hasRole은 null Role이 전달되면 false를 반환한다")
    void hasRole_whenRoleIsNull_returnsFalse() {
        // given
        User user = User.ofLocal(
                "noah@test.com",
                "noah",
                "encoded-password"
        );

        // when
        boolean result = user.hasRole(null);

        // then
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("addRole은 이미 같은 Role이 있으면 중복 추가하지 않는다")
    void addRole_whenSameRoleAlreadyExists_doesNotAddDuplicate() {
        // given
        Role role = Role.ofName(RoleEnum.USER);

        User user = User.ofLocal(
                "noah@test.com",
                "noah",
                "encoded-password"
        );

        user.addRole(role);

        // when
        user.addRole(role);

        // then
        assertThat(user.getRoles()).hasSize(1);
        assertThat(user.getRoleNames())
                .containsExactly(RoleEnum.USER.getName());
    }

    @Test
    @DisplayName("addRole은 null Role이 전달되면 IllegalArgumentException을 던진다")
    void addRole_whenRoleIsNull_throwsIllegalArgumentException() {
        // given
        User user = User.ofLocal(
                "noah@test.com",
                "noah",
                "encoded-password"
        );

        // when & then
        assertThatThrownBy(() -> user.addRole(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("role must not be null");
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
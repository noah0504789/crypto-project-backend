package org.example.user.account.domain.model;

import org.example.user.account.domain.exception.UserAccessDeniedException;
import org.example.user.role.domain.model.RoleEnum;
import org.example.user.role.domain.model.Role;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
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
    @DisplayName("addRole은 Role을 User에 추가한다")
    void addRole_addsRoleToUser() {
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
        assertThat(user.getRoles()).containsExactly(role);
        assertThat(user.getRoleNames())
                .containsExactly(RoleEnum.USER.getName());
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
    @DisplayName("addRole은 같은 이름의 Role이면 다른 객체여도 중복 추가하지 않는다")
    void addRole_whenSameRoleNameAlreadyExists_doesNotAddDuplicate() {
        // given
        Role role1 = Role.ofName(RoleEnum.USER);
        Role role2 = Role.ofName(RoleEnum.USER);

        User user = User.ofLocal(
                "noah@test.com",
                "noah",
                "encoded-password"
        );

        user.addRole(role1);

        // when
        user.addRole(role2);

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

    @Nested
    @DisplayName("validateOwner")
    class ValidateOwnerTest {

        private static final UUID PUBLIC_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
        private static final UUID OTHER_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");

        @Test
        @DisplayName("행위자가 본인(publicId 일치)이면 예외가 발생하지 않는다")
        void validateOwner_shouldNotThrow_whenActorIsOwner() {
            // given
            User user = userWithPublicId(PUBLIC_ID);

            // when & then
            assertThatCode(() -> user.validateOwner(PUBLIC_ID))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("행위자 publicId가 본인과 다르면 UserAccessDeniedException이 발생한다")
        void validateOwner_shouldThrow_whenActorIsNotOwner() {
            // given
            User user = userWithPublicId(PUBLIC_ID);

            // when & then
            assertThatThrownBy(() -> user.validateOwner(OTHER_ID))
                    .isInstanceOf(UserAccessDeniedException.class);
        }

        @Test
        @DisplayName("행위자 publicId가 null이면 UserAccessDeniedException이 발생한다")
        void validateOwner_shouldThrow_whenActorIdIsNull() {
            // given
            User user = userWithPublicId(PUBLIC_ID);

            // when & then
            assertThatThrownBy(() -> user.validateOwner(null))
                    .isInstanceOf(UserAccessDeniedException.class);
        }

        private User userWithPublicId(UUID publicId) {
            return User.rehydrate(
                    1L,
                    publicId,
                    "sub",
                    "noah@test.com",
                    "noah",
                    "encoded-password",
                    new HashSet<>(),
                    null,
                    null
            );
        }
    }
}
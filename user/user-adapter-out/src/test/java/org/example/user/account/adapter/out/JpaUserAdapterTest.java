package org.example.user.account.adapter.out;

import org.example.user.account.domain.model.User;
import org.example.user.role.adapter.out.JpaRole;
import org.example.user.role.adapter.out.JpaRoleRepository;
import org.example.user.role.domain.model.Role;
import org.example.user.role.domain.model.RoleEnum;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class JpaUserAdapterTest {

    @Mock
    private JpaUserRepository userRepository;

    @Mock
    private JpaRoleRepository roleRepository;

    @InjectMocks
    private JpaUserAdapter adapter;

    @Test
    @DisplayName("updateProfile(): 기존 JpaUser의 프로필만 변경하고 역할 관계를 재생성하지 않는다")
    void updateProfile_updatesManagedUserWithoutRecreatingRoles() {
        // given
        long userId = 1L;
        UUID publicId = UUID.randomUUID();
        User user = User.rehydrate(
                userId,
                publicId,
                null,
                "noah@test.com",
                "updated-noah",
                "encoded-password",
                Set.of(Role.rehydrate(1L, RoleEnum.USER)),
                null,
                null
        );
        JpaUser managedUser = mock(JpaUser.class);

        given(userRepository.findById(userId))
                .willReturn(Optional.of(managedUser));

        // when
        adapter.updateProfile(user);

        // then
        verify(managedUser).updateProfile(user);
        then(userRepository).should(never()).save(any(JpaUser.class));
        then(roleRepository).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("save(): User의 Role을 JpaRole로 변환해 저장하고 저장된 User를 반환한다")
    void save_persistsUserWithResolvedRoles() {
        // given
        Role role = Role.rehydrate(
                1L,
                RoleEnum.USER
        );

        User user = User.ofLocal(
                "noah@test.com",
                "noah",
                "encoded-password"
        );
        user.addRole(role);

        JpaRole jpaRole = mock(JpaRole.class);

        given(roleRepository.findByName(RoleEnum.USER))
                .willReturn(Optional.of(jpaRole));

        given(jpaRole.toDomain())
                .willReturn(role);

        given(userRepository.save(any(JpaUser.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        // when
        User savedUser = adapter.save(user);

        // then
        assertThat(savedUser.getEmail()).isEqualTo("noah@test.com");
        assertThat(savedUser.getNickname()).isEqualTo("noah");
        assertThat(savedUser.getPassword()).isEqualTo("encoded-password");
        assertThat(savedUser.getRoleNames())
                .containsExactly(RoleEnum.USER.getName());

        then(roleRepository).should()
                .findByName(RoleEnum.USER);

        ArgumentCaptor<JpaUser> captor = ArgumentCaptor.forClass(JpaUser.class);

        then(userRepository).should()
                .save(captor.capture());

        JpaUser savedJpaUser = captor.getValue();

        assertThat(savedJpaUser.getEmail()).isEqualTo("noah@test.com");
        assertThat(savedJpaUser.getNickname()).isEqualTo("noah");
        assertThat(savedJpaUser.getPassword()).isEqualTo("encoded-password");
        assertThat(savedJpaUser.getRoles()).hasSize(1);
    }

    @Test
    @DisplayName("save(): User에 Role이 없으면 Role 조회 없이 저장한다")
    void save_whenUserHasNoRoles_persistsWithoutResolvingRoles() {
        // given
        User user = User.ofLocal(
                "noah@test.com",
                "noah",
                "encoded-password"
        );

        given(userRepository.save(any(JpaUser.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        // when
        User savedUser = adapter.save(user);

        // then
        assertThat(savedUser.getEmail()).isEqualTo("noah@test.com");
        assertThat(savedUser.getRoles()).isEmpty();

        then(roleRepository).shouldHaveNoInteractions();

        ArgumentCaptor<JpaUser> captor = ArgumentCaptor.forClass(JpaUser.class);

        then(userRepository).should()
                .save(captor.capture());

        assertThat(captor.getValue().getRoles()).isEmpty();
    }

    @Test
    @DisplayName("save(): Role을 찾지 못하면 예외를 던지고 User를 저장하지 않는다")
    void save_whenRoleNotFound_throwsException() {
        // given
        Role role = Role.rehydrate(
                1L,
                RoleEnum.USER
        );

        User user = User.ofLocal(
                "noah@test.com",
                "noah",
                "encoded-password"
        );
        user.addRole(role);

        given(roleRepository.findByName(RoleEnum.USER))
                .willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> adapter.save(user))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Role not found: " + RoleEnum.USER);

        then(roleRepository).should()
                .findByName(RoleEnum.USER);

        then(userRepository).should(never())
                .save(any(JpaUser.class));
    }
}

package org.example.user.account.application.service;

import org.example.user.account.application.port.out.UserPersistencePort;
import org.example.user.account.application.service.command.SignUpLocalCommand;
import org.example.user.account.application.service.command.SignUpOauth2Command;
import org.example.user.account.application.service.command.UpdateProfileCommand;
import org.example.user.account.application.exception.UserNotFoundException;
import org.example.user.account.domain.exception.UserAccessDeniedException;
import org.example.user.account.domain.model.User;
import org.example.user.role.application.port.out.RolePersistencePort;
import org.example.user.role.application.exception.RoleNotFoundException;
import org.example.user.role.domain.model.Role;
import org.example.user.role.domain.model.RoleEnum;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class UserCommandServiceTest {

    @Mock
    private UserPersistencePort userRepository;

    @Mock
    private RolePersistencePort roleRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserCommandService sut;

    private static final UUID PUBLIC_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private static final String EMAIL = "test@example.com";
    private static final String NICKNAME = "nickname";
    private static final String NEW_NICKNAME = "newNickname";
    private static final String PASSWORD = "raw-password";
    private static final String ENCODED_PASSWORD = "encoded-password";
    private static final String SUB = "oauth2-sub";

    @Nested
    @DisplayName("updateProfile")
    class UpdateProfileTest {

        @Test
        @DisplayName("publicId로 유저를 조회한 뒤 닉네임을 수정하고 저장한다")
        void updateProfile_should_update_nickname_and_save_user() {
            // given
            User user = mock(User.class);

            UpdateProfileCommand command = new UpdateProfileCommand(
                    PUBLIC_ID,
                    NEW_NICKNAME
            );

            given(userRepository.findByPublicId(PUBLIC_ID))
                    .willReturn(Optional.of(user));

            // when
            sut.updateProfile(command);

            // then
            InOrder inOrder = inOrder(userRepository, user);

            inOrder.verify(userRepository).findByPublicId(PUBLIC_ID);
            inOrder.verify(user).validateOwner(PUBLIC_ID);
            inOrder.verify(user).updateNickname(NEW_NICKNAME);
            inOrder.verify(userRepository).updateProfile(user);
        }

        @Test
        @DisplayName("소유자가 아니면 UserAccessDeniedException을 던지고 닉네임을 수정하거나 저장하지 않는다")
        void updateProfile_should_throw_when_actor_is_not_owner() {
            // given
            User user = mock(User.class);

            UpdateProfileCommand command = new UpdateProfileCommand(
                    PUBLIC_ID,
                    NEW_NICKNAME
            );

            given(userRepository.findByPublicId(PUBLIC_ID))
                    .willReturn(Optional.of(user));
            doThrow(new UserAccessDeniedException(PUBLIC_ID, PUBLIC_ID))
                    .when(user).validateOwner(PUBLIC_ID);

            // when & then
            assertThatThrownBy(() -> sut.updateProfile(command))
                    .isInstanceOf(UserAccessDeniedException.class);

            verify(user, never()).updateNickname(anyString());
            verify(userRepository, never()).updateProfile(any());
        }

        @Test
        @DisplayName("publicId에 해당하는 유저가 없으면 UserNotFoundException을 던진다")
        void updateProfile_should_throw_exception_when_user_not_found() {
            // given
            UpdateProfileCommand command = new UpdateProfileCommand(
                    PUBLIC_ID,
                    NEW_NICKNAME
            );

            given(userRepository.findByPublicId(PUBLIC_ID))
                    .willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> sut.updateProfile(command))
                    .isInstanceOf(UserNotFoundException.class);

            verify(userRepository).findByPublicId(PUBLIC_ID);
            verify(userRepository, never()).updateProfile(any());
        }
    }

    @Nested
    @DisplayName("signUpOauth2")
    class SignUpOauth2Test {

        @Test
        @DisplayName("기본 권한을 조회하고 OAuth2 유저를 생성한 뒤 저장한다")
        void signUpOauth2_should_create_oauth2_user_with_default_role_and_save() {
            // given
            RoleEnum defaultRole = RoleEnum.USER;
            Role role = mock(Role.class);
            User newUser = mock(User.class);
            User savedUser = mock(User.class);

            SignUpOauth2Command command = new SignUpOauth2Command(SUB, EMAIL, NICKNAME);

            given(roleRepository.findByName(defaultRole))
                    .willReturn(Optional.of(role));

            given(userRepository.save(newUser))
                    .willReturn(savedUser);

            try (MockedStatic<User> mockedUser = Mockito.mockStatic(User.class)) {
                mockedUser.when(User::getDefaultRole)
                        .thenReturn(defaultRole);

                mockedUser.when(() -> User.ofOAuth2(SUB, EMAIL, NICKNAME))
                        .thenReturn(newUser);

                // when
                User result = sut.signUpOauth2(command);

                // then
                assertThat(result).isSameAs(savedUser);

                InOrder inOrder = inOrder(roleRepository, newUser, userRepository);

                inOrder.verify(roleRepository).findByName(defaultRole);
                inOrder.verify(newUser).addRole(role);
                inOrder.verify(userRepository).save(newUser);

                mockedUser.verify(User::getDefaultRole);
                mockedUser.verify(() -> User.ofOAuth2(SUB, EMAIL, NICKNAME));
            }
        }

        @Test
        @DisplayName("기본 권한이 없으면 RoleNotFoundException을 던지고 유저를 저장하지 않는다")
        void signUpOauth2_should_throw_exception_when_default_role_not_found() {
            // given
            RoleEnum defaultRole = RoleEnum.USER;

            SignUpOauth2Command command = new SignUpOauth2Command(SUB, EMAIL, NICKNAME);

            given(roleRepository.findByName(defaultRole))
                    .willReturn(Optional.empty());

            try (MockedStatic<User> mockedUser = Mockito.mockStatic(User.class)) {
                mockedUser.when(User::getDefaultRole)
                        .thenReturn(defaultRole);

                // when & then
                assertThatThrownBy(() -> sut.signUpOauth2(command))
                        .isInstanceOf(RoleNotFoundException.class);

                verify(roleRepository).findByName(defaultRole);
                verify(userRepository, never()).save(any());

                mockedUser.verify(User::getDefaultRole);
                mockedUser.verify(
                        () -> User.ofOAuth2(SUB, EMAIL, NICKNAME),
                        never()
                );
            }
        }
    }

    @Nested
    @DisplayName("signUpLocal")
    class SignUpLocalTest {

        @Test
        @DisplayName("비밀번호를 암호화하고 기본 권한을 부여한 뒤 로컬 유저를 저장한다")
        void signUpLocal_should_encode_password_create_local_user_with_default_role_and_save() {
            // given
            RoleEnum defaultRole = RoleEnum.USER;
            Role role = mock(Role.class);
            User newUser = mock(User.class);
            User savedUser = mock(User.class);

            SignUpLocalCommand command = new SignUpLocalCommand(EMAIL, NICKNAME, PASSWORD);

            given(roleRepository.findByName(defaultRole))
                    .willReturn(Optional.of(role));

            given(passwordEncoder.encode(PASSWORD))
                    .willReturn(ENCODED_PASSWORD);

            given(userRepository.save(newUser))
                    .willReturn(savedUser);

            try (MockedStatic<User> mockedUser = Mockito.mockStatic(User.class)) {
                mockedUser.when(User::getDefaultRole)
                        .thenReturn(defaultRole);

                mockedUser.when(() -> User.ofLocal(EMAIL, NICKNAME, ENCODED_PASSWORD))
                        .thenReturn(newUser);

                // when
                User result = sut.signUpLocal(command);

                // then
                assertThat(result).isSameAs(savedUser);

                InOrder inOrder = inOrder(
                        roleRepository,
                        passwordEncoder,
                        newUser,
                        userRepository
                );

                inOrder.verify(roleRepository).findByName(defaultRole);
                inOrder.verify(passwordEncoder).encode(PASSWORD);
                inOrder.verify(newUser).addRole(role);
                inOrder.verify(userRepository).save(newUser);

                mockedUser.verify(User::getDefaultRole);
                mockedUser.verify(() -> User.ofLocal(EMAIL, NICKNAME, ENCODED_PASSWORD));
            }
        }

        @Test
        @DisplayName("기본 권한이 없으면 비밀번호를 암호화하지 않고 RoleNotFoundException을 던진다")
        void signUpLocal_should_throw_exception_when_default_role_not_found() {
            // given
            RoleEnum defaultRole = RoleEnum.USER;

            SignUpLocalCommand command = new SignUpLocalCommand(EMAIL, NICKNAME, PASSWORD);

            given(roleRepository.findByName(defaultRole))
                    .willReturn(Optional.empty());

            try (MockedStatic<User> mockedUser = Mockito.mockStatic(User.class)) {
                mockedUser.when(User::getDefaultRole)
                        .thenReturn(defaultRole);

                // when & then
                assertThatThrownBy(() -> sut.signUpLocal(command))
                        .isInstanceOf(RoleNotFoundException.class);

                verify(roleRepository).findByName(defaultRole);
                verify(passwordEncoder, never()).encode(anyString());
                verify(userRepository, never()).save(any());

                mockedUser.verify(User::getDefaultRole);
                mockedUser.verify(
                        () -> User.ofLocal(EMAIL, NICKNAME, PASSWORD),
                        never()
                );
            }
        }
    }
}

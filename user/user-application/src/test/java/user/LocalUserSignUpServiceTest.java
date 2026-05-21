package user;

import org.example.user.application.service.LocalUserSignUpService;
import org.example.user.application.service.UserCommandService;
import org.example.user.application.service.UserQueryService;
import org.example.user.model.RoleEnum;
import org.example.user.model.domain.Role;
import org.example.user.model.domain.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LocalUserSignUpServiceTest {

    @Mock
    private UserQueryService userQueryService;

    @Mock
    private UserCommandService userCommandService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private LocalUserSignUpService sut;

    @Test
    @DisplayName("기본 Role이 이미 존재하면 Role 저장 없이 User에 Role을 추가하고 비밀번호를 인코딩한 뒤 저장한다")
    void signUp_whenDefaultRoleExists_savesUserWithEncodedPassword() {
        // given
        Role existingRole = Role.ofName(RoleEnum.USER);

        when(userQueryService.findRoleByName(RoleEnum.USER))
                .thenReturn(Optional.of(existingRole));

        when(passwordEncoder.encode("raw-password"))
                .thenReturn("encoded-password");

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);

        // when
        sut.signUp("noah@test.com", "noah", "raw-password");

        // then
        verify(userQueryService).findRoleByName(RoleEnum.USER);
        verify(userCommandService, never()).saveRole(any(Role.class));
        verify(passwordEncoder).encode("raw-password");
        verify(userCommandService).save(userCaptor.capture());

        User savedUser = userCaptor.getValue();

        assertThat(savedUser.getEmail()).isEqualTo("noah@test.com");
        assertThat(savedUser.getNickname()).isEqualTo("noah");
        assertThat(savedUser.getPassword()).isEqualTo("encoded-password");
        assertThat(savedUser.getRoleNames()).contains(RoleEnum.USER.getName());
    }

    @Test
    @DisplayName("기본 Role이 없으면 Role을 저장한 뒤 User에 추가하고 저장한다")
    void signUp_whenDefaultRoleDoesNotExist_savesRoleAndUser() {
        // given
        Role savedRole = Role.ofName(RoleEnum.USER);

        when(userQueryService.findRoleByName(RoleEnum.USER))
                .thenReturn(Optional.empty());

        when(userCommandService.saveRole(any(Role.class)))
                .thenReturn(savedRole);

        when(passwordEncoder.encode("raw-password"))
                .thenReturn("encoded-password");

        ArgumentCaptor<Role> roleCaptor = ArgumentCaptor.forClass(Role.class);
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);

        // when
        sut.signUp("noah@test.com", "noah", "raw-password");

        // then
        verify(userQueryService).findRoleByName(RoleEnum.USER);
        verify(userCommandService).saveRole(roleCaptor.capture());
        verify(passwordEncoder).encode("raw-password");
        verify(userCommandService).save(userCaptor.capture());

        Role roleToSave = roleCaptor.getValue();
        assertThat(roleToSave.getName()).isEqualTo(RoleEnum.USER);

        User savedUser = userCaptor.getValue();

        assertThat(savedUser.getEmail()).isEqualTo("noah@test.com");
        assertThat(savedUser.getNickname()).isEqualTo("noah");
        assertThat(savedUser.getPassword()).isEqualTo("encoded-password");
        assertThat(savedUser.getRoleNames()).contains(RoleEnum.USER.getName());
    }

    @Test
    @DisplayName("회원가입 인자의 email, nickname, password를 User 도메인에 반영한다")
    void signUp_convertsRequestToUserDomain() {
        // given
        Role role = Role.ofName(RoleEnum.USER);

        when(userQueryService.findRoleByName(RoleEnum.USER))
                .thenReturn(Optional.of(role));

        when(passwordEncoder.encode("password123"))
                .thenReturn("encoded-password123");

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);

        // when
        sut.signUp("member@test.com", "member", "password123");

        // then
        verify(userCommandService).save(userCaptor.capture());

        User savedUser = userCaptor.getValue();

        assertThat(savedUser.getEmail()).isEqualTo("member@test.com");
        assertThat(savedUser.getNickname()).isEqualTo("member");
        assertThat(savedUser.getPassword()).isEqualTo("encoded-password123");
    }
}

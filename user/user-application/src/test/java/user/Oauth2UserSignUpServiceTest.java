package user;

import org.example.user.application.service.Oauth2UserSignUpService;
import org.example.user.application.service.UserCommandService;
import org.example.user.application.service.UserQueryService;
import org.example.user.domain.model.RoleEnum;
import org.example.user.domain.model.Role;
import org.example.user.domain.model.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class Oauth2UserSignUpServiceTest {

    @Mock
    private UserQueryService userQueryService;

    @Mock
    private UserCommandService userCommandService;

    @InjectMocks
    private Oauth2UserSignUpService sut;

    private final String sub = "oidc-sub";
    private final String email = "test@test.com";
    private final String nickname = "test";

    @Test
    @DisplayName("기본 Role이 이미 존재하면 Role을 새로 저장하지 않고 User만 저장한다")
    void signUp_whenDefaultRoleExists_savesUserOnly() {
        // given
        Role existingRole = Role.ofName(RoleEnum.USER);

        when(userQueryService.findRoleByName(RoleEnum.USER))
                .thenReturn(Optional.of(existingRole));

        when(userCommandService.save(any(User.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);

        // when
        User result = sut.signUp(sub, email, nickname);

        // then
        verify(userQueryService).findRoleByName(RoleEnum.USER);
        verify(userCommandService, never()).saveRole(any(Role.class));
        verify(userCommandService).save(userCaptor.capture());

        User savedUser = userCaptor.getValue();

        assertThat(savedUser.getSub()).isEqualTo(sub);
        assertThat(savedUser.getEmail()).isEqualTo(email);
        assertThat(savedUser.getNickname()).isEqualTo(nickname);
        assertThat(savedUser.getRoleNames()).contains(RoleEnum.USER.getName());

        assertThat(result).isSameAs(savedUser);
    }

    @Test
    @DisplayName("기본 Role이 없으면 Role을 저장한 뒤 User에 추가하고 User를 저장한다")
    void signUp_whenDefaultRoleDoesNotExist_savesRoleAndUser() {
        // given


        Role savedRole = Role.ofName(RoleEnum.USER);

        when(userQueryService.findRoleByName(RoleEnum.USER))
                .thenReturn(Optional.empty());

        when(userCommandService.saveRole(any(Role.class)))
                .thenReturn(savedRole);

        when(userCommandService.save(any(User.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ArgumentCaptor<Role> roleCaptor = ArgumentCaptor.forClass(Role.class);
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);

        // when
        User result = sut.signUp(sub, email, nickname);

        // then
        verify(userQueryService).findRoleByName(RoleEnum.USER);
        verify(userCommandService).saveRole(roleCaptor.capture());
        verify(userCommandService).save(userCaptor.capture());

        Role roleToSave = roleCaptor.getValue();

        assertThat(roleToSave.getName()).isEqualTo(RoleEnum.USER);

        User savedUser = userCaptor.getValue();

        assertThat(savedUser.getSub()).isEqualTo(sub);
        assertThat(savedUser.getEmail()).isEqualTo(email);
        assertThat(savedUser.getNickname()).isEqualTo(nickname);
        assertThat(savedUser.getRoleNames()).contains(RoleEnum.USER.getName());

        assertThat(result).isSameAs(savedUser);
    }

    @Test
    @DisplayName("User 저장 결과를 그대로 반환한다")
    void signUp_returnsSavedUser() {
        // given
        Role role = Role.ofName(RoleEnum.USER);

        User persistedUser = User.ofOAuth2(sub, email, nickname, role);

        when(userQueryService.findRoleByName(RoleEnum.USER))
                .thenReturn(Optional.of(role));

        when(userCommandService.save(any(User.class)))
                .thenReturn(persistedUser);

        // when
        User result = sut.signUp(sub, email, nickname);

        // then
        assertThat(result).isSameAs(persistedUser);
        assertThat(result.getEmail()).isEqualTo(email);
        assertThat(result.getRoleNames()).contains(RoleEnum.USER.getName());

        verify(userQueryService).findRoleByName(RoleEnum.USER);
        verify(userCommandService).save(any(User.class));
        verify(userCommandService, never()).saveRole(any(Role.class));
    }
}
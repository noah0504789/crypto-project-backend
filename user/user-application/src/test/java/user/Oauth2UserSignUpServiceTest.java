package user;

import org.example.user.role.application.service.RoleQueryService;
import org.example.user.account.application.service.Oauth2UserSignUpService;
import org.example.user.account.application.service.UserCommandService;
import org.example.user.role.domain.exception.RoleNotFoundException;
import org.example.user.role.domain.model.RoleEnum;
import org.example.user.role.domain.model.Role;
import org.example.user.account.domain.model.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class Oauth2UserSignUpServiceTest {

    @Mock
    private RoleQueryService roleQueryService;

    @Mock
    private UserCommandService userCommandService;

    @InjectMocks
    private Oauth2UserSignUpService sut;

    private final String sub = "oidc-sub";
    private final String email = "test@test.com";
    private final String nickname = "test";

    @Test
    @DisplayName("기본 Role이 존재하면 User에 Role을 추가하고 User만 저장한다")
    void signUp_whenDefaultRoleExists_savesUserOnly() {
        // given
        Role existingRole = Role.ofName(RoleEnum.USER);

        when(roleQueryService.findByName(RoleEnum.USER))
                .thenReturn(Optional.of(existingRole));

        when(userCommandService.save(any(User.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);

        // when
        User result = sut.signUp(sub, email, nickname);

        // then
        verify(roleQueryService).findByName(RoleEnum.USER);
        verify(userCommandService).save(userCaptor.capture());

        User savedUser = userCaptor.getValue();

        assertThat(savedUser.getSub()).isEqualTo(sub);
        assertThat(savedUser.getEmail()).isEqualTo(email);
        assertThat(savedUser.getNickname()).isEqualTo(nickname);
        assertThat(savedUser.getRoleNames()).contains(RoleEnum.USER.getName());

        assertThat(result).isSameAs(savedUser);
    }

    @Test
    @DisplayName("기본 Role이 없으면 RoleNotFoundException을 던지고 User를 저장하지 않는다")
    void signUp_whenDefaultRoleDoesNotExist_throwsRoleNotFoundException() {
        // given
        when(roleQueryService.findByName(RoleEnum.USER))
                .thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> sut.signUp(sub, email, nickname))
                .isInstanceOf(RoleNotFoundException.class);

        verify(roleQueryService).findByName(RoleEnum.USER);
        verify(userCommandService, never()).save(any(User.class));
    }

    @Test
    @DisplayName("User 저장 결과를 그대로 반환한다")
    void signUp_returnsSavedUser() {
        // given
        Role role = Role.ofName(RoleEnum.USER);

        User persistedUser = User.ofOAuth2(sub, email, nickname);
        persistedUser.addRole(role);

        when(roleQueryService.findByName(RoleEnum.USER))
                .thenReturn(Optional.of(role));

        when(userCommandService.save(any(User.class)))
                .thenReturn(persistedUser);

        // when
        User result = sut.signUp(sub, email, nickname);

        // then
        assertThat(result).isSameAs(persistedUser);
        assertThat(result.getEmail()).isEqualTo(email);
        assertThat(result.getRoleNames()).contains(RoleEnum.USER.getName());

        verify(roleQueryService).findByName(RoleEnum.USER);
        verify(userCommandService).save(any(User.class));
    }
}
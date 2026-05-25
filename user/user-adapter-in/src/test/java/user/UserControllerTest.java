package user;

import org.example.user.domain.exception.UserNotFoundException;
import org.example.user.adapter.in.web.dto.UserRequest;
import org.example.user.adapter.in.web.dto.UserResponse;
import org.example.user.adapter.in.web.UserController;
import org.example.user.application.service.LocalUserSignUpService;
import org.example.user.application.service.UserQueryService;
import org.example.role.domain.model.RoleEnum;
import org.example.role.domain.model.Role;
import org.example.user.domain.model.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserControllerTest {

    @Mock
    private UserQueryService userQueryService;

    @Mock
    private LocalUserSignUpService localUserSignUpService;

    @InjectMocks
    private UserController sut;

    @Test
    @DisplayName("회원가입 요청 시 LocalUserSignUpService를 호출하고 201 Created를 반환한다")
    void signUp() {
        // given
        UserRequest request = new UserRequest(
                "test@test.com",
                "test",
                "raw-password"
        );

        // when
        ResponseEntity<?> response = sut.signUp(request);

        // then
        verify(localUserSignUpService).signUp("test@test.com", "test", "raw-password");

        assertThat(response.getStatusCode().value()).isEqualTo(201);
        assertThat(response.getHeaders().getLocation()).isEqualTo(URI.create("/home"));
        assertThat(response.getBody()).isNull();
    }

    @Test
    @DisplayName("내 프로필 조회 시 X-User-Id로 유저를 조회하고 UserResponse를 반환한다")
    void myProfile() {
        // given
        UUID publicId = UUID.randomUUID();

        User user = createUser(
                publicId,
                "local@test.com",
                "local-user"
        );

        when(userQueryService.findByPublicId(publicId))
                .thenReturn(Optional.of(user));

        // when
        ResponseEntity<UserResponse> response = sut.myProfile(publicId);

        // then
        verify(userQueryService).findByPublicId(publicId);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isNotNull();

        UserResponse body = response.getBody();

        assertThat(body.id()).isEqualTo(publicId);
        assertThat(body.email()).isEqualTo("local@test.com");
        assertThat(body.nickname()).isEqualTo("local-user");
    }

    @Test
    @DisplayName("내 프로필 조회 시 유저가 없으면 UserNotFoundException이 발생한다")
    void myProfile_whenUserDoesNotExist_throwsException() {
        // given
        UUID publicId = UUID.randomUUID();

        when(userQueryService.findByPublicId(publicId))
                .thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> sut.myProfile(publicId))
                .isInstanceOf(UserNotFoundException.class);

        verify(userQueryService).findByPublicId(publicId);
    }

    @Test
    @DisplayName("다른 유저 프로필 조회 시 path variable publicId로 유저를 조회하고 UserResponse를 반환한다")
    void otherProfile() {
        // given
        UUID publicId = UUID.randomUUID();

        User user = createUser(
                publicId,
                "other@test.com",
                "other-user"
        );

        when(userQueryService.findByPublicId(publicId))
                .thenReturn(Optional.of(user));

        // when
        ResponseEntity<UserResponse> response = sut.otherProfile(publicId);

        // then
        verify(userQueryService).findByPublicId(publicId);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isNotNull();

        UserResponse body = response.getBody();

        assertThat(body.id()).isEqualTo(publicId);
        assertThat(body.email()).isEqualTo("other@test.com");
        assertThat(body.nickname()).isEqualTo("other-user");
    }

    @Test
    @DisplayName("다른 유저 프로필 조회 시 유저가 없으면 UserNotFoundException이 발생한다")
    void otherProfile_whenUserDoesNotExist_throwsException() {
        // given
        UUID publicId = UUID.randomUUID();

        when(userQueryService.findByPublicId(publicId))
                .thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> sut.otherProfile(publicId))
                .isInstanceOf(UserNotFoundException.class);

        verify(userQueryService).findByPublicId(publicId);
    }

    private User createUser(UUID publicId, String email, String nickname) {
        Role role = Role.ofName(RoleEnum.USER);

        User user = User.ofLocal(
                email,
                nickname,
                "encoded-password"
        );

        user.addRole(role);

        ReflectionTestUtils.setField(user, "publicId", publicId);
        ReflectionTestUtils.setField(user, "createdAt", LocalDateTime.now());

        return user;
    }
}
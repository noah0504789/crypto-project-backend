package org.example.user.account.adapter.in.web;

import org.example.user.account.adapter.in.web.dto.UserProfileUpdateRequest;
import org.example.user.account.application.port.in.UserCommandUseCase;
import org.example.user.account.application.port.in.UserQueryUseCase;
import org.example.user.account.application.service.command.SignUpLocalCommand;
import org.example.user.account.application.service.command.UpdateProfileCommand;
import org.example.user.account.application.exception.UserNotFoundException;
import org.example.user.account.adapter.in.web.dto.UserCreateRequest;
import org.example.user.account.adapter.in.web.dto.UserResponse;
import org.example.user.role.domain.model.RoleEnum;
import org.example.user.role.domain.model.Role;
import org.example.user.account.domain.model.User;
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
    private UserQueryUseCase userQueryUseCase;

    @Mock
    private UserCommandUseCase userCommandUseCase;

    @InjectMocks
    private UserController sut;

    @Test
    @DisplayName("회원가입 요청 시 UserCommandUseCase를 호출하고 201 Created를 반환한다")
    void signUp() {
        // given
        UserCreateRequest request = new UserCreateRequest(
                "test@test.com",
                "test",
                "raw-password"
        );

        SignUpLocalCommand command = new SignUpLocalCommand(
                "test@test.com",
                "test",
                "raw-password"
        );

        // when
        ResponseEntity<Void> response = sut.signUp(request);

        // then
        verify(userCommandUseCase).signUpLocal(command);

        assertThat(response.getStatusCode().value()).isEqualTo(201);
        assertThat(response.getHeaders().getLocation()).isEqualTo(URI.create("/"));
        assertThat(response.getBody()).isNull();

        verifyNoInteractions(userQueryUseCase);
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

        when(userQueryUseCase.findByPublicId(publicId))
                .thenReturn(Optional.of(user));

        // when
        ResponseEntity<UserResponse> response = sut.myProfile(publicId);

        // then
        verify(userQueryUseCase).findByPublicId(publicId);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isNotNull();

        UserResponse body = response.getBody();

        assertThat(body.id()).isEqualTo(publicId);
        assertThat(body.email()).isEqualTo("local@test.com");
        assertThat(body.nickname()).isEqualTo("local-user");

        verifyNoInteractions(userCommandUseCase);
    }

    @Test
    @DisplayName("내 프로필 조회 시 유저가 없으면 UserNotFoundException이 발생한다")
    void myProfile_whenUserDoesNotExist_throwsException() {
        // given
        UUID publicId = UUID.randomUUID();

        when(userQueryUseCase.findByPublicId(publicId))
                .thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> sut.myProfile(publicId))
                .isInstanceOf(UserNotFoundException.class);

        verify(userQueryUseCase).findByPublicId(publicId);
        verifyNoInteractions(userCommandUseCase);
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

        when(userQueryUseCase.findByPublicId(publicId))
                .thenReturn(Optional.of(user));

        // when
        ResponseEntity<UserResponse> response = sut.otherProfile(publicId);

        // then
        verify(userQueryUseCase).findByPublicId(publicId);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isNotNull();

        UserResponse body = response.getBody();

        assertThat(body.id()).isEqualTo(publicId);
        assertThat(body.email()).isEqualTo("other@test.com");
        assertThat(body.nickname()).isEqualTo("other-user");

        verifyNoInteractions(userCommandUseCase);
    }

    @Test
    @DisplayName("다른 유저 프로필 조회 시 유저가 없으면 UserNotFoundException이 발생한다")
    void otherProfile_whenUserDoesNotExist_throwsException() {
        // given
        UUID publicId = UUID.randomUUID();

        when(userQueryUseCase.findByPublicId(publicId))
                .thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> sut.otherProfile(publicId))
                .isInstanceOf(UserNotFoundException.class);

        verify(userQueryUseCase).findByPublicId(publicId);
        verifyNoInteractions(userCommandUseCase);
    }

    @Test
    @DisplayName("프로필 수정 요청 시 UserCommandUseCase를 호출하고 204 No Content를 반환한다")
    void updateProfile() {
        // given
        UUID publicId = UUID.randomUUID();

        UserProfileUpdateRequest request = new UserProfileUpdateRequest(
                "updated-user"
        );

        UpdateProfileCommand command = new UpdateProfileCommand(
                publicId,
                "updated-user"
        );

        // when
        ResponseEntity<Void> response = sut.updateProfile(publicId, request);

        // then
        verify(userCommandUseCase).updateProfile(command);

        assertThat(response.getStatusCode().value()).isEqualTo(204);
        assertThat(response.getBody()).isNull();

        verifyNoInteractions(userQueryUseCase);
    }

    @Test
    @DisplayName("프로필 수정 요청이 비어 있으면 400 Bad Request를 반환하고 UserCommandUseCase를 호출하지 않는다")
    void updateProfile_whenRequestIsEmpty_returnsBadRequest() {
        // given
        UUID publicId = UUID.randomUUID();

        UserProfileUpdateRequest request = new UserProfileUpdateRequest(null);

        // when
        ResponseEntity<Void> response = sut.updateProfile(publicId, request);

        // then
        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody()).isNull();

        verifyNoInteractions(userCommandUseCase);
        verifyNoInteractions(userQueryUseCase);
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
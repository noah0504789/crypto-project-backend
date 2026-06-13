package org.example.user.account.adapter.in.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.common.enums.HttpHeaderKey;
import org.example.common.exception.GlobalExceptionHandler;
import org.example.common.test.config.TestBootApplication;
import org.example.common.validation.NotBlankIfPresentValidator;
import org.example.user.account.adapter.in.web.dto.UserProfileUpdateRequest;
import org.example.user.account.application.port.out.UserPersistencePort;
import org.example.user.account.application.service.LocalUserSignUpService;
import org.example.user.account.application.service.UserCommandService;
import org.example.user.account.application.service.UserQueryService;
import org.example.user.account.application.validation.UniqueUserNicknameValidator;
import org.example.user.account.domain.model.User;
import org.example.user.role.domain.model.Role;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(UserController.class)
@ContextConfiguration(classes = {
        TestBootApplication.class,
        UserController.class,
        NotBlankIfPresentValidator.class,
        UniqueUserNicknameValidator.class,
        GlobalExceptionHandler.class
})
class UserControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private UserQueryService userQueryService;

    @MockitoBean
    private LocalUserSignUpService localUserSignUpService;

    @MockitoBean
    private UserCommandService userCommandService;

    @MockitoBean
    private UserPersistencePort userPersistencePort;

    @Test
    @DisplayName("내 프로필 조회 요청이 성공하면 200 OK와 UserResponse를 반환한다")
    void myProfile_shouldReturnUserResponse() throws Exception {
        UUID publicId = UUID.randomUUID();

        User user = createUser(
                publicId,
                "local@test.com",
                "local-user"
        );

        given(userQueryService.findByPublicId(publicId))
                .willReturn(Optional.of(user));

        mockMvc.perform(
                        get("/user/me/profile")
                                .header(HttpHeaderKey.USER_ID_VALUE, publicId.toString())
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(publicId.toString()))
                .andExpect(jsonPath("$.email").value("local@test.com"))
                .andExpect(jsonPath("$.nickname").value("local-user"));

        then(userQueryService)
                .should()
                .findByPublicId(publicId);
    }

    @Test
    @DisplayName("내 프로필 조회 시 유저가 없으면 404 Not Found를 반환한다")
    void myProfile_shouldReturnNotFound_whenUserDoesNotExist() throws Exception {
        UUID publicId = UUID.randomUUID();

        given(userQueryService.findByPublicId(publicId))
                .willReturn(Optional.empty());

        mockMvc.perform(
                        get("/user/me/profile")
                                .header(HttpHeaderKey.USER_ID_VALUE, publicId.toString())
                )
                .andExpect(status().isNoContent());

        then(userQueryService)
                .should()
                .findByPublicId(publicId);
    }

    @Test
    @DisplayName("다른 유저 프로필 조회 요청이 성공하면 200 OK와 UserResponse를 반환한다")
    void otherProfile_shouldReturnUserResponse() throws Exception {
        UUID publicId = UUID.randomUUID();

        User user = createUser(
                publicId,
                "other@test.com",
                "other-user"
        );

        given(userQueryService.findByPublicId(publicId))
                .willReturn(Optional.of(user));

        mockMvc.perform(
                        get("/user/{publicId}/profile", publicId)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(publicId.toString()))
                .andExpect(jsonPath("$.email").value("other@test.com"))
                .andExpect(jsonPath("$.nickname").value("other-user"));

        then(userQueryService)
                .should()
                .findByPublicId(publicId);
    }

    @Test
    @DisplayName("다른 유저 프로필 조회 시 유저가 없으면 404 Not Found를 반환한다")
    void otherProfile_shouldReturnNotFound_whenUserDoesNotExist() throws Exception {
        UUID publicId = UUID.randomUUID();

        given(userQueryService.findByPublicId(publicId))
                .willReturn(Optional.empty());

        mockMvc.perform(get("/user/{publicId}/profile", publicId))
                .andExpect(status().isNoContent());

        then(userQueryService)
                .should()
                .findByPublicId(publicId);
    }

    @Test
    @DisplayName("프로필 수정 요청이 유효하면 204 No Content를 반환한다")
    void updateProfile_shouldReturnNoContent_whenRequestIsValid() throws Exception {
        // given
        UUID publicId = UUID.randomUUID();

        UserProfileUpdateRequest request =
                new UserProfileUpdateRequest("updatedUser");

        given(userPersistencePort.existsByNickname("updatedUser"))
                .willReturn(false);

        // when & then
        mockMvc.perform(
                        patch("/user/me/profile")
                                .header(HttpHeaderKey.USER_ID_VALUE, publicId.toString())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                )
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        then(userPersistencePort)
                .should()
                .existsByNickname("updatedUser");

        then(userCommandService)
                .should()
                .updateProfile(publicId, "updatedUser");

        then(userQueryService)
                .shouldHaveNoInteractions();

        then(localUserSignUpService)
                .shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("프로필 수정 요청 바디가 비어 있으면 400 Bad Request를 반환하고 body는 없다")
    void updateProfile_shouldReturnBadRequestWithoutBody_whenRequestIsEmpty() throws Exception {
        // given
        UUID publicId = UUID.randomUUID();

        // when & then
        mockMvc.perform(
                        patch("/user/me/profile")
                                .header(HttpHeaderKey.USER_ID_VALUE, publicId.toString())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}")
                )
                .andExpect(status().isBadRequest())
                .andExpect(content().string(""));

        then(userCommandService)
                .shouldHaveNoInteractions();

        then(userPersistencePort)
                .shouldHaveNoInteractions();

        then(userQueryService)
                .shouldHaveNoInteractions();

        then(localUserSignUpService)
                .shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("nickname이 blank이면 GlobalExceptionHandler가 ValidationResult 형식으로 응답한다")
    void updateProfile_shouldReturnValidationResult_whenNicknameIsBlank() throws Exception {
        // given
        UUID publicId = UUID.randomUUID();

        UserProfileUpdateRequest request =
                new UserProfileUpdateRequest("   ");

        // when & then
        mockMvc.perform(
                        patch("/user/me/profile")
                                .header(HttpHeaderKey.USER_ID_VALUE, publicId.toString())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors").isArray())
                .andExpect(jsonPath("$.errors[0].field").value("nickname"))
                .andExpect(jsonPath("$.errors[0].code").value("NotBlankIfPresent"))
                .andExpect(jsonPath("$.errors[0].message").value("닉네임은 비어 있을 수 없습니다."));

        then(userCommandService)
                .shouldHaveNoInteractions();

        then(userPersistencePort)
                .shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("nickname이 2자 미만이면 GlobalExceptionHandler가 ValidationResult 형식으로 응답한다")
    void updateProfile_shouldReturnValidationResult_whenNicknameIsTooShort() throws Exception {
        // given
        UUID publicId = UUID.randomUUID();

        UserProfileUpdateRequest request =
                new UserProfileUpdateRequest("a");

        given(userPersistencePort.existsByNickname("a"))
                .willReturn(false);

        // when & then
        mockMvc.perform(
                        patch("/user/me/profile")
                                .header(HttpHeaderKey.USER_ID_VALUE, publicId.toString())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors").isArray())
                .andExpect(jsonPath("$.errors[0].field").value("nickname"))
                .andExpect(jsonPath("$.errors[0].code").value("Size"))
                .andExpect(jsonPath("$.errors[0].message").value("닉네임은 2자 이상 20자 이하로 입력해야 합니다."));

        then(userPersistencePort)
                .should()
                .existsByNickname("a");

        then(userCommandService)
                .shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("nickname이 20자를 초과하면 GlobalExceptionHandler가 ValidationResult 형식으로 응답한다")
    void updateProfile_shouldReturnValidationResult_whenNicknameIsTooLong() throws Exception {
        // given
        UUID publicId = UUID.randomUUID();

        String nickname = "a".repeat(21);

        UserProfileUpdateRequest request =
                new UserProfileUpdateRequest(nickname);

        given(userPersistencePort.existsByNickname(nickname))
                .willReturn(false);

        // when & then
        mockMvc.perform(
                        patch("/user/me/profile")
                                .header(HttpHeaderKey.USER_ID_VALUE, publicId.toString())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors").isArray())
                .andExpect(jsonPath("$.errors[0].field").value("nickname"))
                .andExpect(jsonPath("$.errors[0].code").value("Size"))
                .andExpect(jsonPath("$.errors[0].message").value("닉네임은 2자 이상 20자 이하로 입력해야 합니다."));

        then(userPersistencePort)
                .should()
                .existsByNickname(nickname);

        then(userCommandService)
                .shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("nickname이 허용되지 않은 문자를 포함하면 GlobalExceptionHandler가 ValidationResult 형식으로 응답한다")
    void updateProfile_shouldReturnValidationResult_whenNicknamePatternIsInvalid() throws Exception {
        // given
        UUID publicId = UUID.randomUUID();

        UserProfileUpdateRequest request =
                new UserProfileUpdateRequest("bad!");

        given(userPersistencePort.existsByNickname("bad!"))
                .willReturn(false);

        // when & then
        mockMvc.perform(
                        patch("/user/me/profile")
                                .header(HttpHeaderKey.USER_ID_VALUE, publicId.toString())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors").isArray())
                .andExpect(jsonPath("$.errors[0].field").value("nickname"))
                .andExpect(jsonPath("$.errors[0].code").value("Pattern"))
                .andExpect(jsonPath("$.errors[0].message").value("닉네임은 한글, 영문, 숫자, 언더스코어만 사용할 수 있습니다."));

        then(userPersistencePort)
                .should()
                .existsByNickname("bad!");

        then(userCommandService)
                .shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("nickname이 이미 존재하면 GlobalExceptionHandler가 ValidationResult 형식으로 응답한다")
    void updateProfile_shouldReturnValidationResult_whenNicknameAlreadyExists() throws Exception {
        // given
        UUID publicId = UUID.randomUUID();

        UserProfileUpdateRequest request =
                new UserProfileUpdateRequest("existingUser");

        given(userPersistencePort.existsByNickname("existingUser"))
                .willReturn(true);

        // when & then
        mockMvc.perform(
                        patch("/user/me/profile")
                                .header(HttpHeaderKey.USER_ID_VALUE, publicId.toString())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors").isArray())
                .andExpect(jsonPath("$.errors[0].field").value("nickname"))
                .andExpect(jsonPath("$.errors[0].code").value("UniqueUserNickname"))
                .andExpect(jsonPath("$.errors[0].message").value("이미 사용 중인 닉네임입니다."));

        then(userPersistencePort)
                .should()
                .existsByNickname("existingUser");

        then(userCommandService)
                .shouldHaveNoInteractions();
    }

    private User createUser(UUID publicId, String email, String nickname) {
        Role role = Role.ofName(User.getDefaultRole());

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
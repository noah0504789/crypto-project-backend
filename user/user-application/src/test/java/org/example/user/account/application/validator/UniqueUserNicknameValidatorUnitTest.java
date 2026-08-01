package org.example.user.account.application.validator;

import org.example.user.account.application.port.out.UserPersistencePort;
import org.example.user.account.application.validation.UniqueUserNicknameValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class UniqueUserNicknameValidatorUnitTest {

    @Mock
    private UserPersistencePort userPersistencePort;

    private UniqueUserNicknameValidator sut;

    @BeforeEach
    void setUp() {
        sut = new UniqueUserNicknameValidator(userPersistencePort);
    }

    @Test
    @DisplayName("nickname이 null이면 true를 반환한다")
    void isValid_shouldReturnTrue_whenNicknameIsNull() {
        // when
        boolean result = sut.isValid(null, null);

        // then
        assertThat(result).isTrue();

        then(userPersistencePort)
                .shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("nickname이 blank이면 true를 반환한다")
    void isValid_shouldReturnTrue_whenNicknameIsBlank() {
        // when
        boolean result = sut.isValid("   ", null);

        // then
        assertThat(result).isTrue();

        then(userPersistencePort)
                .shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("nickname이 존재하지 않으면 true를 반환한다")
    void isValid_shouldReturnTrue_whenNicknameDoesNotExist() {
        // given
        String nickname = "newNickname";

        given(userPersistencePort.existsByNickname(nickname))
                .willReturn(false);

        // when
        boolean result = sut.isValid(nickname, null);

        // then
        assertThat(result).isTrue();

        then(userPersistencePort)
                .should()
                .existsByNickname(nickname);
    }

    @Test
    @DisplayName("nickname이 이미 존재하면 false를 반환한다")
    void isValid_shouldReturnFalse_whenNicknameAlreadyExists() {
        // given
        String nickname = "existingNickname";

        given(userPersistencePort.existsByNickname(nickname))
                .willReturn(true);

        // when
        boolean result = sut.isValid(nickname, null);

        // then
        assertThat(result).isFalse();

        then(userPersistencePort)
                .should()
                .existsByNickname(nickname);
    }
}
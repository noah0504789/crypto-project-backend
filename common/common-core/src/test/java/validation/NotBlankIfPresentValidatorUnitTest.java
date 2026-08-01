package validation;

import jakarta.validation.ConstraintValidatorContext;
import org.example.common.validation.NotBlankIfPresentValidator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotBlankIfPresentValidatorUnitTest {

    @Mock
    private ConstraintValidatorContext context;

    @InjectMocks
    private NotBlankIfPresentValidator sut;

    @Test
    @DisplayName("value가 null이면 필드가 없는 것으로 보고 true를 반환한다")
    void isValidNull() {
        // when
        boolean result = sut.isValid(null, context);

        // then
        assertThat(result).isTrue();
        verifyNoInteractions(context);
    }

    @Test
    @DisplayName("value가 빈 문자열이면 false를 반환한다")
    void isValidEmptyString() {
        // when
        boolean result = sut.isValid("", context);

        // then
        assertThat(result).isFalse();
        verifyNoInteractions(context);
    }

    @Test
    @DisplayName("value가 공백 문자열이면 false를 반환한다")
    void isValidBlankString() {
        // when
        boolean result = sut.isValid("   ", context);

        // then
        assertThat(result).isFalse();
        verifyNoInteractions(context);
    }

    @Test
    @DisplayName("value가 공백이 아닌 문자열이면 true를 반환한다")
    void isValidNonBlankString() {
        // when
        boolean result = sut.isValid("수정 제목", context);

        // then
        assertThat(result).isTrue();
        verifyNoInteractions(context);
    }

    @Test
    @DisplayName("value 앞뒤에 공백이 있어도 실제 문자가 있으면 true를 반환한다")
    void isValidStringWithSpaces() {
        // when
        boolean result = sut.isValid("  수정 제목  ", context);

        // then
        assertThat(result).isTrue();
        verifyNoInteractions(context);
    }
}
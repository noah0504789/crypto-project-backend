package chatroom.validation;

import jakarta.validation.ConstraintValidatorContext;
import org.example.chatroom.application.port.in.ChatRoomQueryUseCase;
import org.example.chatroom.application.validation.UniqueChatRoomTitleValidator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UniqueChatRoomTitleValidatorTest {

    @Mock
    private ChatRoomQueryUseCase chatRoomQueryUseCase;

    @Mock
    private ConstraintValidatorContext context;

    @Mock
    private ConstraintValidatorContext.ConstraintViolationBuilder violationBuilder;

    @Mock
    private ConstraintValidatorContext.ConstraintViolationBuilder.NodeBuilderCustomizableContext nodeBuilder;

    @InjectMocks
    private UniqueChatRoomTitleValidator sut;

    @Test
    @DisplayName("title이 null이면 중복 검사를 하지 않고 true를 반환한다")
    void isValidNullTitle() {
        // when
        boolean result = sut.isValid(null, context);

        // then
        assertThat(result).isTrue();

        verifyNoInteractions(chatRoomQueryUseCase);
        verifyNoInteractions(context);
    }

    @Test
    @DisplayName("title이 blank이면 중복 검사를 하지 않고 true를 반환한다")
    void isValidBlankTitle() {
        // when
        boolean result = sut.isValid("   ", context);

        // then
        assertThat(result).isTrue();

        verifyNoInteractions(chatRoomQueryUseCase);
        verifyNoInteractions(context);
    }

    @Test
    @DisplayName("같은 제목의 채팅방이 없으면 true를 반환한다")
    void isValidWhenTitleDoesNotExist() {
        // given
        String title = "새 채팅방";

        given(chatRoomQueryUseCase.existsByTitle(title))
                .willReturn(false);

        // when
        boolean result = sut.isValid(title, context);

        // then
        assertThat(result).isTrue();

        verify(chatRoomQueryUseCase).existsByTitle(title);
        verifyNoInteractions(context);
    }

    @Test
    @DisplayName("같은 제목의 채팅방이 있으면 false를 반환하고 title 필드에 violation을 추가한다")
    void isInvalidWhenTitleExists() {
        // given
        String title = "중복 채팅방";
        String messageTemplate = "{chatroom.title.unique}";

        given(chatRoomQueryUseCase.existsByTitle(title))
                .willReturn(true);

        given(context.getDefaultConstraintMessageTemplate())
                .willReturn(messageTemplate);

        given(context.buildConstraintViolationWithTemplate(messageTemplate))
                .willReturn(violationBuilder);

        given(violationBuilder.addPropertyNode("title"))
                .willReturn(nodeBuilder);

        given(nodeBuilder.addConstraintViolation())
                .willReturn(context);

        // when
        boolean result = sut.isValid(title, context);

        // then
        assertThat(result).isFalse();

        verify(chatRoomQueryUseCase).existsByTitle(title);
        verify(context).disableDefaultConstraintViolation();
        verify(context).getDefaultConstraintMessageTemplate();
        verify(context).buildConstraintViolationWithTemplate(messageTemplate);
        verify(violationBuilder).addPropertyNode("title");
        verify(nodeBuilder).addConstraintViolation();
    }
}
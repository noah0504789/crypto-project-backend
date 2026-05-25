package common;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.example.chat.chatmessage.adapter.in.exception.ChatMessageResourceExhaustedException;
import org.example.chat.common.exception.ChatMessageCacheException;
import org.example.chat.common.exception.ChatMessagePersistException;
import org.example.chat.common.exception.GlobalGrpcExceptionAdvice;
import org.example.chat.chatmessage.adapter.in.exception.ChatMessageGrpcCancelledException;
import org.example.chat.chatmessage.domain.event.dlq.ChatMessageDlqEventList;
import org.example.chat.chatmessage.domain.event.ChatMessageEventList;
import org.example.chat.chatmessage.application.service.ChatMessageCommandService;
import org.example.chat.chatmessage.domain.model.ChatMessage;
import org.example.chat.chatroom.domain.exception.ChatRoomNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GlobalGrpcExceptionAdviceTest {

    @Mock
    private ChatMessageCommandService chatMessageCommandService;

    @InjectMocks
    private GlobalGrpcExceptionAdvice sut;

    private final String messageId = "100000000000000000000001";
    private final String roomId = "000000000000000000000001";

    @Test
    @DisplayName("ChatRoomNotFoundException은 NOT_FOUND로 변환한다")
    void handleChatRoomNotFound() {
        // given
        ChatRoomNotFoundException exception = new ChatRoomNotFoundException(roomId);

        // when
        StatusRuntimeException result = sut.handleChatRoomNotFound(exception);

        // then
        assertThat(result.getStatus().getCode()).isEqualTo(Status.Code.NOT_FOUND);
        assertThat(result.getStatus().getDescription()).isEqualTo(exception.getMessage());

        verifyNoInteractions(chatMessageCommandService);
    }

    @Test
    @DisplayName("IllegalArgumentException은 INVALID_ARGUMENT로 변환한다")
    void handleIllegalArgument() {
        // given
        IllegalArgumentException exception = new IllegalArgumentException("invalid request");

        // when
        StatusRuntimeException result = sut.handleIllegalArgument(exception);

        // then
        assertThat(result.getStatus().getCode()).isEqualTo(Status.Code.INVALID_ARGUMENT);
        assertThat(result.getStatus().getDescription()).isEqualTo("invalid request");

        verifyNoInteractions(chatMessageCommandService);
    }

    @Test
    @DisplayName("StatusRuntimeException은 그대로 반환한다")
    void handleStatusRuntimeException() {
        // given
        StatusRuntimeException exception = Status.PERMISSION_DENIED
                .withDescription("permission denied")
                .asRuntimeException();

        // when
        StatusRuntimeException result = sut.handleStatusRuntimeException(exception);

        // then
        assertThat(result).isSameAs(exception);
        assertThat(result.getStatus().getCode()).isEqualTo(Status.Code.PERMISSION_DENIED);
        assertThat(result.getStatus().getDescription()).isEqualTo("permission denied");

        verifyNoInteractions(chatMessageCommandService);
    }

    @Test
    @DisplayName("알 수 없는 예외는 INTERNAL로 변환한다")
    void handleUnknown() {
        // given
        RuntimeException exception = new RuntimeException("unknown error");

        // when
        StatusRuntimeException result = sut.handleUnknown(exception);

        // then
        assertThat(result.getStatus().getCode()).isEqualTo(Status.Code.INTERNAL);
        assertThat(result.getStatus().getDescription()).isEqualTo("unexpected grpc server error");

        verifyNoInteractions(chatMessageCommandService);
    }

    @Test
    @DisplayName("ResourceExhausted 예외는 RESOURCE_EXHAUSTED로 변환하고 보상 삭제는 하지 않는다")
    void handleResourceExhausted() {
        // given
        ChatMessageResourceExhaustedException exception =
                new ChatMessageResourceExhaustedException(
                        "resource exhausted",
                        new RuntimeException("mongo resource exhausted")
                );

        // when
        StatusRuntimeException result = sut.handleResourceExhausted(exception);

        // then
        assertThat(result.getStatus().getCode()).isEqualTo(Status.Code.RESOURCE_EXHAUSTED);
        assertThat(result.getStatus().getDescription())
                .isEqualTo("chat message save rejected due to db resource exhaustion");

        verifyNoInteractions(chatMessageCommandService);
    }

    @Test
    @DisplayName("Cancelled 예외가 rollback을 요구하면 hardDelete 보상 삭제를 수행하고 CANCELLED로 변환한다")
    void handleCancelledWithRollback() {
        // given
        ChatMessage rollbackTarget = chatMessage();

        ChatMessageGrpcCancelledException exception =
                mock(ChatMessageGrpcCancelledException.class);

        given(exception.requiresRollback()).willReturn(true);
        given(exception.getRollbackTarget()).willReturn(rollbackTarget);
        given(exception.getMessage()).willReturn("client cancelled");

        // when
        StatusRuntimeException result = sut.handleCancelled(exception);

        // then
        assertThat(result.getStatus().getCode()).isEqualTo(Status.Code.CANCELLED);
        assertThat(result.getStatus().getDescription()).isEqualTo("client cancelled");

        verify(chatMessageCommandService).hardDelete(messageId, roomId);
    }

    @Test
    @DisplayName("Cancelled 예외가 rollback을 요구하지 않으면 보상 삭제 없이 CANCELLED로 변환한다")
    void handleCancelledWithoutRollback() {
        // given
        ChatMessageGrpcCancelledException exception =
                mock(ChatMessageGrpcCancelledException.class);

        given(exception.requiresRollback()).willReturn(false);
        given(exception.getMessage()).willReturn("client cancelled");

        // when
        StatusRuntimeException result = sut.handleCancelled(exception);

        // then
        assertThat(result.getStatus().getCode()).isEqualTo(Status.Code.CANCELLED);
        assertThat(result.getStatus().getDescription()).isEqualTo("client cancelled");

        verifyNoInteractions(chatMessageCommandService);
    }

    @Test
    @DisplayName("Cancelled 예외가 rollback을 요구해도 rollbackTarget이 null이면 보상 삭제를 하지 않는다")
    void handleCancelledWithNullRollbackTarget() {
        // given
        ChatMessageGrpcCancelledException exception =
                mock(ChatMessageGrpcCancelledException.class);

        given(exception.requiresRollback()).willReturn(true);
        given(exception.getRollbackTarget()).willReturn(null);
        given(exception.getMessage()).willReturn("client cancelled");

        // when
        StatusRuntimeException result = sut.handleCancelled(exception);

        // then
        assertThat(result.getStatus().getCode()).isEqualTo(Status.Code.CANCELLED);
        assertThat(result.getStatus().getDescription()).isEqualTo("client cancelled");

        verifyNoInteractions(chatMessageCommandService);
    }

    @Test
    @DisplayName("Persist 예외가 rollback을 요구하면 hardDelete 보상 삭제 후 INTERNAL로 변환한다")
    void handlePersistWithRollback() {
        // given
        ChatMessage rollbackTarget = chatMessage();

        ChatMessagePersistException exception = mock(ChatMessagePersistException.class);

        given(exception.getRollbackTarget()).willReturn(rollbackTarget);
        given(exception.getMessage()).willReturn("persist failed");

        // when
        StatusRuntimeException result = sut.handlePersist(exception);

        // then
        assertThat(result.getStatus().getCode()).isEqualTo(Status.Code.INTERNAL);
        assertThat(result.getStatus().getDescription()).isEqualTo("chat message core save failed");

        verify(chatMessageCommandService).hardDelete(messageId, roomId);
    }

    @Test
    @DisplayName("Cache 예외가 rollback을 요구하면 hardDelete 보상 삭제 후 INTERNAL로 변환한다")
    void handleCacheWithRollback() {
        // given
        ChatMessage rollbackTarget = chatMessage();

        ChatMessageCacheException exception = mock(ChatMessageCacheException.class);

        given(exception.getRollbackTarget()).willReturn(rollbackTarget);
        given(exception.getMessage()).willReturn("cache failed");

        // when
        StatusRuntimeException result = sut.handleCache(exception);

        // then
        assertThat(result.getStatus().getCode()).isEqualTo(Status.Code.INTERNAL);
        assertThat(result.getStatus().getDescription()).isEqualTo("chat message cache save failed");

        verify(chatMessageCommandService).hardDelete(messageId, roomId);
    }

    @Test
    @DisplayName("보상 삭제 중 예외가 발생해도 원래 gRPC 예외 변환은 정상 수행한다")
    void compensationFailureDoesNotBreakGrpcResponse() {
        // given
        ChatMessage rollbackTarget = chatMessage();

        ChatMessageCacheException exception = mock(ChatMessageCacheException.class);

        given(exception.getRollbackTarget()).willReturn(rollbackTarget);
        given(exception.getMessage()).willReturn("cache failed");

        doThrow(new RuntimeException("hardDelete failed"))
                .when(chatMessageCommandService)
                .hardDelete(messageId, roomId);

        // when
        StatusRuntimeException result = sut.handleCache(exception);

        // then
        assertThat(result.getStatus().getCode()).isEqualTo(Status.Code.INTERNAL);
        assertThat(result.getStatus().getDescription()).isEqualTo("chat message cache save failed");

        verify(chatMessageCommandService).hardDelete(messageId, roomId);
    }

    private ChatMessage chatMessage() {
        return ChatMessage.builder()
                .id(messageId)
                .roomId(roomId)
                .writerId("writer-1")
                .content("hello")
                .createdAt(LocalDateTime.of(2026, 1, 1, 10, 0))
                .eventList(new ChatMessageEventList())
                .dlqEventList(new ChatMessageDlqEventList())
                .build();
    }
}
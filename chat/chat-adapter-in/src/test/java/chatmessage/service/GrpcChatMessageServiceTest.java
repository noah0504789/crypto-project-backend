package chatmessage.service;

import io.grpc.Context;
import io.grpc.stub.StreamObserver;
import org.example.chat.chatmessage.adapter.in.grpc.GrpcChatMessageService;
import org.example.chat.chatmessage.application.dto.ChatMessageSaveCommand;
import org.example.chat.chatmessage.application.dto.ChatMessageSaveResult;
import org.example.chat.chatmessage.application.service.ChatMessageCommandService;
import org.example.chat.chatmessage.adapter.in.exception.ChatMessageGrpcCancelledException;
import org.example.grpc.chatmessage.GrpcChatMessageRequest;
import org.example.grpc.chatmessage.GrpcChatMessageResponse;
import org.example.grpc.chatmessage.GrpcChatMessageHardDeleteRequest;
import org.example.grpc.chatmessage.GrpcChatMessageHardDeleteResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GrpcChatMessageServiceTest {

    @Mock
    private ChatMessageCommandService chatMessageCommandService;

    @InjectMocks
    private GrpcChatMessageService sut;

    private final String messageId = "msg1";
    private final String roomId = "room1";
    private final String writerId = "user1";
    private final String content = "hello";
    private final String clientMessageId = "client-msg-1";

    @Nested
    @DisplayName("save")
    class SaveTest {

        @Test
        @DisplayName("메시지 저장 요청을 command usecase에 위임하고 성공 응답을 전송한다")
        void grpcSaveShouldDelegateToUseCaseAndReturnSuccessResponse() {
            // given
            GrpcChatMessageRequest request = request();
            ChatMessageSaveResult result = new ChatMessageSaveResult(messageId, 1234L);

            given(chatMessageCommandService.save(any(ChatMessageSaveCommand.class)))
                    .willReturn(result);

            @SuppressWarnings("unchecked")
            StreamObserver<GrpcChatMessageResponse> observer = mock(StreamObserver.class);

            // when
            sut.save(request, observer);

            // then
            ArgumentCaptor<ChatMessageSaveCommand> commandCaptor =
                    ArgumentCaptor.forClass(ChatMessageSaveCommand.class);

            ArgumentCaptor<GrpcChatMessageResponse> responseCaptor =
                    ArgumentCaptor.forClass(GrpcChatMessageResponse.class);

            verify(chatMessageCommandService).save(commandCaptor.capture());

            ChatMessageSaveCommand command = commandCaptor.getValue();

            assertEquals(messageId, command.messageId());
            assertEquals(roomId, command.roomId());
            assertEquals(writerId, command.writerId());
            assertEquals(content, command.content());
            assertEquals(clientMessageId, command.clientMessageId());

            verify(observer).onNext(responseCaptor.capture());
            verify(observer).onCompleted();
            verify(observer, never()).onError(any());

            GrpcChatMessageResponse response = responseCaptor.getValue();

            assertTrue(response.getSuccess());
            assertEquals(messageId, response.getId());
            assertEquals(1234L, response.getTs());
        }

        @Test
        @DisplayName("command usecase에서 예외가 발생하면 예외를 전파하고 응답 전송을 하지 않는다")
        void grpcSaveShouldThrowWhenUseCaseFails() {
            // given
            GrpcChatMessageRequest request = request();

            given(chatMessageCommandService.save(any(ChatMessageSaveCommand.class)))
                    .willThrow(new RuntimeException("save failed"));

            @SuppressWarnings("unchecked")
            StreamObserver<GrpcChatMessageResponse> observer = mock(StreamObserver.class);

            // when & then
            RuntimeException ex = assertThrows(
                    RuntimeException.class,
                    () -> sut.save(request, observer)
            );

            assertEquals("save failed", ex.getMessage());

            verify(chatMessageCommandService).save(any(ChatMessageSaveCommand.class));
            verify(observer, never()).onNext(any());
            verify(observer, never()).onCompleted();
            verify(observer, never()).onError(any());
        }

        @Test
        @DisplayName("save 호출 전에 gRPC Context가 취소되면 ChatMessageGrpcCancelledException을 던지고 usecase를 호출하지 않는다")
        void grpcSaveShouldThrowCancelledExceptionWhenContextIsCancelledBeforeSave() {
            // given
            GrpcChatMessageRequest request = request();

            @SuppressWarnings("unchecked")
            StreamObserver<GrpcChatMessageResponse> observer = mock(StreamObserver.class);

            Context.CancellableContext ctx = Context.current().withCancellation();
            ctx.cancel(null);

            try {
                assertThrows(
                        ChatMessageGrpcCancelledException.class,
                        () -> ctx.run(() -> sut.save(request, observer))
                );
            } finally {
                ctx.close();
            }

            verifyNoInteractions(chatMessageCommandService);
            verify(observer, never()).onNext(any());
            verify(observer, never()).onCompleted();
            verify(observer, never()).onError(any());
        }

        @Test
        @DisplayName("usecase 호출 후 응답 전송 전에 gRPC Context가 취소되면 ChatMessageGrpcCancelledException을 던지고 응답을 전송하지 않는다")
        void grpcSaveShouldThrowCancelledExceptionWhenContextIsCancelledAfterSave() {
            // given
            GrpcChatMessageRequest request = request();

            @SuppressWarnings("unchecked")
            StreamObserver<GrpcChatMessageResponse> observer = mock(StreamObserver.class);

            Context.CancellableContext ctx = Context.current().withCancellation();

            given(chatMessageCommandService.save(any(ChatMessageSaveCommand.class)))
                    .willAnswer(invocation -> {
                        ctx.cancel(null);
                        return new ChatMessageSaveResult(messageId, 1234L);
                    });

            try {
                assertThrows(
                        ChatMessageGrpcCancelledException.class,
                        () -> ctx.run(() -> sut.save(request, observer))
                );
            } finally {
                ctx.close();
            }

            verify(chatMessageCommandService).save(any(ChatMessageSaveCommand.class));
            verify(observer, never()).onNext(any());
            verify(observer, never()).onCompleted();
            verify(observer, never()).onError(any());
        }
    }

    @Nested
    @DisplayName("hardDelete")
    class HardDeleteTest {

        @Test
        @DisplayName("하드 삭제 요청을 command usecase에 위임하고 성공 응답을 전송한다")
        void grpcHardDeleteShouldDelegateToUseCaseAndReturnSuccessResponse() {
            // given
            GrpcChatMessageHardDeleteRequest request = hardDeleteRequest();

            @SuppressWarnings("unchecked")
            StreamObserver<GrpcChatMessageHardDeleteResponse> observer = mock(StreamObserver.class);

            // when
            sut.hardDelete(request, observer);

            // then
            ArgumentCaptor<GrpcChatMessageHardDeleteResponse> captor =
                    ArgumentCaptor.forClass(GrpcChatMessageHardDeleteResponse.class);

            verify(chatMessageCommandService).hardDelete(messageId, roomId);

            verify(observer).onNext(captor.capture());
            verify(observer).onCompleted();
            verify(observer, never()).onError(any());

            GrpcChatMessageHardDeleteResponse response = captor.getValue();

            assertTrue(response.getSuccess());
            assertEquals(messageId, response.getMessageId());
        }

        @Test
        @DisplayName("하드 삭제 중 command usecase 예외가 발생하면 예외를 전파하고 응답 전송을 하지 않는다")
        void grpcHardDeleteShouldThrowWhenUseCaseFails() {
            // given
            GrpcChatMessageHardDeleteRequest request = hardDeleteRequest();

            doThrow(new RuntimeException("hard delete failed"))
                    .when(chatMessageCommandService)
                    .hardDelete(messageId, roomId);

            @SuppressWarnings("unchecked")
            StreamObserver<GrpcChatMessageHardDeleteResponse> observer = mock(StreamObserver.class);

            // when & then
            RuntimeException ex = assertThrows(
                    RuntimeException.class,
                    () -> sut.hardDelete(request, observer)
            );

            assertEquals("hard delete failed", ex.getMessage());

            verify(chatMessageCommandService).hardDelete(messageId, roomId);

            verify(observer, never()).onNext(any());
            verify(observer, never()).onCompleted();
            verify(observer, never()).onError(any());
        }

        @Test
        @DisplayName("hardDelete 호출 전에 gRPC Context가 취소되면 ChatMessageGrpcCancelledException을 던지고 usecase를 호출하지 않는다")
        void grpcHardDeleteShouldThrowCancelledExceptionWhenContextIsCancelledBeforeHardDelete() {
            // given
            GrpcChatMessageHardDeleteRequest request = hardDeleteRequest();

            @SuppressWarnings("unchecked")
            StreamObserver<GrpcChatMessageHardDeleteResponse> observer = mock(StreamObserver.class);

            Context.CancellableContext ctx = Context.current().withCancellation();
            ctx.cancel(null);

            try {
                assertThrows(
                        ChatMessageGrpcCancelledException.class,
                        () -> ctx.run(() -> sut.hardDelete(request, observer))
                );
            } finally {
                ctx.close();
            }

            verifyNoInteractions(chatMessageCommandService);
            verify(observer, never()).onNext(any());
            verify(observer, never()).onCompleted();
            verify(observer, never()).onError(any());
        }
    }

    private GrpcChatMessageRequest request() {
        return GrpcChatMessageRequest.newBuilder()
                .setMessageId(messageId)
                .setRoomId(roomId)
                .setWriterId(writerId)
                .setContent(content)
                .setClientMessageId(clientMessageId)
                .build();
    }

    private GrpcChatMessageHardDeleteRequest hardDeleteRequest() {
        return GrpcChatMessageHardDeleteRequest.newBuilder()
                .setMessageId(messageId)
                .setRoomId(roomId)
                .build();
    }
}
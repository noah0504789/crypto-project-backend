package org.example.websocket.gateway.chatmessage.adapter.out.grpc;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.example.chat.chatmessage.client.ChatMessageClient;
import org.example.common.grpc.exception.GrpcClientException;
import org.example.common.grpc.exception.GrpcFailureCode;
import org.example.grpc.chatmessage.GrpcChatMessageHardDeleteRequest;
import org.example.grpc.chatmessage.GrpcChatMessageHardDeleteResponse;
import org.example.grpc.chatmessage.GrpcChatMessageRequest;
import org.example.grpc.chatmessage.GrpcChatMessageResponse;
import org.example.websocket.gateway.chatmessage.application.service.command.ChatMessageHardDeleteCommand;
import org.example.websocket.gateway.chatmessage.application.service.command.ChatMessageSendCommand;
import org.example.websocket.gateway.chatmessage.application.service.result.ChatMessageHardDeleteResult;
import org.example.websocket.gateway.chatmessage.application.service.result.ChatMessageSendResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class GrpcChatMessageCommandAdapterUnitTest {

    @Mock
    private ChatMessageClient chatMessageClient;

    @InjectMocks
    private GrpcChatMessageCommandAdapter sut;

    @Test
    @DisplayName("save 성공 시 gRPC 요청을 생성하고 ChatMessageSendResult를 반환한다")
    void save_shouldReturnResult_whenGrpcResponseReceived() {
        ChatMessageSendCommand command = new ChatMessageSendCommand(
                "client-body-id",
                "body-id",
                "room-id",
                "writer-id",
                "hello"
        );

        GrpcChatMessageResponse response = GrpcChatMessageResponse.newBuilder()
                .setSuccess(true)
                .setId("body-id")
                .setTs(12345L)
                .build();
        when(chatMessageClient.save(any())).thenReturn(CompletableFuture.completedFuture(response));

        CompletableFuture<ChatMessageSendResult> future = sut.save(command);

        ArgumentCaptor<GrpcChatMessageRequest> requestCaptor =
                ArgumentCaptor.forClass(GrpcChatMessageRequest.class);

        verify(chatMessageClient).save(requestCaptor.capture());

        GrpcChatMessageRequest request = requestCaptor.getValue();

        assertThat(request.getClientMessageId()).isEqualTo(command.clientMessageId());
        assertThat(request.getMessageId()).isEqualTo(command.messageId());
        assertThat(request.getRoomId()).isEqualTo(command.roomId());
        assertThat(request.getWriterId()).isEqualTo(command.writerId());
        assertThat(request.getContent()).isEqualTo(command.content());

        ChatMessageSendResult result = future.join();

        assertThat(result.success()).isTrue();
        assertThat(result.messageId()).isEqualTo("body-id");
        assertThat(result.timestamp()).isEqualTo(12345L);
    }

    @Test
    @DisplayName("save 중 DEADLINE_EXCEEDED 발생 시 GrpcClientException의 코드가 DEADLINE_EXCEEDED가 된다")
    void save_shouldCompleteExceptionally_whenGrpcErrorReceived() {
        ChatMessageSendCommand command = new ChatMessageSendCommand(
                "client-body-id",
                "body-id",
                "room-id",
                "writer-id",
                "hello"
        );

        StatusRuntimeException error = Status.DEADLINE_EXCEEDED
                .withDescription("deadline exceeded")
                .asRuntimeException();
        when(chatMessageClient.save(any())).thenReturn(CompletableFuture.failedFuture(error));

        CompletableFuture<ChatMessageSendResult> future = sut.save(command);

        GrpcClientException resolved = extractGrpcClientException(future);

        assertThat(resolved.getCode()).isEqualTo(GrpcFailureCode.DEADLINE_EXCEEDED);
    }

    @Test
    @DisplayName("hardDelete 성공 시 gRPC 요청을 생성하고 ChatMessageHardDeleteResult를 반환한다")
    void hardDelete_shouldReturnResult_whenGrpcResponseReceived() {
        ChatMessageHardDeleteCommand command = new ChatMessageHardDeleteCommand(
                "body-id",
                "room-id",
                "save failed after timeout"
        );

        GrpcChatMessageHardDeleteResponse response = GrpcChatMessageHardDeleteResponse.newBuilder()
                .setSuccess(true)
                .setMessageId("body-id")
                .setDeleted(true)
                .setAlreadyDeleted(false)
                .setNotFound(false)
                .build();
        when(chatMessageClient.hardDelete(any())).thenReturn(CompletableFuture.completedFuture(response));

        CompletableFuture<ChatMessageHardDeleteResult> future = sut.hardDelete(command);

        ArgumentCaptor<GrpcChatMessageHardDeleteRequest> requestCaptor =
                ArgumentCaptor.forClass(GrpcChatMessageHardDeleteRequest.class);

        verify(chatMessageClient).hardDelete(requestCaptor.capture());

        GrpcChatMessageHardDeleteRequest request = requestCaptor.getValue();

        assertThat(request.getMessageId()).isEqualTo(command.messageId());
        assertThat(request.getRoomId()).isEqualTo(command.roomId());
        assertThat(request.getReason()).isEqualTo(command.reason());

        ChatMessageHardDeleteResult result = future.join();

        assertThat(result.success()).isTrue();
        assertThat(result.messageId()).isEqualTo("body-id");
        assertThat(result.deleted()).isTrue();
        assertThat(result.alreadyDeleted()).isFalse();
        assertThat(result.notFound()).isFalse();
    }

    @Test
    @DisplayName("hardDelete 중 CANCELLED 발생 시 GrpcClientException의 코드가 CANCELLED가 된다")
    void hardDelete_shouldCompleteExceptionally_whenGrpcErrorReceived() {
        ChatMessageHardDeleteCommand command = new ChatMessageHardDeleteCommand(
                "body-id",
                "room-id",
                "save failed after timeout"
        );

        StatusRuntimeException error = Status.CANCELLED
                .withDescription("cancelled")
                .asRuntimeException();
        when(chatMessageClient.hardDelete(any())).thenReturn(CompletableFuture.failedFuture(error));

        CompletableFuture<ChatMessageHardDeleteResult> future = sut.hardDelete(command);

        GrpcClientException resolved = extractGrpcClientException(future);

        assertThat(resolved.getCode()).isEqualTo(GrpcFailureCode.CANCELLED);
    }

    private GrpcClientException extractGrpcClientException(CompletableFuture<?> future) {
        Throwable cur = future.handle((result, throwable) -> throwable).join();

        while (cur != null) {
            if (cur instanceof GrpcClientException e) {
                return e;
            }

            cur = cur.getCause();
        }

        throw new AssertionError("GrpcClientException not found");
    }
}

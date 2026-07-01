package org.example.chat.chatmessage.adapter.in.grpc;

import io.grpc.Context;
import io.grpc.stub.StreamObserver;
import org.example.chat.chatmessage.adapter.in.grpc.exception.ChatMessageGrpcCancelledException;
import org.example.chat.chatmessage.application.service.command.ChatMessageSaveCommand;
import org.example.chat.chatmessage.application.service.result.ChatMessageSaveResult;
import org.example.chat.chatmessage.application.port.in.ChatMessageCommandUseCase;
import org.example.grpc.chatmessage.GrpcChatMessageHardDeleteRequest;
import org.example.grpc.chatmessage.GrpcChatMessageHardDeleteResponse;
import org.example.grpc.chatmessage.GrpcChatMessageRequest;
import org.example.grpc.chatmessage.GrpcChatMessageResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GrpcChatMessageServiceTest {

    @Mock
    private ChatMessageCommandUseCase chatMessageCommandUseCase;

    @Mock
    private GrpcChatMessageMapper grpcChatMessageMapper;

    @Mock
    private StreamObserver<GrpcChatMessageResponse> saveResponseObserver;

    @Mock
    private StreamObserver<GrpcChatMessageHardDeleteResponse> hardDeleteResponseObserver;

    @InjectMocks
    private GrpcChatMessageService service;

    @Test
    @DisplayName("save 성공 시 command로 변환하여 메시지를 저장하고 gRPC 응답을 반환한다")
    void save_shouldSaveMessageAndReturnGrpcResponse() {
        GrpcChatMessageRequest request = GrpcChatMessageRequest.newBuilder()
                .setClientMessageId("client-message-id")
                .setMessageId("message-id")
                .setRoomId("room-id")
                .setWriterId("writer-id")
                .setContent("hello")
                .build();

        ChatMessageSaveCommand command = new ChatMessageSaveCommand(
                "client-message-id",
                "message-id",
                "room-id",
                "writer-id",
                "hello"
        );

        ChatMessageSaveResult result = new ChatMessageSaveResult(
                "message-id",
                12345L
        );

        GrpcChatMessageResponse response = GrpcChatMessageResponse.newBuilder()
                .setSuccess(true)
                .setId("message-id")
                .setTs(12345L)
                .build();

        when(grpcChatMessageMapper.toSaveCommand(request)).thenReturn(command);
        when(chatMessageCommandUseCase.save(command)).thenReturn(result);
        when(grpcChatMessageMapper.toSaveResponse(result)).thenReturn(response);

        service.save(request, saveResponseObserver);

        verify(grpcChatMessageMapper).toSaveCommand(request);
        verify(chatMessageCommandUseCase).save(command);
        verify(grpcChatMessageMapper).toSaveResponse(result);
        verify(saveResponseObserver).onNext(response);
        verify(saveResponseObserver).onCompleted();
    }

    @Test
    @DisplayName("hardDelete 성공 시 messageId와 roomId로 메시지를 hard delete하고 gRPC 응답을 반환한다")
    void hardDelete_shouldDeleteMessageAndReturnGrpcResponse() {
        GrpcChatMessageHardDeleteRequest request = GrpcChatMessageHardDeleteRequest.newBuilder()
                .setMessageId("message-id")
                .setRoomId("room-id")
                .setReason("save failed after timeout")
                .build();

        GrpcChatMessageHardDeleteResponse response = GrpcChatMessageHardDeleteResponse.newBuilder()
                .setSuccess(true)
                .setMessageId("message-id")
                .setDeleted(true)
                .setAlreadyDeleted(false)
                .setNotFound(false)
                .build();

        when(grpcChatMessageMapper.toHardDeleteResponse("message-id")).thenReturn(response);

        service.hardDelete(request, hardDeleteResponseObserver);

        verify(chatMessageCommandUseCase).hardDelete("message-id", "room-id");
        verify(grpcChatMessageMapper).toHardDeleteResponse("message-id");
        verify(hardDeleteResponseObserver).onNext(response);
        verify(hardDeleteResponseObserver).onCompleted();
    }

    @Test
    @DisplayName("save 시작 전 요청이 취소된 상태면 ChatMessageGrpcCancelledException을 던진다")
    void save_shouldThrowCancelledException_whenContextAlreadyCancelled() {
        GrpcChatMessageRequest request = GrpcChatMessageRequest.newBuilder()
                .setClientMessageId("client-message-id")
                .setMessageId("message-id")
                .setRoomId("room-id")
                .setWriterId("writer-id")
                .setContent("hello")
                .build();

        Context.CancellableContext cancelledContext = Context.current().withCancellation();
        cancelledContext.cancel(new RuntimeException("cancelled"));

        Context previous = cancelledContext.attach();

        try {
            assertThatThrownBy(() -> service.save(request, saveResponseObserver))
                    .isInstanceOf(ChatMessageGrpcCancelledException.class)
                    .hasMessageContaining("client cancelled or deadline exceeded: before save");

            verify(grpcChatMessageMapper, never()).toSaveCommand(request);
            verify(chatMessageCommandUseCase, never()).save(org.mockito.ArgumentMatchers.any());
            verify(saveResponseObserver, never()).onNext(org.mockito.ArgumentMatchers.any());
            verify(saveResponseObserver, never()).onCompleted();
        } finally {
            cancelledContext.detach(previous);
        }
    }

    @Test
    @DisplayName("hardDelete 시작 전 요청이 취소된 상태면 ChatMessageGrpcCancelledException을 던진다")
    void hardDelete_shouldThrowCancelledException_whenContextAlreadyCancelled() {
        GrpcChatMessageHardDeleteRequest request = GrpcChatMessageHardDeleteRequest.newBuilder()
                .setMessageId("message-id")
                .setRoomId("room-id")
                .setReason("save failed after timeout")
                .build();

        Context.CancellableContext cancelledContext = Context.current().withCancellation();
        cancelledContext.cancel(new RuntimeException("cancelled"));

        Context previous = cancelledContext.attach();

        try {
            assertThatThrownBy(() -> service.hardDelete(request, hardDeleteResponseObserver))
                    .isInstanceOf(ChatMessageGrpcCancelledException.class)
                    .hasMessageContaining("client cancelled or deadline exceeded: before hardDelete");

            verify(chatMessageCommandUseCase, never()).hardDelete(
                    org.mockito.ArgumentMatchers.any(),
                    org.mockito.ArgumentMatchers.any()
            );
            verify(grpcChatMessageMapper, never()).toHardDeleteResponse(org.mockito.ArgumentMatchers.any());
            verify(hardDeleteResponseObserver, never()).onNext(org.mockito.ArgumentMatchers.any());
            verify(hardDeleteResponseObserver, never()).onCompleted();
        } finally {
            cancelledContext.detach(previous);
        }
    }

    @Test
    @DisplayName("save 저장 후 요청이 취소된 상태면 응답을 보내지 않고 ChatMessageGrpcCancelledException을 던진다")
    void save_shouldThrowCancelledException_whenContextCancelledAfterSave() {
        GrpcChatMessageRequest request = GrpcChatMessageRequest.newBuilder()
                .setClientMessageId("client-message-id")
                .setMessageId("message-id")
                .setRoomId("room-id")
                .setWriterId("writer-id")
                .setContent("hello")
                .build();

        ChatMessageSaveCommand command = new ChatMessageSaveCommand(
                "client-message-id",
                "message-id",
                "room-id",
                "writer-id",
                "hello"
        );

        ChatMessageSaveResult result = new ChatMessageSaveResult(
                "message-id",
                12345L
        );

        Context.CancellableContext cancellableContext = Context.current().withCancellation();
        Context previous = cancellableContext.attach();

        try {
            when(grpcChatMessageMapper.toSaveCommand(request)).thenReturn(command);
            when(chatMessageCommandUseCase.save(command)).thenAnswer(invocation -> {
                cancellableContext.cancel(new RuntimeException("cancelled after save"));
                return result;
            });

            assertThatThrownBy(() -> service.save(request, saveResponseObserver))
                    .isInstanceOf(ChatMessageGrpcCancelledException.class)
                    .hasMessageContaining("client cancelled or deadline exceeded: after save");

            verify(grpcChatMessageMapper).toSaveCommand(request);
            verify(chatMessageCommandUseCase).save(command);
            verify(grpcChatMessageMapper, never()).toSaveResponse(result);
            verify(saveResponseObserver, never()).onNext(org.mockito.ArgumentMatchers.any());
            verify(saveResponseObserver, never()).onCompleted();
        } finally {
            cancellableContext.detach(previous);
        }
    }

    @Test
    @DisplayName("hardDelete 삭제 후 요청이 취소된 상태면 응답을 보내지 않고 ChatMessageGrpcCancelledException을 던진다")
    void hardDelete_shouldThrowCancelledException_whenContextCancelledAfterHardDelete() {
        GrpcChatMessageHardDeleteRequest request = GrpcChatMessageHardDeleteRequest.newBuilder()
                .setMessageId("message-id")
                .setRoomId("room-id")
                .setReason("save failed after timeout")
                .build();

        Context.CancellableContext cancellableContext = Context.current().withCancellation();
        Context previous = cancellableContext.attach();

        try {
            org.mockito.Mockito.doAnswer(invocation -> {
                cancellableContext.cancel(new RuntimeException("cancelled after hardDelete"));
                return null;
            }).when(chatMessageCommandUseCase).hardDelete("message-id", "room-id");

            assertThatThrownBy(() -> service.hardDelete(request, hardDeleteResponseObserver))
                    .isInstanceOf(ChatMessageGrpcCancelledException.class)
                    .hasMessageContaining("client cancelled or deadline exceeded: after hardDelete");

            verify(chatMessageCommandUseCase).hardDelete("message-id", "room-id");
            verify(grpcChatMessageMapper, never()).toHardDeleteResponse("message-id");
            verify(hardDeleteResponseObserver, never()).onNext(org.mockito.ArgumentMatchers.any());
            verify(hardDeleteResponseObserver, never()).onCompleted();
        } finally {
            cancellableContext.detach(previous);
        }
    }
}
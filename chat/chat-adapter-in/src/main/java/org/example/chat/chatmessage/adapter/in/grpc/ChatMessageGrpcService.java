package org.example.chat.chatmessage.adapter.in.grpc;

import io.grpc.Context;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;
import org.example.chat.chatmessage.adapter.in.exception.ChatMessageGrpcCancelledException;
import org.example.chat.chatmessage.application.dto.ChatMessageSaveCommand;
import org.example.chat.chatmessage.application.dto.ChatMessageSaveResult;
import org.example.chat.chatmessage.application.service.ChatMessageCommandService;
import org.example.grpc.chatmessage.*;

@Slf4j
@GrpcService
@RequiredArgsConstructor
public class ChatMessageGrpcService extends ChatMessageServiceGrpc.ChatMessageServiceImplBase {

    private final ChatMessageCommandService chatMessageCommandService;

    public void save(ChatMessageGrpcRequest request, StreamObserver<ChatMessageGrpcResponse> responseObserver) {
        throwIfCancelled("before save");

        ChatMessageSaveResult result = chatMessageCommandService.save(toCommand(request));

        throwIfCancelled("after save");

        ChatMessageGrpcResponse response = ChatMessageGrpcResponse.newBuilder()
                .setSuccess(true)
                .setId(result.id())
                .setTs(result.ts())
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    public void hardDelete(ChatMessageHardDeleteGrpcRequest request, StreamObserver<ChatMessageHardDeleteGrpcResponse> responseObserver) {
        throwIfCancelled("before hardDelete");

        chatMessageCommandService.hardDelete(request.getMessageId(), request.getRoomId());

        throwIfCancelled("after hardDelete");

        ChatMessageHardDeleteGrpcResponse response = ChatMessageHardDeleteGrpcResponse.newBuilder()
                .setSuccess(true)
                .setMessageId(request.getMessageId())
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    private ChatMessageSaveCommand toCommand(ChatMessageGrpcRequest request) {
        return new ChatMessageSaveCommand(
                request.getMessageId(),
                request.getRoomId(),
                request.getWriterId(),
                request.getContent(),
                request.getClientMessageId()
        );
    }

    private void throwIfCancelled(String phase) {
        if (Context.current().isCancelled()) {
            log.warn("request cancelled: phase={}", phase);

            throw new ChatMessageGrpcCancelledException(
                    null,
                    "client cancelled or deadline exceeded: " + phase
            );
        }
    }
}
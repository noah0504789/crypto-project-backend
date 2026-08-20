package org.example.websocket.gateway.chatmessage.adapter.out.grpc;

import lombok.RequiredArgsConstructor;
import org.example.chat.chatmessage.client.ChatMessageClient;
import org.example.common.grpc.client.GrpcFutures;
import org.example.common.grpc.exception.GrpcExceptionTranslator;
import org.example.grpc.chatmessage.GrpcChatMessageHardDeleteRequest;
import org.example.grpc.chatmessage.GrpcChatMessageHardDeleteResponse;
import org.example.grpc.chatmessage.GrpcChatMessageRequest;
import org.example.grpc.chatmessage.GrpcChatMessageResponse;
import org.example.websocket.gateway.chatmessage.application.port.out.ChatMessageCommandPort;
import org.example.websocket.gateway.chatmessage.application.service.command.ChatMessageHardDeleteCommand;
import org.example.websocket.gateway.chatmessage.application.service.command.ChatMessageSendCommand;
import org.example.websocket.gateway.chatmessage.application.service.result.ChatMessageHardDeleteResult;
import org.example.websocket.gateway.chatmessage.application.service.result.ChatMessageSendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Component
@RequiredArgsConstructor
public class GrpcChatMessageCommandAdapter implements ChatMessageCommandPort {

    private final ChatMessageClient chatMessageClient;

    @Override
    public CompletableFuture<ChatMessageSendResult> save(ChatMessageSendCommand command) {
        return GrpcFutures.map(
                chatMessageClient.save(toGrpcRequest(command)),
                this::toResult,
                GrpcExceptionTranslator::translate
        );
    }

    @Override
    public CompletableFuture<ChatMessageHardDeleteResult> hardDelete(ChatMessageHardDeleteCommand command) {
        return GrpcFutures.map(
                chatMessageClient.hardDelete(toGrpcRequest(command)),
                this::toResult,
                GrpcExceptionTranslator::translate
        );
    }

    private GrpcChatMessageRequest toGrpcRequest(ChatMessageSendCommand command) {
        return GrpcChatMessageRequest.newBuilder()
                .setClientMessageId(command.clientMessageId())
                .setMessageId(command.messageId())
                .setRoomId(command.roomId())
                .setWriterId(command.writerId())
                .setContent(command.content())
                .build();
    }

    private GrpcChatMessageHardDeleteRequest toGrpcRequest(ChatMessageHardDeleteCommand command) {
        return GrpcChatMessageHardDeleteRequest.newBuilder()
                .setMessageId(command.messageId())
                .setRoomId(command.roomId())
                .setReason(command.reason())
                .build();
    }

    private ChatMessageSendResult toResult(GrpcChatMessageResponse response) {
        return new ChatMessageSendResult(
                response.getSuccess(),
                response.getId(),
                response.getTs()
        );
    }

    private ChatMessageHardDeleteResult toResult(GrpcChatMessageHardDeleteResponse response) {
        return new ChatMessageHardDeleteResult(
                response.getSuccess(),
                response.getMessageId(),
                response.getDeleted(),
                response.getAlreadyDeleted(),
                response.getNotFound()
        );
    }
}

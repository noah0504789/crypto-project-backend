package org.example.chat.chatmessage.adapter.in.grpc;

import org.example.chat.chatmessage.application.service.command.ChatMessageSaveCommand;
import org.example.chat.chatmessage.application.service.result.ChatMessageSaveResult;
import org.example.grpc.chatmessage.GrpcChatMessageHardDeleteResponse;
import org.example.grpc.chatmessage.GrpcChatMessageRequest;
import org.example.grpc.chatmessage.GrpcChatMessageResponse;
import org.springframework.stereotype.Component;

@Component
public class GrpcChatMessageMapper {

    public ChatMessageSaveCommand toSaveCommand(GrpcChatMessageRequest request) {
        return new ChatMessageSaveCommand(
                request.getMessageId(),
                request.getRoomId(),
                request.getWriterId(),
                request.getContent(),
                request.getClientMessageId()
        );
    }

    public GrpcChatMessageResponse toSaveResponse(ChatMessageSaveResult result) {
        return GrpcChatMessageResponse.newBuilder()
                .setSuccess(true)
                .setId(result.id())
                .setTs(result.ts())
                .build();
    }

    public GrpcChatMessageHardDeleteResponse toHardDeleteResponse(String messageId) {
        return GrpcChatMessageHardDeleteResponse.newBuilder()
                .setSuccess(true)
                .setMessageId(messageId)
                .build();
    }
}
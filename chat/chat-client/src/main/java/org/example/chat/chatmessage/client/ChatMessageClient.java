package org.example.chat.chatmessage.client;

import org.example.grpc.chatmessage.GrpcChatMessageRequest;
import org.example.grpc.chatmessage.GrpcChatMessageResponse;
import org.example.grpc.chatmessage.GrpcChatMessageHardDeleteRequest;
import org.example.grpc.chatmessage.GrpcChatMessageHardDeleteResponse;

import java.util.concurrent.CompletableFuture;

public interface ChatMessageClient {

    CompletableFuture<GrpcChatMessageResponse> save(GrpcChatMessageRequest request);

    CompletableFuture<GrpcChatMessageHardDeleteResponse> hardDelete(GrpcChatMessageHardDeleteRequest request);
}

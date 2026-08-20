package org.example.chat.chatmessage.client;

import io.grpc.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.example.common.grpc.client.GrpcFutures;
import org.example.grpc.chatmessage.GrpcChatMessageRequest;
import org.example.grpc.chatmessage.GrpcChatMessageResponse;
import org.example.grpc.chatmessage.GrpcChatMessageHardDeleteRequest;
import org.example.grpc.chatmessage.GrpcChatMessageHardDeleteResponse;
import org.example.grpc.chatmessage.ChatMessageServiceGrpc;
import org.example.chat.chatmessage.client.properties.GrpcChatMessageClientProperties;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class GrpcChatMessageClient implements ChatMessageClient {

    @GrpcClient("chat-client")
    private Channel channel;

    private final GrpcChatMessageClientProperties grpcChatMessageClientProperties;

    @Override
    public CompletableFuture<GrpcChatMessageResponse> save(GrpcChatMessageRequest request) {
        return GrpcFutures.toCompletableFuture(stub().save(request));
    }

    @Override
    public CompletableFuture<GrpcChatMessageHardDeleteResponse> hardDelete(GrpcChatMessageHardDeleteRequest request) {
        return GrpcFutures.toCompletableFuture(stub().hardDelete(request));
    }

    private ChatMessageServiceGrpc.ChatMessageServiceFutureStub stub() {
        return ChatMessageServiceGrpc.newFutureStub(channel)
                .withDeadlineAfter(grpcChatMessageClientProperties.deadlineMillis(), TimeUnit.MILLISECONDS);
    }
}

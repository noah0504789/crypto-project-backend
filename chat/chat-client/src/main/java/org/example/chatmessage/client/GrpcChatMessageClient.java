package org.example.chatmessage.client;

import io.grpc.Channel;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.example.grpc.chatmessage.ChatMessageGrpcRequest;
import org.example.grpc.chatmessage.ChatMessageGrpcResponse;
import org.example.grpc.chatmessage.ChatMessageHardDeleteGrpcRequest;
import org.example.grpc.chatmessage.ChatMessageHardDeleteGrpcResponse;
import org.example.grpc.chatmessage.ChatMessageServiceGrpc;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class GrpcChatMessageClient implements ChatMessageClient {

    @GrpcClient("chat-client")
    private Channel channel;

    @Override
    public void save(ChatMessageGrpcRequest request, StreamObserver<ChatMessageGrpcResponse> responseObserver) {
        stub().save(request, responseObserver);
    }

    @Override
    public void hardDelete(
            ChatMessageHardDeleteGrpcRequest request,
            StreamObserver<ChatMessageHardDeleteGrpcResponse> responseObserver
    ) {
        stub().hardDelete(request, responseObserver);
    }

    private ChatMessageServiceGrpc.ChatMessageServiceStub stub() {
        return ChatMessageServiceGrpc.newStub(channel).withDeadlineAfter(10000, TimeUnit.MILLISECONDS);
    }
}

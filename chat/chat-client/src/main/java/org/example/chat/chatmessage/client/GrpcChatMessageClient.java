package org.example.chat.chatmessage.client;

import io.grpc.Channel;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.example.grpc.chatmessage.GrpcChatMessageRequest;
import org.example.grpc.chatmessage.GrpcChatMessageResponse;
import org.example.grpc.chatmessage.GrpcChatMessageHardDeleteRequest;
import org.example.grpc.chatmessage.GrpcChatMessageHardDeleteResponse;
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
    public void save(GrpcChatMessageRequest request, StreamObserver<GrpcChatMessageResponse> responseObserver) {
        stub().save(request, responseObserver);
    }

    @Override
    public void hardDelete(GrpcChatMessageHardDeleteRequest request, StreamObserver<GrpcChatMessageHardDeleteResponse> responseObserver) {
        stub().hardDelete(request, responseObserver);
    }

    private ChatMessageServiceGrpc.ChatMessageServiceStub stub() {
        return ChatMessageServiceGrpc.newStub(channel).withDeadlineAfter(10000, TimeUnit.MILLISECONDS);
    }
}

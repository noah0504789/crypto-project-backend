package org.example.event.chatmessage;

import io.grpc.Channel;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.example.grpc.chatmessage.*;
import org.example.event.chatmessage.dto.ChatMessageRequest;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatMessageGrpcClient implements ChatMessageGatewayClient {

    @GrpcClient("chat-client")
    private Channel channel;

    @Override
    public void save(ChatMessageRequest request, String messageId, StreamObserver<ChatMessageGrpcResponse> responseObserver) {
        ChatMessageServiceGrpc.ChatMessageServiceStub asyncStub = ChatMessageServiceGrpc.newStub(channel)
                .withDeadlineAfter(10000, TimeUnit.MILLISECONDS);

        asyncStub.save(toGrpcRequest(request, messageId), responseObserver);
    }

    @Override
    public void hardDelete(String messageId, String roomId, StreamObserver<ChatMessageHardDeleteGrpcResponse> responseObserver) {
        ChatMessageServiceGrpc.ChatMessageServiceStub asyncStub = ChatMessageServiceGrpc.newStub(channel)
                .withDeadlineAfter(10000, TimeUnit.MILLISECONDS);

        ChatMessageHardDeleteGrpcRequest request = ChatMessageHardDeleteGrpcRequest.newBuilder()
                .setMessageId(messageId)
                .setRoomId(roomId)
                .build();

        asyncStub.hardDelete(request, responseObserver);
    }

    private static ChatMessageGrpcRequest toGrpcRequest(ChatMessageRequest request, String messageId) {
        return ChatMessageGrpcRequest.newBuilder()
                .setClientMessageId(request.clientMessageId())
                .setMessageId(messageId)
                .setRoomId(request.roomId())
                .setWriterId(request.writerId())
                .setContent(request.content())
                .build();
    }
}

package org.example.event.chatmessage;

import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import org.example.chatmessage.client.ChatMessageClient;
import org.example.event.chatmessage.dto.ChatMessageRequest;
import org.example.grpc.chatmessage.ChatMessageGrpcRequest;
import org.example.grpc.chatmessage.ChatMessageGrpcResponse;
import org.example.grpc.chatmessage.ChatMessageHardDeleteGrpcRequest;
import org.example.grpc.chatmessage.ChatMessageHardDeleteGrpcResponse;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ChatMessageClientAdapter implements ChatMessageGatewayClient {

    private final ChatMessageClient chatMessageClient;

    @Override
    public void save(ChatMessageRequest request, String messageId, StreamObserver<ChatMessageGrpcResponse> responseObserver) {
        chatMessageClient.save(toGrpcRequest(request, messageId), responseObserver);
    }

    @Override
    public void hardDelete(String messageId, String roomId, StreamObserver<ChatMessageHardDeleteGrpcResponse> responseObserver) {
        chatMessageClient.hardDelete(toGrpcRequest(messageId, roomId), responseObserver);
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

    private static ChatMessageHardDeleteGrpcRequest toGrpcRequest(String messageId, String roomId) {
        return ChatMessageHardDeleteGrpcRequest.newBuilder()
                .setMessageId(messageId)
                .setRoomId(roomId)
                .build();
    }
}

package org.example.websocket.gateway.chatmessage.application.port.out;

import io.grpc.stub.StreamObserver;
import org.example.websocket.gateway.stomp.dto.ChatMessageRequest;
import org.example.grpc.chatmessage.ChatMessageGrpcResponse;
import org.example.grpc.chatmessage.ChatMessageHardDeleteGrpcResponse;

public interface ChatMessageGatewayClient {
    void save(ChatMessageRequest request, String messageId, StreamObserver<ChatMessageGrpcResponse> responseObserver);

    void hardDelete(String messageId, String roomId, StreamObserver<ChatMessageHardDeleteGrpcResponse> responseObserver);
}

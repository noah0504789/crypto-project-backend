package org.example.chatmessage.client;

import io.grpc.stub.StreamObserver;
import org.example.grpc.chatmessage.ChatMessageGrpcRequest;
import org.example.grpc.chatmessage.ChatMessageGrpcResponse;
import org.example.grpc.chatmessage.ChatMessageHardDeleteGrpcRequest;
import org.example.grpc.chatmessage.ChatMessageHardDeleteGrpcResponse;

public interface ChatMessageClient {

    void save(ChatMessageGrpcRequest request, StreamObserver<ChatMessageGrpcResponse> responseObserver);

    void hardDelete(ChatMessageHardDeleteGrpcRequest request, StreamObserver<ChatMessageHardDeleteGrpcResponse> responseObserver);
}

package org.example.chat.chatmessage.client;

import io.grpc.stub.StreamObserver;
import org.example.grpc.chatmessage.GrpcChatMessageRequest;
import org.example.grpc.chatmessage.GrpcChatMessageResponse;
import org.example.grpc.chatmessage.GrpcChatMessageHardDeleteRequest;
import org.example.grpc.chatmessage.GrpcChatMessageHardDeleteResponse;

public interface ChatMessageClient {

    void save(GrpcChatMessageRequest request, StreamObserver<GrpcChatMessageResponse> responseObserver);

    void hardDelete(GrpcChatMessageHardDeleteRequest request, StreamObserver<GrpcChatMessageHardDeleteResponse> responseObserver);
}

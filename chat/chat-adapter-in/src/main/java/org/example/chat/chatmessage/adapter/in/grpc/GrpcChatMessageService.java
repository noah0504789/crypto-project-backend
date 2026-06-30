package org.example.chat.chatmessage.adapter.in.grpc;

import io.grpc.Context;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;
import org.example.chat.chatmessage.adapter.in.exception.ChatMessageGrpcCancelledException;
import org.example.chat.chatmessage.application.dto.ChatMessageSaveResult;
import org.example.chat.chatmessage.application.port.in.ChatMessageCommandUseCase;
import org.example.grpc.chatmessage.*;

@Slf4j
@GrpcService
@RequiredArgsConstructor
public class GrpcChatMessageService extends ChatMessageServiceGrpc.ChatMessageServiceImplBase {

    private final ChatMessageCommandUseCase chatMessageCommandUseCase;
    private final GrpcChatMessageMapper grpcChatMessageMapper;

    @Override
    public void save(GrpcChatMessageRequest request, StreamObserver<GrpcChatMessageResponse> responseObserver) {
        throwIfCancelled("before save");

        ChatMessageSaveResult result = chatMessageCommandUseCase.save(
                grpcChatMessageMapper.toSaveCommand(request)
        );

        throwIfCancelled("after save");

        responseObserver.onNext(grpcChatMessageMapper.toSaveResponse(result));
        responseObserver.onCompleted();
    }

    @Override
    public void hardDelete(GrpcChatMessageHardDeleteRequest request, StreamObserver<GrpcChatMessageHardDeleteResponse> responseObserver) {
        throwIfCancelled("before hardDelete");

        chatMessageCommandUseCase.hardDelete(request.getMessageId(), request.getRoomId());

        throwIfCancelled("after hardDelete");

        responseObserver.onNext(grpcChatMessageMapper.toHardDeleteResponse(request.getMessageId()));
        responseObserver.onCompleted();
    }

    private void throwIfCancelled(String phase) {
        if (Context.current().isCancelled()) {
            log.warn("request cancelled: phase={}", phase);

            throw new ChatMessageGrpcCancelledException(null, "client cancelled or deadline exceeded: " + phase);
        }
    }
}

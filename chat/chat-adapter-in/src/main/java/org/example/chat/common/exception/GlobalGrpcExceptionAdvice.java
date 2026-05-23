package org.example.chat.common.exception;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.advice.GrpcAdvice;
import net.devh.boot.grpc.server.advice.GrpcExceptionHandler;
import org.example.chatmessage.adapter.in.exception.ChatMessageGrpcCancelledException;
import org.example.chatmessage.adapter.in.exception.ChatMessageGrpcException;
import org.example.chatmessage.adapter.in.exception.ChatMessageResourceExhaustedException;
import org.example.chatmessage.application.service.ChatMessageCommandService;
import org.example.chatmessage.domain.model.ChatMessage;
import org.example.chatroom.domain.exception.ChatRoomNotFoundException;
import org.example.common.exception.BaseGrpcExceptionAdvice;

@Slf4j
@GrpcAdvice
@RequiredArgsConstructor
public class GlobalGrpcExceptionAdvice extends BaseGrpcExceptionAdvice {

    private final ChatMessageCommandService chatMessageCommandService;

    @GrpcExceptionHandler(ChatRoomNotFoundException.class)
    public StatusRuntimeException handleChatRoomNotFound(ChatRoomNotFoundException e) {
        log.warn("grpc NOT_FOUND (custom): {}", e.getMessage());
        return Status.NOT_FOUND
                .withDescription(e.getMessage())
                .asRuntimeException();
    }

    @GrpcExceptionHandler(ChatMessageGrpcCancelledException.class)
    public StatusRuntimeException handleCancelled(ChatMessageGrpcCancelledException e) {
        compensateIfNeeded(e);

        log.warn("grpc CANCELLED: {}", e.getMessage(), e);

        return Status.CANCELLED
                .withDescription(e.getMessage())
                .asRuntimeException();
    }

    @GrpcExceptionHandler(ChatMessagePersistException.class)
    public StatusRuntimeException handlePersist(ChatMessagePersistException e) {
        compensateIfNeeded(e.getRollbackTarget());

        log.error("grpc INTERNAL(core): {}", e.getMessage(), e);

        return Status.INTERNAL
                .withDescription("chat message core save failed")
                .asRuntimeException();
    }

    @GrpcExceptionHandler(ChatMessageCacheException.class)
    public StatusRuntimeException handleCache(ChatMessageCacheException e) {
        compensateIfNeeded(e.getRollbackTarget());

        log.error("grpc INTERNAL(cache): {}", e.getMessage(), e);

        return Status.INTERNAL
                .withDescription("chat message cache save failed")
                .asRuntimeException();
    }

    @GrpcExceptionHandler(ChatMessageResourceExhaustedException.class)
    public StatusRuntimeException handleResourceExhausted(ChatMessageResourceExhaustedException e) {
        log.warn("grpc RESOURCE_EXHAUSTED: {}", e.getMessage(), e);

        return Status.RESOURCE_EXHAUSTED
                .withDescription("chat message save rejected due to db resource exhaustion")
                .asRuntimeException();
    }

    private void compensateIfNeeded(ChatMessageGrpcException e) {
        if (!e.requiresRollback()) {
            return;
        }

        compensateIfNeeded(e.getRollbackTarget());
    }

    private void compensateIfNeeded(ChatMessage rollbackTarget) {
        if (rollbackTarget == null) {
            return;
        }

        try {
            chatMessageCommandService.hardDelete(
                    rollbackTarget.getId(),
                    rollbackTarget.getRoomId()
            );
        } catch (Exception compensationEx) {
            log.error(
                    "save compensation failed: chatMessageId={}, roomId={}, error={}",
                    rollbackTarget.getId(),
                    rollbackTarget.getRoomId(),
                    compensationEx.getMessage(),
                    compensationEx
            );
        }
    }
}
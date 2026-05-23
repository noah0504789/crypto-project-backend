package org.example.chatmessage.adapter.in.grpc;

import io.grpc.Context;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;
import org.example.chat.common.exception.ChatMessageCacheException;
import org.example.chat.common.exception.ChatMessagePersistException;
import org.example.chatmessage.adapter.in.exception.ChatMessageGrpcCancelledException;
import org.example.chatmessage.adapter.in.exception.ChatMessageGrpcException;
import org.example.chatmessage.adapter.in.exception.ChatMessageResourceExhaustedException;
import org.example.chatmessage.application.port.out.ChatMessageCachePort;
import org.example.chatmessage.application.service.ChatMessageCommandService;
import org.example.chatmessage.domain.model.ChatMessage;
import org.example.chatroom.application.service.ChatRoomQueryService;
import org.example.chatroom.domain.exception.ChatRoomNotFoundException;
import org.example.chatroom.domain.model.ChatRoom;
import org.example.chatroom.domain.model.ChatRoomCategory;
import org.example.grpc.chatmessage.*;

import java.util.Set;

@Slf4j
@GrpcService
@RequiredArgsConstructor
public class ChatMessageGrpcService extends ChatMessageServiceGrpc.ChatMessageServiceImplBase {

    private final ChatMessageCachePort cache;
    private final ChatRoomQueryService chatRoomQueryService;
    private final ChatMessageCommandService chatMessageCommandService;

    @Override
    public void save(ChatMessageGrpcRequest request, StreamObserver<ChatMessageGrpcResponse> responseObserver) {
        ChatMessage domain = toDomain(request);

        handleSave(domain, request.getClientMessageId());

        ChatMessageGrpcResponse response = ChatMessageGrpcResponse.newBuilder()
                .setSuccess(true)
                .setId(domain.getId())
                .setTs(domain.toEpochMillis())
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    @Override
    public void hardDelete(ChatMessageHardDeleteGrpcRequest request, StreamObserver<ChatMessageHardDeleteGrpcResponse> responseObserver) {
        handleHardDelete(request.getMessageId(), request.getRoomId());

        ChatMessageHardDeleteGrpcResponse response = ChatMessageHardDeleteGrpcResponse.newBuilder()
                .setSuccess(true)
                .setMessageId(request.getMessageId())
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    private void handleSave(ChatMessage domain, String clientMessageId) {
        ChatRoom chatRoom = loadChatRoomForSave(domain.getRoomId());
        Set<String> memberIds = chatRoom.getMemberIds();
        ChatRoomCategory category = chatRoom.getCategory();

        publishPersistEvent(domain, memberIds, clientMessageId);
        saveCache(domain, category, memberIds);
    }

    private void handleHardDelete(String messageId, String roomId) {
        chatMessageCommandService.hardDelete(messageId, roomId);
    }

    private ChatRoom loadChatRoomForSave(String roomId) {
        throwIfCancelled("before chat room lookup", null);
        ChatRoom chatRoom = chatRoomQueryService.findById(roomId);

        if (chatRoom == null) throw new ChatRoomNotFoundException(roomId);

        return chatRoom;
    }

    private void publishPersistEvent(ChatMessage domain, Set<String> memberIds, String clientMessageId) {
        try {
            throwIfCancelled("before persist", null);
            domain.persist(memberIds, clientMessageId);
        } catch (ChatMessageGrpcException e) {
            throw e;
        } catch (Exception e) {
            if (isConnectionAcquireFailure(e)) {
                throw new ChatMessageResourceExhaustedException("failed to acquire db connection before persist. chatMessageId=" + domain.getId(), e);
            }

            throw new ChatMessagePersistException(domain, "failed during persist/broadcast. chatMessageId=" + domain.getId(), e);
        }
    }

    private void saveCache(ChatMessage domain, ChatRoomCategory category, Set<String> memberIds) {
        try {
            throwIfCancelled("before cache save", domain);
            cache.save(domain, category, memberIds);
        } catch (ChatMessageGrpcException e) {
            throw e;
        } catch (Exception e) {
            throw new ChatMessageCacheException(domain, "failed during cache save. chatMessageId=" + domain.getId(), e);
        }
    }

    private void throwIfCancelled(String phase, ChatMessage rollbackTarget) {
        if (Context.current().isCancelled()) {
            log.warn("request cancelled: phase={}, chatMessageId={}", phase, rollbackTarget != null ? rollbackTarget.getId() : "N/A");

            throw new ChatMessageGrpcCancelledException(rollbackTarget, "client cancelled or deadline exceeded: " + phase);
        }
    }

    private ChatMessage toDomain(ChatMessageGrpcRequest request) {
        return ChatMessage.ofNewMessage(
                request.getMessageId(),
                request.getRoomId(),
                request.getWriterId(),
                request.getContent()
        );
    }

    private boolean isConnectionAcquireFailure(Throwable t) {
        Throwable cur = t;
        while (cur != null) {
            if (cur instanceof java.sql.SQLTransientConnectionException) return true;
            if (cur instanceof org.hibernate.exception.JDBCConnectionException) return true;

            String msg = cur.getMessage();
            if (msg != null && msg.contains("Connection is not available")) return true;

            cur = cur.getCause();
        }
        return false;
    }
}
package org.example.chatmessage.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.chat.common.exception.ChatMessageCacheException;
import org.example.chat.common.exception.ChatMessagePersistException;
import org.example.chatmessage.application.dto.ChatMessageSaveCommand;
import org.example.chatmessage.application.dto.ChatMessageSaveResult;
import org.example.chatmessage.application.port.out.ChatMessageCachePort;
import org.example.chatmessage.application.port.out.ChatMessagePersistencePort;
import org.example.chatmessage.domain.model.ChatMessage;
import org.example.chatroom.application.dto.ChatRoomMembershipScore;
import org.example.chatroom.application.port.out.ChatRoomPersistencePort;
import org.example.chatroom.domain.exception.ChatRoomNotFoundException;
import org.example.chatroom.domain.model.ChatRoom;
import org.example.chatroom.domain.model.ChatRoomCategory;
import org.hibernate.exception.JDBCConnectionException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.data.mongodb.MongoTransactionException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.SQLTransientConnectionException;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatMessageCommandService  {

    private final ChatMessagePersistencePort chatMessagePersistencePort;
    private final ChatMessageCachePort chatMessageCachePort;
    private final ChatRoomPersistencePort chatRoomPersistencePort;

    @Transactional("chatMongoTransactionManager")
    public ChatMessageSaveResult save(ChatMessageSaveCommand command) {
        ChatRoom chatRoom = chatRoomPersistencePort.findById(command.roomId())
                .orElseThrow(() -> new ChatRoomNotFoundException(command.roomId()));
        chatRoom.validateWritable(command.writerId());

        ChatMessage message = ChatMessage.ofNewMessage(
                command.messageId(),
                command.roomId(),
                command.writerId(),
                command.content()
        );

        Set<String> memberIds = chatRoom.getMemberIds();
        ChatRoomCategory category = chatRoom.getCategory();

        publishPersistEvent(message, memberIds, command.clientMessageId());
        saveCache(message, category, memberIds);

        return ChatMessageSaveResult.from(message);
    }

    @Retryable(
            retryFor = {
                    DataIntegrityViolationException.class,
                    TransientDataAccessException.class,
                    MongoTransactionException.class
            },
            maxAttempts = 3,
            backoff = @Backoff(delay = 100, multiplier = 2)
    )
    @Transactional("chatMongoTransactionManager")
    public void hardDelete(String messageId, String roomId) {
        boolean deleted = chatMessagePersistencePort.hardDelete(messageId);

        if (!deleted) {
            log.warn(
                    "[chat message] hardDelete skipped. mongo message not found. messageId={}, roomId={}",
                    messageId,
                    roomId
            );
            return;
        }

        chatRoomPersistencePort.decrementMsgCnt(roomId);

        Long fallbackMsgCreatedAt = chatMessagePersistencePort.findLatestExcluding(roomId, messageId)
                .map(ChatMessage::toEpochMillis)
                .orElse(0L);

        List<ChatRoomMembershipScore> chatRoomMembershipScores = chatRoomPersistencePort.refreshMembershipScores(roomId, fallbackMsgCreatedAt);

        hardDeleteCacheSafely(messageId, roomId, chatRoomMembershipScores);
    }

    @Recover
    public void recover(DataIntegrityViolationException e, String messageId, String roomId) {
        log.error(
                "[chat message] hardDelete retry exhausted. messageId={}, roomId={}, error={}",
                messageId,
                roomId,
                e.getMessage(),
                e
        );
    }

    @Recover
    public void recover(TransientDataAccessException e, String messageId, String roomId) {
        log.error(
                "[chat message] hardDelete retry exhausted. messageId={}, roomId={}, error={}",
                messageId,
                roomId,
                e.getMessage(),
                e
        );
    }

    @Recover
    public void recover(MongoTransactionException e, String messageId, String roomId) {
        log.error(
                "[chat message] hardDelete retry exhausted. messageId={}, roomId={}, error={}",
                messageId,
                roomId,
                e.getMessage(),
                e
        );
    }

    @Recover
    public void recover(RuntimeException e, String messageId, String roomId) {
        log.error(
                "[chat message] hardDelete retry exhausted. messageId={}, roomId={}, error={}",
                messageId,
                roomId,
                e.getMessage(),
                e
        );
    }

    private void publishPersistEvent(ChatMessage message, Set<String> memberIds, String clientMessageId) {
        try {
            message.persist(memberIds, clientMessageId);
        } catch (Exception e) {
            if (isConnectionAcquireFailure(e)) {
                throw new ChatMessagePersistException(
                        message,
                        "failed to acquire db connection before persist. chatMessageId=" + message.getId(),
                        e
                );
            }

            throw new ChatMessagePersistException(
                    message,
                    "failed during persist/broadcast. chatMessageId=" + message.getId(),
                    e
            );
        }
    }

    private void saveCache(ChatMessage message, ChatRoomCategory category, Set<String> memberIds) {
        try {
            chatMessageCachePort.save(message, category, memberIds);
        } catch (Exception e) {
            throw new ChatMessageCacheException(
                    message,
                    "failed during cache save. chatMessageId=" + message.getId(),
                    e
            );
        }
    }

    private void hardDeleteCacheSafely(String messageId, String roomId, List<ChatRoomMembershipScore> chatRoomMembershipScores) {
        try {
            chatMessageCachePort.hardDelete(messageId, roomId, chatRoomMembershipScores);
        } catch (Exception e) {
            log.warn(
                    "[redis] chatmessage hardDelete failed. messageId={}, roomId={}, error={}",
                    messageId,
                    roomId,
                    e.getMessage(),
                    e
            );
        }
    }

    private boolean isConnectionAcquireFailure(Throwable t) {
        Throwable cur = t;

        while (cur != null) {
            if (cur instanceof SQLTransientConnectionException) {
                return true;
            }

            if (cur instanceof JDBCConnectionException) {
                return true;
            }

            String msg = cur.getMessage();
            if (msg != null && msg.contains("Connection is not available")) {
                return true;
            }

            cur = cur.getCause();
        }

        return false;
    }
}
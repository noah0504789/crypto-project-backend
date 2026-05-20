package org.example.chatmessage.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.chatmessage.application.port.out.ChatMessageCachePort;
import org.example.chatmessage.application.port.out.ChatMessagePersistencePort;
import org.example.chatmessage.domain.model.ChatMessage;
import org.example.chatroom.adapter.dto.MembershipScore;
import org.example.chatroom.application.port.out.ChatRoomPersistencePort;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.data.mongodb.MongoTransactionException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatMessageCommandService {

    private final ChatMessagePersistencePort chatMessagePersistencePort;
    private final ChatRoomPersistencePort chatRoomPersistencePort;
    private final ChatMessageCachePort chatMessageCachePort;

    @Retryable(
            retryFor = {DataIntegrityViolationException.class, TransientDataAccessException.class, MongoTransactionException.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 100, multiplier = 2)
    )
    @Transactional("chatMongoTransactionManager")
    public void hardDelete(String messageId, String roomId) {
        boolean deleted = chatMessagePersistencePort.hardDelete(messageId);

        if (!deleted) {
            log.warn("[chat message] hardDelete skipped. mongo message not found. messageId={}, roomId={}", messageId, roomId);
            return;
        }

        chatRoomPersistencePort.decrementMsgCnt(roomId);

        Long fallbackMsgCreatedAt = chatMessagePersistencePort.findLatestExcluding(roomId, messageId)
                .map(ChatMessage::toEpochMillis)
                .orElse(0L);

        List<MembershipScore> membershipScores = chatRoomPersistencePort.refreshMembershipScores(roomId, fallbackMsgCreatedAt);

        hardDeleteCacheSafely(messageId, roomId, membershipScores);
    }

    @Recover
    public void recover(DataIntegrityViolationException e, String messageId, String roomId) {
        log.error("[chat message] hardDelete retry exhausted. messageId={}, roomId={}, error={}", messageId, roomId, e.getMessage(), e);
    }

    @Recover
    public void recover(TransientDataAccessException e, String messageId, String roomId) {
        log.error("[chat message] hardDelete retry exhausted. messageId={}, roomId={}, error={}", messageId, roomId, e.getMessage(), e);
    }

    @Recover
    public void recover(MongoTransactionException e, String messageId, String roomId) {
        log.error("[chat message] hardDelete retry exhausted. messageId={}, roomId={}, error={}", messageId, roomId, e.getMessage(), e);
    }

    @Recover
    public void recover(RuntimeException e, String messageId, String roomId) {
        log.error("[chat message] hardDelete retry exhausted. messageId={}, roomId={}, error={}", messageId, roomId, e.getMessage(), e);
    }

    private void hardDeleteCacheSafely(String messageId, String roomId, List<MembershipScore> membershipScores) {
        try {
            chatMessageCachePort.hardDelete(messageId, roomId, membershipScores);
        } catch (Exception e) {
            log.warn("[redis] chatmessage hardDelete failed. messageId={}, roomId={}, error={}", messageId, roomId, e.getMessage(), e);
        }
    }
}
package org.example.chat.chatmessage.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.chat.chatmessage.application.port.in.ChatMessageCommandUseCase;
import org.example.chat.chatmessage.application.exception.ChatMessageCacheException;
import org.example.chat.chatmessage.application.service.command.ChatMessageSaveCommand;
import org.example.chat.chatmessage.application.service.result.ChatMessageSaveResult;
import org.example.chat.chatmessage.application.port.out.ChatMessageCachePort;
import org.example.chat.chatmessage.application.port.out.ChatMessagePersistencePort;
import org.example.chat.chatmessage.domain.model.ChatMessage;
import org.example.chat.chatroom.application.service.result.ChatRoomMembershipScore;
import org.example.chat.chatroom.application.port.out.ChatRoomPersistencePort;
import org.example.chat.chatroom.domain.exception.ChatRoomNotFoundException;
import org.example.chat.chatroom.domain.model.ChatRoom;
import org.example.chat.chatroom.domain.model.ChatRoomCategory;
import org.example.chat.chatmessage.application.exception.ChatMessagePersistException;
import org.example.chat.exception.TemporaryChatPersistenceException;
import org.example.common.outbox.application.port.out.OutboxEventListPublishPort;
import org.example.common.outbox.exception.TemporaryOutboxPersistenceException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatMessageCommandService implements ChatMessageCommandUseCase {

    private final ChatMessagePersistencePort chatMessagePersistencePort;
    private final ChatMessageCachePort chatMessageCachePort;
    private final ChatRoomPersistencePort chatRoomPersistencePort;
    private final OutboxEventListPublishPort outboxEventListPublishPort;

    @Retryable(
            retryFor = TemporaryOutboxPersistenceException.class,
            maxAttempts = 3,
            backoff = @Backoff(delay = 100, multiplier = 2)
    )
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
        saveCacheSafely(message, category, memberIds);

        return ChatMessageSaveResult.from(message);
    }

    @Retryable(
            retryFor = TemporaryChatPersistenceException.class,
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
    public void recover(
            TemporaryChatPersistenceException e,
            String messageId,
            String roomId
    ) {
        log.error(
                "[chat message] hardDelete retry exhausted. messageId={}, roomId={}, error={}",
                messageId,
                roomId,
                e.getMessage(),
                e
        );
    }

    private void publishPersistEvent(
            ChatMessage message,
            Set<String> memberIds,
            String clientMessageId
    ) {
        try {
            message.registerPersistEvents(memberIds, clientMessageId);

            outboxEventListPublishPort.publish(message.pullEventList());
        } catch (TemporaryOutboxPersistenceException e) {
            throw e;
        } catch (Exception e) {
            throw new ChatMessagePersistException(
                    message,
                    "failed during chat message persist event publish. chatMessageId=" + message.getId(),
                    e
            );
        }
    }

    private void saveCacheSafely(
            ChatMessage message,
            ChatRoomCategory category,
            Set<String> memberIds
    ) {
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

    private void hardDeleteCacheSafely(
            String messageId,
            String roomId,
            List<ChatRoomMembershipScore> chatRoomMembershipScores
    ) {
        try {
            chatMessageCachePort.hardDelete(messageId, roomId, chatRoomMembershipScores);
        } catch (Exception e) {
            log.warn(
                    "[redis] chat message hardDelete failed. messageId={}, roomId={}, error={}",
                    messageId,
                    roomId,
                    e.getMessage(),
                    e
            );
        }
    }
}
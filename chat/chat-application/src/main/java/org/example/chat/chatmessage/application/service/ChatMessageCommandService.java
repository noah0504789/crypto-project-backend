package org.example.chat.chatmessage.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.chat.chatmessage.application.event.ChatMessageEventList;
import org.example.chat.chatmessage.application.event.ChatMessagePersistEvent;
import org.example.chat.chatmessage.application.mapper.ChatMessagePayloadMapper;
import org.example.chat.chatmessage.application.port.in.ChatMessageCommandUseCase;
import org.example.chat.chatmessage.application.service.command.ChatMessageSaveCommand;
import org.example.chat.chatmessage.application.service.result.ChatMessageSaveResult;
import org.example.chat.chatmessage.application.port.out.ChatMessageCachePort;
import org.example.chat.chatmessage.application.port.out.ChatMessagePersistencePort;
import org.example.chat.chatmessage.domain.model.ChatMessage;
import org.example.chat.chatroom.application.service.result.ChatRoomMembershipScore;
import org.example.chat.chatroom.application.port.out.ChatRoomPersistencePort;
import org.example.chat.chatroom.application.exception.ChatRoomNotFoundException;
import org.example.chat.chatroom.domain.model.ChatRoom;
import org.example.chat.chatmessage.application.exception.ChatMessagePersistException;
import org.example.chat.exception.TemporaryChatPersistenceException;
import org.example.common.clock.Clock;
import org.example.common.outbox.application.port.out.OutboxEventListPublishPort;
import org.example.common.outbox.exception.TemporaryOutboxPersistenceException;
import org.example.common.tx.AfterCommitExecutor;
import org.example.contract.chatmessage.ChatMessageBroadcastEvent;
import org.example.contract.chatmessage.ChatMessagePayload;
import org.example.contract.chatroom.MyChatRoomBadgeEvent;
import org.example.contract.chatroom.MyChatRoomBadgePayload;
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
    private final Clock clock;

    @Retryable(
            retryFor = TemporaryOutboxPersistenceException.class,
            maxAttempts = 3,
            backoff = @Backoff(delay = 100, multiplier = 2)
    )
    @Transactional("transactionManager")
    public ChatMessageSaveResult save(ChatMessageSaveCommand command) {
        ChatRoom chatRoom = chatRoomPersistencePort.findById(command.roomId())
                .orElseThrow(() -> new ChatRoomNotFoundException(command.roomId()));

        chatRoom.validateWritable(command.writerId());

        ChatMessage message = ChatMessage.create(
                command.messageId(),
                command.roomId(),
                command.writerId(),
                command.content(),
                clock.nowLocalDateTime()
        );

        Set<String> memberIds = chatRoom.getMemberIds();

        publishPersistEvent(message, memberIds, command.clientMessageId());
        saveCacheSafely(message, memberIds);

        return ChatMessageSaveResult.from(message);
    }

    @Retryable(
            retryFor = TemporaryChatPersistenceException.class,
            maxAttempts = 3,
            backoff = @Backoff(delay = 100, multiplier = 2)
    )
    @Transactional("chatMongoTransactionManager")
    public void hardDelete(String messageId, String roomId) {
        boolean deleted = chatMessagePersistencePort.hardDeleteById(messageId);

        if (!deleted) {
            log.warn(
                    "[chat message] hardDelete skipped. mongo message not found. messageId={}, roomId={}",
                    messageId,
                    roomId
            );
            return;
        }

        chatRoomPersistencePort.decrementMessageCount(roomId);

        Long fallbackMsgCreatedAt = chatMessagePersistencePort.findLatestMessageExcluding(roomId, messageId)
                .map(ChatMessage::createdAtEpochMillis)
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
            ChatMessagePayload chatMessagePayload = ChatMessagePayloadMapper.fromDomain(message);
            MyChatRoomBadgePayload myChatRoomBadgePayload = MyChatRoomBadgePayload.ofLastMessage(message.getRoomId(), memberIds, message.getContent(), message.createdAtInstant());

            ChatMessageEventList chatMessageEventList =
                    ChatMessageEventList.of(
                            new ChatMessagePersistEvent(chatMessagePayload, memberIds),
                            new ChatMessageBroadcastEvent(chatMessagePayload, memberIds, clientMessageId),
                            new MyChatRoomBadgeEvent(myChatRoomBadgePayload)
                    );

            outboxEventListPublishPort.publish(chatMessageEventList);
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
            Set<String> memberIds
    ) {
        // outbox(영속 이벤트)가 커밋된 뒤에만 캐시에 반영한다. 커밋 전 캐싱 시 롤백되면 Mongo엔 없는데
        // 캐시엔 있는 유령 메시지가 생긴다. 커밋 후 캐시 실패는 로그만 남기고 조회 repair 가 흡수한다.
        AfterCommitExecutor.run(() -> {
            try {
                chatMessageCachePort.save(message, memberIds);
            } catch (Exception e) {
                log.warn(
                        "[redis] chat message cache save failed after commit (repair will cover). chatMessageId={}, roomId={}",
                        message.getId(),
                        message.getRoomId(),
                        e
                );
            }
        });
    }


    private void hardDeleteCacheSafely(
            String messageId,
            String roomId,
            List<ChatRoomMembershipScore> chatRoomMembershipScores
    ) {
        // Mongo 삭제가 커밋된 뒤에만 캐시에서 제거한다. 커밋 전 제거 시 롤백되면 Mongo엔 남았는데 캐시엔 없어
        // 조회 repair 로 되살아나는 불일치가 생긴다. 커밋 후 제거 실패는 로그만 남기고 repair/TTL 이 흡수한다.
        AfterCommitExecutor.run(() -> {
            try {
                chatMessageCachePort.hardDelete(messageId, roomId, chatRoomMembershipScores);
            } catch (Exception e) {
                log.warn(
                        "[redis] chat message hardDelete failed after commit. messageId={}, roomId={}, error={}",
                        messageId,
                        roomId,
                        e.getMessage(),
                        e
                );
            }
        });
    }
}

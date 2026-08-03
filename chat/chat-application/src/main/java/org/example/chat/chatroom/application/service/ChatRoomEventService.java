package org.example.chat.chatroom.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.chat.chatroom.application.event.dlq.*;
import org.example.chat.chatroom.application.event.payload.ChatRoomPersistPayload;
import org.example.chat.chatroom.application.event.payload.ChatRoomUpdatedPayload;
import org.example.chat.chatroom.application.mapper.ChatRoomPayloadMapper;
import org.example.chat.chatroom.application.port.out.ChatRoomCachePort;
import org.example.chat.chatroom.application.port.out.ChatRoomPersistencePort;
import org.example.chat.chatroom.application.event.ChatRoomActiveEvent;
import org.example.chat.chatroom.application.event.ChatRoomCacheActivityInvalidateEvent;
import org.example.chat.chatroom.application.event.ChatRoomCacheDeleteEvent;
import org.example.chat.chatroom.application.event.ChatRoomCacheInfoInvalidateEvent;
import org.example.chat.chatroom.application.event.ChatRoomCacheSaveEvent;
import org.example.chat.chatroom.application.event.ChatRoomCacheUpdateEvent;
import org.example.chat.chatroom.application.event.ChatRoomDeletedEvent;
import org.example.chat.chatroom.application.event.ChatRoomJoinedEvent;
import org.example.chat.chatroom.application.event.ChatRoomLeavedEvent;
import org.example.chat.chatroom.application.event.ChatRoomPersistedEvent;
import org.example.chat.chatroom.application.event.ChatRoomUpdatedEvent;
import org.example.chat.chatroom.domain.model.ChatRoom;
import org.example.chat.chatroom.application.port.in.ChatRoomEventHandler;
import org.example.chat.chatroom.domain.model.ChatRoomCategory;
import org.example.chat.exception.TemporaryChatCacheException;
import org.example.chat.exception.TemporaryChatPersistenceException;
import org.example.common.dlq.application.port.out.DlqEventListPublishPort;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Set;

@Slf4j
@Retryable(
        retryFor = {
                TemporaryChatPersistenceException.class,
                TemporaryChatCacheException.class
        },
        maxAttempts = 3,
        backoff = @Backoff(delay = 100, multiplier = 2)
)
@Service
@RequiredArgsConstructor
public class ChatRoomEventService implements ChatRoomEventHandler {

    private final ChatRoomPersistencePort persistence;
    private final ChatRoomCachePort cache;
    private final DlqEventListPublishPort dlqEventListPublishPort;

    public void handle(ChatRoomPersistedEvent event, String txId) {
        ChatRoomPersistPayload payload = event.getPayload();
        ChatRoom domain = ChatRoomPayloadMapper.toDomain(payload);

        persistence.save(domain);
    }

    public void handle(ChatRoomUpdatedEvent event, String txId) {
        persistence.updateRoomAndReturn(
                event.getId(),
                event.getUpdated().toUpdateMap()
        );
    }

    public void handle(ChatRoomJoinedEvent event, String txId) {
        persistence.joinMembership(
                event.getId(),
                event.getMemberId()
        );
    }

    @Transactional("chatMongoTransactionManager")
    public void handle(ChatRoomLeavedEvent event, String txId) {
        persistence.leaveMembership(
                event.getId(),
                event.getMemberId()
        );
    }

    @Transactional("chatMongoTransactionManager")
    public void handle(ChatRoomDeletedEvent event, String txId) {
        persistence.deleteById(event.getId());
    }

    public void handle(ChatRoomActiveEvent event, String txId) {
        persistence.activateMembership(
                event.getId(),
                event.getMemberId(),
                event.getLastMsgSeq(),
                event.getLastMsgMs()
        );
    }

    @Transactional(
            transactionManager = "chatMongoTransactionManager",
            readOnly = true
    )
    public void handle(ChatRoomCacheSaveEvent event, String txId) {
        String id = event.getId();

        persistence.findByIdWithLatestMessage(id)
                .ifPresent(cache::warmUp);
    }

    @Transactional(
            transactionManager = "chatMongoTransactionManager",
            readOnly = true
    )
    public void handle(ChatRoomCacheUpdateEvent event, String txId) {
        String id = event.getId();
        String oldTitle = event.getOldTitle();

        persistence.findByIdWithLatestMessage(id)
                .ifPresent(chatRoom -> cache.recoverRoomUpdate(chatRoom, oldTitle));
    }

    public void handle(ChatRoomCacheDeleteEvent event, String txId) {
        cache.deleteRoom(
                event.getId(),
                event.getCategory(),
                event.getTitle(),
                event.getMemberids()
        );
    }

    public void handle(ChatRoomCacheActivityInvalidateEvent event, String txId) {
        cache.invalidateMembershipActivity(
                event.getId(),
                event.getMemberId()
        );
    }

    public void handle(ChatRoomCacheInfoInvalidateEvent event, String txId) {
        cache.invalidateRoomInfo(event.getId());
    }

    @Recover
    public void recover(
            TemporaryChatPersistenceException e,
            ChatRoomPersistedEvent event,
            String txId
    ) {
        log.error(
                "[dlq] chatroom persist retry exhausted. txId={}",
                txId,
                e
        );

        ChatRoomPersistPayload payload = event.getPayload();
        String errorMessage = e.getMessage();

        runRecover(
                "chatroom persist recover",
                txId,
                e,
                () -> publishDlqEvent(
                        ChatRoomDlqEventList.of(new ChatRoomPersistedDlqEvent(payload, errorMessage))
                ),
                payload
        );
    }

    @Recover
    public void recover(
            TemporaryChatPersistenceException e,
            ChatRoomUpdatedEvent event,
            String txId
    ) {
        String id = event.getId();
        ChatRoomUpdatedPayload updated = event.getUpdated();
        String errorMessage = e.getMessage();

        log.error(
                "[dlq] chatroom update retry exhausted. roomId={}, txId={}",
                id,
                txId,
                errorMessage,
                e
        );

        runRecover(
                "chatroom update recover",
                txId,
                e,
                () -> publishDlqEvent(
                        ChatRoomDlqEventList.of(new ChatRoomUpdatedDlqEvent(id, updated, errorMessage))
                ),
                id,
                updated
        );
    }

    @Recover
    public void recover(
            TemporaryChatPersistenceException e,
            ChatRoomJoinedEvent event,
            String txId
    ) {
        String id = event.getId();
        String memberId = event.getMemberId();
        String errorMessage = e.getMessage();

        log.error(
                "[dlq] chatroom join retry exhausted. roomId={}, memberId={}, txId={}",
                id,
                memberId,
                txId,
                errorMessage,
                e
        );

        runRecover(
                "chatroom join recover",
                txId,
                e,
                () -> publishDlqEvent(
                        ChatRoomDlqEventList.of(new ChatRoomJoinedDlqEvent(id, memberId, errorMessage))
                ),
                id,
                memberId
        );
    }

    @Recover
    public void recover(
            TemporaryChatPersistenceException e,
            ChatRoomLeavedEvent event,
            String txId
    ) {
        String id = event.getId();
        String memberId = event.getMemberId();
        String errorMessage = e.getMessage();

        log.error(
                "[dlq] chatroom leave retry exhausted. roomId={}, memberId={}, txId={}",
                id,
                memberId,
                txId,
                errorMessage,
                e
        );

        runRecover(
                "chatroom leave recover",
                txId,
                e,
                () -> publishDlqEvent(
                        ChatRoomDlqEventList.of(new ChatRoomLeavedDlqEvent(id, memberId, errorMessage))
                ),
                id,
                memberId
        );
    }

    @Recover
    public void recover(
            TemporaryChatPersistenceException e,
            ChatRoomDeletedEvent event,
            String txId
    ) {
        String id = event.getId();
        ChatRoomCategory category = event.getCategory();
        String errorMessage = e.getMessage();

        log.error(
                "[dlq] chatroom delete retry exhausted. roomId={}, txId={}",
                id,
                txId,
                errorMessage,
                e
        );

        runRecover(
                "chatroom delete recover",
                txId,
                e,
                () -> publishDlqEvent(
                        ChatRoomDlqEventList.of(new ChatRoomDeletedDlqEvent(id, category, errorMessage))
                ),
                id,
                category
        );
    }

    @Recover
    public void recover(
            TemporaryChatPersistenceException e,
            ChatRoomActiveEvent event,
            String txId
    ) {
        String id = event.getId();
        String memberId = event.getMemberId();
        Long lastMsgSeq = event.getLastMsgSeq();
        Long lastMsgMs = event.getLastMsgMs();
        String errorMessage = e.getMessage();

        log.error(
                "[dlq] chatroom active retry exhausted. roomId={}, memberId={}, txId={}",
                id,
                memberId,
                txId,
                errorMessage,
                e
        );

        runRecover(
                "chatroom active recover",
                txId,
                e,
                () -> publishDlqEvent(
                        ChatRoomDlqEventList.of(new ChatRoomActiveDlqEvent(id, memberId, lastMsgSeq, lastMsgMs, errorMessage))
                ),
                id,
                memberId,
                lastMsgSeq,
                lastMsgMs
        );
    }

    @Recover
    public void recover(
            TemporaryChatCacheException e,
            ChatRoomCacheSaveEvent event,
            String txId
    ) {
        String id = event.getId();
        String errorMessage = e.getMessage();

        log.error(
                "[dlq] chatroom cache save retry exhausted. roomId={}, txId={}",
                id,
                txId,
                errorMessage,
                e
        );

        runRecover(
                "chatroom cache save recover",
                txId,
                e,
                () -> publishDlqEvent(
                        ChatRoomDlqEventList.of(new ChatRoomCacheSaveDlqEvent(id, errorMessage))
                ),
                id
        );
    }

    @Recover
    public void recover(
            TemporaryChatCacheException e,
            ChatRoomCacheUpdateEvent event,
            String txId
    ) {
        String id = event.getId();
        String oldTitle = event.getOldTitle();
        String errorMessage = e.getMessage();

        log.error(
                "[dlq] chatroom cache update retry exhausted. roomId={}, txId={}",
                id,
                txId,
                errorMessage,
                e
        );

        runRecover(
                "chatroom cache update recover",
                txId,
                e,
                () -> publishDlqEvent(
                        ChatRoomDlqEventList.of(new ChatRoomCacheUpdateDlqEvent(id, oldTitle, errorMessage))
                ),
                id,
                oldTitle
        );
    }

    @Recover
    public void recover(
            TemporaryChatCacheException e,
            ChatRoomCacheDeleteEvent event,
            String txId
    ) {
        String id = event.getId();
        ChatRoomCategory category = event.getCategory();
        String title = event.getTitle();
        Set<String> memberIds = event.getMemberids();
        String errorMessage = e.getMessage();

        log.error(
                "[dlq] chatroom cache delete retry exhausted. roomId={}, txId={}",
                id,
                txId,
                errorMessage,
                e
        );

        runRecover(
                "chatroom cache delete recover",
                txId,
                e,
                () -> publishDlqEvent(
                        ChatRoomDlqEventList.of(new ChatRoomCacheDeleteDlqEvent(id, category, title, memberIds, errorMessage))
                ),
                id,
                category,
                title,
                memberIds
        );
    }

    @Recover
    public void recover(
            TemporaryChatCacheException e,
            ChatRoomCacheActivityInvalidateEvent event,
            String txId
    ) {
        String id = event.getId();
        String memberId = event.getMemberId();
        String errorMessage = e.getMessage();

        log.error(
                "[dlq] chatroom cache invalidate activity retry exhausted. roomId={}, memberId={}, txId={}",
                id,
                memberId,
                txId,
                errorMessage,
                e
        );

        runRecover(
                "chatroom cache invalidate activity recover",
                txId,
                e,
                () -> publishDlqEvent(
                        ChatRoomDlqEventList.of(new ChatRoomCacheActivityInvalidateDlqEvent(
                                id,
                                memberId,
                                errorMessage
                        ))
                ),
                id,
                memberId
        );
    }

    @Recover
    public void recover(
            TemporaryChatCacheException e,
            ChatRoomCacheInfoInvalidateEvent event,
            String txId
    ) {
        String id = event.getId();
        String errorMessage = e.getMessage();

        log.error(
                "[dlq] chatroom cache invalidate info retry exhausted. roomId={}, txId={}",
                id,
                txId,
                errorMessage,
                e
        );

        runRecover(
                "chatroom cache invalidate info recover",
                txId,
                e,
                () -> publishDlqEvent(
                        ChatRoomDlqEventList.of(new ChatRoomCacheInfoInvalidateDlqEvent(id, errorMessage))
                ),
                id
        );
    }

    private void publishDlqEvent(ChatRoomDlqEventList eventList) {
        dlqEventListPublishPort.publish(eventList);
    }

    private void runRecover(
            String context,
            String txId,
            RuntimeException original,
            Runnable recoverAction,
            Object... details
    ) {
        try {
            recoverAction.run();
        } catch (Exception recoverEx) {
            log.error(
                    "[recover-fallback] {} failed. txId={}, originalError={}, recoverError={}, details={}",
                    context,
                    txId,
                    original.getMessage(),
                    recoverEx.getMessage(),
                    Arrays.toString(details),
                    recoverEx
            );
        }
    }
}
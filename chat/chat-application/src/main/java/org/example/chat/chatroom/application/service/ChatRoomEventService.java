package org.example.chat.chatroom.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.chat.chatroom.application.port.out.ChatRoomCachePort;
import org.example.chat.chatroom.application.port.out.ChatRoomPersistencePort;
import org.example.chat.chatroom.domain.event.ChatRoomActiveEvent;
import org.example.chat.chatroom.domain.event.ChatRoomCacheActivityInvalidateEvent;
import org.example.chat.chatroom.domain.event.ChatRoomCacheDeleteEvent;
import org.example.chat.chatroom.domain.event.ChatRoomCacheInfoInvalidateEvent;
import org.example.chat.chatroom.domain.event.ChatRoomCacheSaveEvent;
import org.example.chat.chatroom.domain.event.ChatRoomCacheUpdateEvent;
import org.example.chat.chatroom.domain.event.ChatRoomDeletedEvent;
import org.example.chat.chatroom.domain.event.ChatRoomJoinedEvent;
import org.example.chat.chatroom.domain.event.ChatRoomLeavedEvent;
import org.example.chat.chatroom.domain.event.ChatRoomPersistedEvent;
import org.example.chat.chatroom.domain.event.ChatRoomUpdatedEvent;
import org.example.chat.chatroom.domain.model.ChatRoom;
import org.example.chat.chatroom.domain.event.handler.ChatRoomEventHandler;
import org.example.chat.exception.TemporaryChatCacheException;
import org.example.chat.exception.TemporaryChatPersistenceException;
import org.example.common.dlq.application.port.out.DlqEventListPublishPort;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;

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

    @Transactional("chatMongoTransactionManager")
    public void handle(ChatRoomPersistedEvent event, String txId) {
        ChatRoom domain = ChatRoom.fromPayload(event.getPayload());

        persistence.save(domain);
    }

    @Transactional("chatMongoTransactionManager")
    public void handle(ChatRoomUpdatedEvent event, String txId) {
        persistence.updateAndReturn(event.getId(), event.getUpdated().toUpdateMap());
    }

    @Transactional("chatMongoTransactionManager")
    public void handle(ChatRoomJoinedEvent event, String txId) {
        persistence.join(event.getId(), event.getMemberId());
    }

    @Transactional("chatMongoTransactionManager")
    public void handle(ChatRoomLeavedEvent event, String txId) {
        persistence.leave(event.getId(), event.getMemberId());
    }

    @Transactional("chatMongoTransactionManager")
    public void handle(ChatRoomDeletedEvent event, String txId) {
        persistence.deleteById(event.getId());
    }

    @Transactional("chatMongoTransactionManager")
    public void handle(ChatRoomActiveEvent event, String txId) {
        persistence.active(event.getId(), event.getMemberId(), event.getLastMsgSeq(), event.getLastMsgMs());
    }

    @Transactional(transactionManager = "chatMongoTransactionManager", readOnly = true)
    public void handle(ChatRoomCacheSaveEvent event, String txId) {
        String id = event.getId();

        persistence.findByIdWithLatest(id)
                .ifPresent(cache::warmUp);
    }

    @Transactional(transactionManager = "chatMongoTransactionManager", readOnly = true)
    public void handle(ChatRoomCacheUpdateEvent event, String txId) {
        String id = event.getId();
        String oldTitle = event.getOldTitle();

        persistence.findByIdWithLatest(id)
                .ifPresent(chatRoom -> cache.recoverUpdate(chatRoom, oldTitle));
    }

    public void handle(ChatRoomCacheDeleteEvent event, String txId) {
        cache.delete(event.getId(), event.getCategory(), event.getTitle(), event.getMemberids());
    }

    public void handle(ChatRoomCacheActivityInvalidateEvent event, String txId) {
        cache.invalidateActivity(event.getId(), event.getMemberId());
    }

    public void handle(ChatRoomCacheInfoInvalidateEvent event, String txId) {
        cache.invalidateInfo(event.getId());
    }

    @Recover
    public void recover(
            TemporaryChatPersistenceException e,
            ChatRoomPersistedEvent event,
            String txId
    ) {
        log.error(
                "❌ chatroom persist retry exhausted. txId={}, error={}",
                txId,
                e.getMessage(),
                e
        );

        ChatRoom domain = ChatRoom.fromPayload(event.getPayload());

        runRecover(
                "chatroom persist recover",
                txId,
                e,
                () -> publishDlqEvent(domain, () -> domain.recoverPersist(e.getMessage())),
                event.getPayload()
        );
    }

    @Recover
    public void recover(
            TemporaryChatPersistenceException e,
            ChatRoomUpdatedEvent event,
            String txId
    ) {
        log.error(
                "❌ chatroom update retry exhausted. roomId={}, txId={}, error={}",
                event.getId(),
                txId,
                e.getMessage(),
                e
        );

        ChatRoom domain = ChatRoom.ofId(event.getId());

        runRecover(
                "chatroom update recover",
                txId,
                e,
                () -> publishDlqEvent(domain, () -> domain.recoverUpdate(event, e.getMessage())),
                event.getId(),
                event.getUpdated()
        );
    }

    @Recover
    public void recover(
            TemporaryChatPersistenceException e,
            ChatRoomJoinedEvent event,
            String txId
    ) {
        log.error(
                "❌ chatroom join retry exhausted. roomId={}, memberId={}, txId={}, error={}",
                event.getId(),
                event.getMemberId(),
                txId,
                e.getMessage(),
                e
        );

        ChatRoom domain = ChatRoom.ofId(event.getId());

        runRecover(
                "chatroom join recover",
                txId,
                e,
                () -> publishDlqEvent(domain, () -> domain.recoverJoin(event, e.getMessage())),
                event.getId(),
                event.getMemberId()
        );
    }

    @Recover
    public void recover(
            TemporaryChatPersistenceException e,
            ChatRoomLeavedEvent event,
            String txId
    ) {
        log.error(
                "❌ chatroom leave retry exhausted. roomId={}, memberId={}, txId={}, error={}",
                event.getId(),
                event.getMemberId(),
                txId,
                e.getMessage(),
                e
        );

        ChatRoom domain = ChatRoom.ofId(event.getId());

        runRecover(
                "chatroom leave recover",
                txId,
                e,
                () -> publishDlqEvent(domain, () -> domain.recoverLeave(event, e.getMessage())),
                event.getId(),
                event.getMemberId()
        );
    }

    @Recover
    public void recover(
            TemporaryChatPersistenceException e,
            ChatRoomDeletedEvent event,
            String txId
    ) {
        log.error(
                "❌ chatroom delete retry exhausted. roomId={}, txId={}, error={}",
                event.getId(),
                txId,
                e.getMessage(),
                e
        );

        ChatRoom domain = ChatRoom.ofIdAndCategory(event.getId(), event.getCategory());

        runRecover(
                "chatroom delete recover",
                txId,
                e,
                () -> publishDlqEvent(domain, () -> domain.recoverDelete(e.getMessage())),
                event.getId(),
                event.getCategory()
        );
    }

    @Recover
    public void recover(
            TemporaryChatPersistenceException e,
            ChatRoomActiveEvent event,
            String txId
    ) {
        log.error(
                "❌ chatroom active retry exhausted. roomId={}, memberId={}, txId={}, error={}",
                event.getId(),
                event.getMemberId(),
                txId,
                e.getMessage(),
                e
        );

        ChatRoom domain = ChatRoom.ofId(event.getId());

        runRecover(
                "chatroom active recover",
                txId,
                e,
                () -> publishDlqEvent(domain, () -> domain.recoverActive(event, e.getMessage())),
                event.getId(),
                event.getMemberId(),
                event.getLastMsgSeq(),
                event.getLastMsgMs()
        );
    }

    @Recover
    public void recover(
            TemporaryChatCacheException e,
            ChatRoomCacheSaveEvent event,
            String txId
    ) {
        log.error(
                "❌ chatroom cache save retry exhausted. roomId={}, txId={}, error={}",
                event.getId(),
                txId,
                e.getMessage(),
                e
        );

        ChatRoom domain = ChatRoom.ofId(event.getId());

        runRecover(
                "chatroom cache save recover",
                txId,
                e,
                () -> publishDlqEvent(domain, () -> domain.recoverCacheSave(e.getMessage())),
                event.getId()
        );
    }

    @Recover
    public void recover(
            TemporaryChatCacheException e,
            ChatRoomCacheUpdateEvent event,
            String txId
    ) {
        log.error(
                "❌ chatroom cache update retry exhausted. roomId={}, txId={}, error={}",
                event.getId(),
                txId,
                e.getMessage(),
                e
        );

        ChatRoom domain = ChatRoom.ofId(event.getId());

        runRecover(
                "chatroom cache update recover",
                txId,
                e,
                () -> publishDlqEvent(domain, () -> domain.recoverCacheUpdate(event, e.getMessage())),
                event.getId()
        );
    }

    @Recover
    public void recover(
            TemporaryChatCacheException e,
            ChatRoomCacheDeleteEvent event,
            String txId
    ) {
        log.error(
                "❌ chatroom cache delete retry exhausted. roomId={}, txId={}, error={}",
                event.getId(),
                txId,
                e.getMessage(),
                e
        );

        ChatRoom domain = ChatRoom.ofId(event.getId());

        runRecover(
                "chatroom cache delete recover",
                txId,
                e,
                () -> publishDlqEvent(domain, () -> domain.recoverCacheDelete(event, e.getMessage())),
                event.getId()
        );
    }

    @Recover
    public void recover(
            TemporaryChatCacheException e,
            ChatRoomCacheActivityInvalidateEvent event,
            String txId
    ) {
        log.error(
                "❌ chatroom cache invalidate activity retry exhausted. roomId={}, memberId={}, txId={}, error={}",
                event.getId(),
                event.getMemberId(),
                txId,
                e.getMessage(),
                e
        );

        ChatRoom domain = ChatRoom.ofId(event.getId());

        runRecover(
                "chatroom cache invalidate activity recover",
                txId,
                e,
                () -> publishDlqEvent(domain, () -> domain.recoverCacheInvalidateActivity(event, e.getMessage())),
                event.getId(),
                event.getMemberId()
        );
    }

    @Recover
    public void recover(
            TemporaryChatCacheException e,
            ChatRoomCacheInfoInvalidateEvent event,
            String txId
    ) {
        log.error(
                "❌ chatroom cache invalidate info retry exhausted. roomId={}, txId={}, error={}",
                event.getId(),
                txId,
                e.getMessage(),
                e
        );

        ChatRoom domain = ChatRoom.ofId(event.getId());

        runRecover(
                "chatroom cache invalidate info recover",
                txId,
                e,
                () -> publishDlqEvent(domain, () -> domain.recoverCacheInvalidateInfo(event, e.getMessage())),
                event.getId()
        );
    }

    private void publishDlqEvent(ChatRoom domain, Runnable registerAction) {
        registerAction.run();

        dlqEventListPublishPort.publish(domain.pullDlqEventList());
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
                    "[RECOVER-FALLBACK] {} failed. txId={}, originalError={}, recoverError={}, details={}",
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
package org.example.chatroom.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.chatroom.domain.event.payload.ChatRoomPayload;
import org.example.chatroom.application.port.out.ChatRoomCachePort;
import org.example.chatroom.application.port.out.ChatRoomPersistencePort;
import org.example.chatroom.domain.event.*;
import org.example.chatroom.domain.model.ChatRoom;
import org.example.chatroom.domain.port.ChatRoomEventHandler;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatRoomEventService implements ChatRoomEventHandler {

    private final ChatRoomPersistencePort persistence;
    private final ChatRoomCachePort cache;

    @Retryable(
            value = RuntimeException.class, // TODO: 구체 클래스
            maxAttempts = 3,
            backoff = @Backoff(delay = 100, multiplier = 2)
    )
    @Transactional("chatMongoTransactionManager")
    public void handle(ChatRoomPersistedEvent event, String txId) {
        ChatRoom domain = ChatRoom.fromPayload(event.getPayload());
        persistence.save(domain);
    }

    @Retryable(
            value = RuntimeException.class,
            maxAttempts = 3,
            backoff = @Backoff(delay = 100, multiplier = 2)
    )
    @Transactional("chatMongoTransactionManager")
    public void handle(ChatRoomUpdatedEvent event, String txId) {
        persistence.updateAndReturn(event.getId(), event.getUpdated());
    }

    @Retryable(
            value = RuntimeException.class,
            maxAttempts = 3,
            backoff = @Backoff(delay = 100, multiplier = 2)
    )
    @Transactional("chatMongoTransactionManager")
    public void handle(ChatRoomJoinedEvent event, String txId) {
        persistence.join(event.getId(), event.getMemberId());
    }

    @Retryable(
            value = RuntimeException.class,
            maxAttempts = 3,
            backoff = @Backoff(delay = 100, multiplier = 2)
    )
    @Transactional("chatMongoTransactionManager")
    public void handle(ChatRoomLeavedEvent event, String txId) {
        persistence.leave(event.getId(), event.getMemberId());
    }

    @Retryable(
            value = RuntimeException.class,
            maxAttempts = 3,
            backoff = @Backoff(delay = 100, multiplier = 2)
    )
    @Transactional("chatMongoTransactionManager")
    public void handle(ChatRoomDeletedEvent event, String txId) {
        persistence.deleteById(event.getId());
    }

    @Retryable(
            value = RuntimeException.class,
            maxAttempts = 3,
            backoff = @Backoff(delay = 100, multiplier = 2)
    )
    @Transactional("chatMongoTransactionManager")
    public void handle(ChatRoomActiveEvent event, String txId) {
        persistence.active(event.getId(), event.getMemberId(), event.getLastMsgSeq(), event.getLastMsgMs());
    }

    @Retryable(
            value = RuntimeException.class,
            maxAttempts = 3,
            backoff = @Backoff(delay = 100, multiplier = 2)
    )
    public void handle(ChatRoomCacheSaveEvent event, String txId) {
        String id = event.getId();
        persistence.findByIdWithLatest(id).ifPresent(cache::warmUp);
    }

    @Retryable(
            value = RuntimeException.class,
            maxAttempts = 3,
            backoff = @Backoff(delay = 100, multiplier = 2)
    )
    public void handle(ChatRoomCacheUpdateEvent event, String txId) {
        String id = event.getId();
        String oldTitle = event.getOldTitle();
        persistence.findByIdWithLatest(id).ifPresent(chatRoom -> cache.recoverUpdate(chatRoom, oldTitle));
    }

    @Retryable(
            value = RuntimeException.class,
            maxAttempts = 3,
            backoff = @Backoff(delay = 100, multiplier = 2)
    )
    public void handle(ChatRoomCacheDeleteEvent event, String txId) {
        cache.delete(event.getId(), event.getCategory(), event.getTitle(), event.getMemberids());
    }

    @Retryable(
            value = RuntimeException.class,
            maxAttempts = 3,
            backoff = @Backoff(delay = 100, multiplier = 2)
    )
    public void handle(ChatRoomCacheActivityInvalidateEvent event, String txId) {
        cache.invalidateActivity(event.getId(), event.getMemberId());
    }

    @Retryable(
            value = RuntimeException.class,
            maxAttempts = 3,
            backoff = @Backoff(delay = 100, multiplier = 2)
    )
    public void handle(ChatRoomCacheInfoInvalidateEvent event, String txId) {
        cache.invalidateInfo(event.getId());
    }

    @Recover
    public void recover(RuntimeException e, ChatRoomPersistedEvent event, String txId) {
        log.error("❌ Mongo 실패. chatroom persist dlq 이벤트 발행: txId={}, error={}", txId, e.getMessage(), e);

        ChatRoomPayload payload = event.getPayload();
        ChatRoom domain = ChatRoom.fromPayload(payload);

        runRecover("chatroom persist recover", txId, e, () -> domain.recoverPersist(e.getMessage()), event.getPayload());
    }

    @Recover
    public void recover(RuntimeException e, ChatRoomUpdatedEvent event, String txId) {
        log.error("❌ Mongo 실패. chatroom update dlq 이벤트 발행: roomId={}, txId={}, error={}", event.getId(), txId, e.getMessage(), e);

        ChatRoom domain = ChatRoom.ofId(event.getId());

        runRecover("chatroom update recover", txId, e, () -> domain.recoverUpdate(event, e.getMessage()), event.getId(), event.getUpdated());
    }

    @Recover
    public void recover(RuntimeException e, ChatRoomJoinedEvent event, String txId) {
        log.error("❌ Mongo 실패. chatroom join dlq 이벤트 발행: roomId={}, memberId={}, txId={}, error={}", event.getId(), event.getMemberId(), txId, e.getMessage(), e);

        ChatRoom domain = ChatRoom.ofId(event.getId());

        runRecover("chatroom join recover", txId, e, () -> domain.recoverJoin(event, e.getMessage()), event.getId(), event.getMemberId());
    }

    @Recover
    public void recover(RuntimeException e, ChatRoomLeavedEvent event, String txId) {
        log.error("❌ Mongo 실패. chatroom leave dlq 이벤트 발행: roomId={}, memberId={}, txId={}, error={}", event.getId(), event.getMemberId(), txId, e.getMessage(), e);

        ChatRoom domain = ChatRoom.ofId(event.getId());

        runRecover("chatroom leave recover", txId, e, () -> domain.recoverLeave(event, e.getMessage()), event.getId(), event.getMemberId());
    }

    @Recover
    public void recover(RuntimeException e, ChatRoomDeletedEvent event, String txId) {
        log.error("❌ Mongo 실패. chatroom delete dlq 이벤트 발행: roomId={}, txId={}, error={}", event.getId(), txId, e.getMessage(), e);

        ChatRoom domain = ChatRoom.ofIdAndCategory(event.getId(), event.getCategory());

        runRecover("chatroom delete recover", txId, e, () -> domain.recoverDelete(e.getMessage()), event.getId(), event.getCategory());
    }

    @Recover
    public void recover(RuntimeException e, ChatRoomActiveEvent event, String txId) {
        log.error("❌ Mongo 실패. chatroom active dlq 이벤트 발행: roomId={}, txId={}, error={}", event.getId(), txId, e.getMessage(), e);

        ChatRoom domain = ChatRoom.ofId(event.getId());

        runRecover("chatroom active recover", txId, e,
                () -> domain.recoverActive(event, e.getMessage()),
                event.getId(), event.getMemberId(), event.getLastMsgSeq(), event.getLastMsgMs()
        );
    }

    @Recover
    public void recover(RuntimeException e, ChatRoomCacheSaveEvent event, String txId) {
        log.error("❌ Redis 실패. chatroom cache dlq 이벤트 발행: roomId={}, txId={}, error={}", event.getId(), txId, e.getMessage(), e);

        ChatRoom domain = ChatRoom.ofId(event.getId());

        runRecover("chatroom cache recover", txId, e,
                () -> domain.recoverCacheSave(e.getMessage()),
                event.getId()
        );
    }

    @Recover
    public void recover(RuntimeException e, ChatRoomCacheUpdateEvent event, String txId) {
        log.error("❌ Redis 실패. chatroom cache update dlq 이벤트 발행: roomId={}, txId={}, error={}", event.getId(), txId, e.getMessage(), e);

        ChatRoom domain = ChatRoom.ofId(event.getId());

        runRecover("chatroom cache update recover", txId, e,
                () -> domain.recoverCacheUpdate(event, e.getMessage()),
                event.getId()
        );
    }

    @Recover
    public void recover(RuntimeException e, ChatRoomCacheDeleteEvent event, String txId) {
        log.error("❌ Redis 실패. chatroom cache delete dlq 이벤트 발행: roomId={}, txId={}, error={}", event.getId(), txId, e.getMessage(), e);

        ChatRoom domain = ChatRoom.ofId(event.getId());

        runRecover("chatroom cache delete recover", txId, e,
                () -> domain.recoverCacheDelete(event, e.getMessage()),
                event.getId()
        );
    }

    @Recover
    public void recover(RuntimeException e, ChatRoomCacheActivityInvalidateEvent event, String txId) {
        log.error("❌ Redis 실패. chatroom cache invalidate activity dlq 이벤트 발행: roomId={}, txId={}, error={}", event.getId(), txId, e.getMessage(), e);

        ChatRoom domain = ChatRoom.ofId(event.getId());

        runRecover("chatroom cache invalidate activity recover", txId, e,
                () -> domain.recoverCacheInvalidateActivity(event, e.getMessage()),
                event.getId()
        );
    }

    @Recover
    public void recover(RuntimeException e, ChatRoomCacheInfoInvalidateEvent event, String txId) {
        log.error("❌ Redis 실패. chatroom cache invalidate info dlq 이벤트 발행: roomId={}, txId={}, error={}", event.getId(), txId, e.getMessage(), e);

        ChatRoom domain = ChatRoom.ofId(event.getId());

        runRecover("chatroom cache invalidate info recover", txId, e,
                () -> domain.recoverCacheInvalidateInfo(event, e.getMessage()),
                event.getId()
        );
    }

    private void runRecover(String context, String txId, RuntimeException original, Runnable recoverAction, Object... details) {
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

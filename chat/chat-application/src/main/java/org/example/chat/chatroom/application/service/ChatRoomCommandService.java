package org.example.chat.chatroom.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.chat.chatroom.application.dto.ChatRoomUpdateCommand;
import org.example.chat.chatroom.application.port.in.ChatRoomCommandUseCase;
import org.example.chat.chatroom.application.port.out.ChatRoomCachePort;
import org.example.chat.chatroom.application.port.out.ChatRoomIdGeneratorPort;
import org.example.chat.chatroom.application.port.out.ChatRoomPersistencePort;
import org.example.chat.chatroom.application.service.command.ChatRoomCreateCommand;
import org.example.chat.chatroom.domain.event.payload.ChatRoomUpdatedPayload;
import org.example.chat.chatroom.domain.model.ChatRoom;
import org.example.chat.chatroom.domain.model.ChatRoomCategory;
import org.example.chat.chatroom.domain.exception.ChatRoomNotFoundException;
import org.example.chat.common.exception.ChatRoomPersistException;
import org.example.common.outbox.application.port.out.OutboxEventListPublishPort;
import org.example.common.outbox.exception.TemporaryOutboxPersistenceException;
import org.springframework.stereotype.Service;

import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatRoomCommandService implements ChatRoomCommandUseCase {

    private final ChatRoomCachePort cache;
    private final ChatRoomPersistencePort persistence;
    private final ChatRoomIdGeneratorPort idGenerator;
    private final OutboxEventListPublishPort outboxEventListPublishPort;

    @Override
    public void create(ChatRoomCreateCommand command) {
        String id = idGenerator.generate();

        ChatRoom domain = ChatRoom.ofNewRoom(
                id,
                command.hostId(),
                command.title(),
                command.description(),
                command.category()
        );

        save(domain);
    }

    public void save(ChatRoom domain) {
        domain.persist();
        publishEvent(domain, "chatroom persist");

        saveCacheSafely(domain);

        activity(domain.getId(), domain.getHostId(), 0L, 0L);
    }

    public void update(String id, ChatRoomUpdateCommand command) {
        ChatRoom domain = persistence.findById(id)
                .orElseThrow(() -> new ChatRoomNotFoundException(id));

        String oldTitle = domain.getTitle();
        ChatRoomUpdatedPayload payload = command.toPayload();

        domain.update(payload);
        publishEvent(domain, "chatroom update");

        updateCacheSafely(domain, payload, oldTitle);
    }

    public boolean join(String id, String memberId) {
        ChatRoom domain = persistence.findById(id)
                .orElseThrow(() -> new ChatRoomNotFoundException(id));

        boolean isJoined = domain.addMember(memberId);

        if (!isJoined) {
            return false;
        }

        publishEvent(domain, "chatroom join");

        joinCacheSafely(domain, memberId);

        return true;
    }

    public void leave(String id, String memberId) {
        ChatRoom domain = persistence.findById(id)
                .orElseThrow(() -> new ChatRoomNotFoundException(id));

        if (domain.isLastMember(memberId)) {
            delete(domain);
            return;
        }

        domain.removeMember(memberId);
        publishEvent(domain, "chatroom leave");

        leaveCacheSafely(domain, memberId);
    }

    public void activity(String id, String memberId, Long lastMsgSeq, Long lastMsgMs) {
        ChatRoom domain = ChatRoom.ofId(id);

        domain.active(memberId, lastMsgSeq, lastMsgMs);
        publishEvent(domain, "chatroom activity");

        activityCacheSafely(domain, memberId, lastMsgSeq, lastMsgMs);
    }

    public void delete(String id) {
        ChatRoom domain = persistence.findById(id)
                .orElseThrow(() -> new ChatRoomNotFoundException(id));

        delete(domain);
    }

    private void delete(ChatRoom domain) {
        String id = domain.getId();
        ChatRoomCategory category = domain.getCategory();
        String title = domain.getTitle();
        Set<String> memberIds = domain.getMemberIds();

        domain.delete();
        publishEvent(domain, "chatroom delete");

        deleteCacheSafely(domain, id, category, title, memberIds);
    }

    private void saveCacheSafely(ChatRoom domain) {
        try {
            cache.save(domain);
        } catch (RuntimeException e) {
            log.warn(
                    "[cache] chatroom save failed. roomId={}",
                    domain.getId(),
                    e
            );

            domain.cacheSave();
            publishEvent(domain, "chatroom cache save fallback");
        }
    }

    private void updateCacheSafely(ChatRoom domain, ChatRoomUpdatedPayload payload, String oldTitle) {
        try {
            cache.update(domain.getId(), payload.toUpdateMap(), oldTitle);
        } catch (RuntimeException e) {
            log.warn(
                    "[cache] chatroom update failed. roomId={}",
                    domain.getId(),
                    e
            );

            domain.cacheUpdate(oldTitle);
            publishEvent(domain, "chatroom cache update fallback");
        }
    }

    private void joinCacheSafely(ChatRoom domain, String memberId) {
        try {
            cache.join(domain.getId(), memberId);
        } catch (RuntimeException e) {
            log.warn(
                    "[cache] chatroom join failed. roomId={}, memberId={}",
                    domain.getId(),
                    memberId,
                    e
            );

            domain.cacheInfoInvalidate();
            publishEvent(domain, "chatroom cache info invalidate");
        }
    }

    private void leaveCacheSafely(ChatRoom domain, String memberId) {
        try {
            cache.leave(domain.getId(), memberId);
        } catch (RuntimeException e) {
            log.warn(
                    "[cache] chatroom leave failed. roomId={}, memberId={}",
                    domain.getId(),
                    memberId,
                    e
            );

            domain.cacheInfoInvalidate();
            publishEvent(domain, "chatroom cache info invalidate");
        }
    }

    private void activityCacheSafely(
            ChatRoom domain,
            String memberId,
            Long lastMsgSeq,
            Long lastMsgMs
    ) {
        try {
            cache.updateLastRead(domain.getId(), memberId, lastMsgSeq);
            cache.updateRecentScore(domain.getId(), memberId, lastMsgMs);
        } catch (RuntimeException e) {
            log.warn(
                    "[cache] chatroom activity failed. roomId={}, memberId={}, lastMsgSeq={}, lastMsgMs={}",
                    domain.getId(),
                    memberId,
                    lastMsgSeq,
                    lastMsgMs,
                    e
            );

            domain.cacheActivityInvalidate(memberId);
            publishEvent(domain, "chatroom cache activity invalidate");
        }
    }

    private void deleteCacheSafely(
            ChatRoom domain,
            String id,
            ChatRoomCategory category,
            String title,
            Set<String> memberIds
    ) {
        try {
            cache.delete(id, category, title, memberIds);
        } catch (RuntimeException e) {
            log.warn(
                    "[cache] chatroom delete failed. roomId={}",
                    id,
                    e
            );

            domain.cacheDelete();
            publishEvent(domain, "chatroom cache delete fallback");
        }
    }

    private void publishEvent(ChatRoom domain, String context) {
        try {
            outboxEventListPublishPort.publish(domain.pullEventList());
        } catch (TemporaryOutboxPersistenceException e) {
            throw e;
        } catch (Exception e) {
            throw new ChatRoomPersistException(
                    domain,
                    "failed to publish chatroom event. context=" + context + ", roomId=" + domain.getId(),
                    e
            );
        }
    }
}
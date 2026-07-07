package org.example.chat.chatroom.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.chat.chatroom.application.event.*;
import org.example.chat.chatroom.application.exception.ChatRoomEventPublishException;
import org.example.chat.chatroom.application.mapper.ChatRoomPayloadMapper;
import org.example.chat.chatroom.application.service.command.ChatRoomActivityCommand;
import org.example.chat.chatroom.application.service.command.ChatRoomUpdateCommand;
import org.example.chat.chatroom.application.port.in.ChatRoomCommandUseCase;
import org.example.chat.chatroom.application.port.out.ChatRoomCachePort;
import org.example.chat.chatroom.application.port.out.ChatRoomIdGeneratorPort;
import org.example.chat.chatroom.application.port.out.ChatRoomPersistencePort;
import org.example.chat.chatroom.application.service.command.ChatRoomCreateCommand;
import org.example.chat.chatroom.application.event.payload.ChatRoomUpdatedPayload;
import org.example.chat.chatroom.domain.model.ChatRoom;
import org.example.chat.chatroom.domain.model.ChatRoomCategory;
import org.example.chat.chatroom.application.exception.ChatRoomNotFoundException;
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

        ChatRoom domain = ChatRoom.create(
                id,
                command.hostId(),
                command.title(),
                command.description(),
                command.category()
        );

        save(domain);
    }

    @Override
    public void save(ChatRoom domain) {
        String id = domain.getId();

        publishChatRoomEvents(
                id,
                ChatRoomEventList.of(
                        new ChatRoomPersistedEvent(ChatRoomPayloadMapper.fromDomain(domain))
                ),
                "chatroom persist"
        );

        cacheSaveSafely(domain);

        activity(new ChatRoomActivityCommand(id, domain.getHostId(), 0L, 0L));
    }

    @Override
    public void update(ChatRoomUpdateCommand command) {
        String id = command.roomId();
        ChatRoomUpdatedPayload updatedPayload = command.toPayload();

        publishChatRoomEvents(
                id,
                ChatRoomEventList.of(
                        new ChatRoomUpdatedEvent(id, updatedPayload)
                ),
                "chatroom update"
        );

        ChatRoom domain = persistence.findById(id)
                .orElseThrow(() -> new ChatRoomNotFoundException(id));
        String oldTitle = domain.getTitle();

        cacheUpdateSafely(id, updatedPayload, oldTitle);
    }

    @Override
    public boolean join(String id, String memberId) {
        ChatRoom domain = persistence.findById(id)
                .orElseThrow(() -> new ChatRoomNotFoundException(id));

        boolean isJoined = domain.addMember(memberId);

        if (!isJoined) {
            return false;
        }

        publishChatRoomEvents(
                id,
                ChatRoomEventList.of(
                        new ChatRoomJoinedEvent(id, memberId)
                ),
                "chatroom join"
        );

        cacheJoinSafely(id, memberId);

        return true;
    }

    @Override
    public void leave(String id, String memberId) {
        ChatRoom domain = persistence.findById(id)
                .orElseThrow(() -> new ChatRoomNotFoundException(id));

        if (domain.isLastMember(memberId)) {
            delete(domain);
            return;
        }

        boolean isRemoved = domain.removeMember(memberId);

        if (!isRemoved) {
            return;
        }

        publishChatRoomEvents(
                id,
                ChatRoomEventList.of(
                        new ChatRoomLeavedEvent(id, memberId)
                ),
                "chatroom leave"
        );

        cacheLeaveSafely(id, memberId);
    }

    @Override
    public void activity(ChatRoomActivityCommand command) {
        String id = command.roomId();
        String memberId = command.memberId();
        Long lastMsgSeq = command.lastMsgReadSeq();
        Long lastMsgMs = command.lastMsgCreatedAtMs();

        publishChatRoomEvents(
                id,
                ChatRoomEventList.of(
                        new ChatRoomActiveEvent(id, memberId, lastMsgSeq, lastMsgMs)
                ),
                "chatroom activity"
        );

        cacheActivitySafely(id, memberId, lastMsgSeq, lastMsgMs);
    }

    @Override
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

        publishChatRoomEvents(
                id,
                ChatRoomEventList.of(
                        new ChatRoomDeletedEvent(id, category)
                ),
                "chatroom delete"
        );

        cacheDeleteSafely(id, category, title, memberIds);
    }

    private void publishChatRoomEvents(
            String roomId,
            ChatRoomEventList eventList,
            String context
    ) {
        try {
            outboxEventListPublishPort.publish(eventList);
        } catch (TemporaryOutboxPersistenceException e) {
            throw e;
        } catch (Exception e) {
            throw new ChatRoomEventPublishException(roomId, context, e);
        }
    }

    private void cacheSaveSafely(ChatRoom domain) {
        try {
            cache.save(domain);
        } catch (RuntimeException e) {
            log.warn(
                    "[cache] chatroom save failed. roomId={}",
                    domain.getId(),
                    e
            );

            publishChatRoomEvents(
                    domain.getId(),
                    ChatRoomEventList.of(
                            new ChatRoomCacheSaveEvent(domain.getId())
                    ),
                    "chatroom cache save fallback"
            );
        }
    }

    private void cacheUpdateSafely(
            String roomId,
            ChatRoomUpdatedPayload payload,
            String oldTitle
    ) {
        try {
            cache.updateRoom(roomId, payload.toUpdateMap(), oldTitle);
        } catch (RuntimeException e) {
            log.warn(
                    "[cache] chatroom update failed. roomId={}",
                    roomId,
                    e
            );

            publishChatRoomEvents(
                    roomId,
                    ChatRoomEventList.of(
                            new ChatRoomCacheUpdateEvent(roomId, oldTitle)
                    ),
                    "chatroom cache update fallback"
            );
        }
    }

    private void cacheJoinSafely(String roomId, String memberId) {
        try {
            cache.joinMembership(roomId, memberId);
        } catch (RuntimeException e) {
            log.warn(
                    "[cache] chatroom join failed. roomId={}, memberId={}",
                    roomId,
                    memberId,
                    e
            );

            publishChatRoomEvents(
                    roomId,
                    ChatRoomEventList.of(
                            new ChatRoomCacheInfoInvalidateEvent(roomId)
                    ),
                    "chatroom cache info invalidate"
            );
        }
    }

    private void cacheLeaveSafely(String roomId, String memberId) {
        try {
            cache.leaveMembership(roomId, memberId);
        } catch (RuntimeException e) {
            log.warn(
                    "[cache] chatroom leave failed. roomId={}, memberId={}",
                    roomId,
                    memberId,
                    e
            );

            publishChatRoomEvents(
                    roomId,
                    ChatRoomEventList.of(
                            new ChatRoomCacheInfoInvalidateEvent(roomId)
                    ),
                    "chatroom cache info invalidate"
            );
        }
    }

    private void cacheActivitySafely(
            String roomId,
            String memberId,
            Long lastMsgSeq,
            Long lastMsgMs
    ) {
        try {
            cache.updateLastReadSeq(roomId, memberId, lastMsgSeq);
            cache.updateActivityScore(roomId, memberId, lastMsgMs);
        } catch (RuntimeException e) {
            log.warn(
                    "[cache] chatroom activity failed. roomId={}, memberId={}, lastMsgReadSeq={}, lastMsgCreatedAtMs={}",
                    roomId,
                    memberId,
                    lastMsgSeq,
                    lastMsgMs,
                    e
            );

            publishChatRoomEvents(
                    roomId,
                    ChatRoomEventList.of(
                            new ChatRoomCacheActivityInvalidateEvent(roomId, memberId)
                    ),
                    "chatroom cache activity invalidate"
            );
        }
    }

    private void cacheDeleteSafely(
            String roomId,
            ChatRoomCategory category,
            String title,
            Set<String> memberIds
    ) {
        try {
            cache.deleteRoom(roomId, category, title, memberIds);
        } catch (RuntimeException e) {
            log.warn(
                    "[cache] chatroom delete failed. roomId={}",
                    roomId,
                    e
            );

            publishChatRoomEvents(
                    roomId,
                    ChatRoomEventList.of(
                            new ChatRoomCacheDeleteEvent(roomId, category, title, memberIds)
                    ),
                    "chatroom cache delete fallback"
            );
        }
    }
}
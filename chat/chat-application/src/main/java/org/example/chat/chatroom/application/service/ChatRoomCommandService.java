package org.example.chat.chatroom.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.example.chat.chatroom.application.dto.ChatRoomCreateRequest;
import org.example.chat.chatroom.application.dto.ChatRoomUpdateCommand;
import org.example.chat.chatroom.application.port.in.ChatRoomCommandUseCase;
import org.example.chat.chatroom.application.port.out.ChatRoomCachePort;
import org.example.chat.chatroom.application.port.out.ChatRoomPersistencePort;
import org.example.chat.chatroom.domain.model.ChatRoom;
import org.example.chat.chatroom.domain.model.ChatRoomCategory;
import org.example.chat.chatroom.domain.exception.ChatRoomNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatRoomCommandService implements ChatRoomCommandUseCase {

    private final ChatRoomCachePort cache;
    private final ChatRoomPersistencePort persistence;

    public void save(String hostId, ChatRoomCreateRequest request) {
        String id = new ObjectId().toHexString();
        ChatRoom domain = ChatRoom.ofNewRoom(id, hostId, request.title(), request.description(), request.category());

        save(domain);
    }

    public void save(ChatRoom domain) {
        domain.persist();

        try {
            cache.save(domain);
        } catch (RuntimeException e) {
            log.warn("[cache] chatroom save() failed. roomId={}", domain.getId(), e);

            domain.cacheSave();
        }

        activity(domain.getId(), domain.getHostId(), 0L, 0L);
    }

    public void update(String id, ChatRoomUpdateCommand command) {
        ChatRoom domain = persistence.findById(id)
                .orElseThrow(() -> new ChatRoomNotFoundException(id));

        String oldTitle = domain.getTitle();

        Map<String, Object> updated = command.toUpdateMap();

        domain.update(updated);

        try {
            cache.update(domain.getId(), updated, oldTitle);
        } catch (RuntimeException e) {
            log.warn("[cache] chatroom update failed. roomId={}", domain.getId(), e);

            domain.cacheUpdate(oldTitle);
        }
    }

    public boolean join(String id, String memberId) {
        ChatRoom domain = persistence.findById(id).orElseThrow(() -> new ChatRoomNotFoundException(id));

        boolean isJoined = domain.addMember(memberId);
        if (!isJoined) return false;

        try {
            cache.join(domain.getId(), memberId);
        } catch (RuntimeException e) {
            log.warn("[cache] chatroom join failed. roomId={}, memberId={}", id, memberId, e);

            domain.cacheInfoInvalidate();
        }

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

        try {
            cache.leave(domain.getId(), memberId);
        } catch (RuntimeException e) {
            log.warn("[cache] chatroom leave failed. roomId={}, memberId={}", id, memberId, e);

            domain.cacheInfoInvalidate();
        }
    }

    public void activity(String id, String memberId, Long lastMsgSeq, Long lastMsgMs) {
        ChatRoom domain = ChatRoom.ofId(id);

        domain.active(memberId, lastMsgSeq, lastMsgMs);

        try {
            cache.updateLastRead(id, memberId, lastMsgSeq);
            cache.updateRecentScore(id, memberId, lastMsgMs);
        } catch (RuntimeException e) {
            log.warn("[cache] chatroom activity failed. roomId={}, memberId={}, lastMsgSeq={}, lastMsgMs={}", id, memberId, lastMsgSeq, lastMsgMs, e);

            domain.cacheActivityInvalidate(memberId);
        }
    }

    public void delete(String id) {
        ChatRoom domain = persistence.findById(id).orElseThrow(() -> new ChatRoomNotFoundException(id));

        delete(domain);
    }

    private void delete(ChatRoom domain) {
        String id = domain.getId();
        ChatRoomCategory category = domain.getCategory();
        String title = domain.getTitle();
        Set<String> memberIds = domain.getMemberIds();

        domain.delete();

        try {
            cache.delete(id, category, title, memberIds);
        } catch (RuntimeException e) {
            log.warn("[cache] chatroom delete() failed. roomId={}", id, e);

            domain.cacheDelete();
        }
    }
}

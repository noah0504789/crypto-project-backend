package org.example.chat.chatroom.application.service;

import lombok.RequiredArgsConstructor;
import org.example.chat.chatroom.domain.event.dlq.*;
import org.example.chat.chatroom.application.port.out.ChatRoomCachePort;
import org.example.chat.chatroom.application.port.out.ChatRoomPersistencePort;
import org.example.chat.chatroom.domain.model.ChatRoom;
import org.example.chat.chatroom.domain.event.handler.ChatRoomDlqHandler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ChatRoomDlqService implements ChatRoomDlqHandler {

    private final ChatRoomPersistencePort persistence;
    private final ChatRoomCachePort cache;

    // TODO: DLQ 실패 정책 (로그 + 알림)

    @Transactional("chatMongoTransactionManager")
    public void handle(ChatRoomPersistedDlqEvent event) {
        ChatRoom domain = ChatRoom.fromPayload(event.getPayload());

        persistence.save(domain);
    }

    @Transactional("chatMongoTransactionManager")
    public void handle(ChatRoomUpdatedDlqEvent event) {
        persistence.updateRoomAndReturn(event.getId(), event.getUpdated().toUpdateMap());
    }

    @Transactional("chatMongoTransactionManager")
    public void handle(ChatRoomDeletedDlqEvent event) {
        persistence.deleteById(event.getId());
    }

    @Transactional("chatMongoTransactionManager")
    public void handle(ChatRoomJoinedDlqEvent event) {
        persistence.joinMembership(event.getId(), event.getMemberId());
    }

    @Transactional("chatMongoTransactionManager")
    public void handle(ChatRoomLeavedDlqEvent event) {
        persistence.leaveMembership(event.getId(), event.getMemberId());
    }

    @Transactional("chatMongoTransactionManager")
    public void handle(ChatRoomActiveDlqEvent event) {
        persistence.activateMembership(event.getId(), event.getMemberId(), event.getLastMsgSeq(), event.getLastMsgMs());
    }

    public void handle(ChatRoomCacheSaveDlqEvent event) {
        String id = event.getId();
        persistence.findByIdWithLatestMessage(id).ifPresent(cache::warmUp);
    }

    public void handle(ChatRoomCacheUpdateDlqEvent event) {
        String id = event.getId();
        String oldTitle = event.getOldTitle();
        persistence.findByIdWithLatestMessage(id).ifPresent(chatRoom -> cache.recoverRoomUpdate(chatRoom, oldTitle));
    }

    public void handle(ChatRoomCacheDeleteDlqEvent event) {
        cache.deleteRoom(event.getId(), event.getCategory(), event.getTitle(), event.getMemberIds());
    }

    public void handle(ChatRoomCacheActivityInvalidateDlqEvent event) {
        cache.invalidateMembershipActivity(event.getId(), event.getMemberId());
    }

    public void handle(ChatRoomCacheInfoInvalidateDlqEvent event) {
        cache.invalidateRoomInfo(event.getId());
    }
}

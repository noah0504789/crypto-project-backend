package org.example.chat.chatroom.application.mapper;

import org.example.chat.chatroom.application.event.payload.ChatRoomPersistPayload;
import org.example.chat.chatroom.domain.model.ChatRoom;

import java.time.LocalDateTime;

import static org.example.common.time.ServiceZoneUtils.ZONE_ID;

public final class ChatRoomPayloadMapper {

    private ChatRoomPayloadMapper() {
    }

    public static ChatRoomPersistPayload fromDomain(ChatRoom chatRoom) {
        return new ChatRoomPersistPayload(
                chatRoom.getId(),
                chatRoom.getHostId(),
                chatRoom.getTitle(),
                chatRoom.getDescription(),
                chatRoom.getCategory(),
                chatRoom.getMemberIds(),
                chatRoom.getCreatedAtInstant()
        );
    }

    public static ChatRoom toDomain(ChatRoomPersistPayload payload) {
        return ChatRoom.rehydrate(
                payload.id(),
                payload.hostId(),
                payload.title(),
                payload.description(),
                payload.category(),
                payload.memberIds(),
                0L,
                LocalDateTime.ofInstant(payload.createdAt(), ZONE_ID)
        );
    }
}
package org.example.chat.chatroom.adapter.out.persistence;

import org.bson.types.ObjectId;
import org.example.chat.chatroom.domain.model.ChatRoomCategory;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface MongoChatRoomRepositoryCustom {

    Optional<MongoChatRoom> findByIdAndDeletedFalseFromSecondary(ObjectId id);

    List<MongoChatRoom> listPopularRooms(
            ChatRoomCategory category,
            int offset,
            int limit
    );

    List<MongoChatRoom> listPopularRoomsAfter(
            ChatRoomCategory category,
            String lastRoomId,
            long lastPopularity,
            int limit)
    ;

    Optional<MongoChatRoom> updateRoomAndReturn(ObjectId roomId, Map<String, Object> updates);

    void incrementRoomField(
            ObjectId roomId,
            String field,
            Integer delta
    );

    void addMember(ObjectId roomId, String userId);

    void removeMember(ObjectId roomId, String userId);

    void softDeleteById(ObjectId roomId);
}

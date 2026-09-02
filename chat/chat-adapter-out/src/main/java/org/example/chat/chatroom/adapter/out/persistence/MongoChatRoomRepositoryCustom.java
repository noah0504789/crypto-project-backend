package org.example.chat.chatroom.adapter.out.persistence;

import org.bson.types.ObjectId;
import org.example.chat.chatroom.domain.model.ChatRoomCategory;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.time.Instant;

public interface MongoChatRoomRepositoryCustom {

    Optional<MongoChatRoom> findByIdAndDeletedFalseFromSecondary(ObjectId id);

    List<MongoChatRoom> listByIdsAndDeletedFalse(List<ObjectId> ids);

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

    List<MongoChatRoom> listAllByCategory(ChatRoomCategory category);

    void bulkUpdatePopularity(Map<String, Long> roomIdToPopularity);

    Optional<MongoChatRoom> updateRoomAndReturn(ObjectId roomId, Map<String, Object> updates);

    Optional<MongoChatRoom> updateMessageState(ObjectId roomId, int count, Instant lastMessageCreatedAt);

    void incrementRoomField(ObjectId roomId, String field, Integer delta);

    void addMember(ObjectId roomId, String userId);

    void removeMember(ObjectId roomId, String userId);

    void softDeleteById(ObjectId roomId);
}

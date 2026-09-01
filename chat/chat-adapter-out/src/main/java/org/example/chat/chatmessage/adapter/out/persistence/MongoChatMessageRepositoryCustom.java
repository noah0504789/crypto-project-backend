package org.example.chat.chatmessage.adapter.out.persistence;

import org.bson.types.ObjectId;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface MongoChatMessageRepositoryCustom {

    Optional<MongoChatMessage> findLatestMessageExcluding(String roomId, String excludedMsgId);

    Optional<MongoChatMessage> findLatestByRoomIdFromSecondary(ObjectId roomId);

    List<MongoChatMessage> listMessagesBefore(
            ObjectId roomId,
            ObjectId lastMsgId,
            Instant lastCreatedAt,
            int limit
    );

    List<MongoChatMessage> listLatestMessagesByRoomIds(List<ObjectId> roomIds);

    List<MongoChatMessage> listLatestMessagesByRoomIdsFromSecondary(List<ObjectId> roomIds);

    Set<ObjectId> findExistingIds(Set<ObjectId> ids);

    void softDeleteByRoomId(ObjectId roomId);

    boolean hardDeleteById(ObjectId id);
}

package org.example.chat.chatmessage.adapter.out.persistence;

import org.bson.types.ObjectId;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface MongoChatMessageRepositoryCustom {

    Optional<MongoChatMessage> findLatestMessageExcluding(String roomId, String excludedMsgId);

    List<MongoChatMessage> listMessagesBefore(
            ObjectId roomId,
            ObjectId lastMsgId,
            Instant lastCreatedAt,
            int limit
    );

    List<MongoChatMessage> listLatestMessagesByRoomIds(List<ObjectId> roomIds);

    void softDeleteByRoomId(ObjectId roomId);

    boolean hardDeleteById(ObjectId id);
}

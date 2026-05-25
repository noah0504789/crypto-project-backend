package org.example.chat.chatmessage.adapter.out.persistence;

import org.bson.types.ObjectId;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface MongoChatMessageRepositoryCustom {

    List<MongoChatMessage> listPrev(ObjectId roomId, ObjectId lastId, Instant lastCreated, int limit);

    void softDeleteByRoomId(ObjectId roomId);

    boolean hardDelete(ObjectId id);

    Optional<MongoChatMessage> findLatestExcluding(String roomId, String id);

    List<MongoChatMessage> findLatestByRoomIds(List<ObjectId> roomIds);
}

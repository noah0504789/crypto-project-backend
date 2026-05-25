package org.example.chat.chatroom.adapter.out.persistence;

import org.bson.types.ObjectId;
import org.example.chat.chatroom.domain.model.ChatRoomCategory;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface MongoChatRoomRepositoryCustom {

    List<MongoChatRoom> listMostPopular(ChatRoomCategory category, int offset, int limit);

    List<MongoChatRoom> listNextPopular(ChatRoomCategory category, String lastId, long lastPopularity, int limit);

    Optional<MongoChatRoom> updateAndReturn(ObjectId roomId, Map<String, Object> updated);

    void incrementField(ObjectId roomId, String field, Integer delta);

    void addMember(ObjectId roomId, String userId);

    void removeMember(ObjectId roomId, String userId);

    void softDeleteById(ObjectId roomId);
}

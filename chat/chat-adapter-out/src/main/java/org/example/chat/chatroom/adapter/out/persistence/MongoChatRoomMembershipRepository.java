package org.example.chat.chatroom.adapter.out.persistence;

import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface MongoChatRoomMembershipRepository extends MongoRepository<MongoChatRoomMembership, String>, MongoChatRoomMembershipRepositoryCustom {

    void deleteByRoomId(ObjectId roomId);

    void deleteByRoomIdAndMemberId(ObjectId roomId, String memberId);

    List<MongoChatRoomMembership> findAllByRoomId(ObjectId roomId);
}

package org.example.chatroom.adapter.out.persistence;

import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface MongoChatRoomRepository extends MongoRepository<MongoChatRoom, ObjectId>, MongoChatRoomRepositoryCustom {

    Optional<MongoChatRoom> findByIdAndDeletedFalse(ObjectId id);

    boolean existsByTitleAndDeletedFalse(String title);
}

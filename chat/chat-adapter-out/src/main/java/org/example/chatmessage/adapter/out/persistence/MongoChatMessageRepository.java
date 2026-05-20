package org.example.chatmessage.adapter.out.persistence;

import org.bson.types.ObjectId;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface MongoChatMessageRepository extends MongoRepository<MongoChatMessage, ObjectId>, MongoChatMessageRepositoryCustom {

    Optional<MongoChatMessage> findTopByRoomIdAndDeletedFalseOrderByCreatedAtDescIdDesc(ObjectId roomId);

    List<MongoChatMessage> findByRoomIdAndDeletedFalse(ObjectId roomId, Pageable pageable);
}

package org.example.chat.chatmessage.adapter.out.persistence;

import com.mongodb.client.result.DeleteResult;
import lombok.RequiredArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class MongoChatMessageRepositoryImpl implements MongoChatMessageRepositoryCustom {

    private final MongoTemplate mongoTemplate;

    @Override
    public List<MongoChatMessage> listMessagesBefore(ObjectId roomId, ObjectId lastMsgId, Instant lastCreatedAt, int limit) {
        Criteria base = Criteria.where("roomId").is(roomId).and("deleted").is(false);

        Criteria createdAtLt = Criteria.where("createdAt").lt(lastCreatedAt);
        Criteria createdAtIs = Criteria.where("createdAt").is(lastCreatedAt);
        Criteria idLt = Criteria.where("_id").lt(lastMsgId);
        Criteria tieBreak = new Criteria().andOperator(createdAtIs, idLt);

        Criteria cursor = new Criteria().orOperator(createdAtLt, tieBreak);

        Query q = new Query(new Criteria().andOperator(base, cursor))
                .with(
                        Sort.by(Sort.Direction.DESC, "createdAt").and(
                        Sort.by(Sort.Direction.DESC, "_id"))
                )
                .limit(limit);

        return mongoTemplate.find(q, MongoChatMessage.class);
    }

    @Override
    public void softDeleteByRoomId(ObjectId roomId) {
        Query query = new Query(Criteria.where("roomId").is(roomId));
        Update update = new Update()
                .set("deleted", true)
                .set("deletedAt", Instant.now());

        mongoTemplate.updateMulti(query, update, MongoChatMessage.class);
    }

    @Override
    public Optional<MongoChatMessage> findLatestMessageExcluding(String roomId, String excludedMsgId) {
        Query query = new Query();
        query.addCriteria(Criteria.where("roomId").is(new ObjectId(roomId)))
                .addCriteria(Criteria.where("_id").ne(new ObjectId(excludedMsgId)))
                .addCriteria(Criteria.where("deleted").is(false))
                .with(Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("_id")))
                .limit(1);

        MongoChatMessage document = mongoTemplate.findOne(query, MongoChatMessage.class);

        return Optional.ofNullable(document);
    }

    @Override
    public List<MongoChatMessage> listLatestMessagesByRoomIds(List<ObjectId> roomIds) {
        Aggregation aggregation = Aggregation.newAggregation(
                Aggregation.match(Criteria.where("roomId").in(roomIds).and("deleted").is(false)),
                Aggregation.sort(Sort.by(
                        Sort.Order.asc("roomId"),
                        Sort.Order.desc("createdAt"),
                        Sort.Order.desc("_id")
                )),
                Aggregation.group("roomId").first(Aggregation.ROOT).as("doc"),
                Aggregation.replaceRoot("doc")
        );

        return mongoTemplate.aggregate(aggregation, "chat_message", MongoChatMessage.class)
                .getMappedResults();
    }

    @Override
    public boolean hardDeleteById(ObjectId id) {
        Query query = new Query(Criteria.where("_id").is(id));

        DeleteResult result = mongoTemplate.remove(query, MongoChatMessage.class);

        return result.getDeletedCount() > 0;
    }
}

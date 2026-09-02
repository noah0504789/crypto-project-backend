package org.example.chat.chatmessage.adapter.out.persistence;

import com.mongodb.client.result.DeleteResult;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Qualifier;
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
import java.util.Set;

@Repository
public class MongoChatMessageRepositoryImpl implements MongoChatMessageRepositoryCustom {

    private final MongoTemplate primaryMongoTemplate;
    private final MongoTemplate secondaryMongoTemplate;

    public MongoChatMessageRepositoryImpl(
            @Qualifier("primaryMongoTemplate") MongoTemplate primaryMongoTemplate,
            @Qualifier("secondaryMongoTemplate") MongoTemplate secondaryMongoTemplate
    ) {
        this.primaryMongoTemplate = primaryMongoTemplate;
        this.secondaryMongoTemplate = secondaryMongoTemplate;
    }

    @Override
    public Optional<MongoChatMessage> findLatestMessageExcluding(
            String roomId,
            String excludedMsgId
    ) {
        Query query = new Query();

        query.addCriteria(Criteria.where("roomId").is(new ObjectId(roomId)))
                .addCriteria(Criteria.where("_id").ne(new ObjectId(excludedMsgId)))
                .addCriteria(Criteria.where("deleted").is(false))
                .with(Sort.by(
                        Sort.Order.desc("createdAt"),
                        Sort.Order.desc("_id")
                ))
                .limit(1);

        MongoChatMessage document = primaryMongoTemplate.findOne(query, MongoChatMessage.class);

        return Optional.ofNullable(document);
    }

    @Override
    public Optional<MongoChatMessage> findLatestByRoomIdFromSecondary(ObjectId roomId) {
        if (roomId == null) {
            return Optional.empty();
        }

        Query query = new Query(
                Criteria.where("roomId")
                        .is(roomId)
                        .and("deleted")
                        .is(false)
        )
                .with(Sort.by(
                        Sort.Order.desc("createdAt"),
                        Sort.Order.desc("_id")
                ))
                .limit(1);

        MongoChatMessage document = secondaryMongoTemplate.findOne(query, MongoChatMessage.class);

        return Optional.ofNullable(document);
    }

    @Override
    public List<MongoChatMessage> listLatestMessagesByRoomIds(List<ObjectId> roomIds) {
        return listLatestMessagesByRoomIds(roomIds, primaryMongoTemplate);
    }

    @Override
    public List<MongoChatMessage> listLatestMessagesByRoomIdsFromSecondary(List<ObjectId> roomIds) {
        return listLatestMessagesByRoomIds(roomIds, secondaryMongoTemplate);
    }

    @Override
    public Set<ObjectId> findExistingIds(Set<ObjectId> ids) {
        if (ids == null || ids.isEmpty()) {
            return Set.of();
        }

        Query query = new Query(Criteria.where("_id").in(ids));
        return Set.copyOf(primaryMongoTemplate.findDistinct(query, "_id", MongoChatMessage.class, ObjectId.class));
    }

    @Override
    public List<MongoChatMessage> listMessagesBefore(
            ObjectId roomId,
            ObjectId lastMsgId,
            Instant lastCreatedAt,
            int limit
    ) {
        Criteria base = Criteria.where("roomId")
                .is(roomId)
                .and("deleted")
                .is(false);

        Criteria createdAtLt = Criteria.where("createdAt").lt(lastCreatedAt);
        Criteria createdAtIs = Criteria.where("createdAt").is(lastCreatedAt);
        Criteria idLt = Criteria.where("_id").lt(lastMsgId);

        Criteria tieBreak = new Criteria().andOperator(createdAtIs, idLt);

        Criteria cursor = new Criteria().orOperator(createdAtLt, tieBreak);

        Query query = new Query(new Criteria().andOperator(base, cursor))
                .with(Sort.by(
                        Sort.Order.desc("createdAt"),
                        Sort.Order.desc("_id")
                ))
                .limit(limit);

        return secondaryMongoTemplate.find(query, MongoChatMessage.class);
    }

    @Override
    public void softDeleteByRoomId(ObjectId roomId) {
        Query query = new Query(Criteria.where("roomId").is(roomId));

        Update update = new Update()
                .set("deleted", true)
                .set("deletedAt", Instant.now());

        primaryMongoTemplate.updateMulti(query, update, MongoChatMessage.class);
    }

    @Override
    public boolean hardDeleteById(ObjectId id) {
        Query query = new Query(Criteria.where("_id").is(id));

        DeleteResult result = primaryMongoTemplate.remove(query, MongoChatMessage.class);

        return result.getDeletedCount() > 0;
    }

    private List<MongoChatMessage> listLatestMessagesByRoomIds(List<ObjectId> roomIds, MongoTemplate mongoTemplate) {
        if (roomIds == null || roomIds.isEmpty()) {
            return List.of();
        }

        Aggregation aggregation = Aggregation.newAggregation(
                Aggregation.match(
                        Criteria.where("roomId")
                                .in(roomIds)
                                .and("deleted")
                                .is(false)
                ),
                Aggregation.sort(Sort.by(
                        Sort.Order.asc("roomId"),
                        Sort.Order.desc("createdAt"),
                        Sort.Order.desc("_id")
                )),
                Aggregation.group("roomId")
                        .first(Aggregation.ROOT)
                        .as("doc"),
                Aggregation.replaceRoot("doc")
        );

        return mongoTemplate.aggregate(
                aggregation,
                MongoChatMessage.class,
                MongoChatMessage.class
        ).getMappedResults();
    }
}

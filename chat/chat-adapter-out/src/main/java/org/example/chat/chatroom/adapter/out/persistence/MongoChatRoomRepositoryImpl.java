package org.example.chat.chatroom.adapter.out.persistence;

import org.bson.types.ObjectId;
import org.example.chat.chatroom.domain.model.ChatRoomCategory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class MongoChatRoomRepositoryImpl implements MongoChatRoomRepositoryCustom {

    private final MongoTemplate primaryMongoTemplate;
    private final MongoTemplate secondaryMongoTemplate;

    public MongoChatRoomRepositoryImpl(
            @Qualifier("primaryMongoTemplate") MongoTemplate primaryMongoTemplate,
            @Qualifier("secondaryMongoTemplate") MongoTemplate secondaryMongoTemplate
    ) {
        this.primaryMongoTemplate = primaryMongoTemplate;
        this.secondaryMongoTemplate = secondaryMongoTemplate;
    }

    // TODO: Popularity Spec

    @Override
    public Optional<MongoChatRoom> findByIdAndDeletedFalseFromSecondary(ObjectId id) {
        if (id == null) {
            return Optional.empty();
        }

        Query query = new Query(
                Criteria.where("_id")
                        .is(id)
                        .and("deleted")
                        .is(false)
        );

        MongoChatRoom document = secondaryMongoTemplate.findOne(
                query,
                MongoChatRoom.class
        );

        return Optional.ofNullable(document);
    }

    @Override
    public List<MongoChatRoom> listPopularRooms(
            ChatRoomCategory category,
            int offset,
            int limit
    ) {
        Criteria criteria = Criteria.where("category")
                .is(category)
                .and("deleted")
                .is(false);

        Query query = new Query(criteria)
                .with(sortMsgCntDesc().and(sortIdDesc()))
                .skip(offset)
                .limit(limit)
                .withHint("idx_category_msgCnt");

        return primaryMongoTemplate.find(query, MongoChatRoom.class);
    }

    @Override
    public List<MongoChatRoom> listPopularRoomsAfter(
            ChatRoomCategory category,
            String lastRoomId,
            long lastPopularity,
            int limit
    ) {
        Criteria base = Criteria.where("category")
                .is(category)
                .and("deleted")
                .is(false);

        Criteria tieBreaker = new Criteria().andOperator(
                Criteria.where("msgCnt").is(lastPopularity),
                Criteria.where("_id").lt(new ObjectId(lastRoomId))
        );

        Criteria cursor = new Criteria().orOperator(
                Criteria.where("msgCnt").lt(lastPopularity),
                tieBreaker
        );

        Query query = new Query(new Criteria().andOperator(base, cursor))
                .with(sortMsgCntDesc().and(sortIdDesc()))
                .limit(limit)
                .withHint("idx_category_msgCnt");

        return secondaryMongoTemplate.find(query, MongoChatRoom.class);
    }

    @Override
    public void incrementRoomField(
            ObjectId roomId,
            String field,
            Integer delta
    ) {
        Query query = new Query(Criteria.where("_id").is(roomId));
        Update update = new Update().inc(field, delta);

        primaryMongoTemplate.updateFirst(
                query,
                update,
                MongoChatRoom.class
        );
    }

    @Override
    public Optional<MongoChatRoom> updateRoomAndReturn(
            ObjectId roomId,
            Map<String, Object> updates
    ) {
        Criteria criteria = Criteria.where("_id").is(roomId);
        Query query = new Query(criteria);

        Update update = new Update();
        updates.forEach(update::set);

        FindAndModifyOptions opts = FindAndModifyOptions.options()
                .returnNew(true);

        return Optional.ofNullable(
                primaryMongoTemplate.findAndModify(
                        query,
                        update,
                        opts,
                        MongoChatRoom.class
                )
        );
    }

    @Override
    public void addMember(ObjectId roomId, String userId) {
        Query query = new Query(Criteria.where("_id").is(roomId));
        Update update = new Update().addToSet("memberIds", userId);

        primaryMongoTemplate.updateFirst(
                query,
                update,
                MongoChatRoom.class
        );
    }

    @Override
    public void removeMember(ObjectId roomId, String userId) {
        Query query = new Query(Criteria.where("_id").is(roomId));
        Update update = new Update().pull("memberIds", userId);

        primaryMongoTemplate.updateFirst(
                query,
                update,
                MongoChatRoom.class
        );
    }

    @Override
    public void softDeleteById(ObjectId roomId) {
        Query query = new Query(Criteria.where("_id").is(roomId));

        Update update = new Update()
                .set("deleted", true)
                .set("deletedAt", LocalDateTime.now());

        primaryMongoTemplate.updateFirst(
                query,
                update,
                MongoChatRoom.class
        );
    }

    private Sort sortIdDesc() {
        return Sort.by(Sort.Direction.DESC, "_id");
    }

    private Sort sortMsgCntDesc() {
        return Sort.by(Sort.Direction.DESC, "msgCnt");
    }
}
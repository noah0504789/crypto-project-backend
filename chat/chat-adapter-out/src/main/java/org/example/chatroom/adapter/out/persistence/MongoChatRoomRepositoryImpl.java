package org.example.chatroom.adapter.out.persistence;

import com.mongodb.client.result.UpdateResult;
import lombok.RequiredArgsConstructor;
import org.bson.types.ObjectId;
import org.example.chatroom.domain.model.ChatRoomCategory;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class MongoChatRoomRepositoryImpl implements MongoChatRoomRepositoryCustom {

    private final MongoTemplate mongoTemplate;

    // TODO: Popularity Spec

    public List<MongoChatRoom> listMostPopular(ChatRoomCategory category, int offset, int limit) {
        Criteria criteria = Criteria.where("category").is(category).and("deleted").is(false);
        Query query = new Query(criteria)
                .with(sortMsgCntDesc().and(sortIdDesc()))
                .skip(offset)
                .limit(limit)
                .withHint("idx_category_msgCnt");

//        return mongoTemplate.find(query, MongoChatRoom.class, "chat_room");
        return mongoTemplate.find(query, MongoChatRoom.class);
    }

    public List<MongoChatRoom> listNextPopular(ChatRoomCategory category, String lastId, long lastPopularity, int limit) {
        Criteria base = Criteria.where("category").is(category).and("deleted").is(false);

        Criteria tieBreaker = new Criteria().andOperator(
            Criteria.where("msgCnt").is(lastPopularity),
            Criteria.where("_id").lt(new ObjectId(lastId))
        );

        Criteria cursor = new Criteria().orOperator(
            Criteria.where("msgCnt").lt(lastPopularity),
            tieBreaker
        );

        Query query = new Query(new Criteria().andOperator(base, cursor))
                .with(sortMsgCntDesc().and(sortIdDesc()))
                .limit(limit)
                .withHint("idx_category_msgCnt");

//        return mongoTemplate.find(query, MongoChatRoom.class, "chat_room");
        return mongoTemplate.find(query, MongoChatRoom.class);
    }

    public void incrementField(ObjectId roomId, String field, Integer delta) {
        Query query = new Query(Criteria.where("_id").is(roomId));
        Update update = new Update().inc(field, delta);

        mongoTemplate.updateFirst(query, update, MongoChatRoom.class);
    }

    public Optional<MongoChatRoom> updateAndReturn(ObjectId roomId, Map<String, Object> updated) {
        Criteria criteria = Criteria.where("_id").is(roomId);
        Query query = new Query(criteria);
        Update update = new Update();
        updated.forEach(update::set);

        FindAndModifyOptions opts = FindAndModifyOptions.options().returnNew(true);

        return Optional.ofNullable(mongoTemplate.findAndModify(query, update, opts, MongoChatRoom.class));
    }

    public void addMember(ObjectId roomId, String userId) {
        Query query = new Query(Criteria.where("_id").is(roomId));
        Update update = new Update().addToSet("memberIds", userId);
        mongoTemplate.updateFirst(query, update, MongoChatRoom.class);
    }

    public void removeMember(ObjectId roomId, String userId) {
        Query query = new Query(Criteria.where("_id").is(roomId));
        Update update = new Update().pull("memberIds", userId);
        mongoTemplate.updateFirst(query, update, MongoChatRoom.class);
    }

    @Override
    public void softDeleteById(ObjectId roomId) {
        Query query = new Query(Criteria.where("_id").is(roomId));
        Update update = new Update()
                .set("deleted", true)
                .set("deletedAt", LocalDateTime.now());

        mongoTemplate.updateFirst(query, update, MongoChatRoom.class);
    }

    private Sort sortIdDesc() {
        return Sort.by(Sort.Direction.DESC, "_id");
    }

    private Sort sortMsgCntDesc() {
        return Sort.by(Sort.Direction.DESC, "msgCnt");
    }
}

package org.example.chatroom.adapter.out.persistence;

import lombok.RequiredArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import java.util.List;


@Repository
@RequiredArgsConstructor
public class MongoChatRoomMembershipRepositoryImpl implements MongoChatRoomMembershipRepositoryCustom {

    private final MongoTemplate mongoTemplate;

    @Override
    public void upsert(MongoChatRoomMembership entity) {
        String id = entity.getId();
        ObjectId roomId = entity.getRoomId();
        String memberId = entity.getMemberId();

        Criteria criteria = Criteria.where("_id").is(id);
        Query query = new Query(criteria);
        Update update = new Update()
                .set("score", entity.getScore())
                .setOnInsert("_id", id)
                .setOnInsert("roomId", roomId)
                .setOnInsert("memberId", memberId)
                .setOnInsert("lastMsgReadSeq", 0L);

        FindAndModifyOptions opts = FindAndModifyOptions.options().upsert(true);

//        mongoTemplate.findAndModify(query, update, opts, MongoChatRoomMembership.class, "chat_room_membership");
        mongoTemplate.findAndModify(query, update, opts, MongoChatRoomMembership.class);
    }

    @Override
    public void refresh(String id, long score) {
        Query query = new Query(Criteria.where("_id").is(id));
        Update update = new Update().set("score", score);

        mongoTemplate.updateFirst(query, update, MongoChatRoomMembership.class);
    }

    // TODO: Popularity Spec

    @Override
    public List<MongoChatRoomMembership> listLatestActive(String memberId, int limit) {
        Criteria criteria = Criteria.where("memberId").is(memberId);
        Query query = new Query(criteria)
                .with(sortScoreDesc().and(sortIdDesc()))
                .limit(limit)
                .withHint("my_rooms");

//        return mongoTemplate.find(query, MongoChatRoomMembership.class, "chat_room_membership");
        return mongoTemplate.find(query, MongoChatRoomMembership.class);
    }

    @Override
    public List<MongoChatRoomMembership> listActiveBefore(String memberId, String lastId, Long score, int limit) {
        Criteria criteria = Criteria.where("memberId").is(memberId);
        Criteria after = new Criteria().orOperator(
                Criteria.where("score").lt(score),
                new Criteria().andOperator(
                        Criteria.where("score").is(score),
                        Criteria.where("roomId").lt(new ObjectId(lastId))
                )
        );

        Query query = new Query()
                .addCriteria(criteria)
                .addCriteria(after)
                .with(sortScoreDesc().and(sortIdDesc())) // TODO: Popularity Spec
                .limit(limit)
                .withHint("my_rooms");

//        return mongoTemplate.find(query, MongoChatRoomMembership.class, "chat_room_membership");
        return mongoTemplate.find(query, MongoChatRoomMembership.class);
    }

    private Sort sortIdDesc() {
        return Sort.by(Sort.Direction.DESC, "_id");
    }

    private Sort sortScoreDesc() {
        return Sort.by(Sort.Direction.DESC, "score");
    }
}

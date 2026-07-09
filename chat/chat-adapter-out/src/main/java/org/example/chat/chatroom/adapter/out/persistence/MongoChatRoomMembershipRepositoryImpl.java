package org.example.chat.chatroom.adapter.out.persistence;

import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class MongoChatRoomMembershipRepositoryImpl implements MongoChatRoomMembershipRepositoryCustom {

    private final MongoTemplate primaryMongoTemplate;
    private final MongoTemplate secondaryMongoTemplate;

    public MongoChatRoomMembershipRepositoryImpl(
            @Qualifier("primaryMongoTemplate") MongoTemplate primaryMongoTemplate,
            @Qualifier("secondaryMongoTemplate") MongoTemplate secondaryMongoTemplate
    ) {
        this.primaryMongoTemplate = primaryMongoTemplate;
        this.secondaryMongoTemplate = secondaryMongoTemplate;
    }

    @Override
    public List<MongoChatRoomMembership> listLatestActiveMemberships(String memberId, int limit) {
        Criteria criteria = Criteria.where("memberId").is(memberId);

        Query query = new Query(criteria)
                .with(sortScoreDesc().and(sortIdDesc()))
                .limit(limit)
                .withHint("my_rooms");

        return primaryMongoTemplate.find(query, MongoChatRoomMembership.class);
    }

    @Override
    public List<MongoChatRoomMembership> listActiveMembershipsBefore(
            String memberId,
            String lastRoomId,
            Long score,
            int limit
    ) {
        Criteria criteria = Criteria.where("memberId").is(memberId);

        Criteria cursor = new Criteria().orOperator(
                Criteria.where("score").lt(score),
                new Criteria().andOperator(
                        Criteria.where("score").is(score),
                        Criteria.where("roomId").lt(new ObjectId(lastRoomId))
                )
        );

        Query query = new Query()
                .addCriteria(criteria)
                .addCriteria(cursor)
                .with(sortScoreDesc().and(sortIdDesc()))
                .limit(limit)
                .withHint("my_rooms");

        return secondaryMongoTemplate.find(query, MongoChatRoomMembership.class);
    }

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

        FindAndModifyOptions opts = FindAndModifyOptions.options()
                .upsert(true);

        primaryMongoTemplate.findAndModify(query, update, opts, MongoChatRoomMembership.class);
    }

    @Override
    public void updateScore(String id, long score) {
        Query query = new Query(Criteria.where("_id").is(id));
        Update update = new Update().set("score", score);

        primaryMongoTemplate.updateFirst(query, update, MongoChatRoomMembership.class);
    }

    private Sort sortIdDesc() {
        return Sort.by(Sort.Direction.DESC, "_id");
    }

    private Sort sortScoreDesc() {
        return Sort.by(Sort.Direction.DESC, "score");
    }
}
package org.example.chat.chatroom.adapter.out.persistence;

import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Set;

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

    /**
     * 메시지 한 건마다 방 멤버 전원의 활동 점수를 갱신한다. 멤버당 왕복하면 비용이 방 크기에
     * 비례하므로 한 번의 bulkWrite 로 보낸다. 순서 보장이 필요 없어 UNORDERED 다.
     */
    @Override
    public void upsertUnreadActivity(String roomId, Set<String> memberIds, long score) {
        if (memberIds == null || memberIds.isEmpty()) {
            return;
        }

        ObjectId roomObjectId = new ObjectId(roomId);

        BulkOperations bulkOperations = primaryMongoTemplate.bulkOps(
                BulkOperations.BulkMode.UNORDERED,
                MongoChatRoomMembership.class
        );

        for (String memberId : memberIds) {
            String id = MongoChatRoomMembership.generateId(roomId, memberId);

            Update update = new Update()
                    .set("score", score)
                    .setOnInsert("roomId", roomObjectId)
                    .setOnInsert("memberId", memberId)
                    .setOnInsert("lastMsgReadSeq", 0L);

            bulkOperations.upsert(new Query(Criteria.where("_id").is(id)), update);
        }

        bulkOperations.execute();
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

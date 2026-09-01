package org.example.chat.chatroom.adapter.out.persistence;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class MongoChatRoomMembershipRepositoryImpl implements MongoChatRoomMembershipRepositoryCustom {

    private final MongoTemplate primaryMongoTemplate;

    public MongoChatRoomMembershipRepositoryImpl(
            @Qualifier("primaryMongoTemplate") MongoTemplate primaryMongoTemplate
    ) {
        this.primaryMongoTemplate = primaryMongoTemplate;
    }

    /**
     * 사용자의 membership 을 정렬 없이 상한까지 읽는다. 정렬 키(unread·최신 활동)는 방 쪽 사실이라
     * 이 컬렉션 인덱스만으로는 정렬할 수 없다 — 정렬은 방을 함께 읽은 뒤 application 이 한다.
     */
    @Override
    public List<MongoChatRoomMembership> listMemberships(String memberId, int limit) {
        Query query = new Query(Criteria.where("memberId").is(memberId))
                .with(Sort.by(Sort.Direction.DESC, "_id"))
                .limit(limit)
                .withHint("my_rooms");

        return primaryMongoTemplate.find(query, MongoChatRoomMembership.class);
    }
}

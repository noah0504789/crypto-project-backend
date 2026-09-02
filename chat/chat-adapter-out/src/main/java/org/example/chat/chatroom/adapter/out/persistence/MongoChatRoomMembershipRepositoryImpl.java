package org.example.chat.chatroom.adapter.out.persistence;

import org.springframework.beans.factory.annotation.Qualifier;
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
     * projection 재생성 후보를 빠뜨리지 않도록 사용자의 membership 전체를 읽는다.
     * unread·최신 활동 점수는 방 상태를 함께 읽은 뒤 application 이 계산하고 상한을 적용한다.
     */
    @Override
    public List<MongoChatRoomMembership> listMemberships(String memberId) {
        Query query = new Query(Criteria.where("memberId").is(memberId))
                .withHint("my_rooms");

        return primaryMongoTemplate.find(query, MongoChatRoomMembership.class);
    }
}

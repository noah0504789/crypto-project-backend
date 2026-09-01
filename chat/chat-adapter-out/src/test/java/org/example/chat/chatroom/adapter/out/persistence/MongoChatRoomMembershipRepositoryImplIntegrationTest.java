package org.example.chat.chatroom.adapter.out.persistence;

import config.TestMongoConfig;
import org.bson.types.ObjectId;
import org.example.common.test.config.TestBootApplication;
import org.example.common.test.testcontainer.MongoDBTestContainerInitializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.test.context.ContextConfiguration;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataMongoTest
@ContextConfiguration(
        classes = {TestBootApplication.class, TestMongoConfig.class},
        initializers = MongoDBTestContainerInitializer.class
)
class MongoChatRoomMembershipRepositoryImplIntegrationTest {

    @Autowired
    private MongoChatRoomMembershipRepository sut;

    @Autowired
    @Qualifier("primaryMongoTemplate")
    private MongoTemplate mongoTemplate;

    private final String memberId1 = "member-1";
    private final String memberId2 = "member-2";

    private final ObjectId roomId1 = new ObjectId("100000000000000000000001");
    private final ObjectId roomId2 = new ObjectId("100000000000000000000002");
    private final ObjectId roomId3 = new ObjectId("100000000000000000000003");
    private final ObjectId roomId4 = new ObjectId("100000000000000000000004");

    @BeforeEach
    void setUp() {
        mongoTemplate.getDb().drop();

        mongoTemplate.indexOps(MongoChatRoomMembership.class)
                .ensureIndex(new Index()
                        .on("roomId", Sort.Direction.ASC)
                        .on("memberId", Sort.Direction.ASC)
                        .unique()
                        .named("ux_room_member"));

        mongoTemplate.indexOps(MongoChatRoomMembership.class)
                .ensureIndex(new Index()
                        .on("memberId", Sort.Direction.ASC)
                        .on("_id", Sort.Direction.DESC)
                        .named("my_rooms"));
    }

    @Nested
    @DisplayName("listMemberships")
    class ListMembershipsTest {

        @Test
        @DisplayName("사용자의 membership 만 조회한다")
        void listMemberships_shouldReturnOnlyOwnMemberships() {
            saveMembership(roomId1, memberId1, 0L);
            saveMembership(roomId2, memberId1, 10L);
            saveMembership(roomId3, memberId2, 20L);

            List<MongoChatRoomMembership> actual = sut.listMemberships(memberId1, 10);

            assertThat(actual)
                    .extracting(MongoChatRoomMembership::getRoomId)
                    .containsExactlyInAnyOrder(roomId1, roomId2);
        }

        @Test
        @DisplayName("읽음 위치를 그대로 돌려준다")
        void listMemberships_shouldReturnLastMsgReadSeq() {
            saveMembership(roomId1, memberId1, 7L);

            List<MongoChatRoomMembership> actual = sut.listMemberships(memberId1, 10);

            assertThat(actual).singleElement()
                    .extracting(MongoChatRoomMembership::getLastMsgReadSeq)
                    .isEqualTo(7L);
        }

        @Test
        @DisplayName("limit 개수만큼 조회한다")
        void listMemberships_shouldApplyLimit() {
            saveMembership(roomId1, memberId1, 0L);
            saveMembership(roomId2, memberId1, 0L);
            saveMembership(roomId3, memberId1, 0L);
            saveMembership(roomId4, memberId1, 0L);

            List<MongoChatRoomMembership> actual = sut.listMemberships(memberId1, 2);

            assertThat(actual).hasSize(2);
        }
    }

    private void saveMembership(
            ObjectId roomId,
            String memberId,
            Long lastMsgReadSeq
    ) {
        MongoChatRoomMembership membership = MongoChatRoomMembership.builder()
                .id(MongoChatRoomMembership.generateId(roomId.toHexString(), memberId))
                .roomId(roomId)
                .memberId(memberId)
                .lastMsgReadSeq(lastMsgReadSeq)
                .build();

        mongoTemplate.save(membership);
    }
}

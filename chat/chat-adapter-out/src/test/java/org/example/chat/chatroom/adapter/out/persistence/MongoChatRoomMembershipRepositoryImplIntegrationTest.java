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
                        .on("score", Sort.Direction.DESC)
                        .on("roomId", Sort.Direction.DESC)
                        .named("my_rooms"));
    }

    @Nested
    @DisplayName("listLatestActiveMemberships")
    class ListLatestActiveMembershipsTest {

        @Test
        @DisplayName("memberId 기준으로 score desc, roomId desc 순서로 최신 활성 방 목록을 조회한다")
        void listLatestActiveMemberships_shouldReturnByMemberOrderedByScoreDescAndIdDesc() {
            saveMembership(roomId1, memberId1, 100L);
            saveMembership(roomId2, memberId1, 300L);
            saveMembership(roomId3, memberId1, 200L);
            saveMembership(roomId4, memberId2, 400L);

            List<MongoChatRoomMembership> actual =
                    sut.listLatestActiveMemberships(memberId1, 10);

            assertRoomIds(actual, roomId2, roomId3, roomId1);
        }

        @Test
        @DisplayName("score가 같으면 roomId desc 순서로 조회한다")
        void listLatestActiveMemberships_shouldSortByIdDescWhenScoreSame() {
            saveMembership(roomId1, memberId1, 300L);
            saveMembership(roomId2, memberId1, 300L);
            saveMembership(roomId3, memberId1, 300L);

            List<MongoChatRoomMembership> actual =
                    sut.listLatestActiveMemberships(memberId1, 10);

            assertRoomIds(actual, roomId3, roomId2, roomId1);
        }

        @Test
        @DisplayName("limit 개수만큼 조회한다")
        void listLatestActiveMemberships_shouldApplyLimit() {
            saveMembership(roomId1, memberId1, 100L);
            saveMembership(roomId2, memberId1, 300L);
            saveMembership(roomId3, memberId1, 200L);

            List<MongoChatRoomMembership> actual =
                    sut.listLatestActiveMemberships(memberId1, 2);

            assertRoomIds(actual, roomId2, roomId3);
        }
    }

    @Nested
    @DisplayName("listActiveMembershipsBefore")
    class ListActiveMembershipsBeforeTest {

        @Test
        @DisplayName("커서보다 이전 데이터를 score desc, _id desc 순서로 조회한다")
        void listActiveMembershipsBefore_shouldReturnItemsBeforeCursor() {
            saveMembership(roomId1, memberId1, 100L);
            saveMembership(roomId2, memberId1, 200L);
            saveMembership(roomId3, memberId1, 300L);
            saveMembership(roomId4, memberId1, 400L);

            List<MongoChatRoomMembership> actual =
                    sut.listActiveMembershipsBefore(
                            memberId1,
                            roomId3.toHexString(),
                            300L,
                            10
                    );

            assertRoomIds(actual, roomId2, roomId1);
        }

        @Test
        @DisplayName("score가 같으면 roomId가 커서보다 작은 데이터만 조회한다")
        void listActiveMembershipsBefore_shouldReturnSameScoreItemsWithLowerRoomId() {
            saveMembership(roomId1, memberId1, 300L);
            saveMembership(roomId2, memberId1, 300L);
            saveMembership(roomId3, memberId1, 300L);
            saveMembership(roomId4, memberId1, 200L);

            List<MongoChatRoomMembership> actual =
                    sut.listActiveMembershipsBefore(
                            memberId1,
                            roomId3.toHexString(),
                            300L,
                            10
                    );

            assertRoomIds(actual, roomId2, roomId1, roomId4);
        }

        @Test
        @DisplayName("다른 memberId의 데이터는 제외한다")
        void listActiveMembershipsBefore_shouldExcludeOtherMemberItems() {
            saveMembership(roomId1, memberId1, 100L);
            saveMembership(roomId2, memberId1, 200L);
            saveMembership(roomId3, memberId1, 300L);
            saveMembership(roomId4, memberId2, 200L);

            List<MongoChatRoomMembership> actual =
                    sut.listActiveMembershipsBefore(
                            memberId1,
                            roomId3.toHexString(),
                            300L,
                            10
                    );

            assertRoomIds(actual, roomId2, roomId1);
        }

        @Test
        @DisplayName("limit 개수만큼 조회한다")
        void listActiveMembershipsBefore_shouldApplyLimit() {
            saveMembership(roomId1, memberId1, 100L);
            saveMembership(roomId2, memberId1, 200L);
            saveMembership(roomId3, memberId1, 300L);
            saveMembership(roomId4, memberId1, 400L);

            List<MongoChatRoomMembership> actual =
                    sut.listActiveMembershipsBefore(
                            memberId1,
                            roomId4.toHexString(),
                            400L,
                            2
                    );

            assertRoomIds(actual, roomId3, roomId2);
        }
    }

    @Nested
    @DisplayName("upsert")
    class UpsertTest {

        @Test
        @DisplayName("없으면 새 membership을 생성한다")
        void upsert_shouldInsertMembershipWhenNotExists() {
            MongoChatRoomMembership membership =
                    MongoChatRoomMembership.ofUnreadActivity(
                            roomId1.toHexString(),
                            memberId1,
                            100L
                    );

            sut.upsert(membership);

            MongoChatRoomMembership actual = mongoTemplate.findById(
                    MongoChatRoomMembership.generateId(
                            roomId1.toHexString(),
                            memberId1
                    ),
                    MongoChatRoomMembership.class
            );

            assertThat(actual).isNotNull();
            assertThat(actual.getRoomId()).isEqualTo(roomId1);
            assertThat(actual.getMemberId()).isEqualTo(memberId1);
            assertThat(actual.getScore()).isEqualTo(100L);
            assertThat(actual.getLastMsgReadSeq()).isEqualTo(0L);
        }

        @Test
        @DisplayName("이미 있으면 score만 갱신한다")
        void upsert_shouldUpdateScoreWhenExists() {
            saveMembership(roomId1, memberId1, 100L);

            MongoChatRoomMembership membership =
                    MongoChatRoomMembership.ofUnreadActivity(
                            roomId1.toHexString(),
                            memberId1,
                            300L
                    );

            sut.upsert(membership);

            MongoChatRoomMembership actual = mongoTemplate.findById(
                    MongoChatRoomMembership.generateId(
                            roomId1.toHexString(),
                            memberId1
                    ),
                    MongoChatRoomMembership.class
            );

            assertThat(actual).isNotNull();
            assertThat(actual.getScore()).isEqualTo(300L);
            assertThat(actual.getRoomId()).isEqualTo(roomId1);
            assertThat(actual.getMemberId()).isEqualTo(memberId1);
        }
    }

    @Nested
    @DisplayName("updateScore")
    class UpdateScoreTest {

        @Test
        @DisplayName("membership score를 수정한다")
        void updateScore_shouldUpdateScore() {
            saveMembership(roomId1, memberId1, 100L);

            String id = MongoChatRoomMembership.generateId(
                    roomId1.toHexString(),
                    memberId1
            );

            sut.updateScore(id, 500L);

            MongoChatRoomMembership actual = mongoTemplate.findById(
                    id,
                    MongoChatRoomMembership.class
            );

            assertThat(actual).isNotNull();
            assertThat(actual.getScore()).isEqualTo(500L);
        }
    }

    private void saveMembership(
            ObjectId roomId,
            String memberId,
            Long score
    ) {
        MongoChatRoomMembership membership =
                MongoChatRoomMembership.ofUnreadActivity(
                        roomId.toHexString(),
                        memberId,
                        score
                );

        mongoTemplate.save(membership);
    }

    private void assertRoomIds(List<MongoChatRoomMembership> actual, ObjectId... expected) {
        assertThat(actual)
                .extracting(MongoChatRoomMembership::getRoomId)
                .containsExactly(expected);
    }
}
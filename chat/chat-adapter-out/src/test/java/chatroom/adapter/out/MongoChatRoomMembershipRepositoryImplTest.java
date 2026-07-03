package chatroom.adapter.out;

import org.example.common.test.config.TestBootApplication;
import config.TestMongoConfig;
import org.example.common.test.testcontainer.MongoDBTestContainerInitializer;
import org.bson.types.ObjectId;
import org.example.chat.chatroom.adapter.out.persistence.MongoChatRoomMembership;
import org.example.chat.chatroom.adapter.out.persistence.MongoChatRoomMembershipRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.test.context.ContextConfiguration;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataMongoTest
@ContextConfiguration(
        classes = {TestBootApplication.class, TestMongoConfig.class},
        initializers = MongoDBTestContainerInitializer.class
)
class MongoChatRoomMembershipRepositoryImplTest {

    @Autowired
    private MongoChatRoomMembershipRepository sut;

    @Autowired
    private MongoTemplate mongoTemplate;

    private final String MEMBER_ID = "member-1";
    private final String OTHER_MEMBER_ID = "member-2";

    private final ObjectId roomId1 = new ObjectId("000000000000000000000001");
    private final ObjectId roomId2 = new ObjectId("000000000000000000000002");
    private final ObjectId roomId3 = new ObjectId("000000000000000000000003");
    private final ObjectId roomId4 = new ObjectId("000000000000000000000004");
    private final ObjectId roomId5 = new ObjectId("000000000000000000000005");

    private final long READ_SEQ_0 = 0L;
    private final long READ_SEQ_10 = 10L;
    private final long READ_SEQ_77 = 77L;

    private final long SCORE_1000 = 1_000L;
    private final long SCORE_2000 = 2_000L;
    private final long SCORE_3000 = 3_000L;
    private final long SCORE_4000 = 4_000L;
    private final long SCORE_5000 = 5_000L;
    private final long SCORE_9999 = 9_999L;

    @BeforeEach
    void setUp() {
        mongoTemplate.getDb().drop();

        mongoTemplate.indexOps(MongoChatRoomMembership.class)
                .ensureIndex(new Index()
                        .on("memberId", Sort.Direction.ASC)
                        .on("score", Sort.Direction.DESC)
                        .on("_id", Sort.Direction.DESC)
                        .named("my_rooms"));
    }

    @Nested
    @DisplayName("upsert")
    class UpsertTest {

        @Test
        @DisplayName("membership이 없으면 새로 생성한다")
        void upsertInsert() {
            // given
            MongoChatRoomMembership entity = unreadMembership(roomId1, MEMBER_ID, SCORE_1000);

            // when
            sut.upsert(entity);

            // then
            MongoChatRoomMembership found = find(roomId1, MEMBER_ID);

            assertThat(found.getId()).isEqualTo(membershipId(roomId1, MEMBER_ID));
            assertThat(found.getRoomId()).isEqualTo(roomId1);
            assertThat(found.getMemberId()).isEqualTo(MEMBER_ID);
            assertThat(found.getScore()).isEqualTo(entity.getScore());
            assertThat(found.getLastMsgReadSeq()).isEqualTo(0L);
        }

        @Test
        @DisplayName("기존 membership이 있으면 score만 갱신하고 lastMsgReadSeq는 유지한다")
        void upsertUpdateKeepingLastMsgReadSeq() {
            // given
            saveMembership(roomId1, MEMBER_ID, READ_SEQ_77, SCORE_1000);

            MongoChatRoomMembership updateEntity = unreadMembership(roomId1, MEMBER_ID, SCORE_3000);

            // when
            sut.upsert(updateEntity);

            // then
            MongoChatRoomMembership found = find(roomId1, MEMBER_ID);

            assertThat(found.getScore()).isEqualTo(updateEntity.getScore());
            assertThat(found.getLastMsgReadSeq()).isEqualTo(READ_SEQ_77);
            assertThat(found.getRoomId()).isEqualTo(roomId1);
            assertThat(found.getMemberId()).isEqualTo(MEMBER_ID);
        }

        @Test
        @DisplayName("다른 memberId는 별도 membership으로 생성한다")
        void upsertDifferentMember() {
            // given
            MongoChatRoomMembership member1 = unreadMembership(roomId1, MEMBER_ID, SCORE_1000);
            MongoChatRoomMembership member2 = unreadMembership(roomId1, OTHER_MEMBER_ID, SCORE_2000);

            // when
            sut.upsert(member1);
            sut.upsert(member2);

            // then
            MongoChatRoomMembership found1 = find(roomId1, MEMBER_ID);
            MongoChatRoomMembership found2 = find(roomId1, OTHER_MEMBER_ID);

            assertThat(found1.getScore()).isEqualTo(member1.getScore());
            assertThat(found2.getScore()).isEqualTo(member2.getScore());
        }
    }

    @Nested
    @DisplayName("refresh")
    class RefreshTest {

        @Test
        @DisplayName("score만 갱신한다")
        void refresh() {
            // given
            MongoChatRoomMembership saved = saveMembership(roomId1, MEMBER_ID, READ_SEQ_10, SCORE_1000);

            // when
            sut.updateScore(saved.getId(), SCORE_9999);

            // then
            MongoChatRoomMembership found = find(roomId1, MEMBER_ID);

            assertThat(found.getScore()).isEqualTo(SCORE_9999);
            assertThat(found.getLastMsgReadSeq()).isEqualTo(READ_SEQ_10);
            assertThat(found.getRoomId()).isEqualTo(roomId1);
            assertThat(found.getMemberId()).isEqualTo(MEMBER_ID);
        }

        @Test
        @DisplayName("존재하지 않는 id를 refresh해도 새 문서를 만들지 않는다")
        void refreshNotFoundDoesNotInsert() {
            // given
            String notFoundId = membershipId(roomId1, MEMBER_ID);

            // when
            sut.updateScore(notFoundId, SCORE_9999);

            // then
            assertThat(sut.findById(notFoundId)).isEmpty();
        }
    }

    @Nested
    @DisplayName("listLatestActive")
    class ListLatestActiveTest {

        @Test
        @DisplayName("memberId 기준으로 score desc 순으로 조회한다")
        void listLatestActiveByScoreDesc() {
            // given
            saveMembership(roomId1, MEMBER_ID, READ_SEQ_0, SCORE_1000);
            saveMembership(roomId2, MEMBER_ID, READ_SEQ_0, SCORE_3000);
            saveMembership(roomId3, MEMBER_ID, READ_SEQ_0, SCORE_2000);

            saveMembership(roomId4, OTHER_MEMBER_ID, READ_SEQ_0, SCORE_9999);

            // when
            List<MongoChatRoomMembership> result = sut.listLatestActiveMemberships(MEMBER_ID, 10);

            // then
            assertRoomIds(result, roomId2, roomId3, roomId1);
        }

        @Test
        @DisplayName("같은 score에서는 _id desc 순으로 조회한다")
        void listLatestActiveTieBreakerByIdDesc() {
            // given
            saveMembership(roomId1, MEMBER_ID, READ_SEQ_0, SCORE_1000);
            saveMembership(roomId2, MEMBER_ID, READ_SEQ_0, SCORE_1000);
            saveMembership(roomId3, MEMBER_ID, READ_SEQ_0, SCORE_1000);

            // when
            List<MongoChatRoomMembership> result = sut.listLatestActiveMemberships(MEMBER_ID, 10);

            // then
            assertRoomIds(result, roomId3, roomId2, roomId1);
        }

        @Test
        @DisplayName("limit을 적용한다")
        void listLatestActiveWithLimit() {
            // given
            saveMembership(roomId1, MEMBER_ID, READ_SEQ_0, SCORE_1000);
            saveMembership(roomId2, MEMBER_ID, READ_SEQ_0, SCORE_3000);
            saveMembership(roomId3, MEMBER_ID, READ_SEQ_0, SCORE_2000);

            // when
            List<MongoChatRoomMembership> result = sut.listLatestActiveMemberships(MEMBER_ID, 2);

            // then
            assertRoomIds(result, roomId2, roomId3);
        }

        @Test
        @DisplayName("다른 memberId의 membership은 제외한다")
        void listLatestActiveExcludeOtherMember() {
            // given
            saveMembership(roomId1, MEMBER_ID, READ_SEQ_0, SCORE_1000);
            saveMembership(roomId2, OTHER_MEMBER_ID, READ_SEQ_0, SCORE_9999);
            saveMembership(roomId3, MEMBER_ID, READ_SEQ_0, SCORE_2000);

            // when
            List<MongoChatRoomMembership> result = sut.listLatestActiveMemberships(MEMBER_ID, 10);

            // then
            assertRoomIds(result, roomId3, roomId1);
        }
    }

    @Nested
    @DisplayName("listActiveBefore")
    class ListActiveBeforeTest {

        @Test
        @DisplayName("score가 cursor보다 작은 membership을 다음 목록으로 조회한다")
        void listActiveBeforeByLowerScore() {
            // given
            saveMembership(roomId4, MEMBER_ID, READ_SEQ_0, SCORE_4000);
            saveMembership(roomId3, MEMBER_ID, READ_SEQ_0, SCORE_3000);
            saveMembership(roomId2, MEMBER_ID, READ_SEQ_0, SCORE_2000);
            saveMembership(roomId1, MEMBER_ID, READ_SEQ_0, SCORE_1000);

            // when
            List<MongoChatRoomMembership> result = sut.listActiveMembershipsBefore(
                    MEMBER_ID,
                    roomId4.toHexString(),
                    SCORE_4000,
                    10
            );

            // then
            assertRoomIds(result, roomId3, roomId2, roomId1);
        }

        @Test
        @DisplayName("같은 score에서는 roomId가 cursor보다 작은 membership을 다음 목록으로 조회한다")
        void listActiveBeforeByTieBreakerRoomId() {
            // given
            saveMembership(roomId1, MEMBER_ID, READ_SEQ_0, SCORE_1000);
            saveMembership(roomId2, MEMBER_ID, READ_SEQ_0, SCORE_1000);
            saveMembership(roomId3, MEMBER_ID, READ_SEQ_0, SCORE_1000);

            // when
            List<MongoChatRoomMembership> result = sut.listActiveMembershipsBefore(
                    MEMBER_ID,
                    roomId3.toHexString(),
                    SCORE_1000,
                    10
            );

            // then
            assertRoomIds(result, roomId2, roomId1);
        }

        @Test
        @DisplayName("다른 memberId의 membership은 다음 목록에서 제외한다")
        void listActiveBeforeExcludeOtherMember() {
            // given
            saveMembership(roomId4, MEMBER_ID, READ_SEQ_0, SCORE_4000);
            saveMembership(roomId3, MEMBER_ID, READ_SEQ_0, SCORE_3000);
            saveMembership(roomId2, OTHER_MEMBER_ID, READ_SEQ_0, SCORE_2000);
            saveMembership(roomId1, MEMBER_ID, READ_SEQ_0, SCORE_1000);

            // when
            List<MongoChatRoomMembership> result = sut.listActiveMembershipsBefore(
                    MEMBER_ID,
                    roomId4.toHexString(),
                    SCORE_4000,
                    10
            );

            // then
            assertRoomIds(result, roomId3, roomId1);
        }

        @Test
        @DisplayName("limit을 적용한다")
        void listActiveBeforeWithLimit() {
            // given
            saveMembership(roomId5, MEMBER_ID, READ_SEQ_0, SCORE_5000);
            saveMembership(roomId4, MEMBER_ID, READ_SEQ_0, SCORE_4000);
            saveMembership(roomId3, MEMBER_ID, READ_SEQ_0, SCORE_3000);
            saveMembership(roomId2, MEMBER_ID, READ_SEQ_0, SCORE_2000);
            saveMembership(roomId1, MEMBER_ID, READ_SEQ_0, SCORE_1000);

            // when
            List<MongoChatRoomMembership> result = sut.listActiveMembershipsBefore(
                    MEMBER_ID,
                    roomId5.toHexString(),
                    SCORE_5000,
                    2
            );

            // then
            assertRoomIds(result, roomId4, roomId3);
        }

        @Test
        @DisplayName("score가 낮은 데이터와 같은 score tie-break 데이터를 함께 조회한다")
        void listActiveBeforeMixedCursorCondition() {
            // given
            saveMembership(roomId5, MEMBER_ID, READ_SEQ_0, SCORE_3000);
            saveMembership(roomId4, MEMBER_ID, READ_SEQ_0, SCORE_3000);
            saveMembership(roomId3, MEMBER_ID, READ_SEQ_0, SCORE_3000);
            saveMembership(roomId2, MEMBER_ID, READ_SEQ_0, SCORE_2000);
            saveMembership(roomId1, MEMBER_ID, READ_SEQ_0, SCORE_1000);

            // when
            List<MongoChatRoomMembership> result = sut.listActiveMembershipsBefore(
                    MEMBER_ID,
                    roomId5.toHexString(),
                    SCORE_3000,
                    10
            );

            // then
            assertRoomIds(result, roomId4, roomId3, roomId2, roomId1);
        }
    }

    private MongoChatRoomMembership saveMembership(ObjectId roomId, String memberId, long lastMsgReadSeq, long score) {
        return sut.save(membership(roomId, memberId, lastMsgReadSeq, score));
    }

    private MongoChatRoomMembership membership(ObjectId roomId, String memberId, long lastMsgReadSeq, long score) {
        return MongoChatRoomMembership.ofReadActivity(
                roomId.toHexString(),
                memberId,
                lastMsgReadSeq,
                score
        );
    }

    private MongoChatRoomMembership unreadMembership(ObjectId roomId, String memberId, long score) {
        return MongoChatRoomMembership.ofUnreadActivity(
                roomId.toHexString(),
                memberId,
                score
        );
    }

    private MongoChatRoomMembership find(ObjectId roomId, String memberId) {
        return sut.findById(membershipId(roomId, memberId)).orElseThrow();
    }

    private String membershipId(ObjectId roomId, String memberId) {
        return MongoChatRoomMembership.generateId(roomId.toHexString(), memberId);
    }

    private void assertRoomIds(List<MongoChatRoomMembership> actual, ObjectId... expected) {
        assertThat(actual)
                .extracting(membership -> membership.getRoomId().toHexString())
                .containsExactly(
                        Arrays.stream(expected)
                                .map(ObjectId::toHexString)
                                .toArray(String[]::new)
                );
    }
}
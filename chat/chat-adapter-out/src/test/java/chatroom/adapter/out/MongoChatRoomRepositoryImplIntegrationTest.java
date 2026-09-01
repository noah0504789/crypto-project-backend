package chatroom.adapter.out;

import org.example.common.test.config.TestBootApplication;
import config.TestMongoConfig;
import org.example.common.test.testcontainer.MongoDBTestContainerInitializer;
import org.bson.types.ObjectId;
import org.example.chat.chatroom.adapter.out.persistence.MongoChatRoom;
import org.example.chat.chatroom.adapter.out.persistence.MongoChatRoomRepository;
import org.example.chat.chatroom.domain.model.ChatRoomCategory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.PartialIndexFilter;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.test.context.ContextConfiguration;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

@DataMongoTest
@ContextConfiguration(
        classes = {TestBootApplication.class, TestMongoConfig.class},
        initializers = MongoDBTestContainerInitializer.class
)
class MongoChatRoomRepositoryImplIntegrationTest {

    @Autowired
    private MongoChatRoomRepository sut;

    @Autowired
    private MongoTemplate mongoTemplate;

    private final ChatRoomCategory category = ChatRoomCategory.values()[0];

    private final String HOST_ID = "host-1";
    private final String MEMBER_ID = "member-1";

    private final String TITLE_1 = "방1";
    private final String TITLE_2 = "방2";
    private final String TITLE_3 = "방3";
    private final String TITLE_DELETED = "삭제방";

    private final ObjectId roomId1 = new ObjectId("000000000000000000000001");
    private final ObjectId roomId2 = new ObjectId("000000000000000000000002");
    private final ObjectId roomId3 = new ObjectId("000000000000000000000003");
    private final ObjectId roomId4 = new ObjectId("000000000000000000000004");
    private final ObjectId roomId5 = new ObjectId("000000000000000000000005");

    @BeforeEach
    void setUp() {
        mongoTemplate.getDb().drop();

        mongoTemplate.indexOps(MongoChatRoom.class)
                .ensureIndex(new Index()
                        .on("category", Sort.Direction.ASC)
                        .on("popularity", Sort.Direction.DESC)
                        .on("_id", Sort.Direction.DESC)
                        .named("idx_category_popularity")
                        .partial(PartialIndexFilter.of(Criteria.where("deleted").is(false))));
    }

    @Nested
    @DisplayName("listMostPopular")
    class ListMostPopularTest {

        @Test
        @DisplayName("deleted=false인 채팅방만 msgCnt desc, _id desc 순으로 조회한다")
        void listMostPopular() {
            // given
            saveRoom(roomId1, TITLE_1, 10);
            saveRoom(roomId2, TITLE_2, 30);
            saveRoom(roomId3, TITLE_3, 20);
            saveDeletedRoom(roomId4, TITLE_DELETED, 100);

            // when
            List<MongoChatRoom> result = sut.listPopularRooms(category, 0, 10);

            // then
            assertRoomIds(result, roomId2, roomId3, roomId1);
        }

        @Test
        @DisplayName("같은 msgCnt에서는 _id desc 순으로 조회한다")
        void listMostPopularTieBreakerByIdDesc() {
            // given
            saveRoom(roomId1, "낮은ID", 10);
            saveRoom(roomId2, "중간ID", 10);
            saveRoom(roomId3, "높은ID", 10);

            // when
            List<MongoChatRoom> result = sut.listPopularRooms(category, 0, 10);

            // then
            assertRoomIds(result, roomId3, roomId2, roomId1);
        }

        @Test
        @DisplayName("offset과 limit을 적용한다")
        void listMostPopularWithOffsetAndLimit() {
            // given
            saveRoom(roomId1, TITLE_1, 10);
            saveRoom(roomId2, TITLE_2, 30);
            saveRoom(roomId3, TITLE_3, 20);

            // when
            List<MongoChatRoom> result = sut.listPopularRooms(category, 1, 1);

            // then
            assertRoomIds(result, roomId3);
        }
    }

    @Nested
    @DisplayName("listNextPopular")
    class ListNextPopularTest {

        @Test
        @DisplayName("msgCnt가 cursor보다 작은 채팅방을 다음 목록으로 조회한다")
        void listNextPopularByLowerMsgCnt() {
            // given
            saveRoom(roomId3, "상", 30);
            saveRoom(roomId2, "중", 20);
            saveRoom(roomId1, "하", 10);

            // when
            List<MongoChatRoom> result = sut.listPopularRoomsAfter(
                    category,
                    roomId3.toHexString(),
                    30L,
                    10
            );

            // then
            assertRoomIds(result, roomId2, roomId1);
        }

        @Test
        @DisplayName("같은 msgCnt에서는 _id가 cursor보다 작은 채팅방을 다음 목록으로 조회한다")
        void listNextPopularByTieBreakerId() {
            // given
            saveRoom(roomId1, "낮은ID", 10);
            saveRoom(roomId2, "중간ID", 10);
            saveRoom(roomId3, "높은ID", 10);

            // when
            List<MongoChatRoom> result = sut.listPopularRoomsAfter(
                    category,
                    roomId3.toHexString(),
                    10L,
                    10
            );

            // then
            assertRoomIds(result, roomId2, roomId1);
        }

        @Test
        @DisplayName("deleted=true인 채팅방은 다음 목록에서 제외한다")
        void listNextPopularExcludeDeletedRoom() {
            // given
            saveRoom(roomId4, "커서방", 30);
            saveRoom(roomId2, "정상방", 20);
            saveDeletedRoom(roomId1, TITLE_DELETED, 10);

            // when
            List<MongoChatRoom> result = sut.listPopularRoomsAfter(
                    category,
                    roomId4.toHexString(),
                    30L,
                    10
            );

            // then
            assertRoomIds(result, roomId2);
        }

        @Test
        @DisplayName("limit을 적용한다")
        void listNextPopularWithLimit() {
            // given
            saveRoom(roomId5, "커서방", 50);
            saveRoom(roomId4, "방1", 40);
            saveRoom(roomId3, "방2", 30);
            saveRoom(roomId2, "방3", 20);

            // when
            List<MongoChatRoom> result = sut.listPopularRoomsAfter(
                    category,
                    roomId5.toHexString(),
                    50L,
                    2
            );

            // then
            assertRoomIds(result, roomId4, roomId3);
        }
    }

    @Nested
    @DisplayName("update")
    class UpdateTest {

        @Test
        @DisplayName("incrementField는 지정 필드를 delta만큼 증가시킨다")
        void incrementField() {
            // given
            saveRoom(roomId1, "카운트방", 0);

            // when
            sut.incrementRoomField(roomId1, "msgCnt", 3);

            // then
            MongoChatRoom found = sut.findById(roomId1).orElseThrow();
            assertThat(found.getMsgCnt()).isEqualTo(3L);
        }

        @Test
        @DisplayName("incrementField는 음수 delta도 적용한다")
        void incrementFieldNegativeDelta() {
            // given
            saveRoom(roomId1, "카운트방", 3);

            // when
            sut.incrementRoomField(roomId1, "msgCnt", -1);

            // then
            MongoChatRoom found = sut.findById(roomId1).orElseThrow();
            assertThat(found.getMsgCnt()).isEqualTo(2L);
        }

        @Test
        @DisplayName("updateMessageState는 msgCnt와 latestMsgSeq를 함께 증가시키고 최신 시각을 유지한다")
        void updateMessageState() {
            // given
            saveRoom(roomId1, "워터마크방", 5);
            Instant latest = Instant.parse("2026-01-01T00:00:00Z");

            // when
            sut.updateMessageState(roomId1, 3, latest).orElseThrow();
            sut.incrementRoomField(roomId1, "msgCnt", -1);
            MongoChatRoom updated = sut.updateMessageState(
                    roomId1,
                    2,
                    latest.minusSeconds(1)
            ).orElseThrow();

            // then
            assertThat(updated.getMsgCnt()).isEqualTo(9L);
            assertThat(updated.getLatestMsgSeq()).isEqualTo(10L);
            assertThat(updated.getLastMsgCreatedAt()).isEqualTo(latest);
        }

        @Test
        @DisplayName("updateAndReturn은 변경 후 문서를 반환한다")
        void updateAndReturn() {
            // given
            saveRoom(roomId1, "기존제목", 0);

            // when
            MongoChatRoom updated = sut.updateRoomAndReturn(
                    roomId1,
                    Map.of(
                            "title", "수정제목",
                            "description", "수정된 설명"
                    )
            ).orElseThrow();

            // then
            assertThat(updated.getTitle()).isEqualTo("수정제목");
            assertThat(updated.getDescription()).isEqualTo("수정된 설명");

            MongoChatRoom found = sut.findById(roomId1).orElseThrow();
            assertThat(found.getTitle()).isEqualTo("수정제목");
            assertThat(found.getDescription()).isEqualTo("수정된 설명");
        }

        @Test
        @DisplayName("존재하지 않는 문서 updateAndReturn은 Optional.empty를 반환한다")
        void updateAndReturnNotFound() {
            // given
            ObjectId notFoundId = new ObjectId("999999999999999999999999");

            // when
            Optional<MongoChatRoom> result = sut.updateRoomAndReturn(
                    notFoundId,
                    Map.of("title", "수정제목")
            );

            // then
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("member")
    class MemberTest {

        @Test
        @DisplayName("addMember는 memberIds에 멤버를 추가한다")
        void addMember() {
            // given
            saveRoom(roomId1, "참여방", 0);

            // when
            sut.addMember(roomId1, MEMBER_ID);

            // then
            MongoChatRoom found = sut.findById(roomId1).orElseThrow();
            assertThat(found.getMemberIds())
                    .containsExactlyInAnyOrder(HOST_ID, MEMBER_ID);
        }

        @Test
        @DisplayName("addMember는 같은 멤버를 중복 추가하지 않는다")
        void addMemberIdempotent() {
            // given
            saveRoom(roomId1, "참여방", 0);

            // when
            sut.addMember(roomId1, MEMBER_ID);
            sut.addMember(roomId1, MEMBER_ID);

            // then
            MongoChatRoom found = sut.findById(roomId1).orElseThrow();
            assertThat(found.getMemberIds())
                    .containsExactlyInAnyOrder(HOST_ID, MEMBER_ID);
        }

        @Test
        @DisplayName("removeMember는 memberIds에서 멤버를 제거한다")
        void removeMember() {
            // given
            saveRoom(roomId1, "퇴장방", 0);
            sut.addMember(roomId1, MEMBER_ID);

            // when
            sut.removeMember(roomId1, MEMBER_ID);

            // then
            MongoChatRoom found = sut.findById(roomId1).orElseThrow();
            assertThat(found.getMemberIds()).containsExactly(HOST_ID);
        }

        @Test
        @DisplayName("없는 멤버 removeMember는 기존 memberIds를 유지한다")
        void removeMemberNotExists() {
            // given
            saveRoom(roomId1, "퇴장방", 0);

            // when
            sut.removeMember(roomId1, "not-exists");

            // then
            MongoChatRoom found = sut.findById(roomId1).orElseThrow();
            assertThat(found.getMemberIds()).containsExactly(HOST_ID);
        }
    }

    @Nested
    @DisplayName("delete")
    class DeleteTest {

        @Test
        @DisplayName("softDeleteById는 deleted=true와 deletedAt을 설정한다")
        void softDeleteById() {
            // given
            saveRoom(roomId1, "삭제방", 0);

            // when
            sut.softDeleteById(roomId1);

            // then
            MongoChatRoom found = sut.findById(roomId1).orElseThrow();
            assertThat(found.isDeleted()).isTrue();
            assertThat(found.getDeletedAt()).isNotNull();
        }
    }

    private void saveRoom(ObjectId id, String title, long msgCnt) {
        sut.save(room(id, title, msgCnt, false));
    }

    private void saveDeletedRoom(ObjectId id, String title, long msgCnt) {
        sut.save(room(id, title, msgCnt, true));
    }

    private MongoChatRoom room(ObjectId id, String title, long msgCnt, boolean deleted) {
        return MongoChatRoom.builder()
                .id(id)
                .hostId(HOST_ID)
                .title(title)
                .description("테스트 설명")
                .category(category)
                .memberIds(Set.of(HOST_ID))
                .msgCnt(msgCnt)
                .popularity(msgCnt)
                .deleted(deleted)
                .deletedAt(deleted ? LocalDateTime.now() : null)
                .createdAt(LocalDateTime.now())
                .build();
    }

    private void assertRoomIds(List<MongoChatRoom> actual, ObjectId... expected) {
        assertThat(actual)
                .extracting(room -> room.getId().toHexString())
                .containsExactly(
                        Arrays.stream(expected)
                                .map(ObjectId::toHexString)
                                .toArray(String[]::new)
                );
    }
}

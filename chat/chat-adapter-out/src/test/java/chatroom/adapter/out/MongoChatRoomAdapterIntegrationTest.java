package chatroom.adapter.out;

import org.example.chat.chatroom.adapter.out.persistence.*;
import org.example.common.test.config.TestBootApplication;
import config.TestMongoConfig;
import org.example.common.test.testcontainer.MongoDBTestContainerInitializer;
import org.bson.types.ObjectId;
import org.example.chat.chatmessage.adapter.out.persistence.MongoChatMessage;
import org.example.chat.chatmessage.adapter.out.persistence.MongoChatMessageRepository;
import org.example.chat.chatroom.application.service.result.MyChatRoomState;
import org.example.chat.chatroom.domain.model.ChatRoom;
import org.example.chat.chatroom.domain.model.ChatRoomCategory;
import org.example.chat.chatroom.application.exception.ChatRoomNotFoundException;
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
import java.time.temporal.ChronoUnit;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataMongoTest
@ContextConfiguration(
        classes = {TestBootApplication.class, TestMongoConfig.class},
        initializers = MongoDBTestContainerInitializer.class
)
class MongoChatRoomAdapterIntegrationTest {

    @Autowired
    private MongoChatRoomAdapter sut;

    @Autowired
    private MongoChatRoomRepository chatRoomRepository;

    @Autowired
    private MongoChatRoomMembershipRepository membershipRepository;

    @Autowired
    private MongoChatMessageRepository chatMessageRepository;

    @Autowired
    private MongoTemplate mongoTemplate;

    private final ChatRoomCategory category = ChatRoomCategory.values()[0];

    private final String HOST_ID = "host-1";
    private final String MEMBER_ID = "member-1";
    private final String OTHER_MEMBER_ID = "member-2";

    private final String TITLE_1 = "방1";
    private final String TITLE_2 = "방2";
    private final String TITLE_3 = "방3";
    private final String DESCRIPTION = "테스트 설명";

    private final long READ_SEQ_0 = 0L;
    private final long READ_SEQ_10 = 10L;
    private final long READ_SEQ_77 = 77L;

    private final long SCORE_1000 = 1_000L;
    private final long SCORE_2000 = 2_000L;
    private final long SCORE_3000 = 3_000L;
    private final long SCORE_4000 = 4_000L;
    private final long SCORE_9999 = 9_999L;

    private final ObjectId roomId1 = new ObjectId("000000000000000000000001");
    private final ObjectId roomId2 = new ObjectId("000000000000000000000002");
    private final ObjectId roomId3 = new ObjectId("000000000000000000000003");
    private final ObjectId roomId4 = new ObjectId("000000000000000000000004");

    private final ObjectId messageId1 = new ObjectId("100000000000000000000001");
    private final ObjectId messageId2 = new ObjectId("100000000000000000000002");
    private final ObjectId messageId3 = new ObjectId("100000000000000000000003");

    private final Instant oldTime = Instant.parse("2026-01-01T01:00:00Z");
    private final Instant latestTime = Instant.parse("2026-01-01T03:00:00Z");

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

        mongoTemplate.indexOps(MongoChatRoomMembership.class)
                .ensureIndex(new Index()
                        .on("memberId", Sort.Direction.ASC)
                        .on("score", Sort.Direction.DESC)
                        .on("_id", Sort.Direction.DESC)
                        .named("my_rooms"));

        mongoTemplate.indexOps(MongoChatMessage.class)
                .ensureIndex(new Index()
                        .on("roomId", Sort.Direction.ASC)
                        .on("createdAt", Sort.Direction.DESC)
                        .on("_id", Sort.Direction.DESC)
                        .named("idx_room_created_id")
                        .partial(PartialIndexFilter.of(Criteria.where("deleted").is(false))));
    }

    @Nested
    @DisplayName("save / find")
    class SaveFindTest {

        @Test
        @DisplayName("save 후 findById로 채팅방을 조회할 수 있다")
        void saveAndFindById() {
            // given
            ChatRoom room = chatRoom(roomId1, TITLE_1);

            // when
            sut.save(room);

            // then
            ChatRoom found = sut.findById(roomId1.toHexString()).orElseThrow();

            assertThat(found.getId()).isEqualTo(roomId1.toHexString());
            assertThat(found.getHostId()).isEqualTo(HOST_ID);
            assertThat(found.getTitle()).isEqualTo(TITLE_1);
            assertThat(found.getDescription()).isEqualTo(DESCRIPTION);
            assertThat(found.getCategory()).isEqualTo(category);
            assertThat(found.getMemberIds()).containsExactlyInAnyOrder(HOST_ID);
            assertThat(found.getMsgCnt()).isEqualTo(0L);
        }

        @Test
        @DisplayName("deleted=true인 채팅방은 findById에서 조회되지 않는다")
        void findByIdExcludeDeletedRoom() {
            // given
            sut.save(chatRoom(roomId1, TITLE_1));
            sut.deleteById(roomId1.toHexString());

            // when
            Optional<ChatRoom> result = sut.findById(roomId1.toHexString());

            // then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("existsByTitle은 삭제되지 않은 채팅방 제목만 true를 반환한다")
        void existsByTitle() {
            // given
            sut.save(chatRoom(roomId1, TITLE_1));
            sut.save(chatRoom(roomId2, TITLE_2));
            sut.deleteById(roomId2.toHexString());

            // when & then
            assertThat(sut.existsByTitle(TITLE_1)).isTrue();
            assertThat(sut.existsByTitle(TITLE_2)).isFalse();
            assertThat(sut.existsByTitle("없는방")).isFalse();
        }
    }

    @Nested
    @DisplayName("update")
    class UpdateTest {

        @Test
        @DisplayName("updateAndReturn은 수정된 채팅방을 도메인으로 반환한다")
        void updateAndReturn() {
            // given
            sut.save(chatRoom(roomId1, TITLE_1));

            // when
            ChatRoom updated = sut.updateRoomAndReturn(
                    roomId1.toHexString(),
                    Map.of(
                            "title", "수정제목",
                            "description", "수정된 설명"
                    )
            );

            // then
            assertThat(updated.getId()).isEqualTo(roomId1.toHexString());
            assertThat(updated.getTitle()).isEqualTo("수정제목");
            assertThat(updated.getDescription()).isEqualTo("수정된 설명");

            ChatRoom found = sut.findById(roomId1.toHexString()).orElseThrow();
            assertThat(found.getTitle()).isEqualTo("수정제목");
            assertThat(found.getDescription()).isEqualTo("수정된 설명");
        }

        @Test
        @DisplayName("없는 채팅방 updateAndReturn은 ChatRoomNotFoundException을 던진다")
        void updateAndReturnNotFound() {
            // given
            String notFoundId = new ObjectId("999999999999999999999999").toHexString();

            // when & then
            assertThatThrownBy(() ->
                    sut.updateRoomAndReturn(notFoundId, Map.of("title", "수정제목"))
            ).isInstanceOf(ChatRoomNotFoundException.class);
        }

        @Test
        @DisplayName("updateMessageState는 msgCnt와 latestMsgSeq를 함께 증가시킨다")
        void updateMessageState() {
            // given
            sut.save(chatRoom(roomId1, TITLE_1));

            // when
            sut.updateMessageState(roomId1.toHexString(), 2, 1L);

            // then
            ChatRoom found = sut.findById(roomId1.toHexString()).orElseThrow();
            assertThat(found.getMsgCnt()).isEqualTo(2L);
            assertThat(found.getLatestMsgSeq()).isEqualTo(2L);
        }

        @Test
        @DisplayName("decrementMsgCnt는 msgCnt를 감소시킨다")
        void decrementMsgCnt() {
            // given
            sut.save(chatRoom(roomId1, TITLE_1));
            sut.updateMessageState(roomId1.toHexString(), 2, 1L);

            // when
            sut.decrementMessageCount(roomId1.toHexString());

            // then
            ChatRoom found = sut.findById(roomId1.toHexString()).orElseThrow();
            assertThat(found.getMsgCnt()).isEqualTo(1L);
        }
    }

    @Nested
    @DisplayName("membership")
    class MembershipTest {

        @Test
        @DisplayName("join은 채팅방 memberIds에 멤버를 추가한다")
        void join() {
            // given
            sut.save(chatRoom(roomId1, TITLE_1));

            // when
            sut.joinMembership(roomId1.toHexString(), MEMBER_ID);

            // then
            ChatRoom found = sut.findById(roomId1.toHexString()).orElseThrow();
            assertThat(found.getMemberIds())
                    .containsExactlyInAnyOrder(HOST_ID, MEMBER_ID);
        }

        @Test
        @DisplayName("join은 같은 멤버를 중복 추가하지 않는다")
        void joinIdempotent() {
            // given
            sut.save(chatRoom(roomId1, TITLE_1));

            // when
            sut.joinMembership(roomId1.toHexString(), MEMBER_ID);
            sut.joinMembership(roomId1.toHexString(), MEMBER_ID);

            // then
            ChatRoom found = sut.findById(roomId1.toHexString()).orElseThrow();
            assertThat(found.getMemberIds())
                    .containsExactlyInAnyOrder(HOST_ID, MEMBER_ID);
        }

        @Test
        @DisplayName("active는 membership을 저장한다")
        void active() {
            // given
            sut.save(chatRoom(roomId1, TITLE_1));

            // when
            sut.activateMembership(roomId1.toHexString(), MEMBER_ID, READ_SEQ_10);

            // then
            MongoChatRoomMembership found = findMembership(roomId1, MEMBER_ID);

            assertThat(found.getRoomId()).isEqualTo(roomId1);
            assertThat(found.getMemberId()).isEqualTo(MEMBER_ID);
            assertThat(found.getLastMsgReadSeq()).isEqualTo(READ_SEQ_10);
        }

        @Test
        @DisplayName("getLastReadSeq는 membership의 lastMsgReadSeq를 반환한다")
        void getLastReadSeq() {
            // given
            sut.save(chatRoom(roomId1, TITLE_1));
            sut.activateMembership(roomId1.toHexString(), MEMBER_ID, READ_SEQ_77);

            // when
            Long lastReadSeq = sut.getLastReadSeq(roomId1.toHexString(), MEMBER_ID);

            // then
            assertThat(lastReadSeq).isEqualTo(READ_SEQ_77);
        }

        @Test
        @DisplayName("leave는 memberIds에서 제거하고 membership도 삭제한다")
        void leave() {
            // given
            sut.save(chatRoom(roomId1, TITLE_1));
            sut.joinMembership(roomId1.toHexString(), MEMBER_ID);
            sut.activateMembership(roomId1.toHexString(), MEMBER_ID, READ_SEQ_10);

            // when
            sut.leaveMembership(roomId1.toHexString(), MEMBER_ID);

            // then
            ChatRoom found = sut.findById(roomId1.toHexString()).orElseThrow();
            assertThat(found.getMemberIds()).containsExactly(HOST_ID);
            assertThat(membershipRepository.findById(membershipId(roomId1, MEMBER_ID))).isEmpty();
        }

    }

    @Nested
    @DisplayName("latest message")
    class LatestMessageTest {

        @Test
        @DisplayName("findByIdWithLatest는 최신 메시지를 붙여 반환한다")
        void findByIdWithLatest() {
            // given
            sut.save(chatRoom(roomId1, TITLE_1));

            saveMessage(messageId1, roomId1, "old", oldTime, false);
            MongoChatMessage latest = saveMessage(messageId2, roomId1, "latest", latestTime, false);

            // when
            ChatRoom found = sut.findByIdWithLatestMessage(roomId1.toHexString()).orElseThrow();

            // then
            assertThat(found.getId()).isEqualTo(roomId1.toHexString());
            assertThat(found.getLastMsgId()).isEqualTo(latest.getId().toHexString());
            assertThat(found.getLastMsgContent()).isEqualTo("latest");
            assertThat(found.getLastMsgCreatedAt()).isEqualTo(latest.getCreatedAt());
        }

        @Test
        @DisplayName("findByIdWithLatest는 deleted=false인 메시지만 최신 메시지로 사용한다")
        void findByIdWithLatestExcludeDeletedMessage() {
            // given
            sut.save(chatRoom(roomId1, TITLE_1));

            MongoChatMessage alive = saveMessage(messageId1, roomId1, "alive", oldTime, false);
            saveMessage(messageId2, roomId1, "deleted-latest", latestTime, true);

            // when
            ChatRoom found = sut.findByIdWithLatestMessage(roomId1.toHexString()).orElseThrow();

            // then
            assertThat(found.getLastMsgId()).isEqualTo(alive.getId().toHexString());
            assertThat(found.getLastMsgContent()).isEqualTo("alive");
        }

        @Test
        @DisplayName("findByIdWithLatest는 메시지가 없으면 latest 필드를 비워 반환한다")
        void findByIdWithLatestWithoutMessage() {
            // given
            sut.save(chatRoom(roomId1, TITLE_1));

            // when
            ChatRoom found = sut.findByIdWithLatestMessage(roomId1.toHexString()).orElseThrow();

            // then
            assertThat(found.getLastMsgId()).isEqualTo("");
            assertThat(found.getLastMsgContent()).isEqualTo("");
            assertThat(found.getLastMsgCreatedAt()).isNull();
        }
    }

    @Nested
    @DisplayName("list")
    class ListTest {

        @Test
        @DisplayName("listMostPopular는 조회된 채팅방에 최신 메시지를 붙인다")
        void listMostPopularAttachLatestMessages() {
            // given
            sut.save(chatRoom(roomId1, TITLE_1));
            sut.save(chatRoom(roomId2, TITLE_2));

            setPopularity(roomId1, 1);
            setPopularity(roomId2, 2);

            saveMessage(messageId1, roomId1, "room1-latest", latestTime, false);
            saveMessage(messageId2, roomId2, "room2-old", oldTime, false);
            MongoChatMessage room2Latest = saveMessage(messageId3, roomId2, "room2-latest", latestTime, false);

            // when
            List<ChatRoom> result = sut.listPopularRooms(category, 10);

            // then
            assertRoomIds(result, roomId2, roomId1);

            ChatRoom first = result.get(0);
            assertThat(first.getId()).isEqualTo(roomId2.toHexString());
            assertThat(first.getLastMsgId()).isEqualTo(room2Latest.getId().toHexString());
            assertThat(first.getLastMsgContent()).isEqualTo("room2-latest");
        }

        @Test
        @DisplayName("listNextPopular는 조회된 다음 채팅방 목록에 최신 메시지를 붙인다")
        void listNextPopularAttachLatestMessages() {
            // given
            sut.save(chatRoom(roomId1, TITLE_1));
            sut.save(chatRoom(roomId2, TITLE_2));
            sut.save(chatRoom(roomId3, TITLE_3));

            setPopularity(roomId3, 3);
            setPopularity(roomId2, 2);
            setPopularity(roomId1, 1);

            saveMessage(messageId1, roomId2, "room2-latest", latestTime, false);

            // when
            List<ChatRoom> result = sut.listPopularRoomsAfter(
                    category,
                    roomId3.toHexString(),
                    3L,
                    10
            );

            // then
            assertRoomIds(result, roomId2, roomId1);
            assertThat(result.get(0).getLastMsgContent()).isEqualTo("room2-latest");
        }

        @Test
        @DisplayName("listMyRoomStates는 사용자의 membership 과 방을 함께 반환한다(정렬은 하지 않는다)")
        void listMyRoomStates() {
            // given
            sut.save(chatRoom(roomId1, TITLE_1));
            sut.save(chatRoom(roomId2, TITLE_2));

            saveMembership(readMembership(roomId1, MEMBER_ID, READ_SEQ_10));
            saveMembership(readMembership(roomId2, MEMBER_ID, READ_SEQ_0));

            saveMessage(messageId1, roomId2, "room2-latest", latestTime, false);

            // when
            List<MyChatRoomState> result = sut.listMyRoomStates(MEMBER_ID, 10);

            // then
            assertThat(result)
                    .extracting(state -> state.room().getId(), MyChatRoomState::lastMsgReadSeq)
                    .containsExactlyInAnyOrder(
                            tuple(roomId1.toHexString(), READ_SEQ_10),
                            tuple(roomId2.toHexString(), READ_SEQ_0)
                    );

            assertThat(result)
                    .filteredOn(state -> state.room().getId().equals(roomId2.toHexString()))
                    .singleElement()
                    .satisfies(state -> assertThat(state.room().getLastMsgContent()).isEqualTo("room2-latest"));
        }

        @Test
        @DisplayName("listMyRoomStates는 상한까지만 읽는다")
        void listMyRoomStatesLimit() {
            // given
            sut.save(chatRoom(roomId1, TITLE_1));
            sut.save(chatRoom(roomId2, TITLE_2));
            sut.save(chatRoom(roomId3, TITLE_3));

            saveMembership(readMembership(roomId1, MEMBER_ID, READ_SEQ_0));
            saveMembership(readMembership(roomId2, MEMBER_ID, READ_SEQ_0));
            saveMembership(readMembership(roomId3, MEMBER_ID, READ_SEQ_0));

            // when
            List<MyChatRoomState> result = sut.listMyRoomStates(MEMBER_ID, 2);

            // then
            assertThat(result).hasSize(2);
        }

        @Test
        @DisplayName("listRoomsForPopularityRecompute는 category의 삭제되지 않은 방을 모두 반환한다")
        void listRoomsForPopularityRecompute() {
            // given
            sut.save(chatRoom(roomId1, TITLE_1));
            sut.save(chatRoom(roomId2, TITLE_2));
            sut.save(chatRoom(roomId3, TITLE_3));
            sut.deleteById(roomId3.toHexString());

            // when
            List<ChatRoom> result = sut.listRoomsForPopularityRecompute(category);

            // then
            assertThat(result)
                    .extracting(ChatRoom::getId)
                    .containsExactlyInAnyOrder(roomId1.toHexString(), roomId2.toHexString());
        }
    }

    @Nested
    @DisplayName("delete")
    class DeleteTest {

        @Test
        @DisplayName("deleteById는 채팅방 soft delete, membership 삭제, 메시지 soft delete를 함께 수행한다")
        void deleteById() {
            // given
            sut.save(chatRoom(roomId1, TITLE_1));

            saveMembership(readMembership(roomId1, MEMBER_ID, READ_SEQ_10));
            MongoChatMessage message = saveMessage(messageId1, roomId1, "삭제될 메시지", latestTime, false);

            // when
            sut.deleteById(roomId1.toHexString());

            // then
            assertThat(sut.findById(roomId1.toHexString())).isEmpty();

            MongoChatRoom deletedRoom = chatRoomRepository.findById(roomId1).orElseThrow();
            assertThat(deletedRoom.isDeleted()).isTrue();
            assertThat(deletedRoom.getDeletedAt()).isNotNull();

            assertThat(membershipRepository.findById(membershipId(roomId1, MEMBER_ID))).isEmpty();

            MongoChatMessage deletedMessage = chatMessageRepository.findById(message.getId()).orElseThrow();
            assertThat(deletedMessage.isDeleted()).isTrue();
            assertThat(deletedMessage.getDeletedAt()).isNotNull();
        }
    }

    private ChatRoom chatRoom(ObjectId id, String title) {
        return ChatRoom.create(
                id.toHexString(),
                HOST_ID,
                title,
                DESCRIPTION,
                category,
                LocalDateTime.of(2026, 1, 1, 12, 0)
        );
    }

    private MongoChatMessage saveMessage(
            ObjectId messageId,
            ObjectId roomId,
            String content,
            Instant createdAt,
            boolean deleted
    ) {
        return chatMessageRepository.save(MongoChatMessage.builder()
                .id(messageId)
                .roomId(roomId)
                .writerId("writer-1")
                .content(content)
                .deleted(deleted)
                .deletedAt(deleted ? createdAt.plus(1, ChronoUnit.MINUTES) : null)
                .createdAt(createdAt)
                .build());
    }

    private MongoChatRoomMembership readMembership(ObjectId roomId, String memberId, Long lastMsgReadSeq) {
        return MongoChatRoomMembership.ofReadActivity(roomId.toHexString(), memberId, lastMsgReadSeq);
    }

    private void saveMembership(MongoChatRoomMembership membership) {
        membershipRepository.save(membership);
    }

    private MongoChatRoomMembership findMembership(ObjectId roomId, String memberId) {
        return membershipRepository.findById(membershipId(roomId, memberId)).orElseThrow();
    }

    private String membershipId(ObjectId roomId, String memberId) {
        return MongoChatRoomMembership.generateId(roomId.toHexString(), memberId);
    }

    private void setPopularity(ObjectId roomId, long popularity) {
        sut.updatePopularities(Map.of(roomId.toHexString(), popularity));
    }

    private void assertRoomIds(List<ChatRoom> actual, ObjectId... expected) {
        assertThat(actual)
                .extracting(ChatRoom::getId)
                .containsExactly(
                        Arrays.stream(expected)
                                .map(ObjectId::toHexString)
                                .toArray(String[]::new)
                );
    }
}

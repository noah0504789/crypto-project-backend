package org.example.chat.chatroom.adapter.out.persistence;

import config.TestMongoConfig;
import org.bson.types.ObjectId;
import org.example.chat.chatroom.domain.model.ChatRoom;
import org.example.chat.chatroom.domain.model.ChatRoomCategory;
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
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.test.context.ContextConfiguration;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DataMongoTest
@ContextConfiguration(
        classes = {TestBootApplication.class, TestMongoConfig.class},
        initializers = MongoDBTestContainerInitializer.class
)
class MongoChatRoomRepositoryImplTest {

    @Autowired
    private MongoChatRoomRepository sut;

    @Autowired
    @Qualifier("primaryMongoTemplate")
    private MongoTemplate mongoTemplate;

    private final ChatRoomCategory category = ChatRoomCategory.values()[0];

    private final ObjectId roomId1 = new ObjectId("100000000000000000000001");
    private final ObjectId roomId2 = new ObjectId("100000000000000000000002");
    private final ObjectId roomId3 = new ObjectId("100000000000000000000003");
    private final ObjectId roomId4 = new ObjectId("100000000000000000000004");

    private final LocalDateTime createdAt = LocalDateTime.of(2026, 1, 1, 0, 0);

    @BeforeEach
    void setUp() {
        mongoTemplate.getDb().drop();

        mongoTemplate.indexOps(MongoChatRoom.class)
                .ensureIndex(new Index()
                        .on("category", Sort.Direction.ASC)
                        .on("popularity", Sort.Direction.DESC)
                        .on("_id", Sort.Direction.DESC)
                        .named("idx_category_popularity"));
    }

    @Nested
    @DisplayName("findByIdAndDeletedFalseFromSecondary")
    class FindByIdAndDeletedFalseFromSecondaryTest {

        @Test
        @DisplayName("secondary 경로로 삭제되지 않은 방을 조회한다")
        void findByIdAndDeletedFalseFromSecondary_shouldReturnActiveRoom() {
            saveRoom(roomId1, category, 10);

            Optional<MongoChatRoom> actual = sut.findByIdAndDeletedFalseFromSecondary(roomId1);

            assertThat(actual).isPresent();
            assertThat(actual.orElseThrow().getId()).isEqualTo(roomId1);
            assertThat(actual.orElseThrow().isDeleted()).isFalse();
        }

        @Test
        @DisplayName("deleted=true 방은 조회하지 않는다")
        void findByIdAndDeletedFalseFromSecondary_shouldReturnEmptyWhenRoomDeleted() {
            saveRoom(roomId1, category, 10);
            softDeleteRoom(roomId1);

            Optional<MongoChatRoom> actual =
                    sut.findByIdAndDeletedFalseFromSecondary(roomId1);

            assertThat(actual).isEmpty();
        }

        @Test
        @DisplayName("존재하지 않는 방이면 Optional.empty를 반환한다")
        void findByIdAndDeletedFalseFromSecondary_shouldReturnEmptyWhenRoomNotFound() {
            Optional<MongoChatRoom> actual =
                    sut.findByIdAndDeletedFalseFromSecondary(roomId1);

            assertThat(actual).isEmpty();
        }

        @Test
        @DisplayName("id가 null이면 Optional.empty를 반환한다")
        void findByIdAndDeletedFalseFromSecondary_shouldReturnEmptyWhenIdIsNull() {
            Optional<MongoChatRoom> actual =
                    sut.findByIdAndDeletedFalseFromSecondary(null);

            assertThat(actual).isEmpty();
        }
    }

    @Nested
    @DisplayName("listPopularRooms")
    class ListPopularRoomsTest {

        @Test
        @DisplayName("category 기준으로 msgCnt desc, _id desc 순서로 인기 방 목록을 조회한다")
        void listPopularRooms_shouldReturnByCategoryOrderedByMsgCntDescAndIdDesc() {
            saveRoom(roomId1, category, 10);
            saveRoom(roomId2, category, 30);
            saveRoom(roomId3, category, 20);
            saveRoom(roomId4, category, 30);

            List<MongoChatRoom> actual = sut.listPopularRooms(
                    category,
                    0,
                    10
            );

            assertRoomIds(actual, roomId4, roomId2, roomId3, roomId1);
        }

        @Test
        @DisplayName("limit 개수만큼 조회한다")
        void listPopularRooms_shouldApplyLimit() {
            saveRoom(roomId1, category, 10);
            saveRoom(roomId2, category, 30);
            saveRoom(roomId3, category, 20);

            List<MongoChatRoom> actual = sut.listPopularRooms(
                    category,
                    0,
                    2
            );

            assertRoomIds(actual, roomId2, roomId3);
        }

        @Test
        @DisplayName("deleted=true 방은 제외한다")
        void listPopularRooms_shouldExcludeDeletedRoom() {
            saveRoom(roomId1, category, 10);
            saveRoom(roomId2, category, 30);
            saveRoom(roomId3, category, 20);
            softDeleteRoom(roomId2);

            List<MongoChatRoom> actual = sut.listPopularRooms(
                    category,
                    0,
                    10
            );

            assertRoomIds(actual, roomId3, roomId1);
        }
    }

    @Nested
    @DisplayName("listPopularRoomsAfter")
    class ListPopularRoomsAfterTest {

        @Test
        @DisplayName("커서보다 이후 데이터를 msgCnt desc, _id desc 순서로 조회한다")
        void listPopularRoomsAfter_shouldReturnItemsAfterCursor() {
            saveRoom(roomId1, category, 10);
            saveRoom(roomId2, category, 30);
            saveRoom(roomId3, category, 20);
            saveRoom(roomId4, category, 40);

            List<MongoChatRoom> actual = sut.listPopularRoomsAfter(
                    category,
                    roomId3.toHexString(),
                    20,
                    10
            );

            assertRoomIds(actual, roomId1);
        }

        @Test
        @DisplayName("msgCnt가 같으면 _id가 커서보다 작은 데이터만 조회한다")
        void listPopularRoomsAfter_shouldReturnSamePopularityItemsWithLowerId() {
            saveRoom(roomId1, category, 30);
            saveRoom(roomId2, category, 30);
            saveRoom(roomId3, category, 30);
            saveRoom(roomId4, category, 20);

            List<MongoChatRoom> actual = sut.listPopularRoomsAfter(
                    category,
                    roomId3.toHexString(),
                    30,
                    10
            );

            assertRoomIds(actual, roomId2, roomId1, roomId4);
        }

        @Test
        @DisplayName("deleted=true 방은 제외한다")
        void listPopularRoomsAfter_shouldExcludeDeletedRoom() {
            saveRoom(roomId1, category, 10);
            saveRoom(roomId2, category, 30);
            saveRoom(roomId3, category, 20);
            saveRoom(roomId4, category, 40);
            softDeleteRoom(roomId1);

            List<MongoChatRoom> actual = sut.listPopularRoomsAfter(
                    category,
                    roomId3.toHexString(),
                    20,
                    10
            );

            assertThat(actual).isEmpty();
        }

        @Test
        @DisplayName("limit 개수만큼 조회한다")
        void listPopularRoomsAfter_shouldApplyLimit() {
            saveRoom(roomId1, category, 10);
            saveRoom(roomId2, category, 20);
            saveRoom(roomId3, category, 30);
            saveRoom(roomId4, category, 40);

            List<MongoChatRoom> actual = sut.listPopularRoomsAfter(
                    category,
                    roomId4.toHexString(),
                    40,
                    2
            );

            assertRoomIds(actual, roomId3, roomId2);
        }
    }

    private void saveRoom(
            ObjectId roomId,
            ChatRoomCategory category,
            long msgCnt
    ) {
        MongoChatRoom room = MongoChatRoom.fromDomain(
                ChatRoom.rehydrate(
                        roomId.toHexString(),
                        "host-id",
                        "title-" + roomId.toHexString(),
                        "description",
                        category,
                        Set.of("host-id"),
                        msgCnt,
                        createdAt
                )
        );

        mongoTemplate.save(room);
    }

    private void softDeleteRoom(ObjectId roomId) {
        Query query = new Query(Criteria.where("_id").is(roomId));

        Update update = new Update()
                .set("deleted", true)
                .set("deletedAt", LocalDateTime.now());

        mongoTemplate.updateFirst(query, update, MongoChatRoom.class);
    }

    private void assertRoomIds(List<MongoChatRoom> actual, ObjectId... expected) {
        assertThat(actual)
                .extracting(MongoChatRoom::getId)
                .containsExactly(expected);
    }
}
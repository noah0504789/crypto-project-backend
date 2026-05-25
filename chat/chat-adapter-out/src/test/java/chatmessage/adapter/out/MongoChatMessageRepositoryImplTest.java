package chatmessage.adapter.out;

import org.example.common.test.config.TestBootApplication;
import config.TestMongoConfig;
import org.example.common.test.testcontainer.MongoDBTestContainerInitializer;
import org.bson.types.ObjectId;
import org.example.chat.chatmessage.adapter.out.persistence.MongoChatMessage;
import org.example.chat.chatmessage.adapter.out.persistence.MongoChatMessageRepository;
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
import java.time.temporal.ChronoUnit;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

@DataMongoTest
@ContextConfiguration(
        classes = {TestBootApplication.class, TestMongoConfig.class},
        initializers = MongoDBTestContainerInitializer.class
)
class MongoChatMessageRepositoryImplTest {

    @Autowired
    private MongoChatMessageRepository sut;

    @Autowired
    private MongoTemplate mongoTemplate;

    private final ObjectId roomId1 = new ObjectId("000000000000000000000001");
    private final ObjectId roomId2 = new ObjectId("000000000000000000000002");

    private final ObjectId messageId1 = new ObjectId("100000000000000000000001");
    private final ObjectId messageId2 = new ObjectId("100000000000000000000002");
    private final ObjectId messageId3 = new ObjectId("100000000000000000000003");
    private final ObjectId messageId4 = new ObjectId("100000000000000000000004");
    private final ObjectId messageId5 = new ObjectId("100000000000000000000005");

    private final String WRITER_ID = "writer-1";

    private final Instant time1 = Instant.parse("2026-01-01T01:00:00Z");
    private final Instant time2 = Instant.parse("2026-01-01T02:00:00Z");
    private final Instant time3 = Instant.parse("2026-01-01T03:00:00Z");
    private final Instant time4 = Instant.parse("2026-01-01T04:00:00Z");

    @BeforeEach
    void setUp() {
        mongoTemplate.getDb().drop();

        mongoTemplate.indexOps(MongoChatMessage.class)
                .ensureIndex(new Index()
                        .on("roomId", Sort.Direction.ASC)
                        .on("createdAt", Sort.Direction.DESC)
                        .on("_id", Sort.Direction.DESC)
                        .named("idx_room_created_id")
                        .partial(PartialIndexFilter.of(Criteria.where("deleted").is(false))));
    }

    @Nested
    @DisplayName("listPrev")
    class ListPrevTest {

        @Test
        @DisplayName("cursor createdAt보다 오래된 메시지를 최신순으로 조회한다")
        void listPrevByCreatedAt() {
            // given
            saveMessage(messageId1, roomId1, "message-1", time1, false);
            saveMessage(messageId2, roomId1, "message-2", time2, false);
            saveMessage(messageId3, roomId1, "message-3", time3, false);
            saveMessage(messageId4, roomId1, "message-4", time4, false);

            // when
            List<MongoChatMessage> result = sut.listPrev(
                    roomId1,
                    messageId4,
                    time4,
                    2
            );

            // then
            assertMessageIds(result, messageId3, messageId2);
        }

        @Test
        @DisplayName("같은 createdAt에서는 _id가 cursor보다 작은 메시지를 조회한다")
        void listPrevTieBreakerById() {
            // given
            saveMessage(messageId1, roomId1, "low-id", time3, false);
            saveMessage(messageId2, roomId1, "mid-id", time3, false);
            saveMessage(messageId3, roomId1, "high-id", time3, false);

            // when
            List<MongoChatMessage> result = sut.listPrev(
                    roomId1,
                    messageId3,
                    time3,
                    10
            );

            // then
            assertMessageIds(result, messageId2, messageId1);
        }

        @Test
        @DisplayName("deleted=true 메시지는 제외한다")
        void listPrevExcludeDeletedMessage() {
            // given
            saveMessage(messageId1, roomId1, "alive-old", time1, false);
            saveMessage(messageId2, roomId1, "deleted-mid", time2, true);
            saveMessage(messageId3, roomId1, "alive-new", time3, false);

            // when
            List<MongoChatMessage> result = sut.listPrev(
                    roomId1,
                    messageId3,
                    time3,
                    10
            );

            // then
            assertMessageIds(result, messageId1);
        }

        @Test
        @DisplayName("다른 roomId의 메시지는 제외한다")
        void listPrevExcludeOtherRoom() {
            // given
            saveMessage(messageId1, roomId1, "room1-old", time1, false);
            saveMessage(messageId2, roomId2, "room2-mid", time2, false);
            saveMessage(messageId3, roomId1, "room1-new", time3, false);

            // when
            List<MongoChatMessage> result = sut.listPrev(
                    roomId1,
                    messageId3,
                    time3,
                    10
            );

            // then
            assertMessageIds(result, messageId1);
        }

        @Test
        @DisplayName("limit을 적용한다")
        void listPrevWithLimit() {
            // given
            saveMessage(messageId1, roomId1, "message-1", time1, false);
            saveMessage(messageId2, roomId1, "message-2", time2, false);
            saveMessage(messageId3, roomId1, "message-3", time3, false);
            saveMessage(messageId4, roomId1, "message-4", time4, false);

            // when
            List<MongoChatMessage> result = sut.listPrev(
                    roomId1,
                    messageId4,
                    time4,
                    1
            );

            // then
            assertMessageIds(result, messageId3);
        }

        @Test
        @DisplayName("cursor 이전 메시지가 없으면 빈 목록을 반환한다")
        void listPrevEmpty() {
            // given
            saveMessage(messageId1, roomId1, "oldest", time1, false);

            // when
            List<MongoChatMessage> result = sut.listPrev(
                    roomId1,
                    messageId1,
                    time1,
                    10
            );

            // then
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("softDeleteByRoomId")
    class SoftDeleteByRoomIdTest {

        @Test
        @DisplayName("같은 roomId의 메시지를 모두 soft delete 처리한다")
        void softDeleteByRoomId() {
            // given
            saveMessage(messageId1, roomId1, "room1-message-1", time1, false);
            saveMessage(messageId2, roomId1, "room1-message-2", time2, false);
            saveMessage(messageId3, roomId2, "room2-message", time3, false);

            // when
            sut.softDeleteByRoomId(roomId1);

            // then
            MongoChatMessage found1 = sut.findById(messageId1).orElseThrow();
            MongoChatMessage found2 = sut.findById(messageId2).orElseThrow();
            MongoChatMessage found3 = sut.findById(messageId3).orElseThrow();

            assertThat(found1.isDeleted()).isTrue();
            assertThat(found1.getDeletedAt()).isNotNull();

            assertThat(found2.isDeleted()).isTrue();
            assertThat(found2.getDeletedAt()).isNotNull();

            assertThat(found3.isDeleted()).isFalse();
            assertThat(found3.getDeletedAt()).isNull();
        }
    }

    @Nested
    @DisplayName("hardDelete")
    class HardDeleteTest {

        @Test
        @DisplayName("존재하는 메시지를 물리 삭제하고 true를 반환한다")
        void hardDelete() {
            // given
            saveMessage(messageId1, roomId1, "delete-target", time1, false);

            // when
            boolean result = sut.hardDelete(messageId1);

            // then
            assertThat(result).isTrue();
            assertThat(sut.findById(messageId1)).isEmpty();
        }

        @Test
        @DisplayName("존재하지 않는 메시지를 삭제하면 false를 반환한다")
        void hardDeleteNotFound() {
            // when
            boolean result = sut.hardDelete(messageId1);

            // then
            assertThat(result).isFalse();
        }
    }

    @Nested
    @DisplayName("findLatestExcluding")
    class FindLatestExcludingTest {

        @Test
        @DisplayName("지정 메시지를 제외하고 가장 최신 메시지를 조회한다")
        void findLatestExcluding() {
            // given
            saveMessage(messageId1, roomId1, "old", time1, false);
            MongoChatMessage latest = saveMessage(messageId2, roomId1, "latest", time2, false);

            // when
            Optional<MongoChatMessage> result = sut.findLatestExcluding(
                    roomId1.toHexString(),
                    messageId1.toHexString()
            );

            // then
            assertThat(result).isPresent();
            assertThat(result.orElseThrow().getId()).isEqualTo(latest.getId());
        }

        @Test
        @DisplayName("가장 최신 메시지를 제외하면 그 이전 메시지를 조회한다")
        void findLatestExcludingLatestMessage() {
            // given
            MongoChatMessage old = saveMessage(messageId1, roomId1, "old", time1, false);
            saveMessage(messageId2, roomId1, "latest", time2, false);

            // when
            Optional<MongoChatMessage> result = sut.findLatestExcluding(
                    roomId1.toHexString(),
                    messageId2.toHexString()
            );

            // then
            assertThat(result).isPresent();
            assertThat(result.orElseThrow().getId()).isEqualTo(old.getId());
        }

        @Test
        @DisplayName("deleted=true 메시지는 최신 메시지 후보에서 제외한다")
        void findLatestExcludingExcludeDeletedMessage() {
            // given
            MongoChatMessage alive = saveMessage(messageId1, roomId1, "alive", time1, false);
            saveMessage(messageId2, roomId1, "deleted-latest", time2, true);

            // when
            Optional<MongoChatMessage> result = sut.findLatestExcluding(
                    roomId1.toHexString(),
                    messageId5.toHexString()
            );

            // then
            assertThat(result).isPresent();
            assertThat(result.orElseThrow().getId()).isEqualTo(alive.getId());
        }

        @Test
        @DisplayName("다른 roomId의 메시지는 제외한다")
        void findLatestExcludingExcludeOtherRoom() {
            // given
            MongoChatMessage room1Message = saveMessage(messageId1, roomId1, "room1-message", time1, false);
            saveMessage(messageId2, roomId2, "room2-latest", time2, false);

            // when
            Optional<MongoChatMessage> result = sut.findLatestExcluding(
                    roomId1.toHexString(),
                    messageId5.toHexString()
            );

            // then
            assertThat(result).isPresent();
            assertThat(result.orElseThrow().getId()).isEqualTo(room1Message.getId());
        }

        @Test
        @DisplayName("조회 가능한 메시지가 없으면 Optional.empty를 반환한다")
        void findLatestExcludingEmpty() {
            // given
            saveMessage(messageId1, roomId1, "only-message", time1, false);

            // when
            Optional<MongoChatMessage> result = sut.findLatestExcluding(
                    roomId1.toHexString(),
                    messageId1.toHexString()
            );

            // then
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("findLatestByRoomIds")
    class FindLatestByRoomIdsTest {

        @Test
        @DisplayName("여러 roomId에 대해 각 방의 최신 메시지를 하나씩 조회한다")
        void findLatestByRoomIds() {
            // given
            MongoChatMessage room1Latest = saveMessage(messageId2, roomId1, "room1-latest", time2, false);
            saveMessage(messageId1, roomId1, "room1-old", time1, false);

            saveMessage(messageId3, roomId2, "room2-old", time1, false);
            MongoChatMessage room2Latest = saveMessage(messageId4, roomId2, "room2-latest", time3, false);

            // when
            List<MongoChatMessage> result = sut.findLatestByRoomIds(List.of(roomId1, roomId2));

            // then
            assertThat(result)
                    .extracting(MongoChatMessage::getId)
                    .containsExactlyInAnyOrder(
                            room1Latest.getId(),
                            room2Latest.getId()
                    );
        }

        @Test
        @DisplayName("같은 createdAt에서는 _id가 큰 메시지를 최신 메시지로 선택한다")
        void findLatestByRoomIdsTieBreakerByIdDesc() {
            // given
            saveMessage(messageId1, roomId1, "low-id", time1, false);
            MongoChatMessage highId = saveMessage(messageId2, roomId1, "high-id", time1, false);

            // when
            List<MongoChatMessage> result = sut.findLatestByRoomIds(List.of(roomId1));

            // then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getId()).isEqualTo(highId.getId());
        }

        @Test
        @DisplayName("deleted=true 메시지는 최신 메시지 후보에서 제외한다")
        void findLatestByRoomIdsExcludeDeletedMessage() {
            // given
            MongoChatMessage alive = saveMessage(messageId1, roomId1, "alive", time1, false);
            saveMessage(messageId2, roomId1, "deleted-latest", time2, true);

            // when
            List<MongoChatMessage> result = sut.findLatestByRoomIds(List.of(roomId1));

            // then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getId()).isEqualTo(alive.getId());
        }

        @Test
        @DisplayName("대상 roomId가 없으면 빈 목록을 반환한다")
        void findLatestByRoomIdsEmptyRoomIds() {
            // when
            List<MongoChatMessage> result = sut.findLatestByRoomIds(List.of());

            // then
            assertThat(result).isEmpty();
        }
    }

    private MongoChatMessage saveMessage(
            ObjectId messageId,
            ObjectId roomId,
            String content,
            Instant createdAt,
            boolean deleted
    ) {
        return sut.save(MongoChatMessage.builder()
                .id(messageId)
                .roomId(roomId)
                .writerId(WRITER_ID)
                .content(content)
                .createdAt(createdAt)
                .deleted(deleted)
                .deletedAt(deleted ? createdAt.plus(1, ChronoUnit.MINUTES) : null)
                .build());
    }

    private void assertMessageIds(List<MongoChatMessage> actual, ObjectId... expected) {
        assertThat(actual)
                .extracting(MongoChatMessage::getId)
                .containsExactly(expected);
    }
}
package org.example.chat.chatmessage.adapter.out.persistence;

import org.example.common.test.config.TestBootApplication;
import config.TestMongoConfig;
import org.example.common.test.testcontainer.MongoDBTestContainerInitializer;
import org.bson.types.ObjectId;
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
class MongoChatMessageRepositoryImplIntegrationTest {

    @Autowired
    private MongoChatMessageRepository sut;

    @Autowired
    @Qualifier("primaryMongoTemplate")
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
                        .partial(PartialIndexFilter.of(
                                Criteria.where("deleted").is(false)
                        )));
    }

    @Nested
    @DisplayName("findLatestMessageExcluding")
    class FindLatestMessageExcludingTest {

        @Test
        @DisplayName("지정 메시지를 제외하고 가장 최신 메시지를 조회한다")
        void findLatestMessageExcluding_shouldReturnLatestMessageExceptExcludedOne() {
            saveMessage(messageId1, roomId1, "old", time1, false);
            MongoChatMessage latest = saveMessage(
                    messageId2,
                    roomId1,
                    "latest",
                    time2,
                    false
            );

            Optional<MongoChatMessage> result = sut.findLatestMessageExcluding(
                    roomId1.toHexString(),
                    messageId1.toHexString()
            );

            assertThat(result).isPresent();
            assertThat(result.orElseThrow().getId()).isEqualTo(latest.getId());
        }

        @Test
        @DisplayName("가장 최신 메시지를 제외하면 그 이전 메시지를 조회한다")
        void findLatestMessageExcluding_shouldReturnPreviousMessageWhenLatestExcluded() {
            MongoChatMessage old = saveMessage(
                    messageId1,
                    roomId1,
                    "old",
                    time1,
                    false
            );
            saveMessage(messageId2, roomId1, "latest", time2, false);

            Optional<MongoChatMessage> result = sut.findLatestMessageExcluding(
                    roomId1.toHexString(),
                    messageId2.toHexString()
            );

            assertThat(result).isPresent();
            assertThat(result.orElseThrow().getId()).isEqualTo(old.getId());
        }

        @Test
        @DisplayName("deleted=true 메시지는 최신 메시지 후보에서 제외한다")
        void findLatestMessageExcluding_shouldExcludeDeletedMessage() {
            MongoChatMessage alive = saveMessage(
                    messageId1,
                    roomId1,
                    "alive",
                    time1,
                    false
            );
            saveMessage(messageId2, roomId1, "deleted-latest", time2, true);

            Optional<MongoChatMessage> result = sut.findLatestMessageExcluding(
                    roomId1.toHexString(),
                    messageId5.toHexString()
            );

            assertThat(result).isPresent();
            assertThat(result.orElseThrow().getId()).isEqualTo(alive.getId());
        }

        @Test
        @DisplayName("다른 roomId의 메시지는 제외한다")
        void findLatestMessageExcluding_shouldExcludeOtherRoom() {
            MongoChatMessage room1Message = saveMessage(
                    messageId1,
                    roomId1,
                    "room1-message",
                    time1,
                    false
            );
            saveMessage(messageId2, roomId2, "room2-latest", time2, false);

            Optional<MongoChatMessage> result = sut.findLatestMessageExcluding(
                    roomId1.toHexString(),
                    messageId5.toHexString()
            );

            assertThat(result).isPresent();
            assertThat(result.orElseThrow().getId()).isEqualTo(room1Message.getId());
        }

        @Test
        @DisplayName("조회 가능한 메시지가 없으면 Optional.empty를 반환한다")
        void findLatestMessageExcluding_shouldReturnEmptyWhenNoMessageAvailable() {
            saveMessage(messageId1, roomId1, "only-message", time1, false);

            Optional<MongoChatMessage> result = sut.findLatestMessageExcluding(
                    roomId1.toHexString(),
                    messageId1.toHexString()
            );

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("findLatestByRoomIdFromSecondary")
    class FindLatestByRoomIdFromSecondaryTest {

        @Test
        @DisplayName("secondary 경로로 roomId의 최신 메시지를 조회한다")
        void findLatestByRoomIdFromSecondary_shouldReturnLatestMessage() {
            saveMessage(messageId1, roomId1, "old", time1, false);
            MongoChatMessage latest = saveMessage(
                    messageId2,
                    roomId1,
                    "latest",
                    time2,
                    false
            );

            Optional<MongoChatMessage> result =
                    sut.findLatestByRoomIdFromSecondary(roomId1);

            assertThat(result).isPresent();
            assertThat(result.orElseThrow().getId()).isEqualTo(latest.getId());
        }

        @Test
        @DisplayName("같은 createdAt에서는 _id가 큰 메시지를 최신 메시지로 조회한다")
        void findLatestByRoomIdFromSecondary_shouldUseIdDescTieBreakerWhenCreatedAtSame() {
            saveMessage(messageId1, roomId1, "low-id", time1, false);
            MongoChatMessage highId = saveMessage(
                    messageId2,
                    roomId1,
                    "high-id",
                    time1,
                    false
            );

            Optional<MongoChatMessage> result =
                    sut.findLatestByRoomIdFromSecondary(roomId1);

            assertThat(result).isPresent();
            assertThat(result.orElseThrow().getId()).isEqualTo(highId.getId());
        }

        @Test
        @DisplayName("deleted=true 메시지는 최신 메시지 후보에서 제외한다")
        void findLatestByRoomIdFromSecondary_shouldExcludeDeletedMessage() {
            MongoChatMessage alive = saveMessage(
                    messageId1,
                    roomId1,
                    "alive",
                    time1,
                    false
            );
            saveMessage(messageId2, roomId1, "deleted-latest", time2, true);

            Optional<MongoChatMessage> result =
                    sut.findLatestByRoomIdFromSecondary(roomId1);

            assertThat(result).isPresent();
            assertThat(result.orElseThrow().getId()).isEqualTo(alive.getId());
        }

        @Test
        @DisplayName("다른 roomId의 메시지는 제외한다")
        void findLatestByRoomIdFromSecondary_shouldExcludeOtherRoom() {
            MongoChatMessage room1Message = saveMessage(
                    messageId1,
                    roomId1,
                    "room1-message",
                    time1,
                    false
            );
            saveMessage(messageId2, roomId2, "room2-latest", time2, false);

            Optional<MongoChatMessage> result =
                    sut.findLatestByRoomIdFromSecondary(roomId1);

            assertThat(result).isPresent();
            assertThat(result.orElseThrow().getId()).isEqualTo(room1Message.getId());
        }

        @Test
        @DisplayName("조회 가능한 메시지가 없으면 Optional.empty를 반환한다")
        void findLatestByRoomIdFromSecondary_shouldReturnEmptyWhenNoMessageAvailable() {
            Optional<MongoChatMessage> result =
                    sut.findLatestByRoomIdFromSecondary(roomId1);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("roomId가 null이면 Optional.empty를 반환한다")
        void findLatestByRoomIdFromSecondary_shouldReturnEmptyWhenRoomIdIsNull() {
            Optional<MongoChatMessage> result =
                    sut.findLatestByRoomIdFromSecondary(null);

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("listLatestMessagesByRoomIds")
    class ListLatestMessagesByRoomIdsTest {

        @Test
        @DisplayName("여러 roomId에 대해 각 방의 최신 메시지를 하나씩 조회한다")
        void listLatestMessagesByRoomIds_shouldReturnLatestMessageByEachRoom() {
            MongoChatMessage room1Latest = saveMessage(
                    messageId2,
                    roomId1,
                    "room1-latest",
                    time2,
                    false
            );
            saveMessage(messageId1, roomId1, "room1-old", time1, false);

            saveMessage(messageId3, roomId2, "room2-old", time1, false);
            MongoChatMessage room2Latest = saveMessage(
                    messageId4,
                    roomId2,
                    "room2-latest",
                    time3,
                    false
            );

            List<MongoChatMessage> result =
                    sut.listLatestMessagesByRoomIds(List.of(roomId1, roomId2));

            assertThat(result)
                    .extracting(MongoChatMessage::getId)
                    .containsExactlyInAnyOrder(
                            room1Latest.getId(),
                            room2Latest.getId()
                    );
        }

        @Test
        @DisplayName("같은 createdAt에서는 _id가 큰 메시지를 최신 메시지로 선택한다")
        void listLatestMessagesByRoomIds_shouldUseIdDescTieBreakerWhenCreatedAtSame() {
            saveMessage(messageId1, roomId1, "low-id", time1, false);
            MongoChatMessage highId = saveMessage(
                    messageId2,
                    roomId1,
                    "high-id",
                    time1,
                    false
            );

            List<MongoChatMessage> result =
                    sut.listLatestMessagesByRoomIds(List.of(roomId1));

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getId()).isEqualTo(highId.getId());
        }

        @Test
        @DisplayName("deleted=true 메시지는 최신 메시지 후보에서 제외한다")
        void listLatestMessagesByRoomIds_shouldExcludeDeletedMessage() {
            MongoChatMessage alive = saveMessage(
                    messageId1,
                    roomId1,
                    "alive",
                    time1,
                    false
            );
            saveMessage(messageId2, roomId1, "deleted-latest", time2, true);

            List<MongoChatMessage> result =
                    sut.listLatestMessagesByRoomIds(List.of(roomId1));

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getId()).isEqualTo(alive.getId());
        }

        @Test
        @DisplayName("대상 roomId가 없으면 빈 목록을 반환한다")
        void listLatestMessagesByRoomIds_shouldReturnEmptyWhenRoomIdsEmpty() {
            List<MongoChatMessage> result =
                    sut.listLatestMessagesByRoomIds(List.of());

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("secondary 경로로 여러 roomId에 대해 각 방의 최신 메시지를 하나씩 조회한다")
        void listLatestMessagesByRoomIdsFromSecondary_shouldReturnLatestMessageByEachRoom() {
            MongoChatMessage room1Latest = saveMessage(
                    messageId2,
                    roomId1,
                    "room1-latest",
                    time2,
                    false
            );
            saveMessage(messageId1, roomId1, "room1-old", time1, false);

            saveMessage(messageId3, roomId2, "room2-old", time1, false);
            MongoChatMessage room2Latest = saveMessage(
                    messageId4,
                    roomId2,
                    "room2-latest",
                    time3,
                    false
            );

            List<MongoChatMessage> result =
                    sut.listLatestMessagesByRoomIdsFromSecondary(List.of(roomId1, roomId2));

            assertThat(result)
                    .extracting(MongoChatMessage::getId)
                    .containsExactlyInAnyOrder(
                            room1Latest.getId(),
                            room2Latest.getId()
                    );
        }

        @Test
        @DisplayName("secondary 경로에서도 같은 createdAt이면 _id가 큰 메시지를 최신 메시지로 선택한다")
        void listLatestMessagesByRoomIdsFromSecondary_shouldUseIdDescTieBreakerWhenCreatedAtSame() {
            saveMessage(messageId1, roomId1, "low-id", time1, false);
            MongoChatMessage highId = saveMessage(
                    messageId2,
                    roomId1,
                    "high-id",
                    time1,
                    false
            );

            List<MongoChatMessage> result =
                    sut.listLatestMessagesByRoomIdsFromSecondary(List.of(roomId1));

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getId()).isEqualTo(highId.getId());
        }

        @Test
        @DisplayName("secondary 경로에서도 deleted=true 메시지는 최신 메시지 후보에서 제외한다")
        void listLatestMessagesByRoomIdsFromSecondary_shouldExcludeDeletedMessage() {
            MongoChatMessage alive = saveMessage(
                    messageId1,
                    roomId1,
                    "alive",
                    time1,
                    false
            );
            saveMessage(messageId2, roomId1, "deleted-latest", time2, true);

            List<MongoChatMessage> result =
                    sut.listLatestMessagesByRoomIdsFromSecondary(List.of(roomId1));

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getId()).isEqualTo(alive.getId());
        }

        @Test
        @DisplayName("secondary 경로에서 대상 roomId가 없으면 빈 목록을 반환한다")
        void listLatestMessagesByRoomIdsFromSecondary_shouldReturnEmptyWhenRoomIdsEmpty() {
            List<MongoChatMessage> result =
                    sut.listLatestMessagesByRoomIdsFromSecondary(List.of());

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("secondary 경로에서 roomIds가 null이면 빈 목록을 반환한다")
        void listLatestMessagesByRoomIdsFromSecondary_shouldReturnEmptyWhenRoomIdsNull() {
            List<MongoChatMessage> result =
                    sut.listLatestMessagesByRoomIdsFromSecondary(null);

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("listMessagesBefore")
    class ListMessagesBeforeTest {

        @Test
        @DisplayName("cursor createdAt보다 오래된 메시지를 최신순으로 조회한다")
        void listMessagesBefore_shouldReturnOlderMessagesOrderedByCreatedAtDesc() {
            saveMessage(messageId1, roomId1, "message-1", time1, false);
            saveMessage(messageId2, roomId1, "message-2", time2, false);
            saveMessage(messageId3, roomId1, "message-3", time3, false);
            saveMessage(messageId4, roomId1, "message-4", time4, false);

            List<MongoChatMessage> result = sut.listMessagesBefore(
                    roomId1,
                    messageId4,
                    time4,
                    2
            );

            assertMessageIds(result, messageId3, messageId2);
        }

        @Test
        @DisplayName("같은 createdAt에서는 _id가 cursor보다 작은 메시지를 조회한다")
        void listMessagesBefore_shouldUseIdTieBreakerWhenCreatedAtSame() {
            saveMessage(messageId1, roomId1, "low-id", time3, false);
            saveMessage(messageId2, roomId1, "mid-id", time3, false);
            saveMessage(messageId3, roomId1, "high-id", time3, false);

            List<MongoChatMessage> result = sut.listMessagesBefore(
                    roomId1,
                    messageId3,
                    time3,
                    10
            );

            assertMessageIds(result, messageId2, messageId1);
        }

        @Test
        @DisplayName("deleted=true 메시지는 제외한다")
        void listMessagesBefore_shouldExcludeDeletedMessage() {
            saveMessage(messageId1, roomId1, "alive-old", time1, false);
            saveMessage(messageId2, roomId1, "deleted-mid", time2, true);
            saveMessage(messageId3, roomId1, "alive-new", time3, false);

            List<MongoChatMessage> result = sut.listMessagesBefore(
                    roomId1,
                    messageId3,
                    time3,
                    10
            );

            assertMessageIds(result, messageId1);
        }

        @Test
        @DisplayName("다른 roomId의 메시지는 제외한다")
        void listMessagesBefore_shouldExcludeOtherRoom() {
            saveMessage(messageId1, roomId1, "room1-old", time1, false);
            saveMessage(messageId2, roomId2, "room2-mid", time2, false);
            saveMessage(messageId3, roomId1, "room1-new", time3, false);

            List<MongoChatMessage> result = sut.listMessagesBefore(
                    roomId1,
                    messageId3,
                    time3,
                    10
            );

            assertMessageIds(result, messageId1);
        }

        @Test
        @DisplayName("limit을 적용한다")
        void listMessagesBefore_shouldApplyLimit() {
            saveMessage(messageId1, roomId1, "message-1", time1, false);
            saveMessage(messageId2, roomId1, "message-2", time2, false);
            saveMessage(messageId3, roomId1, "message-3", time3, false);
            saveMessage(messageId4, roomId1, "message-4", time4, false);

            List<MongoChatMessage> result = sut.listMessagesBefore(
                    roomId1,
                    messageId4,
                    time4,
                    1
            );

            assertMessageIds(result, messageId3);
        }

        @Test
        @DisplayName("cursor 이전 메시지가 없으면 빈 목록을 반환한다")
        void listMessagesBefore_shouldReturnEmptyWhenNoPreviousMessage() {
            saveMessage(messageId1, roomId1, "oldest", time1, false);

            List<MongoChatMessage> result = sut.listMessagesBefore(
                    roomId1,
                    messageId1,
                    time1,
                    10
            );

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("softDeleteByRoomId")
    class SoftDeleteByRoomIdTest {

        @Test
        @DisplayName("같은 roomId의 메시지를 모두 soft delete 처리한다")
        void softDeleteByRoomId_shouldSoftDeleteMessagesInSameRoom() {
            saveMessage(messageId1, roomId1, "room1-message-1", time1, false);
            saveMessage(messageId2, roomId1, "room1-message-2", time2, false);
            saveMessage(messageId3, roomId2, "room2-message", time3, false);

            sut.softDeleteByRoomId(roomId1);

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
    @DisplayName("hardDeleteById")
    class HardDeleteByIdTest {

        @Test
        @DisplayName("존재하는 메시지를 물리 삭제하고 true를 반환한다")
        void hardDeleteById_shouldDeleteMessageAndReturnTrue() {
            saveMessage(messageId1, roomId1, "delete-target", time1, false);

            boolean result = sut.hardDeleteById(messageId1);

            assertThat(result).isTrue();
            assertThat(sut.findById(messageId1)).isEmpty();
        }

        @Test
        @DisplayName("존재하지 않는 메시지를 삭제하면 false를 반환한다")
        void hardDeleteById_shouldReturnFalseWhenMessageNotFound() {
            boolean result = sut.hardDeleteById(messageId1);

            assertThat(result).isFalse();
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
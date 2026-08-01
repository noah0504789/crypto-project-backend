package org.example.notification.adapter.out.persistence;

import config.TestMongoConfig;
import org.bson.types.ObjectId;
import org.example.common.test.config.TestBootApplication;
import org.example.common.test.testcontainer.MongoDBTestContainerInitializer;
import org.example.common.time.ServiceZoneUtils;
import org.example.notification.domain.model.NotificationRecipient;
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

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataMongoTest
@ContextConfiguration(
        classes = {TestBootApplication.class, TestMongoConfig.class},
        initializers = MongoDBTestContainerInitializer.class
)
class MongoNotificationRecipientRepositoryImplIntegrationTest {

    @Autowired
    private MongoNotificationRecipientRepository sut;

    @Autowired
    @Qualifier("primaryMongoTemplate")
    private MongoTemplate mongoTemplate;

    private final UUID receiverId1 = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private final UUID receiverId2 = UUID.fromString("00000000-0000-0000-0000-000000000002");

    private final ObjectId notificationId1 = new ObjectId("100000000000000000000001");
    private final ObjectId notificationId2 = new ObjectId("100000000000000000000002");
    private final ObjectId notificationId3 = new ObjectId("100000000000000000000003");
    private final ObjectId notificationId4 = new ObjectId("100000000000000000000004");

    private final ObjectId recipientId1 = new ObjectId("200000000000000000000001");
    private final ObjectId recipientId2 = new ObjectId("200000000000000000000002");
    private final ObjectId recipientId3 = new ObjectId("200000000000000000000003");
    private final ObjectId recipientId4 = new ObjectId("200000000000000000000004");

    private final Instant time1 = Instant.parse("2026-01-01T01:00:00Z");
    private final Instant time2 = Instant.parse("2026-01-01T02:00:00Z");
    private final Instant time3 = Instant.parse("2026-01-01T03:00:00Z");
    private final Instant time4 = Instant.parse("2026-01-01T04:00:00Z");

    @BeforeEach
    void setUp() {
        mongoTemplate.getDb().drop();

        mongoTemplate.indexOps(MongoNotificationRecipient.class)
                .ensureIndex(new Index()
                        .on("notificationId", Sort.Direction.ASC)
                        .on("receiverId", Sort.Direction.ASC)
                        .unique()
                        .named("ux_notification_recipient_notification_receiver"));

        mongoTemplate.indexOps(MongoNotificationRecipient.class)
                .ensureIndex(new Index()
                        .on("receiverId", Sort.Direction.ASC)
                        .on("deliveredAt", Sort.Direction.DESC)
                        .on("_id", Sort.Direction.DESC)
                        .named("idx_receiver_delivered"));

        mongoTemplate.indexOps(MongoNotificationRecipient.class)
                .ensureIndex(new Index()
                        .on("receiverId", Sort.Direction.ASC)
                        .on("read", Sort.Direction.ASC)
                        .on("deliveredAt", Sort.Direction.DESC)
                        .named("idx_receiver_read_delivered"));

        mongoTemplate.indexOps(MongoNotificationRecipient.class)
                .ensureIndex(new Index()
                        .on("notificationId", Sort.Direction.ASC)
                        .named("idx_notification"));
    }

    @Nested
    @DisplayName("listLatest")
    class ListLatestTest {

        @Test
        @DisplayName("receiverId 기준으로 deliveredAt desc, _id desc 순서로 최신 목록을 조회한다")
        void listLatest_shouldReturnByReceiverOrderedByDeliveredAtDescAndIdDesc() {
            saveRecipient(recipientId1, notificationId1, receiverId1, false, null, time1);
            saveRecipient(recipientId2, notificationId2, receiverId1, false, null, time3);
            saveRecipient(recipientId3, notificationId3, receiverId1, false, null, time2);
            saveRecipient(recipientId4, notificationId4, receiverId2, false, null, time4);

            List<MongoNotificationRecipient> actual = sut.listLatest(receiverId1, 10);

            assertRecipientIds(actual, recipientId2, recipientId3, recipientId1);
        }

        @Test
        @DisplayName("deliveredAt이 같으면 _id desc 순서로 조회한다")
        void listLatest_shouldSortByIdDescWhenDeliveredAtSame() {
            saveRecipient(recipientId1, notificationId1, receiverId1, false, null, time3);
            saveRecipient(recipientId2, notificationId2, receiverId1, false, null, time3);
            saveRecipient(recipientId3, notificationId3, receiverId1, false, null, time3);

            List<MongoNotificationRecipient> actual = sut.listLatest(receiverId1, 10);

            assertRecipientIds(actual, recipientId3, recipientId2, recipientId1);
        }

        @Test
        @DisplayName("limit 개수만큼 조회한다")
        void listLatest_shouldApplyLimit() {
            saveRecipient(recipientId1, notificationId1, receiverId1, false, null, time1);
            saveRecipient(recipientId2, notificationId2, receiverId1, false, null, time2);
            saveRecipient(recipientId3, notificationId3, receiverId1, false, null, time3);

            List<MongoNotificationRecipient> actual = sut.listLatest(receiverId1, 2);

            assertRecipientIds(actual, recipientId3, recipientId2);
        }

        @Test
        @DisplayName("receiverId가 null이면 빈 목록을 반환한다")
        void listLatest_shouldReturnEmptyWhenReceiverIdIsNull() {
            saveRecipient(recipientId1, notificationId1, receiverId1, false, null, time1);

            List<MongoNotificationRecipient> actual = sut.listLatest(null, 10);

            assertThat(actual).isEmpty();
        }

        @Test
        @DisplayName("limit이 0 이하이면 빈 목록을 반환한다")
        void listLatest_shouldReturnEmptyWhenLimitIsInvalid() {
            saveRecipient(recipientId1, notificationId1, receiverId1, false, null, time1);

            List<MongoNotificationRecipient> actual = sut.listLatest(receiverId1, 0);

            assertThat(actual).isEmpty();
        }
    }

    @Nested
    @DisplayName("listHistoricalBefore")
    class ListHistoricalBeforeTest {

        @Test
        @DisplayName("커서보다 이전 데이터를 deliveredAt desc, _id desc 순서로 조회한다")
        void listHistoricalBefore_shouldReturnPreviousItemsByCursor() {
            saveRecipient(recipientId1, notificationId1, receiverId1, false, null, time1);
            saveRecipient(recipientId2, notificationId2, receiverId1, false, null, time2);
            saveRecipient(recipientId3, notificationId3, receiverId1, false, null, time3);
            saveRecipient(recipientId4, notificationId4, receiverId1, false, null, time4);

            List<MongoNotificationRecipient> actual = sut.listHistoricalBefore(
                    receiverId1,
                    recipientId3,
                    time3,
                    10
            );

            assertRecipientIds(actual, recipientId2, recipientId1);
        }

        @Test
        @DisplayName("deliveredAt이 같으면 _id가 커서보다 작은 데이터만 조회한다")
        void listHistoricalBefore_shouldReturnItemsWithSameDeliveredAtAndLowerId() {
            saveRecipient(recipientId1, notificationId1, receiverId1, false, null, time3);
            saveRecipient(recipientId2, notificationId2, receiverId1, false, null, time3);
            saveRecipient(recipientId3, notificationId3, receiverId1, false, null, time3);
            saveRecipient(recipientId4, notificationId4, receiverId1, false, null, time2);

            List<MongoNotificationRecipient> actual = sut.listHistoricalBefore(
                    receiverId1,
                    recipientId3,
                    time3,
                    10
            );

            assertRecipientIds(actual, recipientId2, recipientId1, recipientId4);
        }

        @Test
        @DisplayName("다른 receiverId의 데이터는 제외한다")
        void listHistoricalBefore_shouldExcludeOtherReceiverItems() {
            saveRecipient(recipientId1, notificationId1, receiverId1, false, null, time1);
            saveRecipient(recipientId2, notificationId2, receiverId1, false, null, time2);
            saveRecipient(recipientId3, notificationId3, receiverId1, false, null, time3);
            saveRecipient(recipientId4, notificationId4, receiverId2, false, null, time2);

            List<MongoNotificationRecipient> actual = sut.listHistoricalBefore(
                    receiverId1,
                    recipientId3,
                    time3,
                    10
            );

            assertRecipientIds(actual, recipientId2, recipientId1);
        }

        @Test
        @DisplayName("limit 개수만큼 조회한다")
        void listHistoricalBefore_shouldApplyLimit() {
            saveRecipient(recipientId1, notificationId1, receiverId1, false, null, time1);
            saveRecipient(recipientId2, notificationId2, receiverId1, false, null, time2);
            saveRecipient(recipientId3, notificationId3, receiverId1, false, null, time3);
            saveRecipient(recipientId4, notificationId4, receiverId1, false, null, time4);

            List<MongoNotificationRecipient> actual = sut.listHistoricalBefore(
                    receiverId1,
                    recipientId4,
                    time4,
                    2
            );

            assertRecipientIds(actual, recipientId3, recipientId2);
        }

        @Test
        @DisplayName("커서 이전 데이터가 없으면 빈 목록을 반환한다")
        void listHistoricalBefore_shouldReturnEmptyWhenNoPreviousItems() {
            saveRecipient(recipientId1, notificationId1, receiverId1, false, null, time1);

            List<MongoNotificationRecipient> actual = sut.listHistoricalBefore(
                    receiverId1,
                    recipientId1,
                    time1,
                    10
            );

            assertThat(actual).isEmpty();
        }

        @Test
        @DisplayName("인자가 유효하지 않으면 빈 목록을 반환한다")
        void listHistoricalBefore_shouldReturnEmptyWhenArgumentsAreInvalid() {
            saveRecipient(recipientId1, notificationId1, receiverId1, false, null, time1);

            assertThat(sut.listHistoricalBefore(null, recipientId1, time1, 10)).isEmpty();
            assertThat(sut.listHistoricalBefore(receiverId1, null, time1, 10)).isEmpty();
            assertThat(sut.listHistoricalBefore(receiverId1, recipientId1, null, 10)).isEmpty();
            assertThat(sut.listHistoricalBefore(receiverId1, recipientId1, time1, 0)).isEmpty();
        }
    }

    @Nested
    @DisplayName("saveAllBulk")
    class SaveAllBulkTest {

        @Test
        @DisplayName("NotificationRecipient 목록을 자연 키 기준 bulk upsert 한다")
        void saveAllBulk_shouldInsertRecipients() {
            List<MongoNotificationRecipient> recipients = List.of(
                    createRecipient(recipientId1, notificationId1, receiverId1, false, null, time1),
                    createRecipient(recipientId2, notificationId2, receiverId1, false, null, time2),
                    createRecipient(recipientId3, notificationId3, receiverId2, false, null, time3)
            );

            sut.saveAllBulk(recipients);

            List<MongoNotificationRecipient> actual =
                    mongoTemplate.findAll(MongoNotificationRecipient.class);

            assertThat(actual)
                    .extracting(MongoNotificationRecipient::getId)
                    .containsExactlyInAnyOrder(recipientId1, recipientId2, recipientId3);
        }

        @Test
        @DisplayName("같은 notificationId와 receiverId를 다시 저장해도 수신자 레코드는 하나다")
        void saveAllBulk_shouldIgnoreDuplicateNaturalKey() {
            MongoNotificationRecipient recipient =
                    createRecipient(recipientId1, notificationId1, receiverId1, false, null, time1);

            sut.saveAllBulk(List.of(recipient));
            sut.saveAllBulk(List.of(recipient));

            List<MongoNotificationRecipient> actual =
                    mongoTemplate.findAll(MongoNotificationRecipient.class);

            assertThat(actual).hasSize(1);
            assertThat(actual.get(0).getId()).isEqualTo(recipientId1);
        }

        @Test
        @DisplayName("빈 목록이면 아무 것도 저장하지 않는다")
        void saveAllBulk_shouldDoNothingWhenEmpty() {
            sut.saveAllBulk(List.of());

            List<MongoNotificationRecipient> actual =
                    mongoTemplate.findAll(MongoNotificationRecipient.class);

            assertThat(actual).isEmpty();
        }

        @Test
        @DisplayName("null이면 아무 것도 저장하지 않는다")
        void saveAllBulk_shouldDoNothingWhenNull() {
            sut.saveAllBulk(null);

            List<MongoNotificationRecipient> actual =
                    mongoTemplate.findAll(MongoNotificationRecipient.class);

            assertThat(actual).isEmpty();
        }
    }

    @Nested
    @DisplayName("markAsRead")
    class MarkAsReadTest {

        @Test
        @DisplayName("읽지 않은 수신자 알림을 읽음 처리한다")
        void markAsRead_shouldUpdateUnreadRecipient() {
            saveRecipient(recipientId1, notificationId1, receiverId1, false, null, time1);

            Instant readAt = Instant.parse("2026-01-01T05:00:00Z");

            long modifiedCount = sut.markAsRead(notificationId1, receiverId1, readAt);

            assertThat(modifiedCount).isEqualTo(1L);

            MongoNotificationRecipient actual = mongoTemplate.findById(
                    recipientId1,
                    MongoNotificationRecipient.class
            );

            assertThat(actual).isNotNull();
            assertThat(actual.isRead()).isTrue();
            assertThat(actual.getReadAt()).isEqualTo(readAt);
        }

        @Test
        @DisplayName("이미 읽은 수신자 알림이면 수정하지 않는다")
        void markAsRead_shouldNotUpdateAlreadyReadRecipient() {
            Instant alreadyReadAt = Instant.parse("2026-01-01T04:00:00Z");

            saveRecipient(
                    recipientId1,
                    notificationId1,
                    receiverId1,
                    true,
                    alreadyReadAt,
                    time1
            );

            Instant readAt = Instant.parse("2026-01-01T05:00:00Z");

            long modifiedCount = sut.markAsRead(notificationId1, receiverId1, readAt);

            assertThat(modifiedCount).isZero();

            MongoNotificationRecipient actual = mongoTemplate.findById(
                    recipientId1,
                    MongoNotificationRecipient.class
            );

            assertThat(actual).isNotNull();
            assertThat(actual.isRead()).isTrue();
            assertThat(actual.getReadAt()).isEqualTo(alreadyReadAt);
        }

        @Test
        @DisplayName("다른 receiverId의 수신자 알림은 수정하지 않는다")
        void markAsRead_shouldNotUpdateOtherReceiverRecipient() {
            saveRecipient(recipientId1, notificationId1, receiverId2, false, null, time1);

            Instant readAt = Instant.parse("2026-01-01T05:00:00Z");

            long modifiedCount = sut.markAsRead(notificationId1, receiverId1, readAt);

            assertThat(modifiedCount).isZero();

            MongoNotificationRecipient actual = mongoTemplate.findById(
                    recipientId1,
                    MongoNotificationRecipient.class
            );

            assertThat(actual).isNotNull();
            assertThat(actual.isRead()).isFalse();
            assertThat(actual.getReadAt()).isNull();
        }

        @Test
        @DisplayName("인자가 유효하지 않으면 0을 반환한다")
        void markAsRead_shouldReturnZeroWhenArgumentsAreInvalid() {
            saveRecipient(recipientId1, notificationId1, receiverId1, false, null, time1);

            Instant readAt = Instant.parse("2026-01-01T05:00:00Z");

            assertThat(sut.markAsRead(null, receiverId1, readAt)).isZero();
            assertThat(sut.markAsRead(notificationId1, null, readAt)).isZero();
            assertThat(sut.markAsRead(notificationId1, receiverId1, null)).isZero();
        }
    }

    private void saveRecipient(
            ObjectId recipientId,
            ObjectId notificationId,
            UUID receiverId,
            boolean read,
            Instant readAt,
            Instant deliveredAt
    ) {
        MongoNotificationRecipient recipient = createRecipient(
                recipientId,
                notificationId,
                receiverId,
                read,
                readAt,
                deliveredAt
        );

        mongoTemplate.save(recipient);
    }

    private MongoNotificationRecipient createRecipient(
            ObjectId recipientId,
            ObjectId notificationId,
            UUID receiverId,
            boolean read,
            Instant readAt,
            Instant deliveredAt
    ) {
        NotificationRecipient recipient = NotificationRecipient.rehydrate(
                recipientId.toHexString(),
                notificationId.toHexString(),
                receiverId,
                read,
                toLocalDateTime(readAt),
                toLocalDateTime(deliveredAt)
        );

        return MongoNotificationRecipient.fromDomain(recipient);
    }

    private LocalDateTime toLocalDateTime(Instant instant) {
        if (instant == null) {
            return null;
        }

        return LocalDateTime.ofInstant(instant, ServiceZoneUtils.ZONE_ID);
    }

    private void assertRecipientIds(
            List<MongoNotificationRecipient> actual,
            ObjectId... expected
    ) {
        assertThat(actual)
                .extracting(MongoNotificationRecipient::getId)
                .containsExactly(expected);
    }
}

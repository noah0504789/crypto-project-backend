package org.example.chat.chatroom.adapter.out.cache;

import config.TestRedisConfig;
import org.example.chat.chatmessage.adapter.out.cache.RedisChatMessageAdapter;
import org.example.chat.chatmessage.domain.model.ChatMessage;
import org.example.chat.chatroom.application.service.result.ChatRoomActivityClaim;
import org.example.chat.chatroom.application.service.result.ChatRoomActivityProjectionResult;
import org.example.chat.chatroom.application.service.result.ChatRoomMemberActivity;
import org.example.chat.chatroom.domain.model.ChatRoom;
import org.example.chat.chatroom.domain.model.ChatRoomCategory;
import org.example.chat.chatroom.domain.service.MyChatRoomScoreCalculator;
import org.example.common.test.config.TestBootApplication;
import org.example.common.test.testcontainer.RedisTestContainerInitializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.redis.DataRedisTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.ContextConfiguration;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.example.common.enums.RedisKey.CHAT_ROOM_ACTIVE_BY_MEMBER_INDEX;
import static org.example.common.enums.RedisKey.CHAT_ROOM_ACTIVITY_RECENT_INDEX;
import static org.example.common.enums.RedisKey.CHAT_ROOM_ACTIVITY_INFLIGHT_INDEX;
import static org.example.common.enums.RedisKey.CHAT_ROOM_LAST_READ_SEQ;

@DataRedisTest(properties = {"spring.data.redis.repositories.enabled=false"})
@ContextConfiguration(
        classes = {TestBootApplication.class, TestRedisConfig.class},
        initializers = RedisTestContainerInitializer.class
)
class RedisChatRoomActivityProjectionAdapterIntegrationTest {

    @Autowired
    private RedisChatRoomActivityProjectionAdapter sut;

    @Autowired
    private RedisChatRoomAdapter chatRoomAdapter;

    @Autowired
    private RedisChatMessageAdapter chatMessageAdapter;

    @Autowired
    private RedisTemplate<String, String> masterHashRedisTemplate;

    private static final String ROOM_ID = "000000000000000000000001";
    private static final String HOST_ID = "member-1";
    private static final String MEMBER_ID = "member-2";
    private static final String OTHER_MEMBER_ID = "member-3";

    private static final long NOW_MS = 1_800_000_000_000L;
    private static final long UNREAD_BOOST = 100_000_000_000_000L;

    private static final LocalDateTime MESSAGE_TIME_1 = LocalDateTime.of(2026, 1, 1, 10, 0);
    private static final LocalDateTime MESSAGE_TIME_2 = LocalDateTime.of(2026, 1, 1, 11, 0);

    @BeforeEach
    void setUp() {
        masterHashRedisTemplate.getConnectionFactory()
                .getConnection()
                .serverCommands()
                .flushDb();

        chatRoomAdapter.save(room());
    }

    @Nested
    @DisplayName("dirty 표시")
    class DirtyMarking {

        @Test
        @DisplayName("메시지 저장이 방을 dirty 목록에 한 번만 남긴다")
        void save_multipleMessagesInSameRoom_marksRoomOnce() {
            chatMessageAdapter.save(message("100000000000000000000001", MESSAGE_TIME_1), members());
            chatMessageAdapter.save(message("100000000000000000000002", MESSAGE_TIME_2), members());

            Long dirtyRooms = masterHashRedisTemplate.opsForZSet().size(CHAT_ROOM_ACTIVITY_RECENT_INDEX.keyFor());
            Double activityMs = masterHashRedisTemplate.opsForZSet()
                    .score(CHAT_ROOM_ACTIVITY_RECENT_INDEX.keyFor(), ROOM_ID);

            assertThat(dirtyRooms).isEqualTo(1);
            assertThat(activityMs).isEqualTo(createdAtMs(MESSAGE_TIME_2));
        }
    }

    @Nested
    @DisplayName("claimDirtyRooms")
    class ClaimDirtyRooms {

        @Test
        @DisplayName("claim 한 방은 dirty 에서 빠지고 inflight 로 옮겨진다")
        void claimDirtyRooms_dirtyRoom_movesToInflight() {
            chatMessageAdapter.save(message("100000000000000000000001", MESSAGE_TIME_1), members());

            List<ChatRoomActivityClaim> claims = sut.claimDirtyRooms(10, NOW_MS);

            assertThat(claims)
                    .extracting(ChatRoomActivityClaim::roomId, ChatRoomActivityClaim::activityMs)
                    .containsExactly(org.assertj.core.groups.Tuple.tuple(ROOM_ID, (long) createdAtMs(MESSAGE_TIME_1)));
            assertThat(sut.countDirtyRooms()).isZero();
            assertThat(masterHashRedisTemplate.opsForZSet().score(CHAT_ROOM_ACTIVITY_INFLIGHT_INDEX.keyFor(), ROOM_ID))
                    .isEqualTo(NOW_MS);
        }

        @Test
        @DisplayName("같은 방을 두 번 claim 하지 않는다")
        void claimDirtyRooms_calledTwice_returnsRoomOnce() {
            chatMessageAdapter.save(message("100000000000000000000001", MESSAGE_TIME_1), members());

            sut.claimDirtyRooms(10, NOW_MS);
            List<ChatRoomActivityClaim> second = sut.claimDirtyRooms(10, NOW_MS);

            assertThat(second).isEmpty();
        }
    }

    @Nested
    @DisplayName("project")
    class Project {

        @Test
        @DisplayName("읽지 않은 멤버에게만 unread 가중치를 준다")
        void project_partiallyReadRoom_appliesUnreadBoostOnlyToUnreadMembers() {
            chatMessageAdapter.save(message("100000000000000000000001", MESSAGE_TIME_1), members());
            long createdAtMs = (long) createdAtMs(MESSAGE_TIME_1);

            // 방 latest_msg_seq 는 메시지 저장으로 1 이 됐다. 읽음 위치가 그 값에 도달한 멤버만 read 다.
            chatRoomAdapter.updateLastReadSeq(ROOM_ID, MEMBER_ID, 1L);

            ChatRoomActivityProjectionResult result = sut.project(ROOM_ID, createdAtMs);

            assertThat(result.cacheMiss()).isFalse();
            assertThat(result.updatedMembers()).isEqualTo(3);
            assertThat(activeScore(MEMBER_ID)).isEqualTo(MyChatRoomScoreCalculator.read(createdAtMs));
            assertThat(activeScore(OTHER_MEMBER_ID)).isEqualTo(MyChatRoomScoreCalculator.unread(createdAtMs));
            assertThat(activeScore(HOST_ID)).isEqualTo(MyChatRoomScoreCalculator.unread(createdAtMs));
        }

        @Test
        @DisplayName("claim 이후 도착한 메시지의 최신 시각까지 같은 flush 로 반영한다")
        void project_messageArrivedAfterClaim_usesLatestCachedActivity() {
            chatMessageAdapter.save(message("100000000000000000000001", MESSAGE_TIME_1), members());
            List<ChatRoomActivityClaim> claims = sut.claimDirtyRooms(10, NOW_MS);
            chatMessageAdapter.save(message("100000000000000000000002", MESSAGE_TIME_2), members());

            sut.project(claims.get(0).roomId(), claims.get(0).activityMs());

            assertThat(activeScore(OTHER_MEMBER_ID))
                    .isEqualTo(UNREAD_BOOST + (long) createdAtMs(MESSAGE_TIME_2));
        }

        @Test
        @DisplayName("claim score 가 더 커도 활동 시각은 캐시의 최신 메시지가 정한다")
        void project_claimScoreNewerThanCache_usesCachedLatestMessage() {
            // 최신 메시지가 지워져 claim score 만 과거 시각을 물고 있는 상황.
            // 둘의 max 를 쓰면 사라진 메시지 시각이 계속 이긴다.
            chatMessageAdapter.warmUpList(List.of(message("100000000000000000000001", MESSAGE_TIME_1)), ROOM_ID);
            long staleClaimMs = (long) createdAtMs(MESSAGE_TIME_2);

            sut.project(ROOM_ID, staleClaimMs);

            assertThat(activeScore(HOST_ID)).isEqualTo((long) createdAtMs(MESSAGE_TIME_1));
            assertThat(activeScore(HOST_ID)).isNotEqualTo(staleClaimMs);
        }

        @Test
        @DisplayName("메시지 캐시가 비었으면 claim score 로 물러선다")
        void project_emptyMessageCache_fallsBackToClaimScore() {
            long claimMs = (long) createdAtMs(MESSAGE_TIME_1);

            sut.project(ROOM_ID, claimMs);

            assertThat(activeScore(HOST_ID)).isEqualTo(claimMs);
        }

        @Test
        @DisplayName("성공하면 inflight 에서 제거한다")
        void project_success_removesInflight() {
            chatMessageAdapter.save(message("100000000000000000000001", MESSAGE_TIME_1), members());
            sut.claimDirtyRooms(10, NOW_MS);

            sut.project(ROOM_ID, (long) createdAtMs(MESSAGE_TIME_1));

            assertThat(masterHashRedisTemplate.opsForZSet().score(CHAT_ROOM_ACTIVITY_INFLIGHT_INDEX.keyFor(), ROOM_ID))
                    .isNull();
        }

        @Test
        @DisplayName("방 캐시가 없으면 cacheMiss 로 알리고 inflight 에 남긴다")
        void project_roomCacheEvicted_returnsCacheMiss() {
            chatMessageAdapter.save(message("100000000000000000000001", MESSAGE_TIME_1), members());
            sut.claimDirtyRooms(10, NOW_MS);
            chatRoomAdapter.invalidateRoomInfo(ROOM_ID);

            ChatRoomActivityProjectionResult result = sut.project(ROOM_ID, (long) createdAtMs(MESSAGE_TIME_1));

            assertThat(result.cacheMiss()).isTrue();
            assertThat(masterHashRedisTemplate.opsForZSet().score(CHAT_ROOM_ACTIVITY_INFLIGHT_INDEX.keyFor(), ROOM_ID))
                    .isEqualTo(NOW_MS);
        }
    }

    @Nested
    @DisplayName("reclaimStalledRooms")
    class ReclaimStalledRooms {

        @Test
        @DisplayName("claim timeout 을 넘긴 방만 회수하고 lease 를 연장한다")
        void reclaimStalledRooms_stalledRoom_returnsRoomAndRenewsLease() {
            chatMessageAdapter.save(message("100000000000000000000001", MESSAGE_TIME_1), members());
            sut.claimDirtyRooms(10, NOW_MS);

            assertThat(sut.reclaimStalledRooms(NOW_MS - 1, 10, NOW_MS + 5_000)).isEmpty();

            List<String> stalled = sut.reclaimStalledRooms(NOW_MS, 10, NOW_MS + 5_000);

            assertThat(stalled).containsExactly(ROOM_ID);
            assertThat(masterHashRedisTemplate.opsForZSet().score(CHAT_ROOM_ACTIVITY_INFLIGHT_INDEX.keyFor(), ROOM_ID))
                    .isEqualTo(NOW_MS + 5_000);
        }
    }

    @Nested
    @DisplayName("rebuild")
    class Rebuild {

        @Test
        @DisplayName("Mongo 기준 값으로 읽음 위치와 정렬 인덱스를 재생성한다")
        void rebuild_memberActivities_restoresLastReadAndActiveIndex() {
            sut.rebuild(ROOM_ID, List.of(
                    new ChatRoomMemberActivity(MEMBER_ID, 7L, MyChatRoomScoreCalculator.read(NOW_MS)),
                    new ChatRoomMemberActivity(OTHER_MEMBER_ID, 2L, MyChatRoomScoreCalculator.unread(NOW_MS))
            ));

            assertThat(lastReadSeq(MEMBER_ID)).isEqualTo("7");
            assertThat(activeScore(MEMBER_ID)).isEqualTo(MyChatRoomScoreCalculator.read(NOW_MS));
            assertThat(activeScore(OTHER_MEMBER_ID)).isEqualTo(MyChatRoomScoreCalculator.unread(NOW_MS));
        }

        @Test
        @DisplayName("캐시에 더 최신 읽음 위치가 있으면 되돌리지 않는다")
        void rebuild_staleReadSeq_keepsNewerCachedValue() {
            chatRoomAdapter.updateLastReadSeq(ROOM_ID, MEMBER_ID, 9L);

            sut.rebuild(ROOM_ID, List.of(
                    new ChatRoomMemberActivity(MEMBER_ID, 3L, MyChatRoomScoreCalculator.read(NOW_MS))
            ));

            assertThat(lastReadSeq(MEMBER_ID)).isEqualTo("9");
        }
    }

    @Nested
    @DisplayName("requeueDirty")
    class RequeueDirty {

        @Test
        @DisplayName("실패한 방을 dirty 로 되돌리고 inflight 에서 뺀다")
        void requeueDirty_claimedRoom_movesBackToDirty() {
            chatMessageAdapter.save(message("100000000000000000000001", MESSAGE_TIME_1), members());
            sut.claimDirtyRooms(10, NOW_MS);

            sut.requeueDirty(ROOM_ID, (long) createdAtMs(MESSAGE_TIME_1));

            assertThat(sut.countDirtyRooms()).isEqualTo(1);
            assertThat(masterHashRedisTemplate.opsForZSet().score(CHAT_ROOM_ACTIVITY_INFLIGHT_INDEX.keyFor(), ROOM_ID))
                    .isNull();
        }
    }

    private ChatRoom room() {
        return ChatRoom.rehydrate(
                ROOM_ID,
                HOST_ID,
                "방 제목",
                "방 설명",
                ChatRoomCategory.FREE,
                Set.of(HOST_ID, MEMBER_ID, OTHER_MEMBER_ID),
                0L,
                LocalDateTime.of(2026, 1, 1, 0, 0)
        );
    }

    private Set<String> members() {
        return Set.of(HOST_ID, MEMBER_ID, OTHER_MEMBER_ID);
    }

    private ChatMessage message(String messageId, LocalDateTime createdAt) {
        return ChatMessage.create(messageId, ROOM_ID, OTHER_MEMBER_ID, "메시지", createdAt);
    }

    private double createdAtMs(LocalDateTime createdAt) {
        return ChatMessage.create("100000000000000000000009", ROOM_ID, HOST_ID, "x", createdAt).createdAtEpochMillis();
    }

    private long activeScore(String memberId) {
        Double score = masterHashRedisTemplate.opsForZSet()
                .score(CHAT_ROOM_ACTIVE_BY_MEMBER_INDEX.keyFor(memberId), ROOM_ID);

        return score == null ? -1L : score.longValue();
    }

    private String lastReadSeq(String memberId) {
        return (String) masterHashRedisTemplate.opsForHash()
                .get(CHAT_ROOM_LAST_READ_SEQ.keyFor(ROOM_ID), memberId);
    }
}

package org.example.chat.chatmessage.adapter.out.cache;

import org.example.common.test.config.TestBootApplication;
import config.TestRedisConfig;
import org.example.common.test.testcontainer.RedisTestContainerInitializer;
import org.example.chat.chatmessage.domain.model.ChatMessage;
import org.example.chat.chatroom.application.service.result.ChatRoomMembershipScore;
import org.example.common.time.Clock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.redis.DataRedisTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.example.common.enums.RedisKey.*;
import static org.mockito.BDDMockito.given;

@DataRedisTest(properties = {"spring.data.redis.repositories.enabled=false"})
@ContextConfiguration(
        classes = {TestBootApplication.class, TestRedisConfig.class},
        initializers = RedisTestContainerInitializer.class
)
class RedisChatMessageAdapterIntegrationTest {

    @Autowired
    private RedisChatMessageAdapter sut;

    @Autowired
    private RedisTemplate<String, String> masterHashRedisTemplate;

    @MockitoBean
    private Clock clock;

    private final String ROOM_ID = "000000000000000000000001";
    private final String MESSAGE_ID_1 = "100000000000000000000001";
    private final String MESSAGE_ID_2 = "100000000000000000000002";
    private final String MESSAGE_ID_3 = "100000000000000000000003";
    private final String MESSAGE_ID_4 = "100000000000000000000004";

    private final String WRITER_ID = "writer-1";
    private final String MEMBER_ID = "member-1";
    private final String OTHER_MEMBER_ID = "member-2";

    private final String CONTENT_1 = "첫 번째 메시지";
    private final String CONTENT_2 = "두 번째 메시지";
    private final String CONTENT_3 = "세 번째 메시지";
    private final String CONTENT_4 = "네 번째 메시지";

    private final long UNREAD_BOOST = 100_000_000_000_000L;

    private final LocalDateTime time1 = LocalDateTime.of(2026, 1, 1, 10, 0);
    private final LocalDateTime time2 = LocalDateTime.of(2026, 1, 1, 11, 0);
    private final LocalDateTime time3 = LocalDateTime.of(2026, 1, 1, 12, 0);
    private final LocalDateTime time4 = LocalDateTime.of(2026, 1, 1, 13, 0);

    private final Instant FIXED_NOW = Instant.parse("2026-01-01T00:00:00Z");

    @BeforeEach
    void setUp() {
        masterHashRedisTemplate.getConnectionFactory()
                .getConnection()
                .serverCommands()
                .flushDb();

        given(clock.nowMs()).willReturn(FIXED_NOW.toEpochMilli());
    }

    @Nested
    @DisplayName("save")
    class SaveTest {

        @Test
        @DisplayName("메시지를 저장하면 message zset, access zset, room msgCnt, recent score를 갱신한다")
        void save() {
            // given
            Set<String> memberIds = Set.of(WRITER_ID, MEMBER_ID, OTHER_MEMBER_ID);
            ChatMessage message = message(MESSAGE_ID_1, CONTENT_1, WRITER_ID, time1);

            // when
            sut.save(message, memberIds);

            // then: save 직후 access score는 message createdAt 기준
            String messageAccessKey = CHAT_MESSAGE_ACCESS_BY_ROOM_INDEX.keyFor(ROOM_ID);
            Double accessScoreAfterSave = masterHashRedisTemplate.opsForZSet()
                    .score(messageAccessKey, MESSAGE_ID_1);

            assertThat(accessScoreAfterSave).isNotNull();
            assertThat(accessScoreAfterSave.longValue()).isEqualTo(message.createdAtEpochMillis());

            // then: message zset 조회
            List<ChatMessage> latest = sut.listLatestMessages(ROOM_ID, 10);

            assertThat(latest)
                    .extracting(ChatMessage::getId)
                    .containsExactly(MESSAGE_ID_1);

            assertThat(latest.get(0).getContent()).isEqualTo(CONTENT_1);

            // then: listLatest 호출 후 access score는 Clock 기준으로 갱신됨
            Double accessScoreAfterRead = masterHashRedisTemplate.opsForZSet()
                    .score(messageAccessKey, MESSAGE_ID_1);

            assertThat(accessScoreAfterRead).isNotNull();
            assertThat(accessScoreAfterRead.longValue()).isEqualTo(FIXED_NOW.toEpochMilli());

            String roomInfoKey = CHAT_ROOM_INFO.keyFor(ROOM_ID);
            Object msgCnt = masterHashRedisTemplate.opsForHash()
                    .get(roomInfoKey, "msg_cnt");
            Object latestMessageSeq = masterHashRedisTemplate.opsForHash()
                    .get(roomInfoKey, "latest_message_seq");

            assertThat(msgCnt).isEqualTo("1");
            assertThat(latestMessageSeq).isEqualTo("1");

            String writerRecentKey = CHAT_ROOM_ACTIVE_BY_MEMBER_INDEX.keyFor(WRITER_ID);
            Double writerRecentScore = masterHashRedisTemplate.opsForZSet()
                    .score(writerRecentKey, ROOM_ID);

            assertThat(writerRecentScore).isNotNull();
            assertThat(writerRecentScore.longValue()).isEqualTo(message.createdAtEpochMillis());

            String memberRecentKey = CHAT_ROOM_ACTIVE_BY_MEMBER_INDEX.keyFor(MEMBER_ID);
            Double memberRecentScore = masterHashRedisTemplate.opsForZSet()
                    .score(memberRecentKey, ROOM_ID);

            assertThat(memberRecentScore).isNotNull();
            assertThat(memberRecentScore.longValue()).isEqualTo(UNREAD_BOOST + message.createdAtEpochMillis());

            String otherMemberRecentKey = CHAT_ROOM_ACTIVE_BY_MEMBER_INDEX.keyFor(OTHER_MEMBER_ID);
            Double otherMemberRecentScore = masterHashRedisTemplate.opsForZSet()
                    .score(otherMemberRecentKey, ROOM_ID);

            assertThat(otherMemberRecentScore).isNotNull();
            assertThat(otherMemberRecentScore.longValue()).isEqualTo(UNREAD_BOOST + message.createdAtEpochMillis());
        }

        @Test
        @DisplayName("같은 메시지를 중복 저장하면 멱등 처리되어 msgCnt가 중복 증가하지 않는다")
        void saveIdempotent() {
            // given
            Set<String> memberIds = Set.of(WRITER_ID, MEMBER_ID);
            ChatMessage message = message(MESSAGE_ID_1, CONTENT_1, WRITER_ID, time1);

            // when
            sut.save(message, memberIds);
            sut.save(message, memberIds);

            // then
            List<ChatMessage> latest = sut.listLatestMessages(ROOM_ID, 10);

            assertThat(latest)
                    .extracting(ChatMessage::getId)
                    .containsExactly(MESSAGE_ID_1);

            String roomInfoKey = CHAT_ROOM_INFO.keyFor(ROOM_ID);
            Object msgCnt = masterHashRedisTemplate.opsForHash()
                    .get(roomInfoKey, "msg_cnt");
            Object latestMessageSeq = masterHashRedisTemplate.opsForHash()
                    .get(roomInfoKey, "latest_message_seq");

            assertThat(msgCnt).isEqualTo("1");
            assertThat(latestMessageSeq).isEqualTo("1");
        }

        @Test
        @DisplayName("기존 room hash에 watermark가 없으면 msgCnt 다음 값부터 시작한다")
        void saveShouldSeedLatestMessageSeqFromLegacyMessageCount() {
            // given
            String roomInfoKey = CHAT_ROOM_INFO.keyFor(ROOM_ID);
            masterHashRedisTemplate.opsForHash().put(roomInfoKey, "msg_cnt", "5");
            ChatMessage message = message(MESSAGE_ID_1, CONTENT_1, WRITER_ID, time1);

            // when
            sut.save(message, Set.of(WRITER_ID));

            // then
            assertThat(masterHashRedisTemplate.opsForHash().get(roomInfoKey, "msg_cnt"))
                    .isEqualTo("6");
            assertThat(masterHashRedisTemplate.opsForHash().get(roomInfoKey, "latest_message_seq"))
                    .isEqualTo("6");
        }

        @Test
        @DisplayName("memberIds에 writer만 있으면 writer recent score만 갱신한다")
        void saveWithWriterOnlyMemberIds() {
            // given
            Set<String> memberIds = Set.of(WRITER_ID);
            ChatMessage message = message(MESSAGE_ID_1, CONTENT_1, WRITER_ID, time1);

            // when
            sut.save(message, memberIds);

            // then
            String writerRecentKey = CHAT_ROOM_ACTIVE_BY_MEMBER_INDEX.keyFor(WRITER_ID);
            Double writerRecentScore = masterHashRedisTemplate.opsForZSet()
                    .score(writerRecentKey, ROOM_ID);

            assertThat(writerRecentScore).isNotNull();
            assertThat(writerRecentScore.longValue()).isEqualTo(message.createdAtEpochMillis());

            String memberRecentKey = CHAT_ROOM_ACTIVE_BY_MEMBER_INDEX.keyFor(MEMBER_ID);
            Double memberRecentScore = masterHashRedisTemplate.opsForZSet()
                    .score(memberRecentKey, ROOM_ID);

            assertThat(memberRecentScore).isNull();

            String otherMemberRecentKey = CHAT_ROOM_ACTIVE_BY_MEMBER_INDEX.keyFor(OTHER_MEMBER_ID);
            Double otherMemberRecentScore = masterHashRedisTemplate.opsForZSet()
                    .score(otherMemberRecentKey, ROOM_ID);

            assertThat(otherMemberRecentScore).isNull();
        }

        @Test
        @DisplayName("memberIds가 null이면 NullPointerException을 던진다")
        void saveWithNullMemberIds() {
            // given
            ChatMessage message = message(MESSAGE_ID_1, CONTENT_1, WRITER_ID, time1);

            // when & then
            assertThatThrownBy(() -> sut.save(message, null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("message가 null이면 NullPointerException을 던진다")
        void saveWithNullMessage() {
            // when & then
            assertThatThrownBy(() -> sut.save(null, Set.of(WRITER_ID, MEMBER_ID)))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("warmUpList / listLatest")
    class WarmUpAndListLatestTest {

        @Test
        @DisplayName("warmUpList는 메시지 목록을 저장하고 listLatest는 최신순으로 조회한다")
        void warmUpListAndListLatest() {
            // given
            ChatMessage oldMessage = message(MESSAGE_ID_1, CONTENT_1, WRITER_ID, time1);
            ChatMessage midMessage = message(MESSAGE_ID_2, CONTENT_2, WRITER_ID, time2);
            ChatMessage latestMessage = message(MESSAGE_ID_3, CONTENT_3, WRITER_ID, time3);

            sut.warmUpList(List.of(oldMessage, midMessage, latestMessage), ROOM_ID);

            // when
            List<ChatMessage> result = sut.listLatestMessages(ROOM_ID, 2);

            // then
            assertThat(result)
                    .extracting(ChatMessage::getId)
                    .containsExactly(MESSAGE_ID_3, MESSAGE_ID_2);

            assertAccessed(MESSAGE_ID_3);
            assertAccessed(MESSAGE_ID_2);
        }

        @Test
        @DisplayName("메시지가 없으면 빈 목록을 반환한다")
        void listLatestEmpty() {
            // when
            List<ChatMessage> result = sut.listLatestMessages(ROOM_ID, 10);

            // then
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("listPrev")
    class ListPrevTest {

        @Test
        @DisplayName("cursor 시간보다 이전 메시지를 최신순으로 limit만큼 조회한다")
        void listPrev() {
            // given
            ChatMessage message1 = message(MESSAGE_ID_1, CONTENT_1, WRITER_ID, time1);
            ChatMessage message2 = message(MESSAGE_ID_2, CONTENT_2, WRITER_ID, time2);
            ChatMessage message3 = message(MESSAGE_ID_3, CONTENT_3, WRITER_ID, time3);
            ChatMessage message4 = message(MESSAGE_ID_4, CONTENT_4, WRITER_ID, time4);

            sut.warmUpList(List.of(message1, message2, message3, message4), ROOM_ID);

            // when
            List<ChatMessage> result = sut.listMessagesBefore(
                    ROOM_ID,
                    MESSAGE_ID_4,
                    message4.createdAtEpochMillis(),
                    2
            );

            // then
            assertThat(result)
                    .extracting(ChatMessage::getId)
                    .containsExactly(MESSAGE_ID_3, MESSAGE_ID_2);

            assertAccessed(MESSAGE_ID_3);
            assertAccessed(MESSAGE_ID_2);
        }

        @Test
        @DisplayName("cursor 이전 메시지가 없으면 빈 목록을 반환한다")
        void listPrevEmpty() {
            // given
            ChatMessage message1 = message(MESSAGE_ID_1, CONTENT_1, WRITER_ID, time1);

            sut.warmUpList(List.of(message1), ROOM_ID);

            // when
            List<ChatMessage> result = sut.listMessagesBefore(
                    ROOM_ID,
                    MESSAGE_ID_1,
                    message1.createdAtEpochMillis(),
                    10
            );

            // then
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("hardDelete")
    class HardDeleteTest {

        @Test
        @DisplayName("latest 200 안에 메시지가 있으면 메시지를 삭제하고 msgCnt를 감소시키며 membership score를 복구한다")
        void hardDeleteApplied() {
            // given
            ChatMessage message1 = message(MESSAGE_ID_1, CONTENT_1, WRITER_ID, time1);
            ChatMessage message2 = message(MESSAGE_ID_2, CONTENT_2, WRITER_ID, time2);

            sut.warmUpList(List.of(message1, message2), ROOM_ID);

            String roomInfoKey = CHAT_ROOM_INFO.keyFor(ROOM_ID);
            masterHashRedisTemplate.opsForHash().put(roomInfoKey, "msg_cnt", "2");
            masterHashRedisTemplate.opsForHash().put(roomInfoKey, "latest_message_seq", "2");

            String memberRecentKey = CHAT_ROOM_ACTIVE_BY_MEMBER_INDEX.keyFor(MEMBER_ID);
            String otherMemberRecentKey = CHAT_ROOM_ACTIVE_BY_MEMBER_INDEX.keyFor(OTHER_MEMBER_ID);

            masterHashRedisTemplate.opsForZSet().add(memberRecentKey, ROOM_ID, 9999D);
            masterHashRedisTemplate.opsForZSet().add(otherMemberRecentKey, ROOM_ID, 8888D);

            List<ChatRoomMembershipScore> chatRoomMembershipScores = List.of(
                    new ChatRoomMembershipScore(MEMBER_ID, 0L),
                    new ChatRoomMembershipScore(OTHER_MEMBER_ID, 1234L)
            );

            // when
            sut.hardDelete(MESSAGE_ID_2, ROOM_ID, chatRoomMembershipScores);

            // then
            List<ChatMessage> latest = sut.listLatestMessages(ROOM_ID, 10);

            assertThat(latest)
                    .extracting(ChatMessage::getId)
                    .containsExactly(MESSAGE_ID_1);

            Object msgCnt = masterHashRedisTemplate.opsForHash()
                    .get(roomInfoKey, "msg_cnt");
            Object latestMessageSeq = masterHashRedisTemplate.opsForHash()
                    .get(roomInfoKey, "latest_message_seq");

            assertThat(msgCnt).isEqualTo("1");
            assertThat(latestMessageSeq).isEqualTo("2");

            assertThat(masterHashRedisTemplate.opsForZSet().score(memberRecentKey, ROOM_ID))
                    .isNull();

            Double otherScore = masterHashRedisTemplate.opsForZSet()
                    .score(otherMemberRecentKey, ROOM_ID);

            assertThat(otherScore).isNotNull();
            assertThat(otherScore.longValue()).isEqualTo(1234L);
        }

        @Test
        @DisplayName("삭제 대상 메시지가 latest 200 안에 없으면 예외 없이 skip한다")
        void hardDeleteSkippedWhenMessageNotFound() {
            // given
            ChatMessage message1 = message(MESSAGE_ID_1, CONTENT_1, WRITER_ID, time1);
            sut.warmUpList(List.of(message1), ROOM_ID);

            String roomInfoKey = CHAT_ROOM_INFO.keyFor(ROOM_ID);
            masterHashRedisTemplate.opsForHash().put(roomInfoKey, "msg_cnt", "1");

            // when
            sut.hardDelete(MESSAGE_ID_2, ROOM_ID, List.of());

            // then
            List<ChatMessage> latest = sut.listLatestMessages(ROOM_ID, 10);

            assertThat(latest)
                    .extracting(ChatMessage::getId)
                    .containsExactly(MESSAGE_ID_1);

            Object msgCnt = masterHashRedisTemplate.opsForHash()
                    .get(roomInfoKey, "msg_cnt");

            assertThat(msgCnt).isEqualTo("1");
        }

        @Test
        @DisplayName("membershipScores가 null이어도 삭제를 수행한다")
        void hardDeleteWithNullMembershipScores() {
            // given
            ChatMessage message1 = message(MESSAGE_ID_1, CONTENT_1, WRITER_ID, time1);
            sut.warmUpList(List.of(message1), ROOM_ID);

            String roomInfoKey = CHAT_ROOM_INFO.keyFor(ROOM_ID);
            masterHashRedisTemplate.opsForHash().put(roomInfoKey, "msg_cnt", "1");

            // when
            sut.hardDelete(MESSAGE_ID_1, ROOM_ID, null);

            // then
            assertThat(sut.listLatestMessages(ROOM_ID, 10)).isEmpty();

            Object msgCnt = masterHashRedisTemplate.opsForHash()
                    .get(roomInfoKey, "msg_cnt");

            assertThat(msgCnt).isEqualTo("0");
        }

        @Test
        @DisplayName("스크립트 결과가 알 수 없는 값이면 RuntimeException을 던진다")
        void hardDeleteUnknownResult() {
            // when & then
            assertThatThrownBy(() -> sut.hardDelete("", ROOM_ID, List.of()))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("[redis] chatmessage delete failed");
        }
    }

    private ChatMessage message(String id, String content, String writerId, LocalDateTime createdAt) {
        return ChatMessage.builder()
                .id(id)
                .roomId(ROOM_ID)
                .writerId(writerId)
                .content(content)
                .createdAt(createdAt)
                .build();
    }

    private void assertAccessed(String messageId) {
        String messageAccessKey = CHAT_MESSAGE_ACCESS_BY_ROOM_INDEX.keyFor(ROOM_ID);

        Double score = masterHashRedisTemplate.opsForZSet()
                .score(messageAccessKey, messageId);

        assertThat(score).isNotNull();
    }
}

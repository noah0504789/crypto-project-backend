package org.example.chat.chatmessage.adapter.out.scheduler;

import org.example.chat.chatmessage.domain.event.dlq.ChatMessageDlqEventList;
import org.example.chat.chatmessage.domain.event.ChatMessageEventList;
import org.example.chat.chatmessage.domain.model.ChatMessage;
import org.example.common.clock.Clock;
import org.example.chat.infra.redis.RedisCollectionRegistry;
import org.example.common.redis.codec.RedisValueCodec;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.support.collections.RedisZSet;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.example.common.enums.RedisKey.CHAT_MESSAGE_ACCESS_BY_ROOM_INDEX;
import static org.example.common.enums.RedisKey.CHAT_MESSAGE_INFO;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatMessageSchedulerTest {

    @Mock
    private RedisCollectionRegistry registry;

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private Clock clock;

    @Mock
    private RedisValueCodec<ChatMessage> redisChatMessageCodec;

    @Mock
    private RedisZSet<String> accessZSet;

    @Mock
    private RedisZSet<String> messageZSet;

    @InjectMocks
    private ChatMessageScheduler sut;

    private final String ROOM_ID = "000000000000000000000001";

    private final String EXPIRED_MESSAGE_ID = "100000000000000000000001";
    private final String ALIVE_MESSAGE_ID = "100000000000000000000002";

    private final String EXPIRED_MESSAGE_VALUE = "expired-message-json";
    private final String ALIVE_MESSAGE_VALUE = "alive-message-json";

    private final Instant NOW = Instant.parse("2026-01-08T00:00:00Z");

    @Test
    @DisplayName("access 기준으로 만료된 메시지를 message zset과 access zset에서 제거한다")
    void removeExpiringMessages() {
        // given
        String messageAccessKey = CHAT_MESSAGE_ACCESS_BY_ROOM_INDEX.keyFor(ROOM_ID);
        String messageKey = CHAT_MESSAGE_INFO.keyFor(ROOM_ID);

        Cursor<String> cursor = cursorOf(messageAccessKey);

        given(clock.now()).willReturn(NOW);
        given(redisTemplate.scan(any(ScanOptions.class))).willReturn(cursor);

        given(registry.getMasterZSet(messageAccessKey)).willReturn(accessZSet);
        given(registry.getMasterZSet(messageKey)).willReturn(messageZSet);

        long cutoff = NOW.toEpochMilli() - Duration.ofDays(7).toMillis();

        given(accessZSet.rangeByScore(0, cutoff))
                .willReturn(Set.of(EXPIRED_MESSAGE_ID));

        given(messageZSet.range(0, -1))
                .willReturn(Set.of(EXPIRED_MESSAGE_VALUE, ALIVE_MESSAGE_VALUE));

        given(redisChatMessageCodec.read(EXPIRED_MESSAGE_VALUE))
                .willReturn(chatMessage(EXPIRED_MESSAGE_ID));

        given(redisChatMessageCodec.read(ALIVE_MESSAGE_VALUE))
                .willReturn(chatMessage(ALIVE_MESSAGE_ID));

        // when
        sut.removeExpiringMessages();

        // then
        verify(messageZSet).remove(EXPIRED_MESSAGE_VALUE);
        verify(messageZSet, never()).remove(ALIVE_MESSAGE_VALUE);

        verify(accessZSet).remove(EXPIRED_MESSAGE_ID);
    }

    @Test
    @DisplayName("만료 메시지가 없으면 삭제 작업을 하지 않는다")
    void removeExpiringMessagesWithoutExpiredMessages() {
        // given
        String messageAccessKey = CHAT_MESSAGE_ACCESS_BY_ROOM_INDEX.keyFor(ROOM_ID);

        Cursor<String> cursor = cursorOf(messageAccessKey);

        given(clock.now()).willReturn(NOW);
        given(redisTemplate.scan(any(ScanOptions.class))).willReturn(cursor);

        given(registry.getMasterZSet(messageAccessKey)).willReturn(accessZSet);

        long cutoff = NOW.toEpochMilli() - Duration.ofDays(7).toMillis();

        given(accessZSet.rangeByScore(0, cutoff))
                .willReturn(Set.of());

        // when
        sut.removeExpiringMessages();

        // then
        verify(accessZSet).rangeByScore(0, cutoff);
        verifyNoInteractions(messageZSet);
    }

    @Test
    @DisplayName("message zset이 비어 있어도 access zset의 만료 id는 제거한다")
    void removeExpiredAccessOnlyWhenMessageCacheIsEmpty() {
        // given
        String messageAccessKey = CHAT_MESSAGE_ACCESS_BY_ROOM_INDEX.keyFor(ROOM_ID);
        String messageKey = CHAT_MESSAGE_INFO.keyFor(ROOM_ID);

        Cursor<String> cursor = cursorOf(messageAccessKey);

        given(clock.now()).willReturn(NOW);
        given(redisTemplate.scan(any(ScanOptions.class))).willReturn(cursor);

        given(registry.getMasterZSet(messageAccessKey)).willReturn(accessZSet);
        given(registry.getMasterZSet(messageKey)).willReturn(messageZSet);

        long cutoff = NOW.toEpochMilli() - Duration.ofDays(7).toMillis();

        given(accessZSet.rangeByScore(0, cutoff))
                .willReturn(Set.of(EXPIRED_MESSAGE_ID));

        given(messageZSet.range(0, -1))
                .willReturn(Set.of());

        // when
        sut.removeExpiringMessages();

        // then
        verify(messageZSet, never()).remove(anyString());
        verify(accessZSet).remove(EXPIRED_MESSAGE_ID);
    }

    @Test
    @DisplayName("scan 중 예외가 발생하면 RuntimeException으로 감싼다")
    void removeExpiringMessagesThrowsRuntimeException() {
        // given
        given(clock.now()).willReturn(NOW);
        given(redisTemplate.scan(any(ScanOptions.class)))
                .willThrow(new RuntimeException("redis scan failed"));

        // when & then
        assertThatThrownBy(() -> sut.removeExpiringMessages())
                .isInstanceOf(RuntimeException.class)
                .hasCauseInstanceOf(RuntimeException.class);
    }

    private ChatMessage chatMessage(String id) {
        return ChatMessage.builder()
                .id(id)
                .roomId(ROOM_ID)
                .writerId("writer-1")
                .content("message")
                .createdAt(LocalDateTime.of(2026, 1, 1, 10, 0))
                .eventList(new ChatMessageEventList())
                .dlqEventList(new ChatMessageDlqEventList())
                .build();
    }

    @SuppressWarnings("unchecked")
    private Cursor<String> cursorOf(String value) {
        Cursor<String> cursor = mock(Cursor.class);

        given(cursor.hasNext()).willReturn(true, false);
        given(cursor.next()).willReturn(value);

        return cursor;
    }
}

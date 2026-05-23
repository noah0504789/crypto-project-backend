package chatmessage.service;

import org.example.chatmessage.domain.event.dlq.ChatMessageDlqEventList;
import org.example.chatmessage.domain.event.ChatMessageEventList;
import org.example.chatmessage.application.port.out.ChatMessageCachePort;
import org.example.chatmessage.application.port.out.ChatMessagePersistencePort;
import org.example.chatmessage.application.service.ChatMessageQueryRepairService;
import org.example.chatmessage.domain.model.ChatMessage;
import org.example.common.redis.lock.DistributedLockExecutor;
import org.example.common.redis.lock.DistributedLockPolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatMessageQueryRepairServiceTest {

    @Mock
    private ChatMessageCachePort cache;

    @Mock
    private ChatMessagePersistencePort persistence;

    @Mock
    private DistributedLockExecutor distributedLockExecutor;

    @InjectMocks
    private ChatMessageQueryRepairService sut;

    private final String roomId = "000000000000000000000001";
    private final String lastId = "100000000000000000000003";

    private final long lastCreatedAtMillis = 1_767_224_400_000L;

    private final int limit = 2;

    private final String writerId = "writer-1";

    private final LocalDateTime time1 = LocalDateTime.of(2026, 1, 1, 10, 0);
    private final LocalDateTime time2 = LocalDateTime.of(2026, 1, 1, 11, 0);

    @BeforeEach
    void setUp() {
        given(distributedLockExecutor.execute(
                anyString(),
                any(),
                eq(DistributedLockPolicy.CACHE_WARM_UP)
        )).willAnswer(invocation -> {
            Supplier<List<ChatMessage>> supplier = invocation.getArgument(1);
            return supplier.get();
        });
    }

    @Nested
    @DisplayName("repairLatest")
    class RepairLatestTest {

        @Test
        @DisplayName("캐시에 최신 메시지가 있으면 DB 조회와 warmUp 없이 캐시 결과를 반환한다")
        void repairLatestReturnsCachedMessages() {
            // given
            List<ChatMessage> cached = List.of(
                    chatMessage("100000000000000000000002", "cached-2", time2),
                    chatMessage("100000000000000000000001", "cached-1", time1)
            );

            given(cache.listLatest(roomId, limit))
                    .willReturn(cached);

            // when
            List<ChatMessage> result = sut.repairLatest(roomId, limit);

            // then
            assertThat(result).isSameAs(cached);
            assertThat(result)
                    .extracting(ChatMessage::getId)
                    .containsExactly(
                            "100000000000000000000002",
                            "100000000000000000000001"
                    );

            verify(cache).listLatest(roomId, limit);
            verify(persistence, never()).listLatest(anyString(), anyInt());
            verify(cache, never()).warmUpList(anyList(), anyString());

            verify(distributedLockExecutor).execute(
                    eq("chatmessage:listLatest:" + roomId + ":" + limit),
                    any(),
                    eq(DistributedLockPolicy.CACHE_WARM_UP)
            );
        }

        @Test
        @DisplayName("캐시가 비어 있으면 DB에서 조회하고 warmUp 후 DB 결과를 반환한다")
        void repairLatestLoadsFromPersistenceAndWarmUp() {
            // given
            List<ChatMessage> stored = List.of(
                    chatMessage("100000000000000000000002", "stored-2", time2),
                    chatMessage("100000000000000000000001", "stored-1", time1)
            );

            given(cache.listLatest(roomId, limit))
                    .willReturn(List.of());

            given(persistence.listLatest(roomId, limit))
                    .willReturn(stored);

            // when
            List<ChatMessage> result = sut.repairLatest(roomId, limit);

            // then
            assertThat(result).isSameAs(stored);
            assertThat(result)
                    .extracting(ChatMessage::getContent)
                    .containsExactly("stored-2", "stored-1");

            verify(cache).listLatest(roomId, limit);
            verify(persistence).listLatest(roomId, limit);
            verify(cache).warmUpList(stored, roomId);
        }

        @Test
        @DisplayName("캐시와 DB가 모두 비어 있으면 빈 리스트를 반환하고 warmUp하지 않는다")
        void repairLatestReturnsEmptyWhenPersistenceEmpty() {
            // given
            given(cache.listLatest(roomId, limit))
                    .willReturn(List.of());

            given(persistence.listLatest(roomId, limit))
                    .willReturn(List.of());

            // when
            List<ChatMessage> result = sut.repairLatest(roomId, limit);

            // then
            assertThat(result).isEmpty();

            verify(cache).listLatest(roomId, limit);
            verify(persistence).listLatest(roomId, limit);
            verify(cache, never()).warmUpList(anyList(), anyString());
        }

        @Test
        @DisplayName("DB 조회 후 warmUp이 실패해도 DB 결과를 반환한다")
        void repairLatestReturnsStoredMessagesEvenWhenWarmUpFails() {
            // given
            List<ChatMessage> stored = List.of(
                    chatMessage("100000000000000000000001", "stored-1", time1)
            );

            given(cache.listLatest(roomId, limit))
                    .willReturn(List.of());

            given(persistence.listLatest(roomId, limit))
                    .willReturn(stored);

            doThrow(new RuntimeException("redis warmUp failed"))
                    .when(cache)
                    .warmUpList(stored, roomId);

            // when & then
            assertThatCode(() -> {
                List<ChatMessage> result = sut.repairLatest(roomId, limit);

                assertThat(result).isSameAs(stored);
            }).doesNotThrowAnyException();

            verify(cache).warmUpList(stored, roomId);
        }
    }

    @Nested
    @DisplayName("repairPrev")
    class RepairPrevTest {

        @Test
        @DisplayName("캐시에 이전 메시지가 있으면 DB 조회와 warmUp 없이 캐시 결과를 반환한다")
        void repairPrevReturnsCachedMessages() {
            // given
            List<ChatMessage> cached = List.of(
                    chatMessage("100000000000000000000002", "cached-2", time2),
                    chatMessage("100000000000000000000001", "cached-1", time1)
            );

            given(cache.listPrev(roomId, lastId, lastCreatedAtMillis, limit))
                    .willReturn(cached);

            // when
            List<ChatMessage> result = sut.repairPrev(
                    roomId,
                    lastId,
                    lastCreatedAtMillis,
                    limit
            );

            // then
            assertThat(result).isSameAs(cached);

            verify(cache).listPrev(roomId, lastId, lastCreatedAtMillis, limit);
            verify(persistence, never()).listPrev(anyString(), anyString(), anyLong(), anyInt());
            verify(cache, never()).warmUpList(anyList(), anyString());

            verify(distributedLockExecutor).execute(
                    eq("chatmessage:listPrev:" + roomId + ":" + lastId + ":" + lastCreatedAtMillis + ":" + limit),
                    any(),
                    eq(DistributedLockPolicy.CACHE_WARM_UP)
            );
        }

        @Test
        @DisplayName("캐시가 비어 있으면 DB에서 이전 메시지를 조회하고 warmUp 후 DB 결과를 반환한다")
        void repairPrevLoadsFromPersistenceAndWarmUp() {
            // given
            List<ChatMessage> stored = List.of(
                    chatMessage("100000000000000000000002", "stored-2", time2),
                    chatMessage("100000000000000000000001", "stored-1", time1)
            );

            given(cache.listPrev(roomId, lastId, lastCreatedAtMillis, limit))
                    .willReturn(List.of());

            given(persistence.listPrev(roomId, lastId, lastCreatedAtMillis, limit))
                    .willReturn(stored);

            // when
            List<ChatMessage> result = sut.repairPrev(
                    roomId,
                    lastId,
                    lastCreatedAtMillis,
                    limit
            );

            // then
            assertThat(result).isSameAs(stored);
            assertThat(result)
                    .extracting(ChatMessage::getContent)
                    .containsExactly("stored-2", "stored-1");

            verify(cache).listPrev(roomId, lastId, lastCreatedAtMillis, limit);
            verify(persistence).listPrev(roomId, lastId, lastCreatedAtMillis, limit);
            verify(cache).warmUpList(stored, roomId);
        }

        @Test
        @DisplayName("캐시와 DB가 모두 비어 있으면 빈 리스트를 반환하고 warmUp하지 않는다")
        void repairPrevReturnsEmptyWhenPersistenceEmpty() {
            // given
            given(cache.listPrev(roomId, lastId, lastCreatedAtMillis, limit))
                    .willReturn(List.of());

            given(persistence.listPrev(roomId, lastId, lastCreatedAtMillis, limit))
                    .willReturn(List.of());

            // when
            List<ChatMessage> result = sut.repairPrev(
                    roomId,
                    lastId,
                    lastCreatedAtMillis,
                    limit
            );

            // then
            assertThat(result).isEmpty();

            verify(cache).listPrev(roomId, lastId, lastCreatedAtMillis, limit);
            verify(persistence).listPrev(roomId, lastId, lastCreatedAtMillis, limit);
            verify(cache, never()).warmUpList(anyList(), anyString());
        }

        @Test
        @DisplayName("DB 조회 후 warmUp이 실패해도 DB 결과를 반환한다")
        void repairPrevReturnsStoredMessagesEvenWhenWarmUpFails() {
            // given
            List<ChatMessage> stored = List.of(
                    chatMessage("100000000000000000000001", "stored-1", time1)
            );

            given(cache.listPrev(roomId, lastId, lastCreatedAtMillis, limit))
                    .willReturn(List.of());

            given(persistence.listPrev(roomId, lastId, lastCreatedAtMillis, limit))
                    .willReturn(stored);

            doThrow(new RuntimeException("redis warmUp failed"))
                    .when(cache)
                    .warmUpList(stored, roomId);

            // when & then
            assertThatCode(() -> {
                List<ChatMessage> result = sut.repairPrev(
                        roomId,
                        lastId,
                        lastCreatedAtMillis,
                        limit
                );

                assertThat(result).isSameAs(stored);
            }).doesNotThrowAnyException();

            verify(cache).warmUpList(stored, roomId);
        }
    }

    private ChatMessage chatMessage(String id, String content, LocalDateTime createdAt) {
        return ChatMessage.builder()
                .id(id)
                .roomId(roomId)
                .writerId(writerId)
                .content(content)
                .createdAt(createdAt)
                .eventList(new ChatMessageEventList())
                .dlqEventList(new ChatMessageDlqEventList())
                .build();
    }
}
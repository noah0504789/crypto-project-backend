package chatmessage.service;

import org.example.chatmessage.adapter.dto.ChatMessageDlqEventList;
import org.example.chatmessage.adapter.dto.ChatMessageEventList;
import org.example.chatmessage.application.port.out.ChatMessageCachePort;
import org.example.chatmessage.application.service.ChatMessageQueryRepairService;
import org.example.chatmessage.application.service.ChatMessageQueryService;
import org.example.chatmessage.domain.model.ChatMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ChatMessageQueryServiceTest {

    @Mock
    private ChatMessageCachePort cache;

    @Mock
    private ChatMessageQueryRepairService queryRepairService;

    @InjectMocks
    private ChatMessageQueryService sut;

    private final String roomId = "000000000000000000000001";
    private final String lastId = "100000000000000000000003";
    private final long lastCreatedAtMillis = 1_767_224_400_000L;
    private final int limit = 2;

    private final String writerId = "writer-1";

    private final LocalDateTime time1 = LocalDateTime.of(2026, 1, 1, 10, 0);
    private final LocalDateTime time2 = LocalDateTime.of(2026, 1, 1, 11, 0);

    @Nested
    @DisplayName("listLatest")
    class ListLatestTest {

        @Test
        @DisplayName("캐시에 최신 메시지가 있으면 캐시 결과를 그대로 반환한다")
        void listLatestReturnsCachedMessages() {
            // given
            List<ChatMessage> cached = List.of(
                    chatMessage("100000000000000000000002", "cached-2", time2),
                    chatMessage("100000000000000000000001", "cached-1", time1)
            );

            given(cache.listLatest(roomId, limit))
                    .willReturn(cached);

            // when
            List<ChatMessage> result = sut.listLatest(roomId, limit);

            // then
            assertThat(result).isSameAs(cached);
            assertThat(result)
                    .extracting(ChatMessage::getId)
                    .containsExactly(
                            "100000000000000000000002",
                            "100000000000000000000001"
                    );

            verify(cache).listLatest(roomId, limit);
            verify(queryRepairService, never()).repairLatest(anyString(), anyInt());
        }

        @Test
        @DisplayName("캐시가 비어 있으면 repairLatest를 호출하고 그 결과를 반환한다")
        void listLatestRepairsWhenCacheMiss() {
            // given
            List<ChatMessage> repaired = List.of(
                    chatMessage("100000000000000000000002", "repaired-2", time2),
                    chatMessage("100000000000000000000001", "repaired-1", time1)
            );

            given(cache.listLatest(roomId, limit))
                    .willReturn(List.of());

            given(queryRepairService.repairLatest(roomId, limit))
                    .willReturn(repaired);

            // when
            List<ChatMessage> result = sut.listLatest(roomId, limit);

            // then
            assertThat(result).isSameAs(repaired);
            assertThat(result)
                    .extracting(ChatMessage::getContent)
                    .containsExactly("repaired-2", "repaired-1");

            verify(cache).listLatest(roomId, limit);
            verify(queryRepairService).repairLatest(roomId, limit);
        }

        @Test
        @DisplayName("캐시와 repair 결과가 모두 비어 있으면 빈 리스트를 반환한다")
        void listLatestReturnsEmptyWhenRepairEmpty() {
            // given
            given(cache.listLatest(roomId, limit))
                    .willReturn(List.of());

            given(queryRepairService.repairLatest(roomId, limit))
                    .willReturn(List.of());

            // when
            List<ChatMessage> result = sut.listLatest(roomId, limit);

            // then
            assertThat(result).isEmpty();

            verify(cache).listLatest(roomId, limit);
            verify(queryRepairService).repairLatest(roomId, limit);
        }
    }

    @Nested
    @DisplayName("listPrev")
    class ListPrevTest {

        @Test
        @DisplayName("캐시에 이전 메시지가 있으면 캐시 결과를 그대로 반환한다")
        void listPrevReturnsCachedMessages() {
            // given
            List<ChatMessage> cached = List.of(
                    chatMessage("100000000000000000000002", "cached-2", time2),
                    chatMessage("100000000000000000000001", "cached-1", time1)
            );

            given(cache.listPrev(roomId, lastId, lastCreatedAtMillis, limit))
                    .willReturn(cached);

            // when
            List<ChatMessage> result = sut.listPrev(
                    roomId,
                    lastId,
                    lastCreatedAtMillis,
                    limit
            );

            // then
            assertThat(result).isSameAs(cached);
            assertThat(result)
                    .extracting(ChatMessage::getId)
                    .containsExactly(
                            "100000000000000000000002",
                            "100000000000000000000001"
                    );

            verify(cache).listPrev(roomId, lastId, lastCreatedAtMillis, limit);
            verify(queryRepairService, never())
                    .repairPrev(anyString(), anyString(), anyLong(), anyInt());
        }

        @Test
        @DisplayName("캐시가 비어 있으면 repairPrev를 호출하고 그 결과를 반환한다")
        void listPrevRepairsWhenCacheMiss() {
            // given
            List<ChatMessage> repaired = List.of(
                    chatMessage("100000000000000000000002", "repaired-2", time2),
                    chatMessage("100000000000000000000001", "repaired-1", time1)
            );

            given(cache.listPrev(roomId, lastId, lastCreatedAtMillis, limit))
                    .willReturn(List.of());

            given(queryRepairService.repairPrev(roomId, lastId, lastCreatedAtMillis, limit))
                    .willReturn(repaired);

            // when
            List<ChatMessage> result = sut.listPrev(
                    roomId,
                    lastId,
                    lastCreatedAtMillis,
                    limit
            );

            // then
            assertThat(result).isSameAs(repaired);
            assertThat(result)
                    .extracting(ChatMessage::getContent)
                    .containsExactly("repaired-2", "repaired-1");

            verify(cache).listPrev(roomId, lastId, lastCreatedAtMillis, limit);
            verify(queryRepairService).repairPrev(roomId, lastId, lastCreatedAtMillis, limit);
        }

        @Test
        @DisplayName("캐시와 repair 결과가 모두 비어 있으면 빈 리스트를 반환한다")
        void listPrevReturnsEmptyWhenRepairEmpty() {
            // given
            given(cache.listPrev(roomId, lastId, lastCreatedAtMillis, limit))
                    .willReturn(List.of());

            given(queryRepairService.repairPrev(roomId, lastId, lastCreatedAtMillis, limit))
                    .willReturn(List.of());

            // when
            List<ChatMessage> result = sut.listPrev(
                    roomId,
                    lastId,
                    lastCreatedAtMillis,
                    limit
            );

            // then
            assertThat(result).isEmpty();

            verify(cache).listPrev(roomId, lastId, lastCreatedAtMillis, limit);
            verify(queryRepairService).repairPrev(roomId, lastId, lastCreatedAtMillis, limit);
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
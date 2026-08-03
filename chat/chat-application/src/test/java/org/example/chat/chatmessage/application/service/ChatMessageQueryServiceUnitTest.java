package org.example.chat.chatmessage.application.service;

import org.example.chat.chatmessage.application.service.query.ListChatMessagesQuery;
import org.example.chat.chatmessage.application.port.out.ChatMessageCachePort;
import org.example.chat.chatmessage.domain.model.ChatMessage;
import org.example.chat.chatroom.application.port.in.ChatRoomQueryUseCase;
import org.example.chat.chatroom.domain.exception.ChatRoomAccessDeniedException;
import org.example.chat.chatroom.domain.model.ChatRoom;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ChatMessageQueryServiceUnitTest {

    @Mock
    private ChatMessageCachePort cache;

    @Mock
    private ChatMessageQueryRepairService queryRepairService;

    @Mock
    private ChatRoomQueryUseCase chatRoomQueryUseCase;

    @InjectMocks
    private ChatMessageQueryService sut;

    private final String roomId = "000000000000000000000001";
    private final String lastId = "100000000000000000000003";
    private final long lastCreatedAtMillis = 1_767_224_400_000L;
    private final int limit = 2;

    private final String writerId = "writer-1";
    private final String myUserId = "member-1";

    @BeforeEach
    void stubMembership() {
        ChatRoom room = ChatRoom.builder()
                .id(roomId)
                .memberIds(new HashSet<>(Set.of(myUserId)))
                .build();

        given(chatRoomQueryUseCase.getRoom(roomId)).willReturn(room);
    }

    private final Instant time1 = Instant.parse("2026-01-01T01:00:00Z");
    private final Instant time2 = Instant.parse("2026-01-01T02:00:00Z");

    @Nested
    @DisplayName("listMessages - authorization")
    class ListMessagesAuthorizationTest {

        @Test
        @DisplayName("채팅방 조회 서비스를 통해 멤버십을 검증한다")
        void listMessagesValidatesMembershipThroughChatRoomQueryService() {
            // given
            ListChatMessagesQuery query = firstPageQuery();
            given(cache.listLatestMessages(roomId, limit)).willReturn(List.of());
            given(queryRepairService.repairLatest(roomId, limit)).willReturn(List.of());

            // when
            sut.listMessages(query);

            // then
            verify(chatRoomQueryUseCase).getRoom(roomId);
        }

        @Test
        @DisplayName("조회된 채팅방의 멤버가 아니면 메시지를 조회하지 않는다")
        void listMessagesRejectsNonMemberBeforeLoadingMessages() {
            // given
            ListChatMessagesQuery query = firstPageQuery();
            ChatRoom room = ChatRoom.builder()
                    .id(roomId)
                    .memberIds(new HashSet<>(Set.of("other-member")))
                    .build();

            given(chatRoomQueryUseCase.getRoom(roomId)).willReturn(room);

            // when & then
            assertThatThrownBy(() -> sut.listMessages(query))
                    .isInstanceOf(ChatRoomAccessDeniedException.class);

            verify(cache, never()).listLatestMessages(anyString(), anyInt());
            verify(cache, never()).listMessagesBefore(anyString(), anyString(), anyLong(), anyInt());
            verify(queryRepairService, never()).repairLatest(anyString(), anyInt());
            verify(queryRepairService, never()).repairPrev(any());
        }
    }

    @Nested
    @DisplayName("listMessages - firstPage")
    class ListMessagesFirstPageTest {

        @Test
        @DisplayName("캐시에 최신 메시지가 있으면 캐시 결과를 그대로 반환한다")
        void listMessagesReturnsCachedLatestMessages() {
            // given
            ListChatMessagesQuery query = firstPageQuery();

            List<ChatMessage> cached = List.of(
                    chatMessage("100000000000000000000002", "cached-2", time2),
                    chatMessage("100000000000000000000001", "cached-1", time1)
            );

            given(cache.listLatestMessages(roomId, limit))
                    .willReturn(cached);

            // when
            List<ChatMessage> result = sut.listMessages(query);

            // then
            assertThat(result).isSameAs(cached);
            assertThat(result)
                    .extracting(ChatMessage::getId)
                    .containsExactly(
                            "100000000000000000000002",
                            "100000000000000000000001"
                    );

            verify(cache).listLatestMessages(roomId, limit);
            verify(cache, never()).listMessagesBefore(anyString(), anyString(), anyLong(), anyInt());
            verify(queryRepairService, never()).repairLatest(anyString(), anyInt());
            verify(queryRepairService, never()).repairPrev(any());
        }

        @Test
        @DisplayName("캐시가 비어 있으면 repairLatest를 호출하고 그 결과를 반환한다")
        void listMessagesRepairsLatestWhenCacheMiss() {
            // given
            ListChatMessagesQuery query = firstPageQuery();

            List<ChatMessage> repaired = List.of(
                    chatMessage("100000000000000000000002", "repaired-2", time2),
                    chatMessage("100000000000000000000001", "repaired-1", time1)
            );

            given(cache.listLatestMessages(roomId, limit))
                    .willReturn(List.of());

            given(queryRepairService.repairLatest(roomId, limit))
                    .willReturn(repaired);

            // when
            List<ChatMessage> result = sut.listMessages(query);

            // then
            assertThat(result).isSameAs(repaired);
            assertThat(result)
                    .extracting(ChatMessage::getContent)
                    .containsExactly("repaired-2", "repaired-1");

            verify(cache).listLatestMessages(roomId, limit);
            verify(cache, never()).listMessagesBefore(anyString(), anyString(), anyLong(), anyInt());
            verify(queryRepairService).repairLatest(roomId, limit);
            verify(queryRepairService, never()).repairPrev(any());
        }

        @Test
        @DisplayName("캐시와 repair 결과가 모두 비어 있으면 빈 리스트를 반환한다")
        void listMessagesReturnsEmptyWhenLatestRepairEmpty() {
            // given
            ListChatMessagesQuery query = firstPageQuery();

            given(cache.listLatestMessages(roomId, limit))
                    .willReturn(List.of());

            given(queryRepairService.repairLatest(roomId, limit))
                    .willReturn(List.of());

            // when
            List<ChatMessage> result = sut.listMessages(query);

            // then
            assertThat(result).isEmpty();

            verify(cache).listLatestMessages(roomId, limit);
            verify(cache, never()).listMessagesBefore(anyString(), anyString(), anyLong(), anyInt());
            verify(queryRepairService).repairLatest(roomId, limit);
            verify(queryRepairService, never()).repairPrev(any());
        }
    }

    @Nested
    @DisplayName("listMessages - prevPage")
    class ListMessagesPrevPageTest {

        @Test
        @DisplayName("캐시에 이전 메시지가 있으면 캐시 결과를 그대로 반환한다")
        void listMessagesReturnsCachedPrevMessages() {
            // given
            ListChatMessagesQuery query = prevPageQuery();

            List<ChatMessage> cached = List.of(
                    chatMessage("100000000000000000000002", "cached-2", time2),
                    chatMessage("100000000000000000000001", "cached-1", time1)
            );

            given(cache.listMessagesBefore(roomId, lastId, lastCreatedAtMillis, limit))
                    .willReturn(cached);

            // when
            List<ChatMessage> result = sut.listMessages(query);

            // then
            assertThat(result).isSameAs(cached);
            assertThat(result)
                    .extracting(ChatMessage::getId)
                    .containsExactly(
                            "100000000000000000000002",
                            "100000000000000000000001"
                    );

            verify(cache, never()).listLatestMessages(anyString(), anyInt());
            verify(cache).listMessagesBefore(roomId, lastId, lastCreatedAtMillis, limit);
            verify(queryRepairService, never()).repairLatest(anyString(), anyInt());
            verify(queryRepairService, never()).repairPrev(any());
        }

        @Test
        @DisplayName("캐시가 비어 있으면 repairPrev를 호출하고 그 결과를 반환한다")
        void listMessagesRepairsPrevWhenCacheMiss() {
            // given
            ListChatMessagesQuery query = prevPageQuery();

            List<ChatMessage> repaired = List.of(
                    chatMessage("100000000000000000000002", "repaired-2", time2),
                    chatMessage("100000000000000000000001", "repaired-1", time1)
            );

            given(cache.listMessagesBefore(roomId, lastId, lastCreatedAtMillis, limit))
                    .willReturn(List.of());

            given(queryRepairService.repairPrev(query))
                    .willReturn(repaired);

            // when
            List<ChatMessage> result = sut.listMessages(query);

            // then
            assertThat(result).isSameAs(repaired);
            assertThat(result)
                    .extracting(ChatMessage::getContent)
                    .containsExactly("repaired-2", "repaired-1");

            verify(cache, never()).listLatestMessages(anyString(), anyInt());
            verify(cache).listMessagesBefore(roomId, lastId, lastCreatedAtMillis, limit);
            verify(queryRepairService, never()).repairLatest(anyString(), anyInt());
            verify(queryRepairService).repairPrev(query);
        }

        @Test
        @DisplayName("캐시와 repair 결과가 모두 비어 있으면 빈 리스트를 반환한다")
        void listMessagesReturnsEmptyWhenPrevRepairEmpty() {
            // given
            ListChatMessagesQuery query = prevPageQuery();

            given(cache.listMessagesBefore(roomId, lastId, lastCreatedAtMillis, limit))
                    .willReturn(List.of());

            given(queryRepairService.repairPrev(query))
                    .willReturn(List.of());

            // when
            List<ChatMessage> result = sut.listMessages(query);

            // then
            assertThat(result).isEmpty();

            verify(cache, never()).listLatestMessages(anyString(), anyInt());
            verify(cache).listMessagesBefore(roomId, lastId, lastCreatedAtMillis, limit);
            verify(queryRepairService, never()).repairLatest(anyString(), anyInt());
            verify(queryRepairService).repairPrev(query);
        }

        @Test
        @DisplayName("lastCreatedAtMillis가 null이면 0을 기준으로 이전 메시지를 조회한다")
        void listMessagesUsesZeroCursorCreatedAtMillisWhenLastCreatedAtMillisIsNull() {
            // given
            ListChatMessagesQuery query = ListChatMessagesQuery.prevPage(
                    roomId,
                    myUserId,
                    lastId,
                    null,
                    limit
            );

            given(cache.listMessagesBefore(roomId, lastId, 0L, limit))
                    .willReturn(List.of());

            given(queryRepairService.repairPrev(query))
                    .willReturn(List.of());

            // when
            List<ChatMessage> result = sut.listMessages(query);

            // then
            assertThat(result).isEmpty();

            verify(cache).listMessagesBefore(roomId, lastId, 0L, limit);
            verify(queryRepairService).repairPrev(query);
        }
    }

    private ListChatMessagesQuery firstPageQuery() {
        return ListChatMessagesQuery.firstPage(roomId, myUserId, limit);
    }

    private ListChatMessagesQuery prevPageQuery() {
        return ListChatMessagesQuery.prevPage(roomId, myUserId, lastId, lastCreatedAtMillis, limit);
    }

    private ChatMessage chatMessage(String id, String content, Instant createdAt) {
        return ChatMessage.rehydrate(id, roomId, writerId, content, createdAt);
    }
}

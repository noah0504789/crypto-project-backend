package org.example.chat.chatroom.application.service;

import org.example.chat.chatroom.application.port.out.ChatRoomCachePort;
import org.example.chat.chatroom.application.port.out.ChatRoomPersistencePort;
import org.example.chat.chatroom.domain.model.ChatRoom;
import org.example.chat.chatroom.application.properties.PopularChatRoomProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.example.chat.chatroom.domain.model.ChatRoomCategory.CRYPTO_CURRENCY;
import static org.example.chat.chatroom.domain.model.ChatRoomCategory.FREE;
import static org.example.chat.chatroom.domain.model.ChatRoomCategory.STUDY;

@ExtendWith(MockitoExtension.class)
class PopularChatRoomRefreshServiceUnitTest {

    @Mock
    private ChatRoomPersistencePort persistence;

    @Mock
    private ChatRoomCachePort cache;

    private static final int POPULAR_INDEX_SIZE = 100;

    private PopularChatRoomRefreshService sut;

    @BeforeEach
    void setUp() {
        sut = new PopularChatRoomRefreshService(
                new PopularChatRoomProperties(POPULAR_INDEX_SIZE),
                persistence,
                cache
        );
    }

    @Test
    @DisplayName("category에 방이 있으면 popularity를 계산해 Mongo bulk 갱신하고 상위순으로 zset을 재구축한다")
    void refresh_recomputesPopularityAndRebuilds() {
        // given: FREE에 msgCnt 10/20 방 2개, 나머지 category는 빈 방
        ChatRoom room10 = ChatRoom.builder().id("room-10").msgCnt(10L).build();
        ChatRoom room20 = ChatRoom.builder().id("room-20").msgCnt(20L).build();

        given(persistence.listRoomsForPopularityRecompute(FREE)).willReturn(List.of(room10, room20));
        given(persistence.listRoomsForPopularityRecompute(STUDY)).willReturn(List.of());
        given(persistence.listRoomsForPopularityRecompute(CRYPTO_CURRENCY)).willReturn(List.of());

        // when
        sut.refresh();

        // then: popularity = round(calculate) = msgCnt
        verify(persistence).updatePopularities(Map.of("room-10", 10L, "room-20", 20L));

        // top-N은 popularity 내림차순
        ArgumentCaptor<List<ChatRoom>> topCaptor = topCaptor();
        verify(cache).rebuildPopularIndex(eq(FREE), topCaptor.capture());
        assertThat(topCaptor.getValue())
                .extracting(ChatRoom::getId)
                .containsExactly("room-20", "room-10");
    }

    @Test
    @DisplayName("방이 없는 category는 popularity 갱신 없이 zset만 비운다")
    void refresh_clearsZsetWithoutUpdateWhenEmpty() {
        // given
        given(persistence.listRoomsForPopularityRecompute(FREE)).willReturn(List.of());
        given(persistence.listRoomsForPopularityRecompute(STUDY)).willReturn(List.of());
        given(persistence.listRoomsForPopularityRecompute(CRYPTO_CURRENCY)).willReturn(List.of());

        // when
        sut.refresh();

        // then
        verify(persistence, never()).updatePopularities(anyMap());
        verify(cache).rebuildPopularIndex(eq(FREE), eq(List.of()));
        verify(cache).rebuildPopularIndex(eq(STUDY), eq(List.of()));
        verify(cache).rebuildPopularIndex(eq(CRYPTO_CURRENCY), eq(List.of()));
    }

    @Test
    @DisplayName("한 category 재계산이 실패해도 나머지 category는 계속 처리한다")
    void refresh_continuesWhenOneCategoryFails() {
        // given
        given(persistence.listRoomsForPopularityRecompute(FREE))
                .willThrow(new RuntimeException("boom"));
        given(persistence.listRoomsForPopularityRecompute(STUDY)).willReturn(List.of());
        given(persistence.listRoomsForPopularityRecompute(CRYPTO_CURRENCY)).willReturn(List.of());

        // when
        sut.refresh();

        // then
        verify(cache).rebuildPopularIndex(eq(STUDY), eq(List.of()));
        verify(cache).rebuildPopularIndex(eq(CRYPTO_CURRENCY), eq(List.of()));
    }

    @SuppressWarnings("unchecked")
    private ArgumentCaptor<List<ChatRoom>> topCaptor() {
        return ArgumentCaptor.forClass(List.class);
    }
}

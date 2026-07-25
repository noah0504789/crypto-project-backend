package org.example.chat.chatroom.application.service;

import org.example.chat.chatroom.application.port.out.ChatRoomCachePort;
import org.example.chat.chatroom.application.port.out.ChatRoomPersistencePort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.example.chat.chatroom.domain.model.ChatRoomCategory.CRYPTO_CURRENCY;
import static org.example.chat.chatroom.domain.model.ChatRoomCategory.FREE;
import static org.example.chat.chatroom.domain.model.ChatRoomCategory.STUDY;

@ExtendWith(MockitoExtension.class)
class PopularChatRoomRefreshServiceTest {

    @Mock
    private ChatRoomPersistencePort persistence;

    @Mock
    private ChatRoomCachePort cache;

    @InjectMocks
    private PopularChatRoomRefreshService sut;

    @Test
    @DisplayName("모든 category에 대해 Mongo 상위 후보를 조회해 인기방 zset을 재구축한다")
    void refresh_rebuildsPopularIndexForEveryCategory() {
        // given
        given(persistence.listPopularRooms(eq(FREE), anyInt())).willReturn(List.of());
        given(persistence.listPopularRooms(eq(STUDY), anyInt())).willReturn(List.of());
        given(persistence.listPopularRooms(eq(CRYPTO_CURRENCY), anyInt())).willReturn(List.of());

        // when
        sut.refresh();

        // then
        verify(cache).rebuildPopularIndex(eq(FREE), eq(List.of()));
        verify(cache).rebuildPopularIndex(eq(STUDY), eq(List.of()));
        verify(cache).rebuildPopularIndex(eq(CRYPTO_CURRENCY), eq(List.of()));
    }

    @Test
    @DisplayName("한 category 재구축이 실패해도 나머지 category는 계속 재구축한다")
    void refresh_continuesWhenOneCategoryFails() {
        // given
        given(persistence.listPopularRooms(eq(FREE), anyInt())).willReturn(List.of());
        given(persistence.listPopularRooms(eq(STUDY), anyInt())).willReturn(List.of());
        given(persistence.listPopularRooms(eq(CRYPTO_CURRENCY), anyInt())).willReturn(List.of());

        doThrow(new RuntimeException("boom"))
                .when(cache).rebuildPopularIndex(eq(FREE), anyList());

        // when
        sut.refresh();

        // then
        verify(cache).rebuildPopularIndex(eq(STUDY), eq(List.of()));
        verify(cache).rebuildPopularIndex(eq(CRYPTO_CURRENCY), eq(List.of()));
    }
}

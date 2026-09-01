package org.example.chat.chatroom.application.service;

import org.example.chat.chatroom.application.port.out.ChatRoomActivityProjectionMetricsPort;
import org.example.chat.chatroom.application.port.out.ChatRoomActivityProjectionPort;
import org.example.chat.chatroom.application.port.out.ChatRoomPersistencePort;
import org.example.chat.chatroom.application.properties.ChatRoomActivityProjectionProperties;
import org.example.chat.chatroom.application.service.result.ChatRoomActivityClaim;
import org.example.chat.chatroom.application.service.result.ChatRoomActivityProjectionResult;
import org.example.chat.chatroom.application.service.result.ChatRoomMemberActivity;
import org.example.chat.chatroom.application.service.result.ChatRoomMemberReadState;
import org.example.chat.chatroom.domain.model.ChatRoom;
import org.example.chat.chatroom.domain.model.ChatRoomCategory;
import org.example.chat.chatroom.domain.service.MyChatRoomScoreCalculator;
import org.example.common.time.Clock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class ChatRoomActivityProjectionServiceUnitTest {

    @Mock
    private ChatRoomActivityProjectionPort projection;

    @Mock
    private ChatRoomPersistencePort persistence;

    @Mock
    private ChatRoomActivityProjectionMetricsPort metrics;

    @Mock
    private Clock clock;

    private ChatRoomActivityProjectionService sut;

    private static final String ROOM_ID = "000000000000000000000001";
    private static final String MEMBER_ID = "member-1";
    private static final String OTHER_MEMBER_ID = "member-2";
    private static final long NOW_MS = 1_800_000_000_000L;
    private static final long ACTIVITY_MS = 1_700_000_000_000L;
    private static final long CLAIM_TIMEOUT_MS = 30_000L;

    private final ChatRoomActivityProjectionProperties properties =
            new ChatRoomActivityProjectionProperties(200, CLAIM_TIMEOUT_MS, 100);

    @BeforeEach
    void setUp() {
        sut = new ChatRoomActivityProjectionService(projection, persistence, properties, metrics, clock);
    }

    @Nested
    @DisplayName("flush")
    class Flush {

        @BeforeEach
        void runFlushInline() {
            willAnswer(invocation -> {
                invocation.getArgument(0, Runnable.class).run();
                return null;
            }).given(metrics).recordFlush(any(Runnable.class));
        }

        @Test
        @DisplayName("dirty 방을 claim 해 방마다 한 번씩만 projection 을 반영한다")
        void flush_claimedRooms_projectsEachRoomOnce() {
            given(clock.nowMs()).willReturn(NOW_MS);
            given(projection.claimDirtyRooms(200, NOW_MS))
                    .willReturn(List.of(new ChatRoomActivityClaim(ROOM_ID, ACTIVITY_MS)));
            given(projection.project(ROOM_ID, ACTIVITY_MS))
                    .willReturn(ChatRoomActivityProjectionResult.of(302, 5));

            sut.flush();

            then(projection).should().project(ROOM_ID, ACTIVITY_MS);
            then(metrics).should().recordClaimedRooms(1);
            then(metrics).should().recordProjectedRoom(302, 5);
            then(persistence).should(never()).findByIdWithLatestMessage(anyString());
        }

        @Test
        @DisplayName("dirty 가 비면 projection 을 호출하지 않는다")
        void flush_noDirtyRoom_doesNotProject() {
            given(clock.nowMs()).willReturn(NOW_MS);
            given(projection.claimDirtyRooms(200, NOW_MS)).willReturn(List.of());

            sut.flush();

            then(projection).should(never()).project(anyString(), anyLong());
            then(metrics).should(never()).recordClaimedRooms(anyInt());
        }

        @Test
        @DisplayName("방 캐시가 비면 Mongo 의 room watermark 와 membership 읽음 위치로 재생성한다")
        void flush_cacheMiss_rebuildsFromPersistence() {
            given(clock.nowMs()).willReturn(NOW_MS);
            given(projection.claimDirtyRooms(200, NOW_MS))
                    .willReturn(List.of(new ChatRoomActivityClaim(ROOM_ID, ACTIVITY_MS)));
            given(projection.project(ROOM_ID, ACTIVITY_MS))
                    .willReturn(ChatRoomActivityProjectionResult.ofCacheMiss());
            given(persistence.findByIdWithLatestMessage(ROOM_ID)).willReturn(Optional.of(room()));
            given(persistence.listMemberReadStates(ROOM_ID)).willReturn(List.of(
                    new ChatRoomMemberReadState(MEMBER_ID, 3L),
                    new ChatRoomMemberReadState(OTHER_MEMBER_ID, 10L)
            ));

            sut.flush();

            ArgumentCaptor<List<ChatRoomMemberActivity>> captor = ArgumentCaptor.forClass(List.class);
            then(projection).should().rebuild(eq(ROOM_ID), captor.capture());

            assertThat(captor.getValue())
                    .extracting(ChatRoomMemberActivity::memberId, ChatRoomMemberActivity::score)
                    .containsExactly(
                            tuple(MEMBER_ID, MyChatRoomScoreCalculator.unread(ACTIVITY_MS)),
                            tuple(OTHER_MEMBER_ID, MyChatRoomScoreCalculator.read(ACTIVITY_MS))
                    );
            then(metrics).should().recordRebuiltRoom(2);
        }

        @Test
        @DisplayName("projection 이 실패하면 dirty 로 되돌려 다음 주기에 다시 처리한다")
        void flush_projectionFailed_requeuesDirty() {
            given(clock.nowMs()).willReturn(NOW_MS);
            given(projection.claimDirtyRooms(200, NOW_MS))
                    .willReturn(List.of(new ChatRoomActivityClaim(ROOM_ID, ACTIVITY_MS)));
            willThrow(new IllegalStateException("redis down"))
                    .given(projection).project(ROOM_ID, ACTIVITY_MS);

            sut.flush();

            then(projection).should().requeueDirty(ROOM_ID, ACTIVITY_MS);
            then(metrics).should().recordFailedRoom();
        }
    }

    @Nested
    @DisplayName("reclaimStalled")
    class ReclaimStalled {

        @Test
        @DisplayName("claim timeout 을 넘긴 방은 Mongo 기준으로 재생성한다")
        void reclaimStalled_stalledRoom_rebuildsFromPersistence() {
            given(clock.nowMs()).willReturn(NOW_MS);
            given(projection.reclaimStalledRooms(NOW_MS - CLAIM_TIMEOUT_MS, 100, NOW_MS))
                    .willReturn(List.of(ROOM_ID));
            given(persistence.findByIdWithLatestMessage(ROOM_ID)).willReturn(Optional.of(room()));
            given(persistence.listMemberReadStates(ROOM_ID))
                    .willReturn(List.of(new ChatRoomMemberReadState(MEMBER_ID, 0L)));

            sut.reclaimStalled();

            then(metrics).should().recordReclaimedRooms(1);
            then(projection).should().rebuild(eq(ROOM_ID), anyList());
        }

        @Test
        @DisplayName("Mongo 에도 없는 방은 작업 목록에서 버린다")
        void reclaimStalled_deletedRoom_discards() {
            given(clock.nowMs()).willReturn(NOW_MS);
            given(projection.reclaimStalledRooms(NOW_MS - CLAIM_TIMEOUT_MS, 100, NOW_MS))
                    .willReturn(List.of(ROOM_ID));
            given(persistence.findByIdWithLatestMessage(ROOM_ID)).willReturn(Optional.empty());

            sut.reclaimStalled();

            then(projection).should().discard(ROOM_ID);
            then(metrics).should().recordDiscardedRoom();
        }
    }

    private ChatRoom room() {
        return ChatRoom.rehydrateWithLatest(
                ROOM_ID,
                MEMBER_ID,
                "방 제목",
                "방 설명",
                ChatRoomCategory.FREE,
                Set.of(MEMBER_ID, OTHER_MEMBER_ID),
                10L,
                10L,
                "100000000000000000000001",
                "마지막 메시지",
                Instant.ofEpochMilli(ACTIVITY_MS),
                LocalDateTime.of(2026, 1, 1, 0, 0)
        );
    }
}

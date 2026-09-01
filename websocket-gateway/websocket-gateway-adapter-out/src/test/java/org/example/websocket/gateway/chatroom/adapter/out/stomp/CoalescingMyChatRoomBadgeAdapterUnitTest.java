package org.example.websocket.gateway.chatroom.adapter.out.stomp;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.example.websocket.gateway.chatroom.application.service.command.MyChatRoomBadgeCommand;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("CoalescingMyChatRoomBadgeAdapter")
class CoalescingMyChatRoomBadgeAdapterUnitTest {

    @Mock
    private DirectStompMyChatRoomBadgeAdapter delegate;

    private MeterRegistry registry;

    private final String roomId = "room-1";
    private final String otherRoomId = "room-2";
    private final Set<String> memberIds = Set.of("member-1", "member-2");
    private final Instant baseTime = Instant.parse("2026-08-28T00:00:00Z");

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
    }

    private CoalescingMyChatRoomBadgeAdapter sut() {
        return new CoalescingMyChatRoomBadgeAdapter(
                delegate,
                new BadgeCoalesceProperties(200L),
                registry
        );
    }

    private MyChatRoomBadgeCommand command(String roomId, String content, Instant createdAt) {
        return new MyChatRoomBadgeCommand(roomId, memberIds, content, createdAt);
    }

    @Test
    @DisplayName("창이 닫히기 전에는 delegate 로 나가지 않는다")
    void doesNotSendBeforeFlush() {
        // given
        CoalescingMyChatRoomBadgeAdapter sut = sut();

        // when
        boolean accepted = sut.send(command(roomId, "첫 번째", baseTime), "tx-1");

        // then
        assertThat(accepted).isTrue();
        verifyNoInteractions(delegate);
    }

    @Test
    @DisplayName("같은 방의 연속 뱃지는 마지막 1건만 전송한다")
    void coalescesSameRoom() {
        // given
        CoalescingMyChatRoomBadgeAdapter sut = sut();
        MyChatRoomBadgeCommand last = command(roomId, "세 번째", baseTime.plusSeconds(2));

        sut.send(command(roomId, "첫 번째", baseTime), "tx-1");
        sut.send(command(roomId, "두 번째", baseTime.plusSeconds(1)), "tx-2");
        sut.send(last, "tx-3");

        // when
        sut.flush();

        // then
        verify(delegate).send(last, "tx-3");
        assertThat(registry.counter("chat.badge.coalesced").count()).isEqualTo(2.0);
        assertThat(registry.counter("chat.badge.flushed").count()).isEqualTo(1.0);
        assertThat(registry.timer("chat.badge.flush").count()).isEqualTo(1L);
    }

    @Test
    @DisplayName("방이 다르면 합치지 않고 각각 전송한다")
    void doesNotCoalesceAcrossRooms() {
        // given
        CoalescingMyChatRoomBadgeAdapter sut = sut();
        MyChatRoomBadgeCommand first = command(roomId, "방1", baseTime);
        MyChatRoomBadgeCommand second = command(otherRoomId, "방2", baseTime);

        sut.send(first, "tx-1");
        sut.send(second, "tx-2");

        // when
        sut.flush();

        // then
        verify(delegate).send(first, "tx-1");
        verify(delegate).send(second, "tx-2");
        assertThat(registry.counter("chat.badge.coalesced").count()).isZero();
        assertThat(registry.counter("chat.badge.flushed").count()).isEqualTo(2.0);
        assertThat(registry.timer("chat.badge.flush").count()).isEqualTo(1L);
    }

    @Test
    @DisplayName("늦게 도착한 과거 뱃지가 최신 뱃지를 덮지 않는다")
    void keepsLatestByTimestamp() {
        // given
        CoalescingMyChatRoomBadgeAdapter sut = sut();
        MyChatRoomBadgeCommand newer = command(roomId, "최신", baseTime.plusSeconds(10));

        sut.send(newer, "tx-new");
        sut.send(command(roomId, "과거", baseTime), "tx-old");

        // when
        sut.flush();

        // then
        verify(delegate).send(newer, "tx-new");
    }

    @Test
    @DisplayName("창을 비운 뒤 다시 flush 해도 재전송하지 않는다")
    void doesNotResendAfterFlush() {
        // given
        CoalescingMyChatRoomBadgeAdapter sut = sut();
        sut.send(command(roomId, "한 번", baseTime), "tx-1");
        sut.flush();

        // when
        sut.flush();

        // then
        verify(delegate).send(any(), any());
    }

}

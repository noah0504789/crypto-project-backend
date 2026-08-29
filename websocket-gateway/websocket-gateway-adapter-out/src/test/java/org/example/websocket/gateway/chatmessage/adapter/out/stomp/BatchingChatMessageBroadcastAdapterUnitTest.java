package org.example.websocket.gateway.chatmessage.adapter.out.stomp;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.example.websocket.gateway.chatmessage.adapter.out.stomp.payload.StompChatMessagePayload;
import org.example.websocket.gateway.chatmessage.application.service.command.ChatMessageBroadcastCommand;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("BatchingChatMessageBroadcastAdapter")
class BatchingChatMessageBroadcastAdapterUnitTest {

    @Mock
    private StompChatMessageBroadcastAdapter delegate;

    private MeterRegistry registry;

    private final String roomId = "room-1";
    private final String otherRoomId = "room-2";

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
    }

    private BatchingChatMessageBroadcastAdapter sut(boolean enabled, int maxBatchSize) {
        return new BatchingChatMessageBroadcastAdapter(
                delegate,
                new ChatMessageBatchProperties(enabled, 100L, maxBatchSize),
                registry
        );
    }

    private ChatMessageBroadcastCommand command(String roomId, String messageId, String content) {
        return new ChatMessageBroadcastCommand(
                messageId,
                roomId,
                "writer-1",
                content,
                1L,
                "client-" + messageId
        );
    }

    @SuppressWarnings("unchecked")
    private List<StompChatMessagePayload> capturedBatch(String roomId) {
        ArgumentCaptor<List<StompChatMessagePayload>> captor = ArgumentCaptor.forClass(List.class);
        verify(delegate).broadcastBatch(eq(roomId), captor.capture(), anyString());

        return captor.getValue();
    }

    @Test
    @DisplayName("창이 닫히기 전에는 delegate 로 나가지 않는다")
    void doesNotSendBeforeFlush() {
        // given
        when(delegate.hasLocalSubscriber(anyString())).thenReturn(true);
        BatchingChatMessageBroadcastAdapter sut = sut(true, 300);

        // when
        boolean accepted = sut.broadcast(command(roomId, "m1", "첫 번째"), "tx-1");

        // then
        assertThat(accepted).isTrue();
        verify(delegate, never()).broadcastBatch(anyString(), any(), anyString());
    }

    @Test
    @DisplayName("같은 방의 메시지를 순서대로 한 프레임에 담는다")
    void batchesInArrivalOrder() {
        // given
        when(delegate.hasLocalSubscriber(anyString())).thenReturn(true);
        BatchingChatMessageBroadcastAdapter sut = sut(true, 300);

        sut.broadcast(command(roomId, "m1", "첫 번째"), "tx-1");
        sut.broadcast(command(roomId, "m2", "두 번째"), "tx-2");
        sut.broadcast(command(roomId, "m3", "세 번째"), "tx-3");

        // when
        sut.flush();

        // then — 한 건도 버리지 않고 순서를 지킨다
        assertThat(capturedBatch(roomId))
                .extracting(StompChatMessagePayload::content)
                .containsExactly("첫 번째", "두 번째", "세 번째");
        assertThat(registry.counter("chat.message.batch.buffered").count()).isEqualTo(3.0);
        assertThat(registry.counter("chat.message.batch.frames").count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("방이 다르면 각각 별도 프레임으로 내보낸다")
    void separatesRooms() {
        // given
        when(delegate.hasLocalSubscriber(anyString())).thenReturn(true);
        BatchingChatMessageBroadcastAdapter sut = sut(true, 300);

        sut.broadcast(command(roomId, "m1", "방1"), "tx-1");
        sut.broadcast(command(otherRoomId, "m2", "방2"), "tx-2");

        // when
        sut.flush();

        // then
        verify(delegate).broadcastBatch(eq(roomId), any(), anyString());
        verify(delegate).broadcastBatch(eq(otherRoomId), any(), anyString());
        assertThat(registry.counter("chat.message.batch.frames").count()).isEqualTo(2.0);
    }

    @Test
    @DisplayName("방당 상한을 넘으면 창을 기다리지 않고 즉시 내보낸다")
    void flushesImmediatelyOnOverflow() {
        // given
        when(delegate.hasLocalSubscriber(anyString())).thenReturn(true);
        BatchingChatMessageBroadcastAdapter sut = sut(true, 2);

        // when — 두 번째 적재에서 상한에 닿는다
        sut.broadcast(command(roomId, "m1", "첫 번째"), "tx-1");
        sut.broadcast(command(roomId, "m2", "두 번째"), "tx-2");

        // then
        assertThat(capturedBatch(roomId))
                .extracting(StompChatMessagePayload::content)
                .containsExactly("첫 번째", "두 번째");
        assertThat(registry.counter("chat.message.batch.overflow").count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("로컬 멤버가 없으면 적재하지 않는다")
    void skipsWhenNoLocalMember() {
        // given
        when(delegate.hasLocalSubscriber(anyString())).thenReturn(false);
        BatchingChatMessageBroadcastAdapter sut = sut(true, 300);

        // when
        boolean accepted = sut.broadcast(command(roomId, "m1", "안 감"), "tx-1");
        sut.flush();

        // then
        assertThat(accepted).isFalse();
        verify(delegate, never()).broadcastBatch(anyString(), any(), anyString());
    }

    @Test
    @DisplayName("비운 뒤 다시 flush 해도 재전송하지 않는다")
    void doesNotResendAfterFlush() {
        // given
        when(delegate.hasLocalSubscriber(anyString())).thenReturn(true);
        BatchingChatMessageBroadcastAdapter sut = sut(true, 300);
        sut.broadcast(command(roomId, "m1", "한 번"), "tx-1");
        sut.flush();

        // when
        sut.flush();

        // then
        verify(delegate).broadcastBatch(anyString(), any(), anyString());
    }

    @Test
    @DisplayName("배칭을 끄면 즉시 delegate 로 위임한다")
    void delegatesImmediatelyWhenDisabled() {
        // given
        BatchingChatMessageBroadcastAdapter sut = sut(false, 300);
        ChatMessageBroadcastCommand command = command(roomId, "m1", "즉시");

        // when
        sut.broadcast(command, "tx-1");

        // then
        verify(delegate).broadcast(command, "tx-1");
        verify(delegate, never()).broadcastBatch(anyString(), any(), anyString());
    }

    @Test
    @DisplayName("배칭을 끄면 스케줄러를 띄우지 않는다")
    void doesNotStartSchedulerWhenDisabled() {
        // given
        BatchingChatMessageBroadcastAdapter sut = sut(false, 300);

        // when
        sut.start();
        sut.flush();

        // then
        verifyNoInteractions(delegate);
    }
}

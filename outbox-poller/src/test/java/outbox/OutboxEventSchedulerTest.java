package outbox;

import org.example.outbox.properties.OutboxPollerProperties;
import org.example.outbox.domain.OutboxDispatchType;
import org.example.outbox.OutboxEventScheduler;
import org.example.outbox.application.OutboxService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OutboxEventSchedulerTest {

    @Mock
    private OutboxService outboxPollerService;

    private OutboxEventScheduler sut;

    @BeforeEach
    void setUp() {
        OutboxPollerProperties outboxPollerProperties = properties(true, true);

        sut = new OutboxEventScheduler(outboxPollerService, outboxPollerProperties);
    }

    @Test
    @DisplayName("general poller가 enabled이면 GENERAL 타입 pending outbox 발행을 요청한다")
    void pollGeneral_whenEnabled_publishesGeneralPendingOutbox() {
        // given
        sut = new OutboxEventScheduler(
                outboxPollerService,
                properties(true, false)
        );

        // when
        sut.pollGeneral();

        // then
        verify(outboxPollerService).publishPending(OutboxDispatchType.GENERAL);
        verify(outboxPollerService, never()).publishPending(OutboxDispatchType.BROADCAST);
    }

    @Test
    @DisplayName("general poller가 disabled이면 아무 작업도 하지 않는다")
    void pollGeneral_whenDisabled_doesNothing() {
        // given
        sut = new OutboxEventScheduler(
                outboxPollerService,
                properties(false, true)
        );

        // when
        sut.pollGeneral();

        // then
        verify(outboxPollerService, never()).publishPending(any());
    }

    @Test
    @DisplayName("broadcast poller가 enabled이면 BROADCAST 타입 pending outbox 발행을 요청한다")
    void pollBroadcast_whenEnabled_publishesBroadcastPendingOutbox() {
        // given
        sut = new OutboxEventScheduler(
                outboxPollerService,
                properties(false, true)
        );

        // when
        sut.pollBroadcast();

        // then
        verify(outboxPollerService).publishPending(OutboxDispatchType.BROADCAST);
        verify(outboxPollerService, never()).publishPending(OutboxDispatchType.GENERAL);
    }

    @Test
    @DisplayName("broadcast poller가 disabled이면 아무 작업도 하지 않는다")
    void pollBroadcast_whenDisabled_doesNothing() {
        // given
        sut = new OutboxEventScheduler(
                outboxPollerService,
                properties(true, false)
        );

        // when
        sut.pollBroadcast();

        // then
        verify(outboxPollerService, never()).publishPending(any());
    }

    private OutboxPollerProperties properties(boolean generalEnabled, boolean broadcastEnabled) {
        return new OutboxPollerProperties(
                new OutboxPollerProperties.Item(
                        generalEnabled,
                        1000,
                        100,
                        3
                ),
                new OutboxPollerProperties.Item(
                        broadcastEnabled,
                        1000,
                        100,
                        3
                )
        );
    }
}
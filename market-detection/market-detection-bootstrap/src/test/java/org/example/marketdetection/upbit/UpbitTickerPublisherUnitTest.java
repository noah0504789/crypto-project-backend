package org.example.marketdetection.upbit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.example.common.time.Clock;
import org.example.marketdetection.infra.properties.UpbitProperties;
import org.example.marketdetection.upbit.UpbitTickerCoalescingBuffer.TickerTask;
import org.example.marketdetection.upbit.event.UpbitTickerEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.messaging.Message;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@ExtendWith(MockitoExtension.class)
class UpbitTickerPublisherUnitTest {

    private static final String CODE = "KRW-BTC";

    @Mock private UpbitTickerCoalescingBuffer tickerBuffer;

    @Mock private StreamBridge streamBridge;

    @Mock private ThreadPoolTaskExecutor workerExecutor;

    @Mock private Future<?> workerFuture;

    @Mock private Clock clock;

    @AfterEach
    void clearInterruptStatus() {
        Thread.interrupted();
    }

    @Test
    @DisplayName("시작과 종료 lifecycle에서 설정된 worker를 제출하고 interrupt 취소한다")
    void lifecycle_startAndStop_submitAndInterruptWorkers() {
        // given
        UpbitTickerPublisher sut = createSut(2);
        doReturn(workerFuture).when(workerExecutor).submit(any(Runnable.class));

        // when
        sut.start();
        sut.stop();

        // then
        verify(workerExecutor, times(2)).submit(any(Runnable.class));
        verify(workerFuture, times(2)).cancel(true);
    }

    @Test
    @DisplayName("worker 제출이 일부 실패하면 시작된 worker를 취소하고 다시 시작할 수 있다")
    void lifecycle_partialSubmissionFailure_cancelSubmittedWorkersAndAllowRestart() {
        // given
        UpbitTickerPublisher sut = createSut(2);
        IllegalStateException rejection = new IllegalStateException("rejected");
        doReturn(workerFuture)
                .doThrow(rejection)
                .doReturn(workerFuture)
                .doReturn(workerFuture)
                .when(workerExecutor)
                .submit(any(Runnable.class));

        // when & then
        assertThatThrownBy(sut::start).isSameAs(rejection);
        assertThat(sut.isRunning()).isFalse();
        verify(workerFuture).cancel(true);

        // when
        sut.start();
        sut.stop();

        // then
        assertThat(sut.isRunning()).isFalse();
        verify(workerFuture, times(3)).cancel(true);
    }

    @Test
    @DisplayName("worker는 최신 ticker를 output binding으로 발행하고 처리를 완료한다")
    void runWorker_tickerTask_publishAndComplete() throws Exception {
        // given
        UpbitTickerPublisher sut = createSut(1);
        UpbitTickerEvent event = tickerEvent(100.0);
        TickerTask task = new TickerTask(CODE, event, 1L);

        given(tickerBuffer.take()).willReturn(task).willThrow(new InterruptedException());
        given(streamBridge.send(any(String.class), any(Message.class))).willReturn(true);
        given(clock.monotonicTimeNanos()).willReturn(100L, 250L);

        // when
        sut.runWorker();

        // then
        ArgumentCaptor<Message<?>> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(streamBridge)
                .send(
                        org.mockito.ArgumentMatchers.eq(UpbitTickerPublisher.OUTPUT_BINDING),
                        messageCaptor.capture());
        verify(tickerBuffer).complete(task);
        verify(clock, times(2)).monotonicTimeNanos();
        assertThat(messageCaptor.getValue().getPayload()).isSameAs(event);
        assertThat(Thread.currentThread().isInterrupted()).isTrue();
    }

    @Test
    @DisplayName("take 대기가 interrupt되면 status를 복원하고 worker loop를 종료한다")
    void runWorker_takeInterrupted_restoreStatusAndExit() throws Exception {
        // given
        UpbitTickerPublisher sut = createSut(1);
        given(tickerBuffer.take()).willThrow(new InterruptedException());

        // when
        sut.runWorker();

        // then
        assertThat(Thread.currentThread().isInterrupted()).isTrue();
        verify(tickerBuffer).take();
    }

    @Test
    @DisplayName("두 worker는 서로 다른 종목을 병렬로 발행할 수 있다")
    void runWorker_differentCodes_publishConcurrently() throws Exception {
        // given
        UpbitProperties properties = createProperties(2);
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        UpbitTickerCoalescingBuffer actualBuffer =
                new UpbitTickerCoalescingBuffer(properties, meterRegistry);
        UpbitTickerPublisher sut =
                new UpbitTickerPublisher(
                        actualBuffer,
                        streamBridge,
                        workerExecutor,
                        properties,
                        meterRegistry,
                        clock);
        ExecutorService workers = Executors.newFixedThreadPool(2);
        CountDownLatch publishing = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);

        given(streamBridge.send(any(String.class), any(Message.class)))
                .willAnswer(
                        invocation -> {
                            publishing.countDown();
                            release.await(1, TimeUnit.SECONDS);
                            return true;
                        });
        given(clock.monotonicTimeNanos()).willReturn(100L);

        try {
            Future<?> firstWorker = workers.submit(sut::runWorker);
            Future<?> secondWorker = workers.submit(sut::runWorker);

            // when
            actualBuffer.offer(tickerEvent(CODE, 100.0));
            actualBuffer.offer(tickerEvent("KRW-ETH", 200.0));

            // then
            assertThat(publishing.await(1, TimeUnit.SECONDS)).isTrue();
            release.countDown();
            firstWorker.cancel(true);
            secondWorker.cancel(true);
        } finally {
            release.countDown();
            workers.shutdownNow();
            assertThat(workers.awaitTermination(1, TimeUnit.SECONDS)).isTrue();
        }
    }

    private UpbitTickerPublisher createSut(int workerCount) {
        return new UpbitTickerPublisher(
                tickerBuffer,
                streamBridge,
                workerExecutor,
                createProperties(workerCount),
                new SimpleMeterRegistry(),
                clock);
    }

    private UpbitProperties createProperties(int workerCount) {
        return new UpbitProperties(
                new UpbitProperties.Websocket(
                        "wss://api.upbit.com/websocket/v1",
                        "test",
                        Duration.ZERO,
                        100,
                        workerCount),
                new UpbitProperties.Ticker(
                        new UpbitProperties.Ticker.Alert(3, Duration.ofSeconds(10))),
                new UpbitProperties.Store(
                        new UpbitProperties.Store.StoreTicker(
                                "upbit-ticker-store",
                                Duration.ofMinutes(3),
                                Duration.ofMinutes(3),
                                false)));
    }

    private UpbitTickerEvent tickerEvent(Double tradePrice) {
        return tickerEvent(CODE, tradePrice);
    }

    private UpbitTickerEvent tickerEvent(String code, Double tradePrice) {
        return new UpbitTickerEvent(
                "ticker",
                code,
                null,
                null,
                null,
                tradePrice,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null);
    }
}

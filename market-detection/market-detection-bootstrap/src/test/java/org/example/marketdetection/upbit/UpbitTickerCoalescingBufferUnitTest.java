package org.example.marketdetection.upbit;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.example.marketdetection.infra.properties.UpbitProperties;
import org.example.marketdetection.upbit.UpbitTickerCoalescingBuffer.TickerTask;
import org.example.marketdetection.upbit.event.UpbitTickerEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class UpbitTickerCoalescingBufferUnitTest {

    private static final String CODE = "KRW-BTC";

    @Test
    @DisplayName("같은 종목의 ticker는 하나의 key로 예약되고 최신값만 유지한다")
    void offer_sameCode_coalesceLatestTicker() throws Exception {
        // given
        UpbitTickerCoalescingBuffer sut = createSut(10);
        UpbitTickerEvent first = tickerEvent(CODE, 100.0);
        UpbitTickerEvent second = tickerEvent(CODE, 101.0);
        UpbitTickerEvent third = tickerEvent(CODE, 102.0);

        // when
        sut.offer(first);
        sut.offer(second);
        sut.offer(third);

        // then
        assertThat(sut.readyQueueSize()).isOne();
        assertThat(sut.latestTicker(CODE)).isSameAs(third);
        assertThat(sut.take().tickerEvent()).isSameAs(third);
    }

    @Test
    @DisplayName("서로 다른 종목은 각각 ready queue에 예약한다")
    void offer_differentCodes_enqueueEachCode() throws Exception {
        // given
        UpbitTickerCoalescingBuffer sut = createSut(3);

        // when
        List.of("KRW-BTC", "KRW-ETH", "KRW-XRP")
                .forEach(code -> sut.offer(tickerEvent(code, 100.0)));

        // then
        assertThat(sut.readyQueueSize()).isEqualTo(3);
        assertThat(List.of(sut.take().code(), sut.take().code(), sut.take().code()))
                .containsExactlyInAnyOrder("KRW-BTC", "KRW-ETH", "KRW-XRP");
    }

    @Test
    @DisplayName("여러 producer가 같은 종목을 동시에 갱신해도 key는 한 번만 예약한다")
    void offer_concurrentSameCode_enqueueOnce() throws Exception {
        // given
        UpbitTickerCoalescingBuffer sut = createSut(10);
        ExecutorService producers = Executors.newFixedThreadPool(8);
        CountDownLatch start = new CountDownLatch(1);

        try {
            List<? extends Future<?>> futures =
                    java.util.stream.IntStream.range(0, 100)
                            .mapToObj(
                                    index ->
                                            producers.submit(
                                                    () -> {
                                                        start.await();
                                                        sut.offer(
                                                                tickerEvent(CODE, (double) index));
                                                        return null;
                                                    }))
                            .toList();

            // when
            start.countDown();

            for (Future<?> future : futures) {
                future.get(3, TimeUnit.SECONDS);
            }

            // then
            assertThat(sut.readyQueueSize()).isOne();
            assertThat(sut.take().code()).isEqualTo(CODE);
        } finally {
            producers.shutdownNow();
        }
    }

    @Test
    @DisplayName("처리 중 들어온 ticker는 최신값으로 합쳐 처리 완료 후 재예약한다")
    void complete_updatedWhileProcessing_requeueLatestTicker() throws Exception {
        // given
        UpbitTickerCoalescingBuffer sut = createSut(10);
        UpbitTickerEvent first = tickerEvent(CODE, 100.0);
        UpbitTickerEvent second = tickerEvent(CODE, 101.0);
        UpbitTickerEvent third = tickerEvent(CODE, 102.0);

        sut.offer(first);
        TickerTask processing = sut.take();

        // when
        sut.offer(second);
        sut.offer(third);
        sut.complete(processing);

        // then
        assertThat(sut.readyQueueSize()).isOne();
        assertThat(sut.take().tickerEvent()).isSameAs(third);
    }

    @Test
    @DisplayName("동일 종목을 처리하는 동안에는 다른 worker가 같은 종목을 가져갈 수 없다")
    void take_sameCodeProcessing_doNotRunConcurrently() throws Exception {
        // given
        UpbitTickerCoalescingBuffer sut = createSut(10);
        ExecutorService consumer = Executors.newSingleThreadExecutor();

        try {
            sut.offer(tickerEvent(CODE, 100.0));
            TickerTask processing = sut.take();
            Future<TickerTask> nextTake = consumer.submit(sut::take);

            // when
            sut.offer(tickerEvent(CODE, 102.0));

            // then
            Thread.sleep(100);
            assertThat(nextTake.isDone()).isFalse();

            sut.complete(processing);
            assertThat(nextTake.get(1, TimeUnit.SECONDS).tickerEvent().tradePrice())
                    .isEqualTo(102.0);
        } finally {
            consumer.shutdownNow();
        }
    }

    @Test
    @DisplayName("ready queue가 가득 차면 예약 상태를 복구해 다음 ticker가 다시 등록할 수 있다")
    void offer_readyQueueFull_nextTickerCanRetry() throws Exception {
        // given
        UpbitTickerCoalescingBuffer sut = createSut(1);
        UpbitTickerEvent firstEth = tickerEvent("KRW-ETH", 100.0);
        UpbitTickerEvent latestEth = tickerEvent("KRW-ETH", 101.0);

        assertThat(sut.offer(tickerEvent(CODE, 100.0))).isTrue();
        assertThat(sut.offer(firstEth)).isFalse();

        TickerTask bitcoin = sut.take();
        sut.complete(bitcoin);

        // when
        boolean retried = sut.offer(latestEth);

        // then
        assertThat(retried).isTrue();
        assertThat(sut.take().tickerEvent()).isSameAs(latestEth);
    }

    @Test
    @DisplayName("빈 ready queue에서 대기하는 take는 interrupt에 응답한다")
    void take_emptyQueue_interruptible() throws Exception {
        // given
        UpbitTickerCoalescingBuffer sut = createSut(1);
        ExecutorService consumer = Executors.newSingleThreadExecutor();
        CountDownLatch started = new CountDownLatch(1);

        try {
            Future<Boolean> interrupted =
                    consumer.submit(
                            () -> {
                                started.countDown();

                                try {
                                    sut.take();
                                    return false;
                                } catch (InterruptedException e) {
                                    Thread.currentThread().interrupt();
                                    return Thread.currentThread().isInterrupted();
                                }
                            });

            started.await(1, TimeUnit.SECONDS);

            // when
            consumer.shutdownNow();

            // then
            assertThat(interrupted.get(1, TimeUnit.SECONDS)).isTrue();
        } finally {
            consumer.shutdownNow();
        }
    }

    private UpbitTickerCoalescingBuffer createSut(int readyQueueCapacity) {
        return new UpbitTickerCoalescingBuffer(
                createProperties(readyQueueCapacity), new SimpleMeterRegistry());
    }

    private UpbitProperties createProperties(int readyQueueCapacity) {
        return new UpbitProperties(
                new UpbitProperties.Websocket(
                        "wss://api.upbit.com/websocket/v1",
                        "test",
                        Duration.ZERO,
                        readyQueueCapacity,
                        3),
                new UpbitProperties.Ticker(
                        new UpbitProperties.Ticker.Alert(3, Duration.ofSeconds(10))),
                new UpbitProperties.Store(
                        new UpbitProperties.Store.StoreTicker(
                                "upbit-ticker-store",
                                Duration.ofMinutes(3),
                                Duration.ofMinutes(3),
                                false)));
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

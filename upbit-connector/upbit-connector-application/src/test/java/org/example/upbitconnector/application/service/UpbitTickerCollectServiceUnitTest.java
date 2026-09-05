package org.example.upbitconnector.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.example.common.time.Clock;
import org.example.upbitconnector.application.port.out.UpbitTickerMetricsPort;
import org.example.upbitconnector.application.properties.UpbitProperties;
import org.example.upbitconnector.contract.event.UpbitTickerEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.test.StepVerifier;

class UpbitTickerCollectServiceUnitTest {

    private static final Duration PUBLISH_INTERVAL = Duration.ofSeconds(7);
    private static final Duration VERIFY_TIMEOUT = Duration.ofSeconds(5);

    private final UpbitProperties properties =
            new UpbitProperties(
                    new UpbitProperties.Websocket(
                            "wss://example.invalid/websocket/v1",
                            "test",
                            PUBLISH_INTERVAL,
                            Duration.ofSeconds(1),
                            Duration.ofSeconds(30)));

    private final List<UpbitTickerEvent> published = new ArrayList<>();

    private final RecordingMetricsPort metrics = new RecordingMetricsPort();

    private final UpbitTickerCollectService collectService =
            new UpbitTickerCollectService(
                    Flux::never,
                    event -> {
                        published.add(event);
                        return Mono.empty();
                    },
                    metrics,
                    properties,
                    new FixedClock());

    @Test
    @DisplayName("7초 구간 안의 여러 시세는 종목별 마지막 값 하나만 남는다")
    void keepsOnlyLatestPerCodeWithinInterval() {
        // given: 7초 구간 안에서 BTC 3건, ETH 1건이 들어온다.
        // 종목별 첫 ticker가 들어와 그룹이 만들어진 시점부터 7초를 센다.
        // BTC는 t=0ms → t=7s, ETH는 t=300ms → t=7.3s에 각 그룹의 최신값을 발행한다.

        // when & then
        StepVerifier.withVirtualTime(() -> collectService.collect(source()))
                .thenAwait(PUBLISH_INTERVAL.multipliedBy(2))
                .expectNextMatches(event -> matches(event, "KRW-BTC", 120.0))
                .expectNextMatches(event -> matches(event, "KRW-ETH", 10.0))
                .thenCancel()
                .verify(VERIFY_TIMEOUT);

        assertThat(published)
                .extracting(UpbitTickerEvent::code, UpbitTickerEvent::tradePrice)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("KRW-BTC", 120.0),
                        org.assertj.core.groups.Tuple.tuple("KRW-ETH", 10.0));
    }

    @Test
    @DisplayName("종목 그룹이 만들어진 후 7초 전에는 ticker를 발행하지 않는다")
    void emitsNothingBeforeInterval() {
        // given & when & then
        StepVerifier.withVirtualTime(() -> collectService.collect(source()))
                .expectSubscription()
                .expectNoEvent(PUBLISH_INTERVAL.minusMillis(1))
                .thenCancel()
                .verify(VERIFY_TIMEOUT);

        assertThat(published).isEmpty();
    }

    @Test
    @DisplayName("Kafka 발행이 느려도 종목별로 가장 최신 ticker 하나만 대기한다")
    void keepsOnlyOneLatestTickerPerCodeWhilePublishIsSlow() {
        // given: 첫 7초 구간의 최신값을 Kafka가 처리하는 동안 다음 두 구간의 ticker가 들어온다.
        Sinks.One<Void> firstPublish = Sinks.one();
        List<UpbitTickerEvent> publishAttempts = new ArrayList<>();
        UpbitTickerCollectService service = new UpbitTickerCollectService(
                Flux::never,
                event -> {
                    publishAttempts.add(event);
                    return publishAttempts.size() == 1 ? firstPublish.asMono() : Mono.empty();
                },
                metrics,
                properties,
                new FixedClock());

        // when & then: 120을 처리하는 동안 130, 140, 150이 들어오면 대기값은 150 하나뿐이다.
        StepVerifier.withVirtualTime(() -> service.collect(slowPublishSource()))
                .thenAwait(PUBLISH_INTERVAL.multipliedBy(3).plusSeconds(1))
                .then(firstPublish::tryEmitEmpty)
                .expectNextMatches(event -> matches(event, "KRW-BTC", 120.0))
                .expectNextMatches(event -> matches(event, "KRW-BTC", 150.0))
                .thenCancel()
                .verify(VERIFY_TIMEOUT);

        assertThat(publishAttempts)
                .extracting(UpbitTickerEvent::tradePrice)
                .containsExactly(120.0, 150.0);
    }

    @Test
    @DisplayName("한 시세의 발행 실패가 다음 시세 처리를 막지 않는다")
    void continuesAfterPublishFailure() {
        // given
        AtomicInteger publishAttempts = new AtomicInteger();
        List<UpbitTickerEvent> successfullyPublished = new ArrayList<>();
        UpbitTickerCollectService service = new UpbitTickerCollectService(
                Flux::never,
                event -> {
                    if (publishAttempts.getAndIncrement() == 0) {
                        return Mono.error(new IllegalStateException("first publish failed"));
                    }
                    successfullyPublished.add(event);
                    return Mono.empty();
                },
                metrics,
                properties,
                new FixedClock());

        // when & then
        StepVerifier.withVirtualTime(() -> service.collect(Flux.concat(
                                Flux.just(ticker("KRW-BTC", 100.0)),
                                Flux.just(ticker("KRW-BTC", 110.0))
                                        .delaySubscription(PUBLISH_INTERVAL.plusMillis(100)))
                        .concatWith(Flux.never())))
                .thenAwait(PUBLISH_INTERVAL.multipliedBy(3))
                .expectNextMatches(event -> matches(event, "KRW-BTC", 100.0))
                .expectNextMatches(event -> matches(event, "KRW-BTC", 110.0))
                .thenCancel()
                .verify(VERIFY_TIMEOUT);

        assertThat(successfullyPublished)
                .extracting(UpbitTickerEvent::tradePrice)
                .containsExactly(110.0);
    }

    @Test
    @DisplayName("수신은 스로틀 이전 전량을, 발행은 통과한 건만 센다")
    void countsReceivedBeforeThrottleAndPublishedAfter() {
        // given & when: 7초 구간에 BTC 3건·ETH 1건이 들어오고 종목별 마지막 값만 발행된다.
        StepVerifier.withVirtualTime(() -> collectService.collect(source()))
                .thenAwait(PUBLISH_INTERVAL.multipliedBy(2))
                .expectNextCount(2)
                .thenCancel()
                .verify(VERIFY_TIMEOUT);

        // then
        assertThat(metrics.received).containsEntry("KRW-BTC", 3L).containsEntry("KRW-ETH", 1L);
        assertThat(metrics.published).containsEntry("KRW-BTC", 1L).containsEntry("KRW-ETH", 1L);
        assertThat(metrics.publishFailed).isEmpty();
    }

    @Test
    @DisplayName("발행 실패는 실패 지표로 세고 성공 지표는 올리지 않는다")
    void countsPublishFailureSeparately() {
        // given
        RecordingMetricsPort failureMetrics = new RecordingMetricsPort();
        UpbitTickerCollectService service = new UpbitTickerCollectService(
                Flux::never,
                event -> Mono.error(new IllegalStateException("publish failed")),
                failureMetrics,
                properties,
                new FixedClock());

        // when
        StepVerifier.withVirtualTime(() -> service.collect(
                        Flux.just(ticker("KRW-BTC", 100.0)).concatWith(Flux.never())))
                .thenAwait(PUBLISH_INTERVAL.plusSeconds(1))
                .expectNextCount(1)
                .thenCancel()
                .verify(VERIFY_TIMEOUT);

        // then
        assertThat(failureMetrics.publishFailed).containsEntry("KRW-BTC", 1L);
        assertThat(failureMetrics.published).isEmpty();
    }

    private boolean matches(UpbitTickerEvent event, String code, double tradePrice) {
        return code.equals(event.code()) && tradePrice == event.tradePrice();
    }

    private static final class RecordingMetricsPort implements UpbitTickerMetricsPort {

        private final Map<String, Long> received = new LinkedHashMap<>();
        private final Map<String, Long> published = new LinkedHashMap<>();
        private final Map<String, Long> publishFailed = new LinkedHashMap<>();

        @Override
        public void tickerReceived(String code) {
            received.merge(code, 1L, Long::sum);
        }

        @Override
        public void tickerPublished(String code, long elapsedNanos) {
            published.merge(code, 1L, Long::sum);
        }

        @Override
        public void tickerPublishFailed(String code) {
            publishFailed.merge(code, 1L, Long::sum);
        }
    }

    /** 경과시간 계산만 쓰므로 단조 증가만 흉내 낸다. */
    private static final class FixedClock implements Clock {

        @Override
        public long nowMs() {
            return 0L;
        }

        @Override
        public long monotonicTimeNanos() {
            return 0L;
        }

        @Override
        public Instant now() {
            return Instant.EPOCH;
        }

        @Override
        public LocalDateTime nowLocalDateTime() {
            return LocalDateTime.ofInstant(Instant.EPOCH, ZoneOffset.UTC);
        }
    }

    /** 완료 시점의 처리 방식에 결과가 좌우되지 않도록 스트림을 끝내지 않는다. */
    private Flux<UpbitTickerEvent> source() {
        return Flux.concat(
                        Flux.just(ticker("KRW-BTC", 100.0)),
                        Flux.just(ticker("KRW-BTC", 110.0)).delayElements(Duration.ofMillis(200)),
                        Flux.just(ticker("KRW-ETH", 10.0)).delayElements(Duration.ofMillis(100)),
                        Flux.just(ticker("KRW-BTC", 120.0)).delayElements(Duration.ofMillis(100)))
                .concatWith(Flux.never());
    }

    private Flux<UpbitTickerEvent> slowPublishSource() {
        return Flux.merge(
                        Flux.just(ticker("KRW-BTC", 100.0)),
                        Flux.just(ticker("KRW-BTC", 110.0)).delaySubscription(Duration.ofSeconds(1)),
                        Flux.just(ticker("KRW-BTC", 120.0)).delaySubscription(Duration.ofSeconds(2)),
                        Flux.just(ticker("KRW-BTC", 130.0)).delaySubscription(Duration.ofSeconds(8)),
                        Flux.just(ticker("KRW-BTC", 140.0)).delaySubscription(Duration.ofSeconds(10)),
                        Flux.just(ticker("KRW-BTC", 150.0)).delaySubscription(Duration.ofSeconds(15)))
                .concatWith(Flux.never());
    }

    private UpbitTickerEvent ticker(String code, Double tradePrice) {
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
                1L,
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
                1L,
                null);
    }
}

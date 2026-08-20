package org.example.upbitconnector.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.upbitconnector.application.port.out.UpbitTickerPublishPort;
import org.example.upbitconnector.application.port.out.UpbitTickerStreamPort;
import org.example.upbitconnector.application.properties.UpbitProperties;
import org.example.upbitconnector.contract.event.UpbitTickerEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@RequiredArgsConstructor
public class UpbitTickerCollectService {

    private final UpbitTickerStreamPort tickerStreamPort;
    private final UpbitTickerPublishPort tickerPublishPort;
    private final UpbitProperties properties;

    public Mono<Void> collect() {
        return collect(tickerStreamPort.ticker())
                .then();
    }

    public Flux<UpbitTickerEvent> collect(Flux<UpbitTickerEvent> source) {
        return source.groupBy(UpbitTickerEvent::code)
                // groupBy로 만든 모든 종목 그룹을 바로 구독해야 원본 스트림이 멈추지 않는다.
                .flatMap(this::throttle, Integer.MAX_VALUE);
    }

    private Flux<UpbitTickerEvent> throttle(Flux<UpbitTickerEvent> codeGroup) {
        return codeGroup
                .sample(properties.websocket().tickerPublishInterval())
                .onBackpressureLatest()
                .concatMap(this::publish);
    }

    private Mono<UpbitTickerEvent> publish(UpbitTickerEvent event) {
        log.debug("[upbit] ticker. code={} price={}", event.code(), event.tradePrice());

        // 한 종목의 발행 실패가 전체 수집을 끊지 않는다. 시세는 다음 구간 값으로 곧 대체된다.
        return tickerPublishPort.publish(event)
                .doOnError(error -> log.error("[upbit] ticker publish failed. code={}", event.code(), error))
                .onErrorComplete()
                .thenReturn(event);
    }
}

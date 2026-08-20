package org.example.upbitconnector.adapter.out.upbit;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import org.example.upbitconnector.application.properties.UpbitProperties;
import org.example.upbitconnector.application.service.UpbitTickerCollectService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.scheduler.VirtualTimeScheduler;

@ExtendWith(MockitoExtension.class)
class UpbitTickerCollectStarterUnitTest {

    @Mock private UpbitTickerCollectService collectService;

    @Test
    @DisplayName("수집 스트림이 오류로 종료되면 backoff 후 새 파이프라인을 구독한다")
    void retriesCollectionAfterTerminalError() {
        given(collectService.collect())
                .willReturn(Mono.error(new IllegalStateException("terminated")))
                .willReturn(Mono.never());

        UpbitTickerCollectStarter starter = new UpbitTickerCollectStarter(collectService, properties());
        VirtualTimeScheduler scheduler = VirtualTimeScheduler.getOrSet();

        try {
            starter.run(null);
            scheduler.advanceTimeBy(Duration.ofSeconds(2));
        } finally {
            starter.stop();
            VirtualTimeScheduler.reset();
        }

        verify(collectService, times(2)).collect();
    }

    @Test
    @DisplayName("수집 스트림이 정상 완료되어도 backoff 후 새 파이프라인을 구독한다")
    void repeatsCollectionAfterCompletion() {
        given(collectService.collect()).willReturn(Mono.empty()).willReturn(Mono.never());

        UpbitTickerCollectStarter starter = new UpbitTickerCollectStarter(collectService, properties());
        VirtualTimeScheduler scheduler = VirtualTimeScheduler.getOrSet();

        try {
            starter.run(null);
            scheduler.advanceTimeBy(Duration.ofSeconds(2));
        } finally {
            starter.stop();
            VirtualTimeScheduler.reset();
        }

        verify(collectService, times(2)).collect();
    }

    private UpbitProperties properties() {
        return new UpbitProperties(new UpbitProperties.Websocket(
                "wss://example.invalid/websocket/v1",
                "test",
                Duration.ofSeconds(7),
                Duration.ofSeconds(1),
                Duration.ofSeconds(1)));
    }
}

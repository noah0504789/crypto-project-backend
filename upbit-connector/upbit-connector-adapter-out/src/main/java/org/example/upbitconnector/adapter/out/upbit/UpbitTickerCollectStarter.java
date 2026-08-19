package org.example.upbitconnector.adapter.out.upbit;

import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.upbitconnector.application.service.UpbitTickerCollectService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import reactor.core.Disposable;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "upbit.websocket", name = "enabled", havingValue = "true", matchIfMissing = true)
public class UpbitTickerCollectStarter implements ApplicationRunner {

    private final UpbitTickerCollectService collectService;

    private volatile Disposable subscription;

    @Override
    public void run(ApplicationArguments args) {
        subscription = collectService.collect()
                .doOnError(this::logStreamTermination)
                .onErrorComplete()
                .subscribe();

        log.info("[upbit] ticker collect started.");
    }

    @PreDestroy
    public void stop() {
        if (subscription == null) {
            return;
        }

        subscription.dispose();
        subscription = null;

        log.info("[upbit] ticker collect stopped.");
    }

    private void logStreamTermination(Throwable error) {
        log.error("[upbit] ticker stream terminated.", error);
    }
}

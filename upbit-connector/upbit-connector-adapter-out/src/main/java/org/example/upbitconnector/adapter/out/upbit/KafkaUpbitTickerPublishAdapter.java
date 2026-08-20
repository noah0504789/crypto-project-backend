package org.example.upbitconnector.adapter.out.upbit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.common.event.KafkaEventFactory;
import org.example.upbitconnector.application.port.out.UpbitTickerPublishPort;
import org.example.upbitconnector.contract.event.UpbitTickerEvent;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaUpbitTickerPublishAdapter implements UpbitTickerPublishPort {

    public static final String OUTPUT_BINDING = "upbitTickerEvent-out-0";

    private final StreamBridge streamBridge;

    @Override
    public Mono<Void> publish(UpbitTickerEvent event) {
        // StreamBridge.send 는 블로킹이라 이벤트 루프에서 부르지 않는다.
        return Mono.<Void>fromRunnable(() -> send(event))
                .subscribeOn(Schedulers.boundedElastic());
    }

    private void send(UpbitTickerEvent event) {
        Message<UpbitTickerEvent> message =
                KafkaEventFactory.createEventMessage(event, event.code(), UpbitTickerEvent.class.getName());

        if (!streamBridge.send(OUTPUT_BINDING, message)) {
            throw new IllegalStateException("Upbit ticker publish failed. code=" + event.code());
        }
    }
}

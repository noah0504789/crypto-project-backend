package org.example.upbitconnector.application.port.out;

import org.example.upbitconnector.contract.event.UpbitTickerEvent;
import reactor.core.publisher.Mono;

public interface UpbitTickerPublishPort {

    Mono<Void> publish(UpbitTickerEvent event);
}

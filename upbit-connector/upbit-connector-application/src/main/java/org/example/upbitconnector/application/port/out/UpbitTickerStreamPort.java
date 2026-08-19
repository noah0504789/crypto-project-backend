package org.example.upbitconnector.application.port.out;

import org.example.upbitconnector.contract.event.UpbitTickerEvent;
import reactor.core.publisher.Flux;

public interface UpbitTickerStreamPort {

    Flux<UpbitTickerEvent> ticker();
}

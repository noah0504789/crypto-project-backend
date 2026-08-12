package org.example.marketdetection.adapter.in.stream;

import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.Named;
import org.example.common.time.Clock;
import org.example.marketdetection.contract.event.PriceAlertDetectedEvent;
import org.example.marketdetection.infra.properties.UpbitProperties;
import org.example.marketdetection.upbit.UpbitTickerProcessor;
import org.example.marketdetection.upbit.event.UpbitTickerEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class KafkaMarketDetectionBinder {

    @Bean
    public Function<KStream<String, UpbitTickerEvent>, KStream<String, PriceAlertDetectedEvent>>
            upbitTickerAlertEventProcessor(UpbitProperties properties, Clock clock) {
        return input ->
                input.process(
                        () -> new UpbitTickerProcessor(properties, clock),
                        Named.as("upbit-ticker-watcher"),
                        properties.store().ticker().name());
    }
}

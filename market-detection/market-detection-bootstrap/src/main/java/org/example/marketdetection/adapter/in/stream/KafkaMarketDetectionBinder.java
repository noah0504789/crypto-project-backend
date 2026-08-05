package org.example.marketdetection.adapter.in.stream;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.Named;
import org.example.common.event.KafkaEvent;
import org.example.common.event.KafkaEventFactory;
import org.example.common.time.Clock;
import org.example.marketdetection.contract.event.PriceAlertDetectedEvent;
import org.example.marketdetection.upbit.event.UpbitTickerEvent;
import org.example.marketdetection.infra.properties.UpbitProperties;
import org.example.marketdetection.upbit.UpbitTickerProcessor;
import org.example.marketdetection.upbit.UpbitWebsocketListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;

import java.util.function.Function;
import java.util.function.Supplier;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class KafkaMarketDetectionBinder {

    @Bean
    public Supplier<Message<KafkaEvent>> upbitTickerEventSupplier(UpbitWebsocketListener upbitWebsocketListener) {
        return () -> {
            KafkaEvent event = upbitWebsocketListener.pollTickerQueue();

            if (event == null) {
                return null;
            }

            return KafkaEventFactory.createEventMessage(
                    event,
                    event.getPartitionKey(),
                    event.getClass().getName()
            );
        };
    }

    @Bean
    public Function<KStream<String, UpbitTickerEvent>, KStream<String, PriceAlertDetectedEvent>> upbitTickerAlertEventProcessor(
            UpbitProperties properties,
            Clock clock
    ) {
        return input -> input.process(
                () -> new UpbitTickerProcessor(properties, clock),
                Named.as("upbit-ticker-watcher"),
                properties.store().ticker().name()
        );
    }
}

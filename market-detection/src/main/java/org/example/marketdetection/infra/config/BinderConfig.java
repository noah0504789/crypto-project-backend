package org.example.marketdetection.infra.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.Named;
import org.example.common.event.KafkaEvent;
import org.example.marketdetection.upbit.event.UpbitTickerEvent;
import org.example.marketdetection.infra.properties.UpbitProperties;
import org.example.marketdetection.upbit.UpbitTickerProcessor;
import org.example.marketdetection.upbit.UpbitWebsocketListener;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;

import java.util.function.Consumer;
import java.util.function.Supplier;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class BinderConfig {

    private final UpbitProperties properties;

    @Bean
    public Supplier<Message<KafkaEvent>> upbitTickerEventSupplier(UpbitWebsocketListener upbitWebsocketListener) {
        return () -> {
            KafkaEvent event = upbitWebsocketListener.pollTickerQueue();

            if (event == null) {
                return null;
            }

            return event.toMessage();
        };
    }

    @Bean
    public Consumer<KStream<String, UpbitTickerEvent>> upbitTickerWatcherEventConsumer(StreamBridge streamBridge, ObjectMapper objectMapper) {
        return input -> input.process(
                () -> new UpbitTickerProcessor(streamBridge, objectMapper, properties),
                Named.as("upbit-ticker-watcher"),
                properties.store().ticker().name()
        );
    }
}
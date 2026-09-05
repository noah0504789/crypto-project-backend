package org.example.marketdetection.adapter.in.stream;

import java.util.function.Function;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.Named;
import org.example.marketdetection.application.port.in.PriceAlertDetectUseCase;
import org.example.marketdetection.application.properties.PriceAlertDetectionProperties;
import org.example.marketdetection.contract.event.PriceAlertDetectedEvent;
import org.example.upbitconnector.contract.event.UpbitTickerEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class KafkaMarketDetectionBinder {

    @Bean
    public Function<KStream<String, UpbitTickerEvent>, KStream<String, PriceAlertDetectedEvent>> priceAlertDetectionProcessor(
            PriceAlertDetectUseCase priceAlertDetectUseCase,
            PriceAlertDetectionProperties properties,
            MeterRegistry meterRegistry
    ) {
        return input ->
                input.process(
                        () -> new PriceAlertDetectionProcessor(priceAlertDetectUseCase, properties, meterRegistry),
                        Named.as("price-alert-detector"),
                        properties.store().name());
    }
}

package org.example.marketdetection.infra.config;

import lombok.RequiredArgsConstructor;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.state.StoreBuilder;
import org.apache.kafka.streams.state.Stores;
import org.apache.kafka.streams.state.WindowStore;
import org.example.marketdetection.application.properties.PriceAlertDetectionProperties;
import org.example.marketdetection.application.dto.PricePoint;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.support.serializer.JsonSerde;

@Configuration
@RequiredArgsConstructor
public class StateStoreConfig {

    private final PriceAlertDetectionProperties properties;

    @Bean
    public StoreBuilder<WindowStore<String, PricePoint>> pricePointStore() {
        PriceAlertDetectionProperties.Store store = properties.store();

        return Stores.windowStoreBuilder(
                Stores.persistentWindowStore(store.name(), store.retention(), store.windowSize(), store.retainDuplicates()),
                Serdes.String(),
                new JsonSerde<>(PricePoint.class));
    }
}

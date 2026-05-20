package org.example.infra.config;

import lombok.RequiredArgsConstructor;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.state.StoreBuilder;
import org.apache.kafka.streams.state.Stores;
import org.apache.kafka.streams.state.WindowStore;
import org.example.upbit.event.UpbitTickerValue;
import org.example.infra.properties.UpbitProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.support.serializer.JsonSerde;

@Configuration
@RequiredArgsConstructor
public class StateStoreConfig {

    private final UpbitProperties properties;

    @Bean
    public StoreBuilder<WindowStore<String, UpbitTickerValue>> upbitTickerStore() {
        UpbitProperties.Store.StoreTicker tickerStore = properties.store().ticker();

        return Stores.windowStoreBuilder(
                Stores.persistentWindowStore(
                        tickerStore.name(),
                        tickerStore.retention(),
                        tickerStore.windowSize(),
                        tickerStore.retainDuplicates()
                ),
                Serdes.String(),
                new JsonSerde<>(UpbitTickerValue.class)
        );
    }
}
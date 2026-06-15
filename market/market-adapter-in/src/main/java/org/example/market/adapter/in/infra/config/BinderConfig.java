package org.example.market.adapter.in.infra.config;

import lombok.extern.slf4j.Slf4j;
import org.example.common.enums.KafkaHeaderKey;
import org.example.common.event.HandleableEvent;
import org.example.market.application.service.MarketEventService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;

import java.util.function.Consumer;

@Slf4j
@Configuration
public class BinderConfig {

    @Bean
    public Consumer<Message<HandleableEvent<MarketEventService>>> marketEventConsumer(MarketEventService handler) {
        return message -> message.getPayload().handle(handler, message.getHeaders().get(KafkaHeaderKey.TRANSACTION_ID.value())+"");
    }
}

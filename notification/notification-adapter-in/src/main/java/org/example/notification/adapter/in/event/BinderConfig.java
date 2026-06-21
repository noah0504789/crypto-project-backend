package org.example.notification.adapter.in.event;

import lombok.extern.slf4j.Slf4j;
import org.example.common.enums.KafkaHeaderKey;
import org.example.common.event.HandleableEvent;
import org.example.marketdetection.contract.event.PriceAlertDetectedEvent;
import org.example.notification.application.service.PriceAlertNotificationEventService;
import org.example.notification.application.service.command.PriceAlertNotificationCreateCommand;
import org.example.notification.domain.port.NotificationEventHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;

import java.util.function.Consumer;

@Slf4j
@Configuration
public class BinderConfig {

    @Bean
    public Consumer<Message<PriceAlertDetectedEvent>> priceAlertDetectedEventConsumer(PriceAlertNotificationEventService priceAlertNotificationEventService) {
        return message -> {
            PriceAlertDetectedEvent event = message.getPayload();
            String transactionId = message.getHeaders().get(KafkaHeaderKey.TRANSACTION_ID.value()) + "";

            PriceAlertNotificationCreateCommand command =
                    PriceAlertNotificationCreateCommand.builder()
                            .code(event.code())
                            .price(event.price())
                            .timestamp(event.timestamp())
                            .avgInterval(event.avgInterval())
                            .avgPrice(event.avgPrice())
                            .changeRate(event.changeRate())
                            .threshold(event.threshold())
                            .typedPayload(event.toPayload())
                            .transactionId(transactionId)
                            .build();

            priceAlertNotificationEventService.create(command);
        };
    }

    @Bean
    public Consumer<Message<HandleableEvent<NotificationEventHandler>>> notificationEventConsumer(NotificationEventHandler handler) {
        return message -> message.getPayload().handle(handler, message.getHeaders().get(KafkaHeaderKey.TRANSACTION_ID.value())+"");
    }
}
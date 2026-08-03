package org.example.notification.adapter.in.stream;

import lombok.extern.slf4j.Slf4j;
import org.example.common.enums.KafkaHeaderKey;
import org.example.common.event.HandleableEvent;
import org.example.common.inbox.exception.DuplicateInboxException;
import org.example.marketdetection.contract.event.PriceAlertDetectedEvent;
import org.example.notification.application.port.in.PriceAlertNotificationCommandUseCase;
import org.example.notification.application.service.command.PriceAlertNotificationCreateCommand;
import org.example.notification.application.port.in.NotificationEventHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;

import java.util.function.Consumer;

@Slf4j
@Configuration
public class KafkaNotificationBinder {

    @Bean
    public Consumer<Message<HandleableEvent<NotificationEventHandler>>> notificationEventConsumer(NotificationEventHandler handler) {
        return message -> message.getPayload().handle(handler, message.getHeaders().get(KafkaHeaderKey.TRANSACTION_ID.value())+"");
    }

    @Bean
    public Consumer<Message<PriceAlertDetectedEvent>> priceAlertDetectedEventConsumer(PriceAlertNotificationCommandUseCase priceAlertNotificationCommandUseCase) {
        return message -> {
            PriceAlertDetectedEvent event = message.getPayload();
            String eventId = event.extractEventId(message);
            String transactionId = message.getHeaders().get(KafkaHeaderKey.TRANSACTION_ID.value()) + "";

            PriceAlertNotificationCreateCommand command =
                    PriceAlertNotificationCreateCommand.builder()
                            .eventId(eventId)
                            .code(event.getCode())
                            .price(event.getPrice())
                            .timestamp(event.getTimestamp())
                            .avgInterval(event.getAvgInterval())
                            .avgPrice(event.getAvgPrice())
                            .changeRate(event.getChangeRate())
                            .threshold(event.getThreshold())
                            .typedPayload(event.toPayload())
                            .transactionId(transactionId)
                            .build();

            try {
                priceAlertNotificationCommandUseCase.create(command);
            } catch (DuplicateInboxException e) {
                log.info(
                        "[inbox] Duplicate price alert event skipped. eventId={}",
                        eventId
                );
            }
        };
    }
}

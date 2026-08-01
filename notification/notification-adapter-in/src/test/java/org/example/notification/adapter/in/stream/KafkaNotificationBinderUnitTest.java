package org.example.notification.adapter.in.stream;

import org.example.common.inbox.exception.DuplicateInboxEventException;
import org.example.marketdetection.contract.event.PriceAlertDetectedEvent;
import org.example.notification.application.port.in.PriceAlertNotificationCommandUseCase;
import org.example.notification.application.service.command.PriceAlertNotificationCreateCommand;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class KafkaNotificationBinderUnitTest {

    private static final String EVENT_ID = "event-1";

    @Test
    @DisplayName("가격 알림 event_id 중복은 Kafka 재시도 없이 성공으로 종료한다")
    void priceAlertDetectedEventConsumerSkipsDuplicate() {
        PriceAlertNotificationCommandUseCase useCase =
                mock(PriceAlertNotificationCommandUseCase.class);
        KafkaNotificationBinder binder = new KafkaNotificationBinder();
        Consumer<Message<PriceAlertDetectedEvent>> consumer =
                binder.priceAlertDetectedEventConsumer(useCase);
        PriceAlertDetectedEvent event = PriceAlertDetectedEvent.builder()
                .code("KRW-BTC")
                .price(110.0)
                .timestamp(1_000L)
                .avgInterval(3)
                .avgPrice(100.0)
                .changeRate(0.1)
                .threshold("PERCENT_3")
                .build();
        doThrow(new DuplicateInboxEventException(
                "notification.price-alert-detected",
                EVENT_ID,
                new RuntimeException("duplicate")
        )).when(useCase).create(any(PriceAlertNotificationCreateCommand.class));

        Message<PriceAlertDetectedEvent> message = MessageBuilder.withPayload(event)
                .setHeader("event_id", EVENT_ID.getBytes(StandardCharsets.UTF_8))
                .build();

        assertThatCode(() -> consumer.accept(message))
                .doesNotThrowAnyException();

        ArgumentCaptor<PriceAlertNotificationCreateCommand> commandCaptor =
                ArgumentCaptor.forClass(PriceAlertNotificationCreateCommand.class);
        verify(useCase).create(commandCaptor.capture());
        assertThat(commandCaptor.getValue().eventId()).isEqualTo(EVENT_ID);
    }
}

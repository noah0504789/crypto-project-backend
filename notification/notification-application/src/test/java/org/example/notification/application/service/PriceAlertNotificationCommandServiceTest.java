package org.example.notification.application.service;

import org.example.common.clock.Clock;
import org.example.common.event.TypedPayload;
import org.example.common.outbox.application.port.out.OutboxEventListPublishPort;
import org.example.notification.application.port.out.PriceAlertRecipientQueryPort;
import org.example.notification.application.service.command.PriceAlertNotificationCreateCommand;
import org.example.notification.domain.event.NotificationEventList;
import org.example.notification.domain.model.Notification;
import org.example.common.outbox.exception.TemporaryOutboxPersistenceException;
import org.example.notification.application.exception.NotificationPersistException;
import org.example.notification.domain.model.NotificationRecipient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PriceAlertNotificationCommandServiceTest {

    @Mock
    private Clock clock;

    @Mock
    private PriceAlertRecipientQueryPort priceAlertRecipientQueryPort;

    @Mock
    private OutboxEventListPublishPort outboxEventListPublishPort;

    @InjectMocks
    private PriceAlertNotificationCommandService sut;

    private static final String CODE = "KRW-BTC";
    private static final Double CHANGE_RATE = 0.05;
    private static final String THRESHOLD = "3";
    private static final String ROUTING_KEY = "KRW-BTC";
    private static final LocalDateTime DELIVERED_AT = LocalDateTime.of(2026, 1, 1, 10, 0);

    private final UUID receiverId1 = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private final UUID receiverId2 = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Nested
    @DisplayName("create")
    class CreateTest {

        @Test
        @DisplayName("가격 알림 생성 명령을 처리하면 수신자를 조회하고 Notification 이벤트를 발행한다")
        void create_should_create_notification_save_events_and_publish() {
            // given
            PriceAlertNotificationCreateCommand command = mock(PriceAlertNotificationCreateCommand.class);
            Notification notification = mock(Notification.class);
            NotificationEventList eventList = mock(NotificationEventList.class);
            TypedPayload typedPayload = mock(TypedPayload.class);
            Map<String, Object> payload = Map.of("code", CODE);

            given(clock.nowLocalDateTime()).willReturn(DELIVERED_AT);

            given(command.code()).willReturn(CODE);
            given(command.changeRate()).willReturn(CHANGE_RATE);
            given(command.threshold()).willReturn(THRESHOLD);
            given(command.routingKey()).willReturn(ROUTING_KEY);
            given(command.typedPayload()).willReturn(typedPayload);
            given(command.toPayload()).willReturn(payload);

            given(priceAlertRecipientQueryPort.findReceiverIds(CODE, THRESHOLD))
                    .willReturn(List.of(receiverId1, receiverId2));

            given(notification.getId()).willReturn("notification-1");
            given(notification.pullEventList()).willReturn(eventList);

            try (MockedStatic<Notification> mockedStatic = Mockito.mockStatic(Notification.class)) {
                mockedStatic.when(() -> Notification.createPriceAlert(CODE, CHANGE_RATE, payload, DELIVERED_AT))
                        .thenReturn(notification);

                // when
                sut.create(command);

                // then
                InOrder inOrder = inOrder(
                        clock,
                        priceAlertRecipientQueryPort,
                        notification,
                        outboxEventListPublishPort
                );

                inOrder.verify(clock).nowLocalDateTime();
                mockedStatic.verify(() -> Notification.createPriceAlert(CODE, CHANGE_RATE, payload, DELIVERED_AT));

                inOrder.verify(priceAlertRecipientQueryPort).findReceiverIds(CODE, THRESHOLD);

                ArgumentCaptor<List<NotificationRecipient>> recipientsCaptor =
                        ArgumentCaptor.forClass(List.class);

                verify(notification).save(
                        eq(typedPayload),
                        eq(ROUTING_KEY),
                        recipientsCaptor.capture()
                );

                List<NotificationRecipient> recipients = recipientsCaptor.getValue();

                assertThat(recipients).hasSize(2);
                assertThat(recipients)
                        .extracting(NotificationRecipient::getReceiverId)
                        .containsExactly(receiverId1, receiverId2);
                assertThat(recipients)
                        .extracting(NotificationRecipient::getNotificationId)
                        .containsExactly("notification-1", "notification-1");
                assertThat(recipients)
                        .extracting(NotificationRecipient::getDeliveredAt)
                        .containsExactly(DELIVERED_AT, DELIVERED_AT);

                inOrder.verify(notification).pullEventList();
                inOrder.verify(outboxEventListPublishPort).publish(eventList);
            }
        }

        @Test
        @DisplayName("수신자가 없어도 빈 recipient 목록으로 Notification 이벤트를 발행한다")
        void create_should_publish_notification_event_even_if_receivers_are_empty() {
            // given
            PriceAlertNotificationCreateCommand command = mock(PriceAlertNotificationCreateCommand.class);
            Notification notification = mock(Notification.class);
            NotificationEventList eventList = mock(NotificationEventList.class);
            TypedPayload typedPayload = mock(TypedPayload.class);
            Map<String, Object> payload = Map.of("code", CODE);

            given(clock.nowLocalDateTime()).willReturn(DELIVERED_AT);

            given(command.code()).willReturn(CODE);
            given(command.changeRate()).willReturn(CHANGE_RATE);
            given(command.threshold()).willReturn(THRESHOLD);
            given(command.routingKey()).willReturn(ROUTING_KEY);
            given(command.typedPayload()).willReturn(typedPayload);
            given(command.toPayload()).willReturn(payload);

            given(priceAlertRecipientQueryPort.findReceiverIds(CODE, THRESHOLD))
                    .willReturn(List.of());

            given(notification.pullEventList()).willReturn(eventList);

            try (MockedStatic<Notification> mockedStatic = Mockito.mockStatic(Notification.class)) {
                mockedStatic.when(() -> Notification.createPriceAlert(CODE, CHANGE_RATE, payload, DELIVERED_AT))
                        .thenReturn(notification);

                // when
                sut.create(command);

                // then
                ArgumentCaptor<List<NotificationRecipient>> recipientsCaptor =
                        ArgumentCaptor.forClass(List.class);

                verify(notification).save(
                        eq(typedPayload),
                        eq(ROUTING_KEY),
                        recipientsCaptor.capture()
                );

                assertThat(recipientsCaptor.getValue()).isEmpty();

                verify(notification).pullEventList();
                verify(outboxEventListPublishPort).publish(eventList);
            }
        }

        @Test
        @DisplayName("수신자 조회가 실패하면 Notification 이벤트를 발행하지 않고 예외를 전파한다")
        void create_should_not_publish_when_find_receivers_fails() {
            // given
            PriceAlertNotificationCreateCommand command = mock(PriceAlertNotificationCreateCommand.class);
            Notification notification = mock(Notification.class);
            Map<String, Object> payload = Map.of("code", CODE);

            RuntimeException exception = new RuntimeException("receiver query failed");

            given(clock.nowLocalDateTime()).willReturn(DELIVERED_AT);

            given(command.code()).willReturn(CODE);
            given(command.changeRate()).willReturn(CHANGE_RATE);
            given(command.threshold()).willReturn(THRESHOLD);
            given(command.toPayload()).willReturn(payload);

            given(priceAlertRecipientQueryPort.findReceiverIds(CODE, THRESHOLD))
                    .willThrow(exception);

            try (MockedStatic<Notification> mockedStatic = Mockito.mockStatic(Notification.class)) {
                mockedStatic.when(() -> Notification.createPriceAlert(CODE, CHANGE_RATE, payload, DELIVERED_AT))
                        .thenReturn(notification);

                // when & then
                assertThatThrownBy(() -> sut.create(command))
                        .isSameAs(exception);

                verify(priceAlertRecipientQueryPort).findReceiverIds(CODE, THRESHOLD);
                verify(notification, never()).save(any(), anyString(), anyList());
                verify(notification, never()).pullEventList();
                verify(outboxEventListPublishPort, never()).publish(any());
            }
        }

        @Test
        @DisplayName("Outbox 일시 장애가 발생하면 TemporaryOutboxPersistenceException을 그대로 전파한다")
        void create_should_rethrow_temporary_outbox_exception() {
            // given
            PriceAlertNotificationCreateCommand command = mock(PriceAlertNotificationCreateCommand.class);
            Notification notification = mock(Notification.class);
            NotificationEventList eventList = mock(NotificationEventList.class);
            TypedPayload typedPayload = mock(TypedPayload.class);
            Map<String, Object> payload = Map.of("code", CODE);

            TemporaryOutboxPersistenceException exception =
                    new TemporaryOutboxPersistenceException(
                            "temporary outbox failure",
                            new RuntimeException("temporary")
                    );

            given(clock.nowLocalDateTime()).willReturn(DELIVERED_AT);

            given(command.code()).willReturn(CODE);
            given(command.changeRate()).willReturn(CHANGE_RATE);
            given(command.threshold()).willReturn(THRESHOLD);
            given(command.routingKey()).willReturn(ROUTING_KEY);
            given(command.typedPayload()).willReturn(typedPayload);
            given(command.toPayload()).willReturn(payload);

            given(priceAlertRecipientQueryPort.findReceiverIds(CODE, THRESHOLD))
                    .willReturn(List.of(receiverId1));

            given(notification.getId()).willReturn("notification-1");
            given(notification.pullEventList()).willReturn(eventList);

            doThrow(exception)
                    .when(outboxEventListPublishPort)
                    .publish(eventList);

            try (MockedStatic<Notification> mockedStatic = Mockito.mockStatic(Notification.class)) {
                mockedStatic.when(() -> Notification.createPriceAlert(CODE, CHANGE_RATE, payload, DELIVERED_AT))
                        .thenReturn(notification);

                // when & then
                assertThatThrownBy(() -> sut.create(command))
                        .isSameAs(exception);

                verify(notification).save(eq(typedPayload), eq(ROUTING_KEY), anyList());
                verify(notification).pullEventList();
                verify(outboxEventListPublishPort).publish(eventList);
            }
        }

        @Test
        @DisplayName("Outbox 일반 장애가 발생하면 NotificationPersistException으로 감싸서 전파한다")
        void create_should_wrap_unexpected_outbox_exception() {
            // given
            PriceAlertNotificationCreateCommand command = mock(PriceAlertNotificationCreateCommand.class);
            Notification notification = mock(Notification.class);
            NotificationEventList eventList = mock(NotificationEventList.class);
            TypedPayload typedPayload = mock(TypedPayload.class);
            Map<String, Object> payload = Map.of("code", CODE);

            RuntimeException exception = new RuntimeException("outbox publish failed");

            given(clock.nowLocalDateTime()).willReturn(DELIVERED_AT);

            given(command.code()).willReturn(CODE);
            given(command.changeRate()).willReturn(CHANGE_RATE);
            given(command.threshold()).willReturn(THRESHOLD);
            given(command.routingKey()).willReturn(ROUTING_KEY);
            given(command.typedPayload()).willReturn(typedPayload);
            given(command.toPayload()).willReturn(payload);

            given(priceAlertRecipientQueryPort.findReceiverIds(CODE, THRESHOLD))
                    .willReturn(List.of(receiverId1));

            given(notification.getId()).willReturn("notification-1");
            given(notification.pullEventList()).willReturn(eventList);

            doThrow(exception)
                    .when(outboxEventListPublishPort)
                    .publish(eventList);

            try (MockedStatic<Notification> mockedStatic = Mockito.mockStatic(Notification.class)) {
                mockedStatic.when(() -> Notification.createPriceAlert(CODE, CHANGE_RATE, payload, DELIVERED_AT))
                        .thenReturn(notification);

                // when & then
                assertThatThrownBy(() -> sut.create(command))
                        .isInstanceOf(NotificationPersistException.class)
                        .hasMessageContaining("failed to publish notification event")
                        .hasCause(exception);

                verify(notification).save(eq(typedPayload), eq(ROUTING_KEY), anyList());
                verify(notification).pullEventList();
                verify(outboxEventListPublishPort).publish(eventList);
            }
        }

        @Test
        @DisplayName("Notification save 이벤트 등록이 실패하면 Outbox 발행을 수행하지 않고 예외를 전파한다")
        void create_should_not_publish_when_notification_save_fails() {
            // given
            PriceAlertNotificationCreateCommand command = mock(PriceAlertNotificationCreateCommand.class);
            Notification notification = mock(Notification.class);
            TypedPayload typedPayload = mock(TypedPayload.class);
            Map<String, Object> payload = Map.of("code", CODE);

            RuntimeException exception = new RuntimeException("notification save failed");

            given(clock.nowLocalDateTime()).willReturn(DELIVERED_AT);

            given(command.code()).willReturn(CODE);
            given(command.changeRate()).willReturn(CHANGE_RATE);
            given(command.threshold()).willReturn(THRESHOLD);
            given(command.routingKey()).willReturn(ROUTING_KEY);
            given(command.typedPayload()).willReturn(typedPayload);
            given(command.toPayload()).willReturn(payload);

            given(priceAlertRecipientQueryPort.findReceiverIds(CODE, THRESHOLD))
                    .willReturn(List.of(receiverId1));

            given(notification.getId()).willReturn("notification-1");

            doThrow(exception)
                    .when(notification)
                    .save(eq(typedPayload), eq(ROUTING_KEY), anyList());

            try (MockedStatic<Notification> mockedStatic = Mockito.mockStatic(Notification.class)) {
                mockedStatic.when(() -> Notification.createPriceAlert(CODE, CHANGE_RATE, payload, DELIVERED_AT))
                        .thenReturn(notification);

                // when & then
                assertThatThrownBy(() -> sut.create(command))
                        .isSameAs(exception);

                verify(notification).save(eq(typedPayload), eq(ROUTING_KEY), anyList());
                verify(notification, never()).pullEventList();
                verify(outboxEventListPublishPort, never()).publish(any());
            }
        }
    }
}
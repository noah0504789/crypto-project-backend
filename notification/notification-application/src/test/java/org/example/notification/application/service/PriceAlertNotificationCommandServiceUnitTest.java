package org.example.notification.application.service;

import org.example.common.time.Clock;
import org.example.common.event.TypedPayload;
import org.example.common.inbox.application.service.InboxService;
import org.example.common.inbox.exception.DuplicateInboxException;
import org.example.common.outbox.application.port.out.OutboxEventListPublishPort;
import org.example.common.outbox.exception.TemporaryOutboxPersistenceException;
import org.example.notification.application.event.NotificationEventList;
import org.example.notification.application.event.NotificationSaveEvent;
import org.example.notification.application.event.payload.NotificationPayload;
import org.example.notification.application.event.payload.NotificationRecipientPayload;
import org.example.notification.application.exception.NotificationPersistException;
import org.example.notification.application.port.out.PriceAlertNotificationIdGeneratorPort;
import org.example.notification.application.port.out.PriceAlertRecipientQueryPort;
import org.example.notification.application.service.command.PriceAlertNotificationCreateCommand;
import org.example.notification.application.service.properties.PriceAlertNotificationProperties;
import org.example.notification.contract.event.WebNotificationBroadcastEvent;
import org.example.notification.contract.event.WebNotificationPayload;
import org.example.notification.domain.model.Notification;
import org.example.notification.domain.model.NotificationType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PriceAlertNotificationCommandServiceUnitTest {

    @Mock
    private Clock clock;

    @Mock
    private PriceAlertNotificationIdGeneratorPort idGeneratorPort;

    @Mock
    private PriceAlertRecipientQueryPort priceAlertRecipientQueryPort;

    @Mock
    private OutboxEventListPublishPort outboxEventListPublishPort;

    @Mock
    private InboxService inboxService;

    private PriceAlertNotificationCommandService sut;

    private static final String NOTIFICATION_ID = "notification-1";
    private static final String EVENT_ID = "event-1";
    private static final String CODE = "KRW-BTC";
    private static final Double CHANGE_RATE = 0.05;
    private static final Double PRICE = 105D;
    private static final Double AVG_PRICE = 100D;
    private static final Integer AVG_INTERVAL = 5;
    private static final String THRESHOLD = "3";
    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 1, 1, 10, 0);
    private static final long CREATED_AT_MS = 1767229200000L;
    private static final Duration MAX_EVENT_AGE = Duration.ofSeconds(10);

    private static final Map<String, Object> PAYLOAD = Map.of(
            "code", CODE,
            "changeRate", CHANGE_RATE
    );

    private final UUID receiverId1 = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private final UUID receiverId2 = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @BeforeEach
    void setUp() {
        sut = new PriceAlertNotificationCommandService(
                clock,
                idGeneratorPort,
                priceAlertRecipientQueryPort,
                outboxEventListPublishPort,
                inboxService,
                new PriceAlertNotificationProperties(MAX_EVENT_AGE)
        );

    }

    @Nested
    @DisplayName("create")
    class CreateTest {

        @Test
        @DisplayName("이미 처리한 event_id면 예외를 전파하고 알림과 Outbox 이벤트를 만들지 않는다")
        void create_should_throw_duplicate_event() {
            PriceAlertNotificationCreateCommand command = mock(PriceAlertNotificationCreateCommand.class);
            given(command.eventId()).willReturn(EVENT_ID);
            given(command.consumerName()).willReturn(PriceAlertNotificationCreateCommand.CONSUMER_NAME);
            doThrow(new DuplicateInboxException(
                    PriceAlertNotificationCreateCommand.CONSUMER_NAME,
                    EVENT_ID,
                    new RuntimeException("duplicate")
            )).when(inboxService).save(
                    PriceAlertNotificationCreateCommand.CONSUMER_NAME,
                    EVENT_ID
            );

            assertThatThrownBy(() -> sut.create(command))
                    .isInstanceOf(DuplicateInboxException.class);

            verify(idGeneratorPort, never()).generate();
            verify(priceAlertRecipientQueryPort, never()).findReceiverIds(anyString(), anyString());
            verify(outboxEventListPublishPort, never()).publish(any());
        }

        @Test
        @DisplayName("허용시간이 지난 가격 알림 이벤트는 inbox에 기록하고 알림을 생성하지 않는다")
        void create_should_skip_stale_event_after_saving_inbox() {
            PriceAlertNotificationCreateCommand command = PriceAlertNotificationCreateCommand.builder()
                    .eventId(EVENT_ID)
                    .occurredAtMs(CREATED_AT_MS)
                    .build();
            long nowMs = CREATED_AT_MS + MAX_EVENT_AGE.toMillis() + 1;

            given(clock.nowMs()).willReturn(nowMs);

            sut.create(command);

            verify(inboxService).save(PriceAlertNotificationCreateCommand.CONSUMER_NAME, EVENT_ID);
            verify(idGeneratorPort, never()).generate();
            verify(clock, never()).nowLocalDateTime();
            verify(priceAlertRecipientQueryPort, never()).findReceiverIds(anyString(), anyString());
            verify(outboxEventListPublishPort, never()).publish(any());
        }

        @Test
        @DisplayName("가격 알림 생성 명령을 처리하면 수신자를 조회하고 Notification 이벤트를 발행한다")
        void create_should_create_notification_events_and_publish() {
            // given
            PriceAlertNotificationCreateCommand command = mockCommand();
            givenNotificationCommandFields(command);
            TypedPayload typedPayload = givenWebNotificationCommandFields(command);

            Notification notification = mockNotification();
            givenWebNotificationFields(notification);

            NotificationPayload notificationPayload = mock(NotificationPayload.class);
            NotificationSaveEvent saveEvent = mock(NotificationSaveEvent.class);
            WebNotificationBroadcastEvent webEvent1 = mock(WebNotificationBroadcastEvent.class);
            WebNotificationBroadcastEvent webEvent2 = mock(WebNotificationBroadcastEvent.class);
            NotificationEventList eventList = mock(NotificationEventList.class);

            givenCommon();

            given(priceAlertRecipientQueryPort.findReceiverIds(CODE, THRESHOLD))
                    .willReturn(List.of(receiverId1, receiverId2));

            try (
                    MockedStatic<Notification> notificationStatic = mockStatic(Notification.class);
                    MockedStatic<NotificationPayload> notificationPayloadStatic = mockStatic(NotificationPayload.class);
                    MockedStatic<NotificationSaveEvent> saveEventStatic = mockStatic(NotificationSaveEvent.class);
                    MockedStatic<WebNotificationBroadcastEvent> webEventStatic = mockStatic(WebNotificationBroadcastEvent.class);
                    MockedStatic<NotificationEventList> eventListStatic = mockStatic(NotificationEventList.class)
            ) {
                notificationStatic.when(() -> Notification.createPriceAlert(
                        NOTIFICATION_ID,
                        CODE,
                        PRICE,
                        AVG_PRICE,
                        AVG_INTERVAL,
                        CHANGE_RATE,
                        PAYLOAD,
                        CREATED_AT
                )).thenReturn(notification);

                notificationPayloadStatic.when(() -> NotificationPayload.from(notification))
                        .thenReturn(notificationPayload);

                saveEventStatic.when(() -> NotificationSaveEvent.from(eq(notificationPayload), any()))
                        .thenReturn(saveEvent);

                webEventStatic.when(() -> WebNotificationBroadcastEvent.of(
                        any(WebNotificationPayload.class),
                        eq(NOTIFICATION_ID),
                        eq(receiverId1.toString())
                )).thenReturn(webEvent1);
                webEventStatic.when(() -> WebNotificationBroadcastEvent.of(
                        any(WebNotificationPayload.class),
                        eq(NOTIFICATION_ID),
                        eq(receiverId2.toString())
                )).thenReturn(webEvent2);

                eventListStatic.when(() -> NotificationEventList.of(saveEvent, webEvent1, webEvent2))
                        .thenReturn(eventList);

                // when
                sut.create(command);

                // then
                verify(idGeneratorPort).generate();
                verify(clock).nowLocalDateTime();

                notificationStatic.verify(() -> Notification.createPriceAlert(
                        NOTIFICATION_ID,
                        CODE,
                        PRICE,
                        AVG_PRICE,
                        AVG_INTERVAL,
                        CHANGE_RATE,
                        PAYLOAD,
                        CREATED_AT
                ));

                verify(priceAlertRecipientQueryPort).findReceiverIds(CODE, THRESHOLD);

                ArgumentCaptor<List<NotificationRecipientPayload>> recipientsCaptor =
                        ArgumentCaptor.forClass(List.class);

                saveEventStatic.verify(() -> NotificationSaveEvent.from(
                        eq(notificationPayload),
                        recipientsCaptor.capture()
                ));

                List<NotificationRecipientPayload> recipients = recipientsCaptor.getValue();

                assertThat(recipients).hasSize(2);
                assertThat(recipients)
                        .extracting(NotificationRecipientPayload::notificationId)
                        .containsExactly(NOTIFICATION_ID, NOTIFICATION_ID);
                assertThat(recipients)
                        .extracting(NotificationRecipientPayload::receiverId)
                        .containsExactly(receiverId1, receiverId2);
                assertThat(recipients)
                        .extracting(NotificationRecipientPayload::deliveredAt)
                        .containsExactly(CREATED_AT, CREATED_AT);

                ArgumentCaptor<WebNotificationPayload> webPayloadCaptor =
                        ArgumentCaptor.forClass(WebNotificationPayload.class);

                webEventStatic.verify(() -> WebNotificationBroadcastEvent.of(
                        webPayloadCaptor.capture(),
                        eq(NOTIFICATION_ID),
                        eq(receiverId1.toString())
                ));
                webEventStatic.verify(() -> WebNotificationBroadcastEvent.of(
                        webPayloadCaptor.capture(),
                        eq(NOTIFICATION_ID),
                        eq(receiverId2.toString())
                ));

                WebNotificationPayload webPayload = webPayloadCaptor.getAllValues().get(0);

                assertThat(webPayload.type()).isEqualTo(NotificationType.PRICE_ALERT.name());
                assertThat(webPayload.title()).isEqualTo("가격 알림");
                assertThat(webPayload.body()).isEqualTo("KRW-BTC이 5.0% 이상 상승했습니다.");
                assertThat(webPayload.createdAtMs()).isEqualTo(CREATED_AT_MS);
                assertThat(webPayload.link()).isNull();
                assertThat(webPayload.typedPayload()).isSameAs(typedPayload);

                assertThat(webPayloadCaptor.getAllValues()).containsOnly(webPayload);

                eventListStatic.verify(() -> NotificationEventList.of(saveEvent, webEvent1, webEvent2));

                verify(outboxEventListPublishPort).publish(eventList);
            }
        }

        @Test
        @DisplayName("수신자가 없으면 알림 이벤트를 발행하지 않는다")
        void create_should_skip_notification_when_receivers_are_empty() {
            // given
            PriceAlertNotificationCreateCommand command = mockCommand();

            given(priceAlertRecipientQueryPort.findReceiverIds(CODE, THRESHOLD))
                    .willReturn(List.of());

            try (
                    MockedStatic<Notification> notificationStatic = mockStatic(Notification.class);
                    MockedStatic<NotificationSaveEvent> saveEventStatic = mockStatic(NotificationSaveEvent.class);
                    MockedStatic<WebNotificationBroadcastEvent> webEventStatic = mockStatic(WebNotificationBroadcastEvent.class);
                    MockedStatic<NotificationEventList> eventListStatic = mockStatic(NotificationEventList.class)
            ) {
                // when
                sut.create(command);

                // then
                verify(priceAlertRecipientQueryPort).findReceiverIds(CODE, THRESHOLD);
                verify(idGeneratorPort, never()).generate();
                verify(clock, never()).nowLocalDateTime();
                notificationStatic.verifyNoInteractions();
                saveEventStatic.verifyNoInteractions();
                webEventStatic.verifyNoInteractions();
                eventListStatic.verifyNoInteractions();
                verify(outboxEventListPublishPort, never()).publish(any());
            }
        }

        @Test
        @DisplayName("수신자 조회가 실패하면 Notification 이벤트를 발행하지 않고 예외를 전파한다")
        void create_should_not_publish_when_find_receivers_fails() {
            // given
            PriceAlertNotificationCreateCommand command = mockCommand();

            RuntimeException exception = new RuntimeException("receiver query failed");

            given(priceAlertRecipientQueryPort.findReceiverIds(CODE, THRESHOLD))
                    .willThrow(exception);

            try (
                    MockedStatic<NotificationPayload> notificationPayloadStatic = mockStatic(NotificationPayload.class);
                    MockedStatic<NotificationSaveEvent> saveEventStatic = mockStatic(NotificationSaveEvent.class);
                    MockedStatic<WebNotificationBroadcastEvent> webEventStatic = mockStatic(WebNotificationBroadcastEvent.class);
                    MockedStatic<NotificationEventList> eventListStatic = mockStatic(NotificationEventList.class)
            ) {
                // when & then
                assertThatThrownBy(() -> sut.create(command))
                        .isSameAs(exception);

                verify(priceAlertRecipientQueryPort).findReceiverIds(CODE, THRESHOLD);
                verify(outboxEventListPublishPort, never()).publish(any());

                notificationPayloadStatic.verifyNoInteractions();
                saveEventStatic.verifyNoInteractions();
                webEventStatic.verifyNoInteractions();
                eventListStatic.verifyNoInteractions();
            }
        }

        @Test
        @DisplayName("Outbox 일시 장애가 발생하면 TemporaryOutboxPersistenceException을 그대로 전파한다")
        void create_should_rethrow_temporary_outbox_exception() {
            // given
            PriceAlertNotificationCreateCommand command = mockCommand();
            givenNotificationCommandFields(command);
            givenWebNotificationCommandFields(command);

            Notification notification = mockNotification();
            givenWebNotificationFields(notification);

            NotificationPayload notificationPayload = mock(NotificationPayload.class);
            NotificationSaveEvent saveEvent = mock(NotificationSaveEvent.class);
            WebNotificationBroadcastEvent webEvent = mock(WebNotificationBroadcastEvent.class);
            NotificationEventList eventList = mock(NotificationEventList.class);

            TemporaryOutboxPersistenceException exception =
                    new TemporaryOutboxPersistenceException(
                            "temporary outbox failure",
                            new RuntimeException("temporary")
                    );

            givenCommon();

            given(priceAlertRecipientQueryPort.findReceiverIds(CODE, THRESHOLD))
                    .willReturn(List.of(receiverId1));

            doThrow(exception)
                    .when(outboxEventListPublishPort)
                    .publish(eventList);

            try (
                    MockedStatic<Notification> notificationStatic = mockStatic(Notification.class);
                    MockedStatic<NotificationPayload> notificationPayloadStatic = mockStatic(NotificationPayload.class);
                    MockedStatic<NotificationSaveEvent> saveEventStatic = mockStatic(NotificationSaveEvent.class);
                    MockedStatic<WebNotificationBroadcastEvent> webEventStatic = mockStatic(WebNotificationBroadcastEvent.class);
                    MockedStatic<NotificationEventList> eventListStatic = mockStatic(NotificationEventList.class)
            ) {
                notificationStatic.when(() -> Notification.createPriceAlert(
                        NOTIFICATION_ID,
                        CODE,
                        PRICE,
                        AVG_PRICE,
                        AVG_INTERVAL,
                        CHANGE_RATE,
                        PAYLOAD,
                        CREATED_AT
                )).thenReturn(notification);

                notificationPayloadStatic.when(() -> NotificationPayload.from(notification))
                        .thenReturn(notificationPayload);

                saveEventStatic.when(() -> NotificationSaveEvent.from(eq(notificationPayload), any()))
                        .thenReturn(saveEvent);

                webEventStatic.when(() -> WebNotificationBroadcastEvent.of(
                        any(WebNotificationPayload.class),
                        eq(NOTIFICATION_ID),
                        eq(receiverId1.toString())
                )).thenReturn(webEvent);

                eventListStatic.when(() -> NotificationEventList.of(saveEvent, webEvent))
                        .thenReturn(eventList);

                // when & then
                assertThatThrownBy(() -> sut.create(command))
                        .isSameAs(exception);

                verify(outboxEventListPublishPort).publish(eventList);
            }
        }

        @Test
        @DisplayName("Outbox 일반 장애가 발생하면 NotificationPersistException으로 감싸서 전파한다")
        void create_should_wrap_unexpected_outbox_exception() {
            // given
            PriceAlertNotificationCreateCommand command = mockCommand();
            givenNotificationCommandFields(command);
            givenWebNotificationCommandFields(command);

            Notification notification = mockNotification();
            givenWebNotificationFields(notification);

            NotificationPayload notificationPayload = mock(NotificationPayload.class);
            NotificationSaveEvent saveEvent = mock(NotificationSaveEvent.class);
            WebNotificationBroadcastEvent webEvent = mock(WebNotificationBroadcastEvent.class);
            NotificationEventList eventList = mock(NotificationEventList.class);

            RuntimeException exception = new RuntimeException("outbox publish failed");

            givenCommon();

            given(priceAlertRecipientQueryPort.findReceiverIds(CODE, THRESHOLD))
                    .willReturn(List.of(receiverId1));

            doThrow(exception)
                    .when(outboxEventListPublishPort)
                    .publish(eventList);

            try (
                    MockedStatic<Notification> notificationStatic = mockStatic(Notification.class);
                    MockedStatic<NotificationPayload> notificationPayloadStatic = mockStatic(NotificationPayload.class);
                    MockedStatic<NotificationSaveEvent> saveEventStatic = mockStatic(NotificationSaveEvent.class);
                    MockedStatic<WebNotificationBroadcastEvent> webEventStatic = mockStatic(WebNotificationBroadcastEvent.class);
                    MockedStatic<NotificationEventList> eventListStatic = mockStatic(NotificationEventList.class)
            ) {
                notificationStatic.when(() -> Notification.createPriceAlert(
                        NOTIFICATION_ID,
                        CODE,
                        PRICE,
                        AVG_PRICE,
                        AVG_INTERVAL,
                        CHANGE_RATE,
                        PAYLOAD,
                        CREATED_AT
                )).thenReturn(notification);

                notificationPayloadStatic.when(() -> NotificationPayload.from(notification))
                        .thenReturn(notificationPayload);

                saveEventStatic.when(() -> NotificationSaveEvent.from(eq(notificationPayload), any()))
                        .thenReturn(saveEvent);

                webEventStatic.when(() -> WebNotificationBroadcastEvent.of(
                        any(WebNotificationPayload.class),
                        eq(NOTIFICATION_ID),
                        eq(receiverId1.toString())
                )).thenReturn(webEvent);

                eventListStatic.when(() -> NotificationEventList.of(saveEvent, webEvent))
                        .thenReturn(eventList);

                // when & then
                assertThatThrownBy(() -> sut.create(command))
                        .isInstanceOf(NotificationPersistException.class)
                        .hasMessageContaining("failed to publish notification events")
                        .hasCause(exception);

                verify(outboxEventListPublishPort).publish(eventList);
            }
        }

        @Test
        @DisplayName("NotificationSaveEvent 생성이 실패하면 Outbox 발행을 수행하지 않고 예외를 전파한다")
        void create_should_not_publish_when_save_event_creation_fails() {
            // given
            PriceAlertNotificationCreateCommand command = mockCommand();
            givenNotificationCommandFields(command);
            givenWebNotificationCommandFields(command);

            Notification notification = mockNotification();

            NotificationPayload notificationPayload = mock(NotificationPayload.class);

            RuntimeException exception =
                    new RuntimeException("notification save event creation failed");

            givenCommon();

            given(priceAlertRecipientQueryPort.findReceiverIds(CODE, THRESHOLD))
                    .willReturn(List.of(receiverId1));

            try (
                    MockedStatic<Notification> notificationStatic = mockStatic(Notification.class);
                    MockedStatic<NotificationPayload> notificationPayloadStatic = mockStatic(NotificationPayload.class);
                    MockedStatic<NotificationSaveEvent> saveEventStatic = mockStatic(NotificationSaveEvent.class);
                    MockedStatic<WebNotificationBroadcastEvent> webEventStatic = mockStatic(WebNotificationBroadcastEvent.class);
                    MockedStatic<NotificationEventList> eventListStatic = mockStatic(NotificationEventList.class)
            ) {
                notificationStatic.when(() -> Notification.createPriceAlert(
                        NOTIFICATION_ID,
                        CODE,
                        PRICE,
                        AVG_PRICE,
                        AVG_INTERVAL,
                        CHANGE_RATE,
                        PAYLOAD,
                        CREATED_AT
                )).thenReturn(notification);

                notificationPayloadStatic.when(() -> NotificationPayload.from(notification))
                        .thenReturn(notificationPayload);

                saveEventStatic.when(() -> NotificationSaveEvent.from(eq(notificationPayload), any()))
                        .thenThrow(exception);

                // when & then
                assertThatThrownBy(() -> sut.create(command))
                        .isSameAs(exception);

                verify(outboxEventListPublishPort, never()).publish(any());

                webEventStatic.verifyNoInteractions();
                eventListStatic.verifyNoInteractions();
            }
        }
    }

    private PriceAlertNotificationCreateCommand mockCommand() {
        PriceAlertNotificationCreateCommand command =
                mock(PriceAlertNotificationCreateCommand.class);

        given(command.code()).willReturn(CODE);
        given(command.eventId()).willReturn(EVENT_ID);
        given(command.consumerName()).willReturn(PriceAlertNotificationCreateCommand.CONSUMER_NAME);
        given(command.threshold()).willReturn(THRESHOLD);

        return command;
    }

    private void givenNotificationCommandFields(PriceAlertNotificationCreateCommand command) {
        given(command.price()).willReturn(PRICE);
        given(command.avgPrice()).willReturn(AVG_PRICE);
        given(command.avgInterval()).willReturn(AVG_INTERVAL);
        given(command.changeRate()).willReturn(CHANGE_RATE);
        given(command.toPayload()).willReturn(PAYLOAD);
    }

    private TypedPayload givenWebNotificationCommandFields(PriceAlertNotificationCreateCommand command) {
        TypedPayload typedPayload = mock(TypedPayload.class);

        given(command.typedPayload()).willReturn(typedPayload);

        return typedPayload;
    }

    private void givenCommon() {
        given(idGeneratorPort.generate()).willReturn(NOTIFICATION_ID);
        given(clock.nowLocalDateTime()).willReturn(CREATED_AT);
    }

    private Notification mockNotification() {
        Notification notification = mock(Notification.class);

        given(notification.getId()).willReturn(NOTIFICATION_ID);

        return notification;
    }

    private void givenWebNotificationFields(Notification notification) {
        given(notification.getType()).willReturn(NotificationType.PRICE_ALERT);
        given(notification.getTitle()).willReturn("가격 알림");
        given(notification.getMessage()).willReturn("KRW-BTC이 5.0% 이상 상승했습니다.");
        given(notification.getCreatedAtMs()).willReturn(CREATED_AT_MS);
    }
}

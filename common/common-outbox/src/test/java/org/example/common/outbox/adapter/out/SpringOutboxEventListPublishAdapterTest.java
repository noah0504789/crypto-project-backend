package org.example.common.outbox.adapter.out;

import org.example.common.event.EventUtils;
import org.example.common.outbox.domain.event.AbstractOutboxEvent;
import org.example.common.outbox.domain.event.AbstractOutboxEventList;
import org.example.common.outbox.exception.OutboxPersistenceException;
import org.example.common.outbox.exception.TemporaryOutboxPersistenceException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.dao.TransientDataAccessResourceException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class SpringOutboxEventListPublishAdapterTest {

    private final SpringOutboxEventListPublishAdapter sut = new SpringOutboxEventListPublishAdapter();

    @Nested
    @DisplayName("publish")
    class PublishTest {

        @Test
        @DisplayName("eventList가 null이면 아무 작업도 하지 않는다")
        void publishSkippedWhenEventListIsNull() {
            try (MockedStatic<EventUtils> eventUtils = mockStatic(EventUtils.class)) {
                // when
                sut.publish(null);

                // then
                eventUtils.verifyNoInteractions();
            }
        }

        @Test
        @DisplayName("eventList가 비어 있으면 txId를 할당하지 않고 이벤트 발행도 하지 않는다")
        void publishSkippedWhenEventListIsEmpty() {
            // given
            AbstractOutboxEventList eventList = mock(AbstractOutboxEventList.class);

            when(eventList.getEventList())
                    .thenReturn(List.of());

            try (MockedStatic<EventUtils> eventUtils = mockStatic(EventUtils.class)) {
                // when
                sut.publish(eventList);

                // then
                verify(eventList).getEventList();

                eventUtils.verifyNoInteractions();
            }
        }

        @Test
        @DisplayName("eventList가 있으면 txId를 할당하고 EventUtils로 발행한다")
        void publishSuccess() {
            // given
            AbstractOutboxEventList eventList = mock(AbstractOutboxEventList.class);
            AbstractOutboxEvent event = mock(AbstractOutboxEvent.class);

            when(eventList.getEventList())
                    .thenReturn(List.of(event));

            try (MockedStatic<EventUtils> eventUtils = mockStatic(EventUtils.class)) {
                // when
                sut.publish(eventList);

                // then
                verify(eventList).getEventList();

                eventUtils.verify(() -> EventUtils.raise(eventList));
            }
        }

        @Test
        @DisplayName("EventUtils 발행 중 OutboxPersistenceException이 발생하면 그대로 전파한다")
        void publishThrowsOutboxPersistenceExceptionAsIs() {
            // given
            AbstractOutboxEventList eventList = mock(AbstractOutboxEventList.class);
            AbstractOutboxEvent event = mock(AbstractOutboxEvent.class);

            OutboxPersistenceException exception =
                    new OutboxPersistenceException("outbox publish failed");

            when(eventList.getEventList())
                    .thenReturn(List.of(event));

            try (MockedStatic<EventUtils> eventUtils = mockStatic(EventUtils.class)) {
                eventUtils
                        .when(() -> EventUtils.raise(eventList))
                        .thenThrow(exception);

                // when & then
                assertThatThrownBy(() -> sut.publish(eventList))
                        .isSameAs(exception);

                eventUtils.verify(() -> EventUtils.raise(eventList));
            }
        }

        @Test
        @DisplayName("EventUtils 발행 중 일시적 DataAccessException이 발생하면 TemporaryOutboxPersistenceException으로 변환한다")
        void publishThrowsTemporaryOutboxPersistenceExceptionWhenTemporaryFailureOccurs() {
            // given
            AbstractOutboxEventList eventList = mock(AbstractOutboxEventList.class);
            AbstractOutboxEvent event = mock(AbstractOutboxEvent.class);

            RuntimeException exception =
                    new TransientDataAccessResourceException("temporary outbox failure");

            when(eventList.getEventList())
                    .thenReturn(List.of(event));
            when(eventList.getTxId())
                    .thenReturn("tx-1");

            try (MockedStatic<EventUtils> eventUtils = mockStatic(EventUtils.class)) {
                eventUtils
                        .when(() -> EventUtils.raise(eventList))
                        .thenThrow(exception);

                // when & then
                assertThatThrownBy(() -> sut.publish(eventList))
                        .isInstanceOf(TemporaryOutboxPersistenceException.class)
                        .hasMessageContaining("failed to publish outbox event list")
                        .hasMessageContaining("txId=tx-1")
                        .hasCause(exception);

                verify(eventList).getTxId();

                eventUtils.verify(() -> EventUtils.raise(eventList));
            }
        }

        @Test
        @DisplayName("EventUtils 발행 중 일반 예외가 발생하면 OutboxPersistenceException으로 변환한다")
        void publishThrowsOutboxPersistenceExceptionWhenUnexpectedFailureOccurs() {
            // given
            AbstractOutboxEventList eventList = mock(AbstractOutboxEventList.class);
            AbstractOutboxEvent event = mock(AbstractOutboxEvent.class);

            RuntimeException exception = new RuntimeException("unexpected failure");

            when(eventList.getEventList())
                    .thenReturn(List.of(event));
            when(eventList.getTxId())
                    .thenReturn("tx-1");

            try (MockedStatic<EventUtils> eventUtils = mockStatic(EventUtils.class)) {
                eventUtils
                        .when(() -> EventUtils.raise(eventList))
                        .thenThrow(exception);

                // when & then
                assertThatThrownBy(() -> sut.publish(eventList))
                        .isInstanceOf(OutboxPersistenceException.class)
                        .isNotInstanceOf(TemporaryOutboxPersistenceException.class)
                        .hasMessageContaining("failed to publish outbox event list")
                        .hasMessageContaining("txId=tx-1")
                        .hasCause(exception);

                verify(eventList).getTxId();

                eventUtils.verify(() -> EventUtils.raise(eventList));
            }
        }
    }
}
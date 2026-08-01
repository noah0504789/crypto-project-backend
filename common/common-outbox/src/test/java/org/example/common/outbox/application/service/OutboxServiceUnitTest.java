package org.example.common.outbox.application.service;

import org.example.common.outbox.application.port.out.EventPublisherPort;
import org.example.common.outbox.adapter.out.OutboxRepository;
import org.example.common.outbox.properties.OutboxPollerProperties;
import org.example.common.outbox.domain.Outbox;
import org.example.common.outbox.domain.OutboxDispatchType;
import org.example.common.outbox.domain.OutboxDomainType;
import org.example.common.outbox.domain.OutboxStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutboxServiceTest {

    @Mock
    private OutboxRepository outboxRepository;

    @Mock
    private ObjectProvider<EventPublisherPort> outboxPublisherProvider;

    @Mock
    private EventPublisherPort outboxPublisher;

    @Mock
    private ObjectProvider<OutboxPollerProperties> outboxPollerPropertiesProvider;

    @Mock
    private OutboxPollerProperties outboxPollerProperties;

    private OutboxService sut;

    @BeforeEach
    void setUp() {
        sut = new OutboxService(outboxRepository, outboxPublisherProvider, outboxPollerPropertiesProvider);
    }

    @Test
    @DisplayName("outbox 단건을 저장한다")
    void save_delegatesToRepository() {
        // given
        Outbox outbox = createOutbox("outbox-1", OutboxDispatchType.GENERAL);

        // when
        sut.save(outbox);

        // then
        verify(outboxRepository).save(outbox);
    }

    @Test
    @DisplayName("outbox 목록을 저장한다")
    void saveAll_delegatesToRepository() {
        // given
        List<Outbox> outboxes = List.of(
                createOutbox("outbox-1", OutboxDispatchType.GENERAL),
                createOutbox("outbox-2", OutboxDispatchType.BROADCAST)
        );

        // when
        sut.saveAll(outboxes);

        // then
        verify(outboxRepository).saveAll(outboxes);
    }

    @Test
    @DisplayName("dispatchType에 맞는 PENDING outbox를 batchSize만큼 조회한다")
    void publishPending_findsPendingOutboxesByDispatchTypeAndBatchSize() {
        // given
        OutboxPollerProperties.Item props =
                new OutboxPollerProperties.Item(true, 1000, 10, 3);

        when(outboxPollerProperties.get(OutboxDispatchType.GENERAL))
                .thenReturn(props);
        when(outboxPollerPropertiesProvider.getObject())
                .thenReturn(outboxPollerProperties);
        when(outboxPublisherProvider.getObject())
                .thenReturn(outboxPublisher);

        when(outboxRepository.findByDispatchTypeAndStatusOrderByCreatedAtAsc(
                eq(OutboxDispatchType.GENERAL),
                eq(OutboxStatus.PENDING),
                any(Pageable.class)
        )).thenReturn(List.of());

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);

        // when
        sut.publishPending(OutboxDispatchType.GENERAL);

        // then
        verify(outboxRepository).findByDispatchTypeAndStatusOrderByCreatedAtAsc(
                eq(OutboxDispatchType.GENERAL),
                eq(OutboxStatus.PENDING),
                pageableCaptor.capture()
        );

        Pageable pageable = pageableCaptor.getValue();

        assertThat(pageable).isEqualTo(PageRequest.of(0, 10));
    }

    @Test
    @DisplayName("publish에 성공하면 outbox를 PUBLISHED 상태로 변경한다")
    void publishPending_whenPublishSucceeds_marksPublished() {
        // given
        OutboxPollerProperties.Item props =
                new OutboxPollerProperties.Item(true, 1000, 10, 3);

        Outbox outbox = createOutbox("outbox-1", OutboxDispatchType.GENERAL);

        when(outboxPollerProperties.get(OutboxDispatchType.GENERAL))
                .thenReturn(props);
        when(outboxPollerPropertiesProvider.getObject())
                .thenReturn(outboxPollerProperties);
        when(outboxPublisherProvider.getObject())
                .thenReturn(outboxPublisher);

        when(outboxRepository.findByDispatchTypeAndStatusOrderByCreatedAtAsc(
                eq(OutboxDispatchType.GENERAL),
                eq(OutboxStatus.PENDING),
                any(Pageable.class)
        )).thenReturn(List.of(outbox));

        // when
        sut.publishPending(OutboxDispatchType.GENERAL);

        // then
        verify(outboxPublisher).publish(outbox);

        assertThat(outbox.getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
        assertThat(outbox.getRetryCnt()).isZero();
    }

    @Test
    @DisplayName("publish에 실패하면 retryCnt를 1 증가시킨다")
    void publishPending_whenPublishFails_increasesRetryCount() {
        // given
        OutboxPollerProperties.Item props =
                new OutboxPollerProperties.Item(true, 1000, 10, 3);

        Outbox outbox = createOutbox("outbox-1", OutboxDispatchType.GENERAL);

        when(outboxPollerProperties.get(OutboxDispatchType.GENERAL))
                .thenReturn(props);
        when(outboxPollerPropertiesProvider.getObject())
                .thenReturn(outboxPollerProperties);
        when(outboxPublisherProvider.getObject())
                .thenReturn(outboxPublisher);

        when(outboxRepository.findByDispatchTypeAndStatusOrderByCreatedAtAsc(
                eq(OutboxDispatchType.GENERAL),
                eq(OutboxStatus.PENDING),
                any(Pageable.class)
        )).thenReturn(List.of(outbox));

        doThrow(new RuntimeException("kafka send failed"))
                .when(outboxPublisher)
                .publish(outbox);

        // when
        sut.publishPending(OutboxDispatchType.GENERAL);

        // then
        assertThat(outbox.getRetryCnt()).isEqualTo(1);
        assertThat(outbox.getStatus()).isEqualTo(OutboxStatus.PENDING);
    }

    @Test
    @DisplayName("publish 실패 후 retryCnt가 maxRetryCnt 이상이면 FAILED 상태로 변경한다")
    void publishPending_whenRetryExhausted_marksFailed() {
        // given
        OutboxPollerProperties.Item props =
                new OutboxPollerProperties.Item(true, 1000, 10, 3);

        Outbox outbox = createOutbox("outbox-1", OutboxDispatchType.GENERAL);

        outbox.increaseRetryCnt();
        outbox.increaseRetryCnt();

        when(outboxPollerProperties.get(OutboxDispatchType.GENERAL))
                .thenReturn(props);
        when(outboxPollerPropertiesProvider.getObject())
                .thenReturn(outboxPollerProperties);
        when(outboxPublisherProvider.getObject())
                .thenReturn(outboxPublisher);

        when(outboxRepository.findByDispatchTypeAndStatusOrderByCreatedAtAsc(
                eq(OutboxDispatchType.GENERAL),
                eq(OutboxStatus.PENDING),
                any(Pageable.class)
        )).thenReturn(List.of(outbox));

        doThrow(new RuntimeException("kafka send failed"))
                .when(outboxPublisher)
                .publish(outbox);

        // when
        sut.publishPending(OutboxDispatchType.GENERAL);

        // then
        assertThat(outbox.getRetryCnt()).isEqualTo(3);
        assertThat(outbox.getStatus()).isEqualTo(OutboxStatus.FAILED);
    }

    @Test
    @DisplayName("한 outbox publish가 실패해도 다음 outbox 처리를 계속한다")
    void publishPending_whenOneFails_continuesNextOutbox() {
        // given
        OutboxPollerProperties.Item props =
                new OutboxPollerProperties.Item(true, 1000, 10, 3);

        Outbox failedOutbox = createOutbox("outbox-1", OutboxDispatchType.GENERAL);
        Outbox successOutbox = createOutbox("outbox-2", OutboxDispatchType.GENERAL);

        when(outboxPollerProperties.get(OutboxDispatchType.GENERAL))
                .thenReturn(props);
        when(outboxPollerPropertiesProvider.getObject())
                .thenReturn(outboxPollerProperties);
        when(outboxPublisherProvider.getObject())
                .thenReturn(outboxPublisher);

        when(outboxRepository.findByDispatchTypeAndStatusOrderByCreatedAtAsc(
                eq(OutboxDispatchType.GENERAL),
                eq(OutboxStatus.PENDING),
                any(Pageable.class)
        )).thenReturn(List.of(failedOutbox, successOutbox));

        doThrow(new RuntimeException("first publish failed"))
                .when(outboxPublisher)
                .publish(failedOutbox);

        // when
        sut.publishPending(OutboxDispatchType.GENERAL);

        // then
        verify(outboxPublisher).publish(failedOutbox);
        verify(outboxPublisher).publish(successOutbox);

        assertThat(failedOutbox.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(failedOutbox.getRetryCnt()).isEqualTo(1);

        assertThat(successOutbox.getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
        assertThat(successOutbox.getRetryCnt()).isZero();
    }

    @Test
    @DisplayName("BROADCAST 타입이면 BROADCAST 설정과 조회 조건을 사용한다")
    void publishPending_withBroadcast_usesBroadcastDispatchType() {
        // given
        OutboxPollerProperties.Item props =
                new OutboxPollerProperties.Item(true, 500, 20, 5);

        Outbox outbox = createOutbox("outbox-1", OutboxDispatchType.BROADCAST);

        when(outboxPollerProperties.get(OutboxDispatchType.BROADCAST))
                .thenReturn(props);
        when(outboxPollerPropertiesProvider.getObject())
                .thenReturn(outboxPollerProperties);
        when(outboxPublisherProvider.getObject())
                .thenReturn(outboxPublisher);

        when(outboxRepository.findByDispatchTypeAndStatusOrderByCreatedAtAsc(
                eq(OutboxDispatchType.BROADCAST),
                eq(OutboxStatus.PENDING),
                any(Pageable.class)
        )).thenReturn(List.of(outbox));

        // when
        sut.publishPending(OutboxDispatchType.BROADCAST);

        // then
        verify(outboxRepository).findByDispatchTypeAndStatusOrderByCreatedAtAsc(
                eq(OutboxDispatchType.BROADCAST),
                eq(OutboxStatus.PENDING),
                eq(PageRequest.of(0, 20))
        );

        verify(outboxPublisher).publish(outbox);

        assertThat(outbox.getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
    }

    private Outbox createOutbox(String id, OutboxDispatchType dispatchType) {
        return Outbox.ofPending(
                id,
                "tx-" + id,
                "aggregate-" + id,
                "chat-message-topic",
                "partition-" + id,
                "{\"message\":\"hello\"}",
                "ChatMessageCreatedEvent",
                OutboxDomainType.CHAT,
                dispatchType
        );
    }
}

package dlq;

import org.example.dlq.adapter.DlqRepository;
import org.example.outbox.application.EventPublisherPort;
import org.example.dlq.DlqService;
import org.example.dlq.Dlq;
import org.example.dlq.DlqStatus;
import org.example.outbox.properties.DlqPollerProperties;
import org.example.outbox.domain.OutboxDomainType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DlqServiceTest {

    @Mock
    private EventPublisherPort eventPublisher;

    @Mock
    private DlqRepository dlqRepository;

    @Mock
    private DlqPollerProperties dlqPollerProperties;

    @InjectMocks
    private DlqService sut;

    @Test
    @DisplayName("PENDING 상태 DLQ를 batchSize만큼 조회한다")
    void publishPending_findsPendingDlqsByBatchSize() {
        // given
        when(dlqPollerProperties.batchSize())
                .thenReturn(10);

        when(dlqRepository.findByStatusOrderByCreatedAtAsc(
                eq(DlqStatus.PENDING),
                any(Pageable.class)
        )).thenReturn(List.of());

        ArgumentCaptor<Pageable> pageableCaptor =
                ArgumentCaptor.forClass(Pageable.class);

        // when
        sut.publishPending();

        // then
        verify(dlqRepository).findByStatusOrderByCreatedAtAsc(
                eq(DlqStatus.PENDING),
                pageableCaptor.capture()
        );

        assertThat(pageableCaptor.getValue())
                .isEqualTo(PageRequest.of(0, 10));

        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("DLQ publish에 성공하면 PUBLISHED 상태로 변경한다")
    void publishPending_whenPublishSucceeds_marksPublished() {
        // given
        Dlq dlq = createDlq("dlq-1");

        when(dlqPollerProperties.batchSize())
                .thenReturn(10);

        when(dlqRepository.findByStatusOrderByCreatedAtAsc(
                eq(DlqStatus.PENDING),
                any(Pageable.class)
        )).thenReturn(List.of(dlq));

        // when
        sut.publishPending();

        // then
        verify(eventPublisher).publish(dlq);

        assertThat(dlq.getStatus())
                .isEqualTo(DlqStatus.PUBLISHED);
    }

    @Test
    @DisplayName("DLQ publish에 실패하면 PUBLISH_FAILED 상태로 변경한다")
    void publishPending_whenPublishFails_marksPublishFailed() {
        // given
        Dlq dlq = createDlq("dlq-1");

        when(dlqPollerProperties.batchSize())
                .thenReturn(10);

        when(dlqRepository.findByStatusOrderByCreatedAtAsc(
                eq(DlqStatus.PENDING),
                any(Pageable.class)
        )).thenReturn(List.of(dlq));

        doThrow(new RuntimeException("kafka publish failed"))
                .when(eventPublisher)
                .publish(dlq);

        // when
        sut.publishPending();

        // then
        verify(eventPublisher).publish(dlq);

        assertThat(dlq.getStatus())
                .isEqualTo(DlqStatus.PUBLISH_FAILED);
    }

    @Test
    @DisplayName("한 DLQ publish가 실패해도 다음 DLQ 처리를 계속한다")
    void publishPending_whenOneFails_continuesNextDlq() {
        // given
        Dlq failedDlq = createDlq("dlq-1");
        Dlq successDlq = createDlq("dlq-2");

        when(dlqPollerProperties.batchSize())
                .thenReturn(10);

        when(dlqRepository.findByStatusOrderByCreatedAtAsc(
                eq(DlqStatus.PENDING),
                any(Pageable.class)
        )).thenReturn(List.of(failedDlq, successDlq));

        doThrow(new RuntimeException("first publish failed"))
                .when(eventPublisher)
                .publish(failedDlq);

        // when
        sut.publishPending();

        // then
        verify(eventPublisher).publish(failedDlq);
        verify(eventPublisher).publish(successDlq);

        assertThat(failedDlq.getStatus())
                .isEqualTo(DlqStatus.PUBLISH_FAILED);

        assertThat(successDlq.getStatus())
                .isEqualTo(DlqStatus.PUBLISHED);
    }

    private Dlq createDlq(String id) {
        return Dlq.ofPending(
                id,
                "outbox-" + id,
                "ChatMessageCreatedEvent",
                "aggregate-" + id,
                "chat-message-topic",
                "tx-" + id,
                OutboxDomainType.CHAT,
                "publish failed",
                "{\"message\":\"hello\"}"
        );
    }
}

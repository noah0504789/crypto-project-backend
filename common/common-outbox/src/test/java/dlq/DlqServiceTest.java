package dlq;

import org.example.common.exception.DlqNotFoundException;
import org.example.common.dlq.adapter.DlqRepository;
import org.example.common.outbox.application.port.out.EventPublisherPort;
import org.example.common.dlq.application.DlqService;
import org.example.common.dlq.domain.Dlq;
import org.example.common.dlq.domain.DlqStatus;
import org.example.common.dlq.properties.DlqPollerProperties;
import org.example.common.outbox.domain.OutboxDomainType;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DlqServiceTest {

    @Mock
    private ObjectProvider<EventPublisherPort> eventPublisherProvider;

    @Mock
    private EventPublisherPort eventPublisher;

    @Mock
    private DlqRepository dlqRepository;

    @Mock
    private ObjectProvider<DlqPollerProperties> dlqPollerPropertiesProvider;

    @Mock
    private DlqPollerProperties dlqPollerProperties;

    private DlqService sut;

    @BeforeEach
    void setUp() {
        sut = new DlqService(eventPublisherProvider, dlqRepository, dlqPollerPropertiesProvider);
    }

    @Test
    @DisplayName("DLQ 단건을 저장한다")
    void save_delegatesToRepository() {
        // given
        Dlq dlq = createDlq("dlq-1");

        // when
        sut.save(dlq);

        // then
        verify(dlqRepository).save(dlq);
    }

    @Test
    @DisplayName("DLQ 목록을 저장한다")
    void saveAll_delegatesToRepository() {
        // given
        List<Dlq> dlqList = List.of(
                createDlq("dlq-1"),
                createDlq("dlq-2")
        );

        // when
        sut.saveAll(dlqList);

        // then
        verify(dlqRepository).saveAll(dlqList);
    }

    @Test
    @DisplayName("DLQ 처리를 완료 상태로 변경한다")
    void complete_marksCompleted() {
        // given
        Dlq dlq = createDlq("dlq-1");

        when(dlqRepository.findById("dlq-1"))
                .thenReturn(Optional.of(dlq));

        // when
        sut.complete("dlq-1");

        // then
        assertThat(dlq.getStatus())
                .isEqualTo(DlqStatus.COMPLETED);
    }

    @Test
    @DisplayName("DLQ 완료 대상이 없으면 예외를 던진다")
    void complete_whenDlqDoesNotExist_throwsException() {
        // given
        when(dlqRepository.findById("missing-dlq"))
                .thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> sut.complete("missing-dlq"))
                .isInstanceOf(DlqNotFoundException.class);
    }

    @Test
    @DisplayName("DLQ 처리를 실패 상태로 변경하고 에러 메시지를 남긴다")
    void fail_marksFailed() {
        // given
        Dlq dlq = createDlq("dlq-1");

        when(dlqRepository.findById("dlq-1"))
                .thenReturn(Optional.of(dlq));

        // when
        sut.fail("dlq-1", "handler failed");

        // then
        assertThat(dlq.getStatus())
                .isEqualTo(DlqStatus.COMSUME_FAILED);
        assertThat(dlq.getErrorMessage())
                .isEqualTo("handler failed");
    }

    @Test
    @DisplayName("DLQ 실패 처리 대상이 없으면 예외를 던진다")
    void fail_whenDlqDoesNotExist_throwsException() {
        // given
        when(dlqRepository.findById("missing-dlq"))
                .thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> sut.fail("missing-dlq", "handler failed"))
                .isInstanceOf(DlqNotFoundException.class);
    }

    @Test
    @DisplayName("PENDING 상태 DLQ를 batchSize만큼 조회한다")
    void publishPending_findsPendingDlqsByBatchSize() {
        // given
        when(dlqPollerProperties.batchSize())
                .thenReturn(10);
        when(dlqPollerPropertiesProvider.getObject())
                .thenReturn(dlqPollerProperties);
        when(eventPublisherProvider.getObject())
                .thenReturn(eventPublisher);

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
        when(dlqPollerPropertiesProvider.getObject())
                .thenReturn(dlqPollerProperties);
        when(eventPublisherProvider.getObject())
                .thenReturn(eventPublisher);

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
        when(dlqPollerPropertiesProvider.getObject())
                .thenReturn(dlqPollerProperties);
        when(eventPublisherProvider.getObject())
                .thenReturn(eventPublisher);

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
        when(dlqPollerPropertiesProvider.getObject())
                .thenReturn(dlqPollerProperties);
        when(eventPublisherProvider.getObject())
                .thenReturn(eventPublisher);

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

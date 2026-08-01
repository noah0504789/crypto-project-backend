package org.example.common.outbox.adapter.in;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.common.outbox.application.service.OutboxService;
import org.example.common.outbox.adapter.out.JpaOutbox;
import org.example.common.outbox.domain.event.AbstractOutboxEvent;
import org.example.common.outbox.domain.event.AbstractOutboxEventList;
import org.example.common.outbox.exception.OutboxPersistenceException;
import org.example.common.outbox.exception.TemporaryOutboxPersistenceException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataRetrievalFailureException;
import org.springframework.dao.TransientDataAccessResourceException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OutboxEventListListenerUnitTest {

    @Mock
    private OutboxService outboxService;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private OutboxEventListListener sut;

    private final String txId = "tx-1";

    @Test
    @DisplayName("OutboxEventList를 받으면 이벤트들을 직렬화하고 JpaOutbox로 변환하여 저장한다")
    void handleOutboxEventList() throws Exception {
        // given
        AbstractOutboxEventList eventList = mock(AbstractOutboxEventList.class);

        AbstractOutboxEvent event1 = mock(AbstractOutboxEvent.class);
        AbstractOutboxEvent event2 = mock(AbstractOutboxEvent.class);

        JpaOutbox outbox1 = mock(JpaOutbox.class);
        JpaOutbox outbox2 = mock(JpaOutbox.class);

        given(eventList.getTxId()).willReturn(txId);
        given(eventList.getEventList()).willReturn(List.of(event1, event2));

        given(objectMapper.writeValueAsString(event1)).willReturn("payload-1");
        given(objectMapper.writeValueAsString(event2)).willReturn("payload-2");

        given(event1.toOutbox(txId, "payload-1")).willReturn(outbox1);
        given(event2.toOutbox(txId, "payload-2")).willReturn(outbox2);

        // when
        sut.handleOutboxEventList(eventList);

        // then
        InOrder inOrder = inOrder(
                eventList,
                objectMapper,
                event1,
                event2,
                outboxService
        );

        inOrder.verify(eventList).getTxId();
        inOrder.verify(eventList).getEventList();

        inOrder.verify(objectMapper).writeValueAsString(event1);
        inOrder.verify(event1).toOutbox(txId, "payload-1");

        inOrder.verify(objectMapper).writeValueAsString(event2);
        inOrder.verify(event2).toOutbox(txId, "payload-2");

        ArgumentCaptor<List<JpaOutbox>> captor = ArgumentCaptor.forClass(List.class);

        verify(outboxService).saveAll(captor.capture());

        assertThat(captor.getValue())
                .containsExactly(outbox1, outbox2);
    }

    @Test
    @DisplayName("이벤트 리스트가 비어 있어도 빈 JpaOutbox 목록을 저장한다")
    void handleOutboxEventListWithEmptyEvents() throws JsonProcessingException {
        // given
        AbstractOutboxEventList eventList = mock(AbstractOutboxEventList.class);

        given(eventList.getTxId()).willReturn(txId);
        given(eventList.getEventList()).willReturn(List.of());

        // when
        sut.handleOutboxEventList(eventList);

        // then
        ArgumentCaptor<List<JpaOutbox>> captor = ArgumentCaptor.forClass(List.class);

        verify(outboxService).saveAll(captor.capture());

        assertThat(captor.getValue()).isEmpty();

        verify(objectMapper, never()).writeValueAsString(any());
    }

    @Test
    @DisplayName("이벤트 직렬화에 실패하면 OutboxPersistenceException으로 감싸고 저장하지 않는다")
    void handleOutboxEventListThrowsOutboxPersistenceExceptionWhenSerializationFails() throws Exception {
        // given
        AbstractOutboxEventList eventList = mock(AbstractOutboxEventList.class);
        AbstractOutboxEvent event = mock(AbstractOutboxEvent.class);

        JsonProcessingException exception = mock(JsonProcessingException.class);

        given(eventList.getTxId()).willReturn(txId);
        given(eventList.getEventList()).willReturn(List.of(event));

        given(objectMapper.writeValueAsString(event))
                .willThrow(exception);

        // when & then
        assertThatThrownBy(() -> sut.handleOutboxEventList(eventList))
                .isInstanceOf(OutboxPersistenceException.class)
                .hasMessageContaining("failed to serialize outbox events")
                .hasCause(exception);

        verify(objectMapper).writeValueAsString(event);
        verify(event, never()).toOutbox(anyString(), anyString());
        verify(outboxService, never()).saveAll(anyList());
    }

    @Test
    @DisplayName("JpaOutbox 저장 중 일반 DataAccessException이 발생하면 OutboxPersistenceException으로 감싼다")
    void handleOutboxEventListThrowsOutboxPersistenceExceptionWhenSaveFails() throws Exception {
        // given
        AbstractOutboxEventList eventList = mock(AbstractOutboxEventList.class);
        AbstractOutboxEvent event = mock(AbstractOutboxEvent.class);
        JpaOutbox outbox = mock(JpaOutbox.class);

        DataAccessException exception =
                new DataRetrievalFailureException("outbox save failed");

        given(eventList.getTxId()).willReturn(txId);
        given(eventList.getEventList()).willReturn(List.of(event));

        given(objectMapper.writeValueAsString(event)).willReturn("payload");
        given(event.toOutbox(txId, "payload")).willReturn(outbox);

        doThrow(exception)
                .when(outboxService)
                .saveAll(anyList());

        // when & then
        assertThatThrownBy(() -> sut.handleOutboxEventList(eventList))
                .isInstanceOf(OutboxPersistenceException.class)
                .isNotInstanceOf(TemporaryOutboxPersistenceException.class)
                .hasMessageContaining("failed to save outbox events")
                .hasCause(exception);

        verify(objectMapper).writeValueAsString(event);
        verify(event).toOutbox(txId, "payload");
        verify(outboxService).saveAll(anyList());
    }

    @Test
    @DisplayName("JpaOutbox 저장 중 일시적 DataAccessException이 발생하면 TemporaryOutboxPersistenceException으로 감싼다")
    void handleOutboxEventListThrowsTemporaryOutboxPersistenceExceptionWhenTemporarySaveFails() throws Exception {
        // given
        AbstractOutboxEventList eventList = mock(AbstractOutboxEventList.class);
        AbstractOutboxEvent event = mock(AbstractOutboxEvent.class);
        JpaOutbox outbox = mock(JpaOutbox.class);

        DataAccessException exception =
                new TransientDataAccessResourceException("temporary outbox save failed");

        given(eventList.getTxId()).willReturn(txId);
        given(eventList.getEventList()).willReturn(List.of(event));

        given(objectMapper.writeValueAsString(event)).willReturn("payload");
        given(event.toOutbox(txId, "payload")).willReturn(outbox);

        doThrow(exception)
                .when(outboxService)
                .saveAll(anyList());

        // when & then
        assertThatThrownBy(() -> sut.handleOutboxEventList(eventList))
                .isInstanceOf(TemporaryOutboxPersistenceException.class)
                .hasMessageContaining("failed to save outbox events")
                .hasCause(exception);

        verify(objectMapper).writeValueAsString(event);
        verify(event).toOutbox(txId, "payload");
        verify(outboxService).saveAll(anyList());
    }

    @Test
    @DisplayName("예상하지 못한 예외가 발생하면 OutboxPersistenceException으로 감싼다")
    void handleOutboxEventListThrowsOutboxPersistenceExceptionWhenUnexpectedExceptionOccurs() throws Exception {
        // given
        AbstractOutboxEventList eventList = mock(AbstractOutboxEventList.class);
        AbstractOutboxEvent event = mock(AbstractOutboxEvent.class);

        RuntimeException exception = new RuntimeException("unexpected failure");

        given(eventList.getTxId()).willReturn(txId);
        given(eventList.getEventList()).willReturn(List.of(event));

        given(objectMapper.writeValueAsString(event)).willReturn("payload");
        given(event.toOutbox(txId, "payload")).willThrow(exception);

        // when & then
        assertThatThrownBy(() -> sut.handleOutboxEventList(eventList))
                .isInstanceOf(OutboxPersistenceException.class)
                .hasMessageContaining("failed to handle outbox event list")
                .hasCause(exception);

        verify(objectMapper).writeValueAsString(event);
        verify(event).toOutbox(txId, "payload");
        verify(outboxService, never()).saveAll(anyList());
    }
}

package org.example.common.dlq.adapter.in;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.common.dlq.application.service.DlqService;
import org.example.common.dlq.domain.Dlq;
import org.example.common.dlq.domain.event.AbstractDlqEvent;
import org.example.common.dlq.domain.event.AbstractDlqEventList;
import org.example.common.dlq.exception.DlqPersistenceException;
import org.example.common.dlq.exception.TemporaryDlqPersistenceException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.Nested;
import org.mockito.ArgumentCaptor;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DlqEventListListenerUnitTest {

    @Mock
    private DlqService dlqService;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private DlqEventListListener sut;

    private final String txId = "tx-1";

    @Nested
    @DisplayName("handleDlqEventList")
    class HandleDlqEventListTest {

        @Test
        @DisplayName("DLQ 이벤트 리스트를 받으면 이벤트를 직렬화하고 Dlq로 변환하여 저장한다")
        void handleDlqEventList() throws Exception {
            // given
            AbstractDlqEventList eventList = mock(AbstractDlqEventList.class);

            AbstractDlqEvent event1 = mock(AbstractDlqEvent.class);
            AbstractDlqEvent event2 = mock(AbstractDlqEvent.class);

            Dlq dlq1 = mock(Dlq.class);
            Dlq dlq2 = mock(Dlq.class);

            given(eventList.getTxId()).willReturn(txId);
            given(eventList.getEventList()).willReturn(List.of(event1, event2));

            given(objectMapper.writeValueAsString(event1))
                    .willReturn("payload-1");
            given(objectMapper.writeValueAsString(event2))
                    .willReturn("payload-2");

            given(event1.toDlq(txId, "payload-1"))
                    .willReturn(dlq1);
            given(event2.toDlq(txId, "payload-2"))
                    .willReturn(dlq2);

            // when
            sut.handleDlqEventList(eventList);

            // then
            ArgumentCaptor<List<Dlq>> captor =
                    ArgumentCaptor.forClass(List.class);

            verify(dlqService).saveAll(captor.capture());

            assertThat(captor.getValue())
                    .containsExactly(dlq1, dlq2);

            verify(objectMapper).writeValueAsString(event1);
            verify(objectMapper).writeValueAsString(event2);

            verify(event1).toDlq(txId, "payload-1");
            verify(event2).toDlq(txId, "payload-2");
        }

        @Test
        @DisplayName("DLQ 이벤트 리스트가 비어 있어도 빈 Dlq 목록을 저장한다")
        void handleDlqEventListWithEmptyEventList() throws JsonProcessingException {
            // given
            AbstractDlqEventList eventList = mock(AbstractDlqEventList.class);

            given(eventList.getTxId()).willReturn(txId);
            given(eventList.getEventList()).willReturn(List.of());

            // when
            sut.handleDlqEventList(eventList);

            // then
            ArgumentCaptor<List<Dlq>> captor =
                    ArgumentCaptor.forClass(List.class);

            verify(dlqService).saveAll(captor.capture());

            assertThat(captor.getValue()).isEmpty();

            verify(objectMapper, never()).writeValueAsString(any());
        }

        @Test
        @DisplayName("DLQ 이벤트 직렬화에 실패하면 DlqPersistenceException으로 변환한다")
        void handleDlqEventListThrowsDlqPersistenceExceptionWhenSerializationFails() throws Exception {
            // given
            AbstractDlqEventList eventList = mock(AbstractDlqEventList.class);
            AbstractDlqEvent event = mock(AbstractDlqEvent.class);

            JsonProcessingException exception = mock(JsonProcessingException.class);

            given(eventList.getTxId()).willReturn(txId);
            given(eventList.getEventList()).willReturn(List.of(event));

            given(objectMapper.writeValueAsString(event))
                    .willThrow(exception);

            // when & then
            assertThatThrownBy(() -> sut.handleDlqEventList(eventList))
                    .isInstanceOf(DlqPersistenceException.class)
                    .isNotInstanceOf(TemporaryDlqPersistenceException.class)
                    .hasMessageContaining("failed to serialize dlq events")
                    .hasMessageContaining(txId)
                    .hasCause(exception);

            verify(objectMapper).writeValueAsString(event);
            verify(event, never()).toDlq(anyString(), anyString());
            verify(dlqService, never()).saveAll(anyList());
        }

        @Test
        @DisplayName("DLQ 저장 중 일반 DataAccessException이 발생하면 DlqPersistenceException으로 변환한다")
        void handleDlqEventListThrowsDlqPersistenceExceptionWhenSaveFails() throws Exception {
            // given
            AbstractDlqEventList eventList = mock(AbstractDlqEventList.class);
            AbstractDlqEvent event = mock(AbstractDlqEvent.class);
            Dlq dlq = mock(Dlq.class);

            DataAccessException exception =
                    new DataRetrievalFailureException("dlq save failed");

            given(eventList.getTxId()).willReturn(txId);
            given(eventList.getEventList()).willReturn(List.of(event));

            given(objectMapper.writeValueAsString(event))
                    .willReturn("payload");

            given(event.toDlq(txId, "payload"))
                    .willReturn(dlq);

            doThrow(exception)
                    .when(dlqService)
                    .saveAll(anyList());

            // when & then
            assertThatThrownBy(() -> sut.handleDlqEventList(eventList))
                    .isInstanceOf(DlqPersistenceException.class)
                    .isNotInstanceOf(TemporaryDlqPersistenceException.class)
                    .hasMessageContaining("failed to save dlq events")
                    .hasMessageContaining(txId)
                    .hasCause(exception);

            verify(objectMapper).writeValueAsString(event);
            verify(event).toDlq(txId, "payload");
            verify(dlqService).saveAll(anyList());
        }

        @Test
        @DisplayName("DLQ 저장 중 일시적 DataAccessException이 발생하면 TemporaryDlqPersistenceException으로 변환한다")
        void handleDlqEventListThrowsTemporaryDlqPersistenceExceptionWhenTemporarySaveFails() throws Exception {
            // given
            AbstractDlqEventList eventList = mock(AbstractDlqEventList.class);
            AbstractDlqEvent event = mock(AbstractDlqEvent.class);
            Dlq dlq = mock(Dlq.class);

            DataAccessException exception =
                    new TransientDataAccessResourceException("temporary dlq save failed");

            given(eventList.getTxId()).willReturn(txId);
            given(eventList.getEventList()).willReturn(List.of(event));

            given(objectMapper.writeValueAsString(event))
                    .willReturn("payload");

            given(event.toDlq(txId, "payload"))
                    .willReturn(dlq);

            doThrow(exception)
                    .when(dlqService)
                    .saveAll(anyList());

            // when & then
            assertThatThrownBy(() -> sut.handleDlqEventList(eventList))
                    .isInstanceOf(TemporaryDlqPersistenceException.class)
                    .hasMessageContaining("failed to save dlq events")
                    .hasMessageContaining(txId)
                    .hasCause(exception);

            verify(objectMapper).writeValueAsString(event);
            verify(event).toDlq(txId, "payload");
            verify(dlqService).saveAll(anyList());
        }

        @Test
        @DisplayName("DLQ 이벤트 변환 중 예상하지 못한 예외가 발생하면 DlqPersistenceException으로 변환한다")
        void handleDlqEventListThrowsDlqPersistenceExceptionWhenUnexpectedExceptionOccurs() throws Exception {
            // given
            AbstractDlqEventList eventList = mock(AbstractDlqEventList.class);
            AbstractDlqEvent event = mock(AbstractDlqEvent.class);

            RuntimeException exception =
                    new RuntimeException("unexpected failure");

            given(eventList.getTxId()).willReturn(txId);
            given(eventList.getEventList()).willReturn(List.of(event));

            given(objectMapper.writeValueAsString(event))
                    .willReturn("payload");

            given(event.toDlq(txId, "payload"))
                    .willThrow(exception);

            // when & then
            assertThatThrownBy(() -> sut.handleDlqEventList(eventList))
                    .isInstanceOf(DlqPersistenceException.class)
                    .isNotInstanceOf(TemporaryDlqPersistenceException.class)
                    .hasMessageContaining("failed to handle dlq event list")
                    .hasMessageContaining(txId)
                    .hasCause(exception);

            verify(objectMapper).writeValueAsString(event);
            verify(event).toDlq(txId, "payload");
            verify(dlqService, never()).saveAll(anyList());
        }

        @Test
        @DisplayName("DLQ 이벤트 변환 중 일시적 예외가 cause에 포함되어 있으면 TemporaryDlqPersistenceException으로 변환한다")
        void handleDlqEventListThrowsTemporaryDlqPersistenceExceptionWhenUnexpectedTemporaryCauseOccurs() throws Exception {
            // given
            AbstractDlqEventList eventList = mock(AbstractDlqEventList.class);
            AbstractDlqEvent event = mock(AbstractDlqEvent.class);

            TransientDataAccessResourceException cause =
                    new TransientDataAccessResourceException("temporary failure");

            RuntimeException exception = new RuntimeException(cause);

            given(eventList.getTxId()).willReturn(txId);
            given(eventList.getEventList()).willReturn(List.of(event));

            given(objectMapper.writeValueAsString(event))
                    .willReturn("payload");

            given(event.toDlq(txId, "payload"))
                    .willThrow(exception);

            // when & then
            assertThatThrownBy(() -> sut.handleDlqEventList(eventList))
                    .isInstanceOf(TemporaryDlqPersistenceException.class)
                    .hasMessageContaining("failed to handle dlq event list")
                    .hasMessageContaining(txId)
                    .hasCause(exception);

            verify(objectMapper).writeValueAsString(event);
            verify(event).toDlq(txId, "payload");
            verify(dlqService, never()).saveAll(anyList());
        }
    }
}
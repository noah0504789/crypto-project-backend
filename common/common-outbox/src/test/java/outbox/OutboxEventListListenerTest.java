package outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.outbox.adapter.OutboxEventListListener;
import org.example.outbox.domain.event.AbstractOutboxEvent;
import org.example.outbox.domain.Outbox;
import org.example.outbox.OutboxService;
import org.example.outbox.domain.event.AbstractOutboxEventList;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import org.mockito.*;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataRetrievalFailureException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class OutboxEventListListenerTest {

    @Mock
    private OutboxService outboxService;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private OutboxEventListListener sut;

    private final String txId = "tx-1";

    @Test
    @DisplayName("OutboxEventList를 받으면 이벤트들을 직렬화하고 Outbox로 변환하여 저장한다")
    void handleOutboxEventList() throws Exception {
        // given
        AbstractOutboxEventList eventList = mock(AbstractOutboxEventList.class);

        AbstractOutboxEvent event1 = mock(AbstractOutboxEvent.class);
        AbstractOutboxEvent event2 = mock(AbstractOutboxEvent.class);

        Outbox outbox1 = mock(Outbox.class);
        Outbox outbox2 = mock(Outbox.class);

        given(eventList.getTxId()).willReturn(txId);
        given(eventList.getEventList()).willReturn(List.of(event1, event2));

        given(objectMapper.writeValueAsString(event1)).willReturn("payload-1");
        given(objectMapper.writeValueAsString(event2)).willReturn("payload-2");

        given(event1.toOutbox(txId, "payload-1")).willReturn(outbox1);
        given(event2.toOutbox(txId, "payload-2")).willReturn(outbox2);

        // when
        sut.handleOutboxEventList(eventList);

        // then
        InOrder inOrder = inOrder(eventList, objectMapper, event1, event2, outboxService);

        inOrder.verify(eventList).getTxId();
        inOrder.verify(eventList).getEventList();

        inOrder.verify(objectMapper).writeValueAsString(event1);
        inOrder.verify(event1).toOutbox(txId, "payload-1");

        inOrder.verify(objectMapper).writeValueAsString(event2);
        inOrder.verify(event2).toOutbox(txId, "payload-2");

        ArgumentCaptor<List<Outbox>> captor = ArgumentCaptor.forClass(List.class);

        verify(outboxService).saveAll(captor.capture());

        assertThat(captor.getValue())
                .containsExactly(outbox1, outbox2);
    }

    @Test
    @DisplayName("이벤트 리스트가 비어 있어도 빈 Outbox 목록을 저장한다")
    void handleOutboxEventListWithEmptyEvents() throws Exception {
        // given
        AbstractOutboxEventList eventList = mock(AbstractOutboxEventList.class);

        given(eventList.getTxId()).willReturn(txId);
        given(eventList.getEventList()).willReturn(List.of());

        // when
        sut.handleOutboxEventList(eventList);

        // then
        ArgumentCaptor<List<Outbox>> captor = ArgumentCaptor.forClass(List.class);

        verify(outboxService).saveAll(captor.capture());

        assertThat(captor.getValue()).isEmpty();

        verify(objectMapper, never()).writeValueAsString(any());
    }

    @Test
    @DisplayName("이벤트 직렬화에 실패하면 JsonProcessingException을 다시 던지고 저장하지 않는다")
    void handleOutboxEventListThrowsJsonProcessingException() throws Exception {
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
                .isSameAs(exception);

        verify(objectMapper).writeValueAsString(event);
        verify(event, never()).toOutbox(anyString(), anyString());
        verify(outboxService, never()).saveAll(anyList());
    }

    @Test
    @DisplayName("Outbox 저장에 실패하면 DataAccessException을 다시 던진다")
    void handleOutboxEventListThrowsDataAccessException() throws Exception {
        // given
        AbstractOutboxEventList eventList = mock(AbstractOutboxEventList.class);
        AbstractOutboxEvent event = mock(AbstractOutboxEvent.class);
        Outbox outbox = mock(Outbox.class);

        DataAccessException exception = new DataRetrievalFailureException("outbox save failed");

        given(eventList.getTxId()).willReturn(txId);
        given(eventList.getEventList()).willReturn(List.of(event));

        given(objectMapper.writeValueAsString(event)).willReturn("payload");
        given(event.toOutbox(txId, "payload")).willReturn(outbox);

        doThrow(exception)
                .when(outboxService)
                .saveAll(anyList());

        // when & then
        assertThatThrownBy(() -> sut.handleOutboxEventList(eventList))
                .isSameAs(exception);

        verify(objectMapper).writeValueAsString(event);
        verify(event).toOutbox(txId, "payload");
        verify(outboxService).saveAll(anyList());
    }
}

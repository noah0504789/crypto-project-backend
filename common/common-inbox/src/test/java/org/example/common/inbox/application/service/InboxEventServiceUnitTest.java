package org.example.common.inbox.application.service;

import org.example.common.inbox.adapter.out.InboxEventRepository;
import org.example.common.inbox.domain.InboxEvent;
import org.example.common.inbox.exception.DuplicateInboxEventException;
import org.example.common.inbox.exception.InboxException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class InboxEventServiceTest {

    private InboxEventRepository repository;
    private InboxEventService sut;

    @BeforeEach
    void setUp() {
        repository = mock(InboxEventRepository.class);
        sut = new InboxEventService(repository);
    }

    @Test
    @DisplayName("event_id 선점은 즉시 INSERT를 실행한다")
    void saveFlushesInboxEvent() {
        sut.save("notification.price-alert", "event-1");

        verify(repository).saveAndFlush(any(InboxEvent.class));
    }

    @Test
    @DisplayName("동일 consumer와 event_id가 이미 처리됐으면 비즈니스 처리를 실행하지 않는다")
    void saveThrowsDuplicateInboxEventException() {
        doThrow(new DataIntegrityViolationException("duplicate"))
                .when(repository)
                .saveAndFlush(any(InboxEvent.class));

        assertThatThrownBy(() -> sut.save("notification.price-alert", "event-1"))
                .isInstanceOf(DuplicateInboxEventException.class)
                .isInstanceOf(InboxException.class)
                .hasMessageContaining("notification.price-alert")
                .hasMessageContaining("event-1");
    }
}

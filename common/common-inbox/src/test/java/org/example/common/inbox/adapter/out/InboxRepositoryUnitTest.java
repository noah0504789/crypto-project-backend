package org.example.common.inbox.adapter.out;

import org.example.common.inbox.domain.Inbox;
import org.example.common.inbox.exception.DuplicateInboxException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;

class InboxRepositoryUnitTest {

    private InboxRepository sut;

    @BeforeEach
    void setUp() {
        sut = mock(InboxRepository.class, CALLS_REAL_METHODS);
    }

    @Test
    @DisplayName("선점은 JpaRepository의 saveAndFlush에 위임한다")
    void insert_delegatesToSaveAndFlush() {
        // given
        Inbox inbox = Inbox.of("consumer", "event");

        // when
        sut.insertAndFlush(inbox);

        // then
        verify(sut).saveAndFlush(inbox);
    }

    @Test
    @DisplayName("MySQL 중복 키 충돌만 중복 Inbox 예외로 변환한다")
    void duplicateKey_translatesException() {
        // given
        DataIntegrityViolationException failure = failure("23000", 1062);
        doThrow(failure).when(sut).saveAndFlush(any(Inbox.class));

        // when / then
        assertThatThrownBy(() -> sut.insertAndFlush(Inbox.of("consumer", "event")))
                .isInstanceOf(DuplicateInboxException.class)
                .hasCause(failure);
    }

    @Test
    @DisplayName("NOT NULL 위반을 중복으로 삼키지 않는다")
    void notNullViolation_propagates() {
        // given
        DataIntegrityViolationException failure = failure("23000", 1048);
        doThrow(failure).when(sut).saveAndFlush(any(Inbox.class));

        // when / then
        assertThatThrownBy(() -> sut.insertAndFlush(Inbox.of("consumer", "event")))
                .isSameAs(failure);
    }

    @Test
    @DisplayName("SQL 원인이 없는 영속성 오류를 그대로 전파한다")
    void persistenceFailure_propagates() {
        // given
        DataIntegrityViolationException failure = new DataIntegrityViolationException("persistence failure");
        doThrow(failure).when(sut).saveAndFlush(any(Inbox.class));

        // when / then
        assertThatThrownBy(() -> sut.insertAndFlush(Inbox.of("consumer", "event")))
                .isSameAs(failure);
    }

    private DataIntegrityViolationException failure(String state, int code) {
        return new DataIntegrityViolationException("insert failed", new SQLException("constraint failure", state, code));
    }
}

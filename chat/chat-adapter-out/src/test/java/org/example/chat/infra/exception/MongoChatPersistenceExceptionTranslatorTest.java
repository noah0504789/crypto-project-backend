package org.example.chat.infra.exception;

import org.example.chat.chatmessage.domain.model.ChatMessage;
import org.example.chat.chatmessage.application.exception.ChatMessagePersistException;
import org.example.chat.exception.ChatPersistenceException;
import org.example.chat.chatmessage.application.exception.DuplicateChatMessageException;
import org.example.chat.exception.TemporaryChatPersistenceException;
import org.hibernate.exception.JDBCConnectionException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.data.mongodb.MongoTransactionException;

import java.sql.SQLTransientConnectionException;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class MongoChatPersistenceExceptionTranslatorTest {

    private final String message = "failed to access mongo";

    @Nested
    @DisplayName("translate")
    class TranslateTest {

        @Test
        @DisplayName("TransientDataAccessException 계열이면 TemporaryChatPersistenceException으로 변환한다")
        void translateTransientDataAccessException() {
            // given
            TransientDataAccessResourceException exception =
                    new TransientDataAccessResourceException("temporary failure");

            // when
            ChatPersistenceException result =
                    MongoChatPersistenceExceptionTranslator.translate(message, exception);

            // then
            assertThat(result)
                    .isInstanceOf(TemporaryChatPersistenceException.class)
                    .hasMessage(message)
                    .hasCause(exception);
        }

        @Test
        @DisplayName("MongoTransactionException이면 TemporaryChatPersistenceException으로 변환한다")
        void translateMongoTransactionException() {
            // given
            MongoTransactionException exception =
                    new MongoTransactionException("mongo transaction failure");

            // when
            ChatPersistenceException result =
                    MongoChatPersistenceExceptionTranslator.translate(message, exception);

            // then
            assertThat(result)
                    .isInstanceOf(TemporaryChatPersistenceException.class)
                    .hasMessage(message)
                    .hasCause(exception);
        }

        @Test
        @DisplayName("SQLTransientConnectionException이 cause에 포함되어 있으면 TemporaryChatPersistenceException으로 변환한다")
        void translateSqlTransientConnectionExceptionCause() {
            // given
            SQLTransientConnectionException cause =
                    new SQLTransientConnectionException("connection failure");

            RuntimeException exception = new RuntimeException(cause);

            // when
            ChatPersistenceException result =
                    MongoChatPersistenceExceptionTranslator.translate(message, exception);

            // then
            assertThat(result)
                    .isInstanceOf(TemporaryChatPersistenceException.class)
                    .hasMessage(message)
                    .hasCause(exception);
        }

        @Test
        @DisplayName("JDBCConnectionException이 cause에 포함되어 있으면 TemporaryChatPersistenceException으로 변환한다")
        void translateJdbcConnectionExceptionCause() {
            // given
            JDBCConnectionException cause =
                    new JDBCConnectionException("jdbc connection failure", null);

            RuntimeException exception = new RuntimeException(cause);

            // when
            ChatPersistenceException result =
                    MongoChatPersistenceExceptionTranslator.translate(message, exception);

            // then
            assertThat(result)
                    .isInstanceOf(TemporaryChatPersistenceException.class)
                    .hasMessage(message)
                    .hasCause(exception);
        }

        @Test
        @DisplayName("Connection is not available 메시지가 포함되어 있으면 TemporaryChatPersistenceException으로 변환한다")
        void translateConnectionNotAvailableMessage() {
            // given
            RuntimeException exception =
                    new RuntimeException("Connection is not available, request timed out");

            // when
            ChatPersistenceException result =
                    MongoChatPersistenceExceptionTranslator.translate(message, exception);

            // then
            assertThat(result)
                    .isInstanceOf(TemporaryChatPersistenceException.class)
                    .hasMessage(message)
                    .hasCause(exception);
        }

        @Test
        @DisplayName("일시적 장애가 아니면 ChatPersistenceException으로 변환한다")
        void translateUnexpectedException() {
            // given
            RuntimeException exception =
                    new RuntimeException("unexpected mongo failure");

            // when
            ChatPersistenceException result =
                    MongoChatPersistenceExceptionTranslator.translate(message, exception);

            // then
            assertThat(result)
                    .isExactlyInstanceOf(ChatPersistenceException.class)
                    .hasMessage(message)
                    .hasCause(exception);
        }

        @Test
        @DisplayName("DuplicateKeyException이어도 일반 translate에서는 ChatPersistenceException으로 변환한다")
        void translateDuplicateKeyExceptionAsGeneralPersistenceException() {
            // given
            DuplicateKeyException exception =
                    new DuplicateKeyException("duplicate key");

            // when
            ChatPersistenceException result =
                    MongoChatPersistenceExceptionTranslator.translate(message, exception);

            // then
            assertThat(result)
                    .isExactlyInstanceOf(ChatPersistenceException.class)
                    .hasMessage(message)
                    .hasCause(exception);
        }
    }

    @Nested
    @DisplayName("translateChatMessageSave")
    class TranslateChatMessageSaveTest {

        @Test
        @DisplayName("DuplicateKeyException이면 DuplicateChatMessageException으로 변환한다")
        void translateDuplicateKeyException() {
            // given
            ChatMessage rollbackTarget = chatMessage();

            DuplicateKeyException exception =
                    new DuplicateKeyException("duplicate message");

            // when
            ChatPersistenceException result =
                    MongoChatPersistenceExceptionTranslator.translateChatMessageSave(
                            rollbackTarget,
                            message,
                            exception
                    );

            // then
            assertThat(result)
                    .isInstanceOf(DuplicateChatMessageException.class)
                    .hasMessage(message)
                    .hasCause(exception);
        }

        @Test
        @DisplayName("DuplicateKeyException이 cause에 포함되어 있으면 DuplicateChatMessageException으로 변환한다")
        void translateDuplicateKeyExceptionCause() {
            // given
            ChatMessage rollbackTarget = chatMessage();

            DuplicateKeyException cause =
                    new DuplicateKeyException("duplicate message");

            RuntimeException exception = new RuntimeException(cause);

            // when
            ChatPersistenceException result =
                    MongoChatPersistenceExceptionTranslator.translateChatMessageSave(
                            rollbackTarget,
                            message,
                            exception
                    );

            // then
            assertThat(result)
                    .isInstanceOf(DuplicateChatMessageException.class)
                    .hasMessage(message)
                    .hasCause(exception);
        }

        @Test
        @DisplayName("일시적 장애이면 TemporaryChatPersistenceException으로 변환한다")
        void translateTemporaryFailure() {
            // given
            ChatMessage rollbackTarget = chatMessage();

            TransientDataAccessResourceException exception =
                    new TransientDataAccessResourceException("temporary mongo failure");

            // when
            ChatPersistenceException result =
                    MongoChatPersistenceExceptionTranslator.translateChatMessageSave(
                            rollbackTarget,
                            message,
                            exception
                    );

            // then
            assertThat(result)
                    .isInstanceOf(TemporaryChatPersistenceException.class)
                    .hasMessage(message)
                    .hasCause(exception);
        }

        @Test
        @DisplayName("일반 저장 실패이면 ChatMessagePersistException으로 변환한다")
        void translateUnexpectedSaveFailure() {
            // given
            ChatMessage rollbackTarget = chatMessage();

            RuntimeException exception =
                    new RuntimeException("unexpected save failure");

            // when
            ChatPersistenceException result =
                    MongoChatPersistenceExceptionTranslator.translateChatMessageSave(
                            rollbackTarget,
                            message,
                            exception
                    );

            // then
            assertThat(result)
                    .isInstanceOf(ChatMessagePersistException.class)
                    .hasMessage(message)
                    .hasCause(exception);
        }

        @Test
        @DisplayName("DuplicateKeyException과 일시적 장애가 함께 있으면 DuplicateChatMessageException을 우선한다")
        void translateDuplicateKeyBeforeTemporaryFailure() {
            // given
            ChatMessage rollbackTarget = chatMessage();

            TransientDataAccessResourceException temporaryCause =
                    new TransientDataAccessResourceException("temporary failure");

            DuplicateKeyException exception =
                    new DuplicateKeyException("duplicate message", temporaryCause);

            // when
            ChatPersistenceException result =
                    MongoChatPersistenceExceptionTranslator.translateChatMessageSave(
                            rollbackTarget,
                            message,
                            exception
                    );

            // then
            assertThat(result)
                    .isInstanceOf(DuplicateChatMessageException.class)
                    .hasMessage(message)
                    .hasCause(exception);
        }
    }

    private ChatMessage chatMessage() {
        return ChatMessage.builder()
                .id("100000000000000000000001")
                .roomId("000000000000000000000001")
                .writerId("writer-1")
                .content("hello")
                .createdAt(LocalDateTime.of(2026, 1, 1, 10, 0))
                .build();
    }
}
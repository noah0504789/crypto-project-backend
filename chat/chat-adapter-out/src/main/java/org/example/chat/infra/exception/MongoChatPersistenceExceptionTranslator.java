package org.example.chat.infra.exception;

import org.example.chat.chatmessage.domain.model.ChatMessage;
import org.example.chat.exception.ChatPersistenceException;
import org.example.chat.exception.TemporaryChatPersistenceException;
import org.example.chat.chatmessage.application.exception.DuplicateChatMessageException;
import org.example.chat.chatmessage.application.exception.ChatMessagePersistException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.data.mongodb.MongoTransactionException;
import org.hibernate.exception.JDBCConnectionException;

import java.sql.SQLTransientConnectionException;

public final class MongoChatPersistenceExceptionTranslator {

    private static final String CONNECTION_NOT_AVAILABLE = "Connection is not available";

    private MongoChatPersistenceExceptionTranslator() {
    }

    public static ChatPersistenceException translate(String message, Exception e) {
        if (isTemporaryFailure(e)) {
            return new TemporaryChatPersistenceException(message, e);
        }

        return new ChatPersistenceException(message, e);
    }

    public static ChatPersistenceException translateChatMessageSave(
            ChatMessage rollbackTarget,
            String message,
            Exception e
    ) {
        if (isDuplicateKey(e)) {
            return new DuplicateChatMessageException(message, e);
        }

        if (isTemporaryFailure(e)) {
            return new TemporaryChatPersistenceException(message, e);
        }

        return new ChatMessagePersistException(rollbackTarget, message, e);
    }

    private static boolean isDuplicateKey(Throwable t) {
        Throwable cur = t;

        while (cur != null) {
            if (cur instanceof DuplicateKeyException) {
                return true;
            }

            cur = cur.getCause();
        }

        return false;
    }

    private static boolean isTemporaryFailure(Throwable t) {
        Throwable cur = t;

        while (cur != null) {
            if (cur instanceof TransientDataAccessException) {
                return true;
            }

            if (cur instanceof MongoTransactionException) {
                return true;
            }

            if (cur instanceof SQLTransientConnectionException) {
                return true;
            }

            if (cur instanceof JDBCConnectionException) {
                return true;
            }

            String message = cur.getMessage();
            if (message != null && message.contains(CONNECTION_NOT_AVAILABLE)) {
                return true;
            }

            cur = cur.getCause();
        }

        return false;
    }
}
package org.example.chat.common.exception;

public class TemporaryChatCacheException extends ChatCacheException {

    public TemporaryChatCacheException(String message) {
        super(message);
    }

    public TemporaryChatCacheException(String message, Throwable cause) {
        super(message, cause);
    }
}
package org.example.chat.common.exception;

import org.example.common.exception.InvalidRequestException;

public class InvalidResourceRequestException extends InvalidRequestException {
    public InvalidResourceRequestException(String message) {
        super(message);
    }
}

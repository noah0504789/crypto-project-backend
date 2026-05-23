package org.example.user.exception;

import org.example.common.exception.InvalidRequestException;

public class InvalidResourceRequestException extends InvalidRequestException {
    public InvalidResourceRequestException(String message) {
        super(message);
    }
}

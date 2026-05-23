package org.example.user.domain.exception;

import org.example.common.exception.InvalidRequestException;

public class InvalidResourceRequestException extends InvalidRequestException {
    public InvalidResourceRequestException(String message) {
        super(message);
    }
}

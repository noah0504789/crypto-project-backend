package org.example.oauth2.exception;

import org.example.common.exception.InvalidRequestException;

public class InvalidTokenRequestException extends InvalidRequestException {
    public InvalidTokenRequestException(String message) {
        super(message);
    }
}

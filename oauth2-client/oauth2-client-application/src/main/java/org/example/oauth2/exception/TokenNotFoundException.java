package org.example.oauth2.exception;

import org.example.common.exception.ResourceNotFoundException;

public class TokenNotFoundException extends ResourceNotFoundException {
    public TokenNotFoundException(String message) {
        super(message);
    }
}

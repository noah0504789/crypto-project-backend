package org.example.oauth2.client.exception;

import org.example.common.exception.ResourceNotFoundException;

public class TokenNotFoundException extends ResourceNotFoundException {
    public TokenNotFoundException(String message) {
        super(message);
    }
}

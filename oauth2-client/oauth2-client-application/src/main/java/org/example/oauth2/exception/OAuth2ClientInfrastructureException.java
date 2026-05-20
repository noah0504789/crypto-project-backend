package org.example.oauth2.exception;

import org.example.common.exception.InfrastructureException;

public class OAuth2ClientInfrastructureException extends InfrastructureException {
    public OAuth2ClientInfrastructureException(String message) {
        super(message);
    }
}

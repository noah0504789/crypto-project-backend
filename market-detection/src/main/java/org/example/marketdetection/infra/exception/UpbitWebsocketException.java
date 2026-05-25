package org.example.marketdetection.infra.exception;

import org.example.common.exception.InfrastructureException;

public class UpbitWebsocketException extends InfrastructureException {

    public UpbitWebsocketException(String message, Throwable cause) {
        super(message, cause);
    }
}
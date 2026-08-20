package org.example.upbitconnector.infra.exception;

public class UpbitWebsocketException extends RuntimeException {

    public UpbitWebsocketException(String message, Throwable cause) {
        super(message, cause);
    }
}

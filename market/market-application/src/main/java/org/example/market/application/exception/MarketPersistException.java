package org.example.market.application.exception;

import org.example.market.exception.MarketException;

public class MarketPersistException extends MarketException {

    public MarketPersistException(String message, Throwable cause) {
        super(message, cause);
    }
}
package org.example.market.exception;

import org.example.common.exception.InfrastructureException;

public class MarketException extends InfrastructureException {

    public MarketException(String message, Throwable cause) {
        super(message, cause);
    }
}
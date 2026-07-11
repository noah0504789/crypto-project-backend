package org.example.configserver.vault.exception;

import org.example.common.exception.InfrastructureException;

public class ConfigInfrastructureException extends InfrastructureException {
    public ConfigInfrastructureException(String message) {
        super(message);
    }
}

package org.example.user.domain.exception;

import org.example.common.exception.InfrastructureException;

public class UserPersistenceException extends InfrastructureException {
    public UserPersistenceException(String message) {
        super(message);
    }

    public UserPersistenceException(String message, Throwable cause) {
        super(message, cause);
    }
}

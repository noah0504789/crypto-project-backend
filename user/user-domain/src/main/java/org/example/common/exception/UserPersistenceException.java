package org.example.common.exception;

public class UserPersistenceException extends InfrastructureException {
    public UserPersistenceException(String message) {
        super(message);
    }

    public UserPersistenceException(String message, Throwable cause) {
        super(message, cause);
    }
}

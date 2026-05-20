package org.example.common.exception;

public class VaultKeyNotFoundException extends ResourceNotFoundException {
    public VaultKeyNotFoundException(String message) {
        super(message);
    }
}

package org.example.configserver.vault.exception;

import org.example.common.exception.ResourceNotFoundException;

public class VaultKeyNotFoundException extends ResourceNotFoundException {
    public VaultKeyNotFoundException(String message) {
        super(message);
    }
}

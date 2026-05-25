package org.example.user.account.domain.exception;

import org.example.common.exception.ResourceNotFoundException;

import java.util.UUID;

public class UserNotFoundException extends ResourceNotFoundException {
    public UserNotFoundException(UUID publicId) {
        super("User not found. publicId="+publicId);
    }
}

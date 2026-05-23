package org.example.user.exception;

import org.example.common.exception.ResourceNotFoundException;

import java.util.UUID;

public class UserNotFoundException extends ResourceNotFoundException {
    public UserNotFoundException(UUID publicId) {
        super("User not found. publicId="+publicId);
    }
}

package org.example.common.exception;

import java.util.UUID;

public class UserNotFoundException extends ResourceNotFoundException {
    public UserNotFoundException(UUID publicId) {
        super("User not found. publicId="+publicId);
    }
}

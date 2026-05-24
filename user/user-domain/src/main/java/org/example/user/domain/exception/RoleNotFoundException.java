package org.example.user.domain.exception;

import org.example.common.exception.ResourceNotFoundException;
import org.example.user.domain.model.RoleEnum;

public class RoleNotFoundException extends ResourceNotFoundException {

    public RoleNotFoundException(RoleEnum role) {
        super("Role not found: " + role);
    }
}
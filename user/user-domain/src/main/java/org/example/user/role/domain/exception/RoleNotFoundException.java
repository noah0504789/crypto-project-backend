package org.example.user.role.domain.exception;

import org.example.common.exception.ResourceNotFoundException;
import org.example.user.role.domain.model.RoleEnum;

public class RoleNotFoundException extends ResourceNotFoundException {

    public RoleNotFoundException(RoleEnum role) {
        super("Role not found: " + role);
    }
}
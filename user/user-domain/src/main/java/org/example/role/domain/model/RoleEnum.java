package org.example.role.domain.model;

import org.example.common.enums.RoleKey;

public enum RoleEnum {

    USER, ADMIN;

    public String getName() {
        return RoleKey.PREFIX.value() + this.name();
    }
}

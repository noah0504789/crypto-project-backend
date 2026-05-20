package org.example.user.model;

import org.example.common.enums.RoleKey;

public enum RoleEnum {

    USER;

    public String getName() {
        return RoleKey.PREFIX.value() + this.name();
    }
}

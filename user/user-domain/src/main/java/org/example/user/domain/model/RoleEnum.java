package org.example.user.domain.model;

import org.example.common.enums.RoleKey;

public enum RoleEnum {

    USER;

    public String getName() {
        return RoleKey.PREFIX.value() + this.name();
    }
}

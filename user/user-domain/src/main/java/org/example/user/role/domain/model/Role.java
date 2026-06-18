package org.example.user.role.domain.model;

import lombok.*;

@Getter
@Builder(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Role {

    private Long id;
    private RoleEnum name;

    public static Role ofName(RoleEnum name) {
        return Role.builder()
                .name(name)
                .build();
    }

    public static Role rehydrate(Long id, RoleEnum name) {
        return Role.builder()
                .id(id)
                .name(name)
                .build();
    }
}
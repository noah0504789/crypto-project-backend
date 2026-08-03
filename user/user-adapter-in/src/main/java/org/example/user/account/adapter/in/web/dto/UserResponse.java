package org.example.user.account.adapter.in.web.dto;

import org.example.user.account.domain.model.User;

import java.time.Instant;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String nickname,
        String email,
        Instant createdAt
) {
    public static UserResponse fromEntity(User entity) {
        return new UserResponse(
                entity.getPublicId(),
                entity.getNickname(),
                entity.getEmail(),
                entity.createdAtInstant()
        );
    }
}

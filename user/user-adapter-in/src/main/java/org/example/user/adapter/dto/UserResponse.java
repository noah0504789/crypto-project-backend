package org.example.user.adapter.dto;

import org.example.user.model.domain.User;

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
                entity.toInstant()
        );
    }
}

package org.example.contract.user;

import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;

@Builder
public record UserResponse(
        String id,
        String sub,
        String nickname,
        String email,
        List<String> roles,
        LocalDateTime createdAt
) {
}

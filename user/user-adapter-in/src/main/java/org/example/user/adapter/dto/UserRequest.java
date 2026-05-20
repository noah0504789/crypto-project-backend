package org.example.user.adapter.dto;

public record UserRequest(
        String email,
        String nickname,
        String password
) {
}
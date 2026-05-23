package org.example.user.adapter.in.web.dto;

public record UserRequest(
        String email,
        String nickname,
        String password
) {
}
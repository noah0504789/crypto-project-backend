package org.example.user.account.adapter.in.web.dto;

public record UserRequest(
        String email,
        String nickname,
        String password
) {
}
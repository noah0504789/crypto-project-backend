package org.example.user.application.service;

public record LocalUserSignUpCommand(
        String email,
        String nickname,
        String password
) {
}

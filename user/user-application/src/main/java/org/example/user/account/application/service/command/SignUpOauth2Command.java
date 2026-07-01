package org.example.user.account.application.service.command;

public record SignUpOauth2Command(
        String sub,
        String email,
        String nickname
) {
}
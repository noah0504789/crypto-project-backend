package org.example.user.account.application.service.command;

public record SignUpLocalCommand(
        String email,
        String nickname,
        String password
) {
}
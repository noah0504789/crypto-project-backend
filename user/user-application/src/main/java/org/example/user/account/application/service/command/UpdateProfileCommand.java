package org.example.user.account.application.service.command;

import java.util.UUID;

public record UpdateProfileCommand(
        UUID publicId,
        String nickname
) {
}
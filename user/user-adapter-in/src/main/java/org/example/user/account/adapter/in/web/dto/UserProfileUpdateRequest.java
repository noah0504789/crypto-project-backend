package org.example.user.account.adapter.in.web.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.example.common.validation.NotBlankIfPresent;
import org.example.user.account.application.service.command.UpdateProfileCommand;
import org.example.user.account.application.validation.UniqueUserNickname;

import java.util.UUID;

public record UserProfileUpdateRequest(

        @UniqueUserNickname
        @NotBlankIfPresent
        @Size(min = 2, max = 20, message = "{user.profile.nickname.size}")
        @Pattern(regexp = "^[가-힣a-zA-Z0-9_]+$", message = "{user.profile.nickname.pattern}")
        String nickname
) {
    public boolean isEmpty() {
        return nickname == null;
    }

    public UpdateProfileCommand toCommand(UUID publicId) {
        return new UpdateProfileCommand(publicId, nickname);
    }
}
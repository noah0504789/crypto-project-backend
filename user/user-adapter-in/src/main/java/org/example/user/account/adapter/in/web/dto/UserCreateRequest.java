package org.example.user.account.adapter.in.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.example.user.account.application.service.command.SignUpLocalCommand;
import org.example.user.account.application.validation.UniqueUserNickname;

public record UserCreateRequest(

    @NotBlank(message = "{user.create.email.not-blank}")
    @Email(message = "{user.create.email.format}")
    String email,

    @UniqueUserNickname
    @NotBlank(message = "{user.create.nickname.not-blank}")
    @Size(min = 2, max = 20, message = "{user.create.nickname.size}")
    @Pattern(regexp = "^[가-힣a-zA-Z0-9_]+$", message = "{user.create.nickname.pattern}")
    String nickname,

    @NotBlank(message = "{user.create.password.not-blank}")
    @Size(min = 8, max = 60, message = "{user.create.password.size}")
    String password
) {

    public SignUpLocalCommand toCommand() {
        return new SignUpLocalCommand(email, nickname, password);
    }
}
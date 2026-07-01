package org.example.user.account.adapter.in.web;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.common.enums.HttpHeaderKey;
import org.example.user.account.adapter.in.web.dto.UserProfileUpdateRequest;
import org.example.user.account.application.port.in.UserCommandUseCase;
import org.example.user.account.application.port.in.UserQueryUseCase;
import org.example.user.account.domain.exception.UserNotFoundException;
import org.example.user.account.adapter.in.web.dto.UserCreateRequest;
import org.example.user.account.adapter.in.web.dto.UserResponse;
import org.example.user.account.domain.model.User;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("${api-path.user.base}")
@RequiredArgsConstructor
public class UserController {

    private final UserQueryUseCase userQueryUseCase;
    private final UserCommandUseCase userCommandUseCase;

    @PostMapping("${api-path.user.sign-up}")
    public ResponseEntity<Void> signUp(@RequestBody @Valid UserCreateRequest request) {
        userCommandUseCase.signUpLocal(request.toCommand());

        return ResponseEntity.created(URI.create("/")).build();
    }

    @GetMapping("${api-path.user.me}")
    public ResponseEntity<UserResponse> myProfile(@RequestHeader(HttpHeaderKey.USER_ID_VALUE) UUID publicId) {
        User entity = userQueryUseCase.findByPublicId(publicId).orElseThrow(() -> new UserNotFoundException(publicId));

        return ResponseEntity.ok(UserResponse.fromEntity(entity));
    }

    @GetMapping("${api-path.user.profile}")
    public ResponseEntity<UserResponse> otherProfile(@PathVariable("publicId") UUID publicId) {
        User entity = userQueryUseCase.findByPublicId(publicId).orElseThrow(() -> new UserNotFoundException(publicId));

        return ResponseEntity.ok().body(UserResponse.fromEntity(entity));
    }

    @PatchMapping("${api-path.user.me}")
    public ResponseEntity<Void> updateProfile(
            @RequestHeader(HttpHeaderKey.USER_ID_VALUE) UUID publicId,
            @RequestBody @Valid UserProfileUpdateRequest request
    ) {
        if (request.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        userCommandUseCase.updateProfile(request.toCommand(publicId));

        return ResponseEntity.noContent().build();
    }
}

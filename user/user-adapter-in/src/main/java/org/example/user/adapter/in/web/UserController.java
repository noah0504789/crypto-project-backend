package org.example.user.adapter.in.web;

import lombok.RequiredArgsConstructor;
import org.example.common.enums.HttpHeaderKey;
import org.example.user.domain.exception.UserNotFoundException;
import org.example.user.adapter.in.web.dto.UserRequest;
import org.example.user.adapter.in.web.dto.UserResponse;
import org.example.user.application.service.LocalUserSignUpService;
import org.example.user.application.service.UserQueryService;
import org.example.user.domain.model.User;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("${api-path.user.base:/user}")
@RequiredArgsConstructor
public class UserController {

    private final UserQueryService userQueryService;
    private final LocalUserSignUpService localUserSignUpService;

    @PostMapping("${api-path.user.sign-up:/sign-up}")
    public ResponseEntity<?> signUp(@RequestBody UserRequest request) {
        localUserSignUpService.signUp(
                request.email(),
                request.nickname(),
                request.password()
        );

        return ResponseEntity.created(URI.create("/home")).build();
    }

    @GetMapping("${api-path.user.me:/me}")
    public ResponseEntity<UserResponse> myProfile(@RequestHeader(HttpHeaderKey.USER_ID_VALUE) UUID publicId) {
        User entity = userQueryService.findByPublicId(publicId).orElseThrow(() -> new UserNotFoundException(publicId));

        return ResponseEntity.ok().body(UserResponse.fromEntity(entity));
    }

    @GetMapping("${api-path.user.profile:/{publicId}/profile}")
    public ResponseEntity<UserResponse> otherProfile(@PathVariable UUID publicId) {
        User entity = userQueryService.findByPublicId(publicId).orElseThrow(() -> new UserNotFoundException(publicId));

        return ResponseEntity.ok().body(UserResponse.fromEntity(entity));
    }
}

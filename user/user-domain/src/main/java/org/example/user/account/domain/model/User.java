package org.example.user.account.domain.model;

import lombok.*;
import org.example.common.time.ServiceTimeConverter;
import org.example.user.account.domain.exception.UserAccessDeniedException;
import org.example.user.role.domain.model.Role;
import org.example.user.role.domain.model.RoleEnum;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Getter
@Builder(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class User {

    private Long id;
    private UUID publicId;
    private String sub;
    private String email;
    private String nickname;
    private String password;

    @Builder.Default
    private Set<Role> roles = new HashSet<>();

    protected LocalDateTime createdAt;
    protected LocalDateTime updatedAt;

    public static User ofLocal(String email, String nickname, String encodedPassword) {
        return User.builder()
                .publicId(UUID.randomUUID())
                .email(email)
                .nickname(nickname)
                .password(encodedPassword)
                .roles(new HashSet<>())
                .build();
    }

    public static User ofOAuth2(String sub, String email, String nickname) {
        return User.builder()
                .publicId(UUID.randomUUID())
                .sub(sub)
                .email(email)
                .nickname(nickname)
                .roles(new HashSet<>())
                .build();
    }

    public static User rehydrate(
            Long id,
            UUID publicId,
            String sub,
            String email,
            String nickname,
            String password,
            Set<Role> roles,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        return User.builder()
                .id(id)
                .publicId(publicId)
                .sub(sub)
                .email(email)
                .nickname(nickname)
                .password(password)
                .roles(roles == null ? new HashSet<>() : roles)
                .createdAt(createdAt)
                .updatedAt(updatedAt)
                .build();
    }

    public static RoleEnum getDefaultRole() {
        return RoleEnum.USER;
    }

    public List<String> getRoleNames() {
        return roles.stream()
                .map(role -> role.getName().getName())
                .toList();
    }

    public boolean hasRole(Role role) {
        if (role == null || roles == null) {
            return false;
        }

        return roles.stream()
                .anyMatch(myRole -> myRole.getName() == role.getName());
    }

    public void addRole(Role role) {
        if (role == null) {
            throw new IllegalArgumentException("role must not be null");
        }

        if (this.roles == null) {
            this.roles = new HashSet<>();
        }

        if (hasRole(role)) {
            return;
        }

        this.roles.add(role);
    }

    public void validateOwner(UUID actorPublicId) {
        if (actorPublicId == null || !Objects.equals(publicId, actorPublicId)) {
            throw new UserAccessDeniedException(publicId, actorPublicId);
        }
    }

    public void updateNickname(String nickname) {
        this.nickname = nickname;
    }

    public Instant toInstant() {
        return ServiceTimeConverter.toInstant(createdAt);
    }
}
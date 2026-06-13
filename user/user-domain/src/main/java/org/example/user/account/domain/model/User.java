package org.example.user.account.domain.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.*;
import org.example.common.jpa.BaseEntity;
import org.example.common.id.annotation.SnowflakeId;
import org.example.common.time.ServiceZoneUtils;
import org.example.user.role.domain.model.Role;
import org.example.user.role.domain.model.RoleEnum;

import java.time.Instant;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "user")
@Getter
@Builder(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class User extends BaseEntity {

    @Id
    @SnowflakeId
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true, updatable = false)
    private UUID publicId;
    private String sub;

    @Column(nullable = false)
    private String email;

    private String nickname;
    private String password;

    @Builder.Default
    @OneToMany(
            mappedBy = "user",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    private Set<UserRole> roles = new HashSet<>();

    @PrePersist
    void init() {
        if (publicId == null) {
            publicId = UUID.randomUUID();
        }

        if (roles == null) {
            roles = new HashSet<>();
        }
    }

    public static User ofLocal(String email, String nickname, String encodedPassword) {
        return User.builder()
                .email(email)
                .nickname(nickname)
                .password(encodedPassword)
                .roles(new HashSet<>())
                .build();
    }

    public static User ofOAuth2(String sub, String email, String nickname) {
        return User.builder()
                .sub(sub)
                .email(email)
                .nickname(nickname)
                .roles(new HashSet<>())
                .build();
    }

    public static RoleEnum getDefaultRole() {
        return RoleEnum.USER;
    }

    public List<String> getRoleNames() {
        return roles.stream()
                .map(UserRole::getRoleName)
                .toList();
    }

    public boolean hasRole(Role role) {
        if (role == null || roles == null) {
            return false;
        }

        return roles.stream().anyMatch(userRole -> userRole.hasRole(role));
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

        this.roles.add(UserRole.of(this, role));
    }

    public void updateNickname(String nickname) {
        this.nickname = nickname;
    }

    public Instant toInstant() {
        return createdAt.atZone(ServiceZoneUtils.ZONE_ID).toInstant();
    }
}

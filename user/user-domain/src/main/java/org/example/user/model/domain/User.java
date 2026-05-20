package org.example.user.model.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.*;
import org.example.common.data.jpa.BaseEntity;
import org.example.user.model.RoleEnum;
import org.example.common.id.annotation.SnowflakeId;

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

    public static User ofLocal(String email, String nickname, String encodedPassword, Role role) {
        User user = User.builder()
                .email(email)
                .nickname(nickname)
                .password(encodedPassword)
                .roles(new HashSet<>())
                .build();

        user.roles.add(UserRole.ofUserAndRole(user, role));

        return user;
    }

    public static User ofOAuth2(String sub, String email, String nickname, Role role) {
        User user = User.builder()
                .sub(sub)
                .email(email)
                .nickname(nickname)
                .roles(new HashSet<>())
                .build();

        user.roles.add(UserRole.ofUserAndRole(user, role));

        return user;
    }

    public static RoleEnum getDefaultRole() {
        return RoleEnum.USER;
    }

    public List<String> getRoleNames() {
        return roles.stream()
                .map(UserRole::getRoleName)
                .toList();
    }

    public Instant toInstant() {
        return createdAt.atZone(ZoneId.systemDefault()).toInstant();
    }
}

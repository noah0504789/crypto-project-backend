package org.example.user.account.adapter.out;

import jakarta.persistence.*;
import lombok.*;
import org.example.common.id.annotation.SnowflakeId;
import org.example.common.jpa.BaseEntity;
import org.example.user.account.domain.model.User;
import org.example.user.role.adapter.out.JpaRole;
import org.example.user.role.domain.model.Role;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Entity
@Table(name = "user")
@Getter
@Builder(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class JpaUser extends BaseEntity {

    @Id
    @SnowflakeId
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true, updatable = false)
    private UUID publicId;
    private String sub;

    @Column(nullable = false)
    private String email;

    @Column(unique = true)
    private String nickname;
    private String password;

    @Builder.Default
    @OneToMany(
            mappedBy = "user",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    private Set<JpaUserRole> roles = new HashSet<>();

    @PrePersist
    void init() {
        if (publicId == null) {
            publicId = UUID.randomUUID();
        }

        if (roles == null) {
            roles = new HashSet<>();
        }
    }

    public static JpaUser fromDomain(
            User user,
            Function<Role, JpaRole> roleResolver
    ) {
        JpaUser jpaUser = JpaUser.builder()
                .id(user.getId())
                .publicId(user.getPublicId())
                .sub(user.getSub())
                .email(user.getEmail())
                .nickname(user.getNickname())
                .password(user.getPassword())
                .roles(new HashSet<>())
                .build();

        if (user.getRoles() != null) {
            user.getRoles().forEach(role -> {
                JpaRole jpaRole = roleResolver.apply(role);
                jpaUser.roles.add(JpaUserRole.of(jpaUser, jpaRole));
            });
        }

        return jpaUser;
    }

    public User toDomain() {
        return User.rehydrate(
                id,
                publicId,
                sub,
                email,
                nickname,
                password,
                roles == null ? new HashSet<>() : roles.stream().map(JpaUserRole::toDomainRole).collect(Collectors.toSet()),
                createdAt,
                updatedAt
        );
    }

    public void updateProfile(User user) {
        this.nickname = user.getNickname();
    }
}

package org.example.user.account.adapter.out;

import jakarta.persistence.*;
import lombok.*;
import org.example.common.id.annotation.SnowflakeId;
import org.example.common.jpa.BaseEntity;
import org.example.user.role.adapter.out.JpaRole;
import org.example.user.role.domain.model.Role;

@Entity
@Table(name = "user_role")
@Getter
@Builder(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class JpaUserRole extends BaseEntity {

    @Id
    @SnowflakeId
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private JpaUser user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "role_id", nullable = false)
    private JpaRole role;

    public static JpaUserRole of(
            JpaUser user,
            JpaRole role
    ) {
        return JpaUserRole.builder()
                .user(user)
                .role(role)
                .build();
    }

    public Role toDomainRole() {
        return role.toDomain();
    }
}
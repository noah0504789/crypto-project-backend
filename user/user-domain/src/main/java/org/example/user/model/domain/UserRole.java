package org.example.user.model.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.example.common.data.jpa.BaseEntity;
import org.example.common.id.annotation.SnowflakeId;

@Entity
@Table(
        name = "user_role",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_user_role_user_id_role_id",
                        columnNames = {"user_id", "role_id"}
                )
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserRole extends BaseEntity {

    @Id
    @SnowflakeId
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "role_id", nullable = false)
    private Role role;

    private UserRole(User user, Role role) {
        this.user = user;
        this.role = role;
    }

    public static UserRole ofUserAndRole(User user, Role role) {
        return new UserRole(user, role);
    }

    public String getRoleName() {
        return role.getName().getName();
    }
}
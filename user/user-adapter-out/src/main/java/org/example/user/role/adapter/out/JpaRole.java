package org.example.user.role.adapter.out;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.example.common.jpa.BaseEntity;
import org.example.user.role.domain.model.Role;
import org.example.user.role.domain.model.RoleEnum;

@Entity
@Table(name = "role")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class JpaRole extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, unique = true)
    private RoleEnum name;

    public Role toDomain() {
        return Role.rehydrate(id, name);
    }
}
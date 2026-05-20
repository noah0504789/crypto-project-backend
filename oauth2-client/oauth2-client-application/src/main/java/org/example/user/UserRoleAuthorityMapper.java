package org.example.user;

import org.example.common.enums.RoleKey;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

@Component
public class UserRoleAuthorityMapper {

    public Collection<? extends GrantedAuthority> toAuthorities(List<String> roles) {
        if (roles == null || roles.isEmpty()) {
            return List.of(new SimpleGrantedAuthority(RoleKey.DEFAULT_USER.value()));
        }

        List<SimpleGrantedAuthority> authorities = roles.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(role -> !role.isBlank())
                .map(this::normalizeRole)
                .distinct()
                .map(SimpleGrantedAuthority::new)
                .toList();

        if (authorities.isEmpty()) {
            return List.of(new SimpleGrantedAuthority(RoleKey.DEFAULT_USER.value()));
        }

        return authorities;
    }

    private String normalizeRole(String role) {
        return role.startsWith(RoleKey.PREFIX.value()) ? role : RoleKey.PREFIX.value() + role;
    }
}

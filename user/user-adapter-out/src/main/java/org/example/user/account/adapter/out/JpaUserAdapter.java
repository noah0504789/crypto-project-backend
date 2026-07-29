package org.example.user.account.adapter.out;

import lombok.RequiredArgsConstructor;
import org.example.user.account.application.port.out.UserPersistencePort;
import org.example.user.account.domain.model.User;
import org.example.user.role.adapter.out.JpaRoleRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class JpaUserAdapter implements UserPersistencePort {

    private final JpaUserRepository userRepository;
    private final JpaRoleRepository roleRepository;

    @Override
    public Optional<User> findByPublicId(UUID publicId) {
        return userRepository.findByPublicId(publicId)
                .map(JpaUser::toDomain);
    }

    @Override
    public Optional<User> findByEmailWithRoles(String email) {
        return userRepository.findByEmailWithRoles(email)
                .map(JpaUser::toDomain);
    }

    @Override
    public boolean existsByNickname(String nickname) {
        return userRepository.existsByNickname(nickname);
    }

    @Override
    public User save(User user) {
        JpaUser jpaUser = JpaUser.fromDomain(
                user,
                role -> roleRepository.findByName(role.getName())
                        .orElseThrow(() -> new IllegalArgumentException("Role not found: " + role.getName()))
        );

        return userRepository.save(jpaUser).toDomain();
    }

    @Override
    public void updateProfile(User user) {
        JpaUser jpaUser = userRepository.findById(user.getId())
                .orElseThrow(() -> new IllegalStateException("User not found: " + user.getId()));

        jpaUser.updateProfile(user);
    }
}

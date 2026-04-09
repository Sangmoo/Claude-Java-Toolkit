package io.github.claudetoolkit.ui.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    Optional<AppUser> findByUsernameAndEnabledTrue(String username);

    Optional<AppUser> findByUsername(String username);

    long countByRoleAndEnabledTrue(String role);

    List<AppUser> findAllByOrderByCreatedAtDesc();
}

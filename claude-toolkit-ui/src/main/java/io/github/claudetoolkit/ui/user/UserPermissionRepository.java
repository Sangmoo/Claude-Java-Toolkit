package io.github.claudetoolkit.ui.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserPermissionRepository extends JpaRepository<UserPermission, Long> {

    List<UserPermission> findByUserId(Long userId);

    Optional<UserPermission> findByUserIdAndFeatureKey(Long userId, String featureKey);

    void deleteByUserId(Long userId);
}

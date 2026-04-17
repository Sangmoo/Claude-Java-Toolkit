package io.github.claudetoolkit.ui.dashboard;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface UserDashboardLayoutRepository extends JpaRepository<UserDashboardLayout, Long> {

    List<UserDashboardLayout> findByUsername(String username);

    @Modifying
    @Transactional
    @Query("DELETE FROM UserDashboardLayout u WHERE u.username = :username")
    void deleteByUsername(@Param("username") String username);
}

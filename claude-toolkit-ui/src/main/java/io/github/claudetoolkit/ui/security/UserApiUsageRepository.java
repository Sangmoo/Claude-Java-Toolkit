package io.github.claudetoolkit.ui.security;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface UserApiUsageRepository extends JpaRepository<UserApiUsage, Long> {

    Optional<UserApiUsage> findByUsernameAndUsageDate(String username, LocalDate usageDate);

    @Query("SELECT SUM(u.requestCount) FROM UserApiUsage u " +
           "WHERE u.username = :username AND u.usageDate BETWEEN :start AND :end")
    Long sumRequestsBetween(@Param("username") String username,
                            @Param("start") LocalDate start,
                            @Param("end") LocalDate end);

    List<UserApiUsage> findByUsernameAndUsageDateBetween(String username, LocalDate start, LocalDate end);

    void deleteByUsageDateBefore(LocalDate cutoff);
}

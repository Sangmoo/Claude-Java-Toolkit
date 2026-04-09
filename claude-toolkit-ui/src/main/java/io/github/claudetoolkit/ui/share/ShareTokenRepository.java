package io.github.claudetoolkit.ui.share;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ShareTokenRepository extends JpaRepository<ShareToken, Long> {

    Optional<ShareToken> findByToken(String token);
}

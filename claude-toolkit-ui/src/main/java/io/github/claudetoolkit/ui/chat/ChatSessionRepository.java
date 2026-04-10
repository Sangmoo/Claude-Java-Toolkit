package io.github.claudetoolkit.ui.chat;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChatSessionRepository extends JpaRepository<ChatSession, Long> {

    List<ChatSession> findByUsernameOrderByUpdatedAtDesc(String username);

    long countByUsername(String username);
}

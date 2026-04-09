package io.github.claudetoolkit.ui.notification;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findByRecipientUsernameOrderByCreatedAtDesc(String username);

    List<Notification> findByRecipientUsernameAndIsReadFalseOrderByCreatedAtDesc(String username);

    long countByRecipientUsernameAndIsReadFalse(String username);

    @Modifying
    @Query("UPDATE Notification n SET n.isRead = true WHERE n.recipientUsername = ?1 AND n.isRead = false")
    void markAllReadByUsername(String username);
}

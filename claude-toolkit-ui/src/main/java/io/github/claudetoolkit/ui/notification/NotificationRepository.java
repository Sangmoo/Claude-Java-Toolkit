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

    /** v4.2.7 — 본인 수신 알림 전체 삭제. "전체 삭제" 버튼에서 호출. */
    @Modifying
    @Query("DELETE FROM Notification n WHERE n.recipientUsername = ?1")
    int deleteAllByRecipientUsername(String username);
}

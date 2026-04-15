package io.github.claudetoolkit.ui.notification;

import io.github.claudetoolkit.ui.history.ReviewHistory;
import io.github.claudetoolkit.ui.user.AppUser;
import io.github.claudetoolkit.ui.user.AppUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * v4.2.7 — VIEWER 권한 계정이 생성한 이력에 대해 REVIEWER/ADMIN 전체에게
 * "승인·거절 대기" 알림을 생성한다.
 *
 * <p>호출 지점 (모든 이력 저장 경로):
 * <ul>
 *   <li>{@code ReviewHistoryService.save(...)} — 일반 분석 결과 (동기 호출)</li>
 *   <li>{@code ReviewHistoryService.saveHarness(...)} — 하네스 리뷰 동기 경로</li>
 *   <li>{@code SseStreamController.saveHistory(...)} — 스트리밍 경로 (백그라운드 스레드)</li>
 *   <li>{@code PipelineExecutor.saveToReviewHistory(...)} — 파이프라인 결과 (비동기)</li>
 * </ul>
 *
 * <p>알림 링크는 `/review-requests?historyId={id}` 형식으로, 프론트가 해당 쿼리
 * 파라미터를 읽어 "팀 리뷰 요청" 페이지의 received 탭에서 해당 이력 카드로
 * 스크롤·하이라이트한다.
 */
@Service
public class PendingReviewNotifier {

    private static final Logger log = LoggerFactory.getLogger(PendingReviewNotifier.class);

    private final AppUserRepository    userRepository;
    private final NotificationPublisher notificationPublisher;

    public PendingReviewNotifier(AppUserRepository userRepository,
                                 NotificationPublisher notificationPublisher) {
        this.userRepository        = userRepository;
        this.notificationPublisher = notificationPublisher;
    }

    /**
     * 저장된 이력의 작성자가 VIEWER 라면 REVIEWER/ADMIN 전원에게 알림을 발행.
     * 작성자를 알 수 없거나(null) 역할이 VIEWER 가 아니면 아무 작업도 하지 않는다.
     *
     * <p>알림 생성 자체는 부가 작업이므로 예외가 발생해도 호출자에게 전파하지 않는다.
     * 이력 저장 트랜잭션과 독립적으로 동작하도록 REQUIRES_NEW 대신 같은 트랜잭션에
     * 참여하되, 모든 조회·알림 저장은 본인 트랜잭션 내에서 수행된다.
     */
    @Transactional
    public void notifyIfViewerCreated(ReviewHistory history) {
        if (history == null) return;
        final long historyId = history.getId();
        final String creator = history.getUsername();
        if (creator == null || creator.isEmpty()) return;

        try {
            AppUser creatorUser = userRepository.findByUsername(creator).orElse(null);
            if (creatorUser == null) return;
            // VIEWER 가 아니면 기존 동작과 동일 (알림 없음)
            if (!"VIEWER".equalsIgnoreCase(creatorUser.getRole())) return;

            // REVIEWER/ADMIN 활성 사용자 조회 — AppUserRepository 에 전용 메서드가
            // 없어 findAll 후 필터. 사용자 수가 크지 않아 부담 없음.
            List<AppUser> all = userRepository.findAll();
            if (all == null || all.isEmpty()) return;

            String typeLabel = history.getType() != null ? history.getType() : "REVIEW";
            String rawTitle  = history.getTitle() != null ? history.getTitle() : "";
            String titlePreview = rawTitle.length() > 60 ? rawTitle.substring(0, 60) + "..." : rawTitle;
            String notiTitle = creator + "님이 검토 대기 이력을 생성했습니다";
            String notiMsg   = "[" + typeLabel + "] " + (titlePreview.isEmpty() ? "(제목 없음)" : titlePreview);
            String notiLink  = "/review-requests?historyId=" + historyId;

            int sent = 0;
            for (AppUser u : all) {
                if (u == null || !u.isEnabled()) continue;
                String role = u.getRole();
                if (!("ADMIN".equalsIgnoreCase(role) || "REVIEWER".equalsIgnoreCase(role))) continue;
                // 본인에게는 알림 안 보냄 (VIEWER 본인 제외 — 이미 role 체크로 걸러지지만 방어적으로)
                if (creator.equals(u.getUsername())) continue;

                Notification noti = new Notification(
                        u.getUsername(),
                        "REVIEW_PENDING",
                        notiTitle,
                        notiMsg,
                        notiLink
                );
                notificationPublisher.publish(noti);
                sent++;
            }
            if (sent > 0) {
                log.info("[PendingReviewNotifier] historyId={} creator={} → {} reviewer(s) notified",
                        historyId, creator, sent);
            }
        } catch (Exception e) {
            // 알림 실패는 이력 저장의 성공/실패에 영향을 주지 않음
            log.warn("[PendingReviewNotifier] failed for historyId={}: {}", historyId, e.getMessage());
        }
    }
}

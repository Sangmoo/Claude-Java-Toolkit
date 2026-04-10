package io.github.claudetoolkit.ui.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 실시간 알림 SSE 발행자 (v2.8.0).
 *
 * <p>기존의 60초 폴링 방식을 SSE(Server-Sent Events) Push로 전환:
 * <ul>
 *   <li>각 사용자별로 여러 브라우저 탭의 {@link SseEmitter}를 구독자로 등록</li>
 *   <li>알림 생성 시 모든 구독 emitter에 즉시 push</li>
 *   <li>연결 실패/완료/타임아웃 시 자동 정리</li>
 *   <li>DB 저장도 함께 수행 (영속성 유지)</li>
 * </ul>
 *
 * <p>폴백: EventSource 연결 실패 시 클라이언트는 기존 60초 폴링 방식으로 자동 전환.
 */
@Component
public class NotificationPublisher {

    private static final Logger log = LoggerFactory.getLogger(NotificationPublisher.class);
    private static final long TIMEOUT_MS = 24L * 60 * 60 * 1000; // 24시간

    /** username → 활성 SSE emitter 목록 */
    private final ConcurrentHashMap<String, CopyOnWriteArrayList<SseEmitter>> subscribers =
            new ConcurrentHashMap<String, CopyOnWriteArrayList<SseEmitter>>();

    private final NotificationRepository notificationRepository;

    public NotificationPublisher(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    /**
     * 특정 사용자에 대해 새 SSE 구독자를 등록합니다.
     */
    public SseEmitter subscribe(String username) {
        final SseEmitter emitter = new SseEmitter(TIMEOUT_MS);

        CopyOnWriteArrayList<SseEmitter> list = subscribers.get(username);
        if (list == null) {
            list = new CopyOnWriteArrayList<SseEmitter>();
            CopyOnWriteArrayList<SseEmitter> existing = subscribers.putIfAbsent(username, list);
            if (existing != null) list = existing;
        }
        final CopyOnWriteArrayList<SseEmitter> finalList = list;
        list.add(emitter);

        // 자동 정리
        emitter.onCompletion(new Runnable() {
            public void run() { finalList.remove(emitter); }
        });
        emitter.onTimeout(new Runnable() {
            public void run() { finalList.remove(emitter); emitter.complete(); }
        });
        emitter.onError(new java.util.function.Consumer<Throwable>() {
            public void accept(Throwable t) { finalList.remove(emitter); }
        });

        // 초기 연결 확인 이벤트
        try {
            emitter.send(SseEmitter.event().name("connected").data("ok"));
        } catch (IOException e) {
            list.remove(emitter);
        }

        return emitter;
    }

    /**
     * 알림을 DB에 저장하고 해당 사용자의 모든 구독자에게 실시간 push.
     */
    @Transactional
    public Notification publish(Notification notification) {
        Notification saved = notificationRepository.save(notification);
        pushToSubscribers(saved.getRecipientUsername(), saved);
        return saved;
    }

    /**
     * 구독자에게 알림을 push. 전송 실패한 emitter는 자동 제거.
     */
    private void pushToSubscribers(String username, Notification noti) {
        CopyOnWriteArrayList<SseEmitter> list = subscribers.get(username);
        if (list == null || list.isEmpty()) return;

        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("id",        noti.getId());
        payload.put("type",      noti.getType());
        payload.put("typeIcon",  noti.getTypeIcon());
        payload.put("title",     noti.getTitle());
        payload.put("message",   noti.getMessage());
        payload.put("link",      noti.getLink());
        payload.put("createdAt", noti.getFormattedDate());

        List<SseEmitter> dead = new ArrayList<SseEmitter>();
        for (SseEmitter emitter : list) {
            try {
                emitter.send(SseEmitter.event().name("notification").data(payload));
            } catch (Exception e) {
                dead.add(emitter);
            }
        }
        if (!dead.isEmpty()) list.removeAll(dead);
    }

    /** 현재 활성 구독자 수 (디버깅/모니터링용) */
    public int getActiveSubscriberCount() {
        int total = 0;
        for (CopyOnWriteArrayList<SseEmitter> list : subscribers.values()) {
            total += list.size();
        }
        return total;
    }
}

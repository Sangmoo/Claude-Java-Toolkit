package io.github.claudetoolkit.ui.chat;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AI 채팅 대화 히스토리의 스레드 안전 저장소.
 *
 * <p>기존에 {@code HttpSession}에 직접 저장하던 방식은 백그라운드 SSE 스트리밍 스레드에서
 * 세션을 동시에 수정하여 레이스 컨디션 위험이 있었습니다. 이 저장소는
 * {@link ConcurrentHashMap} 기반으로 스레드 안전하게 동작하며, 세션은 조회 키로만 사용합니다.
 *
 * <p>마지막 접근 시각을 기록하여 24시간 동안 사용되지 않은 항목은 매일 새벽 자동 삭제됩니다.
 */
@Component
public class ChatHistoryStore {

    /** 세션 ID → 대화 히스토리 */
    private final ConcurrentHashMap<String, List<Map<String, String>>> store =
            new ConcurrentHashMap<String, List<Map<String, String>>>();

    /** 세션 ID → 마지막 접근 시각 (epoch millis) */
    private final ConcurrentHashMap<String, Long> lastAccess =
            new ConcurrentHashMap<String, Long>();

    /** TTL: 24시간 */
    private static final long TTL_MILLIS = 24L * 60 * 60 * 1000;

    /**
     * 특정 세션의 대화 히스토리를 조회합니다.
     * @return 히스토리 리스트 (없으면 빈 리스트 반환, null 아님)
     */
    public List<Map<String, String>> getHistory(String sessionId) {
        if (sessionId == null) return new ArrayList<Map<String, String>>();
        lastAccess.put(sessionId, System.currentTimeMillis());
        List<Map<String, String>> history = store.get(sessionId);
        if (history == null) return new ArrayList<Map<String, String>>();
        // 호출자가 안전하게 수정할 수 있도록 복사본 반환
        return new ArrayList<Map<String, String>>(history);
    }

    /**
     * 특정 세션의 대화 히스토리를 저장(또는 교체)합니다.
     */
    public void putHistory(String sessionId, List<Map<String, String>> history) {
        if (sessionId == null) return;
        if (history == null) {
            store.remove(sessionId);
        } else {
            // 방어적 복사
            store.put(sessionId, new ArrayList<Map<String, String>>(history));
        }
        lastAccess.put(sessionId, System.currentTimeMillis());
    }

    /**
     * 특정 세션의 히스토리를 삭제합니다.
     */
    public void clear(String sessionId) {
        if (sessionId == null) return;
        store.remove(sessionId);
        lastAccess.remove(sessionId);
    }

    /**
     * 24시간 이상 접근되지 않은 히스토리 자동 정리 — 매일 새벽 4시 (KST).
     */
    @Scheduled(cron = "0 0 4 * * ?")
    public void cleanupStale() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<String, Long>> it = lastAccess.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Long> entry = it.next();
            if (now - entry.getValue() > TTL_MILLIS) {
                store.remove(entry.getKey());
                it.remove();
            }
        }
    }
}

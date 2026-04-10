package io.github.claudetoolkit.ui.pipeline;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 파이프라인 실행 SSE 브로커 (v2.9.5).
 *
 * <p>{@link io.github.claudetoolkit.ui.notification.NotificationPublisher}와 유사한 구조:
 * {@code executionId → List<SseEmitter>}로 구독자 관리, 실행 엔진이 {@link #push(Long, String, Object)}
 * 호출 시 모든 구독자에게 이벤트 전파.
 */
@Component
public class PipelineStreamBroker {

    private static final Logger log = LoggerFactory.getLogger(PipelineStreamBroker.class);
    private static final long TIMEOUT_MS = 30L * 60 * 1000; // 30분

    private final ConcurrentHashMap<Long, CopyOnWriteArrayList<SseEmitter>> subscribers =
            new ConcurrentHashMap<Long, CopyOnWriteArrayList<SseEmitter>>();

    public SseEmitter subscribe(Long executionId) {
        final SseEmitter emitter = new SseEmitter(TIMEOUT_MS);

        CopyOnWriteArrayList<SseEmitter> list = subscribers.get(executionId);
        if (list == null) {
            list = new CopyOnWriteArrayList<SseEmitter>();
            CopyOnWriteArrayList<SseEmitter> existing = subscribers.putIfAbsent(executionId, list);
            if (existing != null) list = existing;
        }
        final CopyOnWriteArrayList<SseEmitter> finalList = list;
        list.add(emitter);

        emitter.onCompletion(new Runnable() {
            public void run() { finalList.remove(emitter); }
        });
        emitter.onTimeout(new Runnable() {
            public void run() { finalList.remove(emitter); emitter.complete(); }
        });
        emitter.onError(new java.util.function.Consumer<Throwable>() {
            public void accept(Throwable t) { finalList.remove(emitter); }
        });

        try {
            emitter.send(SseEmitter.event().name("connected").data("ok"));
        } catch (IOException e) {
            list.remove(emitter);
        }
        return emitter;
    }

    /**
     * 실행 이벤트를 해당 구독자 전체에 push.
     */
    public void push(Long executionId, String eventName, Object data) {
        CopyOnWriteArrayList<SseEmitter> list = subscribers.get(executionId);
        if (list == null || list.isEmpty()) return;

        List<SseEmitter> dead = new ArrayList<SseEmitter>();
        for (SseEmitter emitter : list) {
            try {
                emitter.send(SseEmitter.event().name(eventName).data(data));
            } catch (Exception e) {
                dead.add(emitter);
            }
        }
        if (!dead.isEmpty()) list.removeAll(dead);
    }

    /**
     * 실행 완료 후 모든 구독자 close.
     */
    public void closeAll(Long executionId) {
        CopyOnWriteArrayList<SseEmitter> list = subscribers.remove(executionId);
        if (list == null) return;
        for (SseEmitter emitter : list) {
            try { emitter.complete(); } catch (Exception ignored) {}
        }
    }
}

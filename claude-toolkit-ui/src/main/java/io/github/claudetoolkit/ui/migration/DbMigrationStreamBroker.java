package io.github.claudetoolkit.ui.migration;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * DB 마이그레이션 작업 SSE 브로커 (v2.9.5).
 */
@Component
public class DbMigrationStreamBroker {

    private static final long TIMEOUT_MS = 60L * 60 * 1000; // 1시간

    private final ConcurrentHashMap<Long, CopyOnWriteArrayList<SseEmitter>> subscribers =
            new ConcurrentHashMap<Long, CopyOnWriteArrayList<SseEmitter>>();

    public SseEmitter subscribe(Long jobId) {
        final SseEmitter emitter = new SseEmitter(TIMEOUT_MS);
        CopyOnWriteArrayList<SseEmitter> list = subscribers.get(jobId);
        if (list == null) {
            list = new CopyOnWriteArrayList<SseEmitter>();
            CopyOnWriteArrayList<SseEmitter> existing = subscribers.putIfAbsent(jobId, list);
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

    public void push(Long jobId, String eventName, Object data) {
        CopyOnWriteArrayList<SseEmitter> list = subscribers.get(jobId);
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

    public void closeAll(Long jobId) {
        CopyOnWriteArrayList<SseEmitter> list = subscribers.remove(jobId);
        if (list == null) return;
        for (SseEmitter e : list) {
            try { e.complete(); } catch (Exception ignored) {}
        }
    }
}

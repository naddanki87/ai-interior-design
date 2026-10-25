package com.aiinterior.factory.session;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** Server-Sent Events: tells browsers a session changed; they then fetch the fresh snapshot. */
@Component
public class SessionEvents {

    private final Map<String, List<SseEmitter>> emitters = new ConcurrentHashMap<>();
    private final Map<String, Long> lastSent = new ConcurrentHashMap<>();

    public SseEmitter subscribe(String sessionId) {
        SseEmitter emitter = new SseEmitter(0L);
        emitters.computeIfAbsent(sessionId, k -> new CopyOnWriteArrayList<>()).add(emitter);
        Runnable remove = () -> emitters.getOrDefault(sessionId, List.of()).remove(emitter);
        emitter.onCompletion(remove);
        emitter.onTimeout(remove);
        emitter.onError(e -> remove.run());
        send(emitter, sessionId, "hello");
        return emitter;
    }

    public void changed(String sessionId) {
        // Coalesce bursts: at most one event per 100 ms per session.
        long now = System.currentTimeMillis();
        Long prev = lastSent.get(sessionId);
        if (prev != null && now - prev < 100) {
            return;
        }
        lastSent.put(sessionId, now);
        for (SseEmitter e : emitters.getOrDefault(sessionId, List.of())) {
            send(e, sessionId, "changed");
        }
    }

    /** Always deliver the final state, even inside the coalescing window. */
    public void flush(String sessionId) {
        lastSent.remove(sessionId);
        changed(sessionId);
    }

    private void send(SseEmitter e, String sessionId, String name) {
        try {
            e.send(SseEmitter.event().name(name).data(Map.of("sessionId", sessionId, "at", System.currentTimeMillis())));
        } catch (IOException | IllegalStateException ex) {
            emitters.getOrDefault(sessionId, List.of()).remove(e);
        }
    }
}

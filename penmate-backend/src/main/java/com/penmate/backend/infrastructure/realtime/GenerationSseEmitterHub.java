package com.penmate.backend.infrastructure.realtime;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class GenerationSseEmitterHub {

    private static final long SSE_TIMEOUT_MS = 10 * 60 * 1000L;

    private final ConcurrentHashMap<Long, Set<SseEmitter>> emittersByTask = new ConcurrentHashMap<>();

    public SseEmitter create(Long taskId) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        emittersByTask.computeIfAbsent(taskId, k -> ConcurrentHashMap.newKeySet()).add(emitter);

        emitter.onCompletion(() -> remove(taskId, emitter));
        emitter.onTimeout(() -> remove(taskId, emitter));
        emitter.onError(ex -> remove(taskId, emitter));

        try {
            emitter.send(SseEmitter.event().name("connected").data(Map.of("taskId", taskId)));
        } catch (IOException e) {
            remove(taskId, emitter);
        }
        return emitter;
    }

    public void publish(Long taskId, String eventName, Object data) {
        Set<SseEmitter> emitters = emittersByTask.get(taskId);
        if (emitters == null || emitters.isEmpty()) {
            return;
        }
        emitters.removeIf(emitter -> {
            try {
                emitter.send(SseEmitter.event().name(eventName).data(data));
                return false;
            } catch (IOException e) {
                return true;
            }
        });
        if (emitters.isEmpty()) {
            emittersByTask.remove(taskId);
        }
    }

    public void complete(Long taskId) {
        Set<SseEmitter> emitters = emittersByTask.remove(taskId);
        if (emitters == null) {
            return;
        }
        for (SseEmitter emitter : emitters) {
            emitter.complete();
        }
    }

    private void remove(Long taskId, SseEmitter emitter) {
        Set<SseEmitter> emitters = emittersByTask.get(taskId);
        if (emitters == null) {
            return;
        }
        emitters.remove(emitter);
        if (emitters.isEmpty()) {
            emittersByTask.remove(taskId);
        }
    }
}


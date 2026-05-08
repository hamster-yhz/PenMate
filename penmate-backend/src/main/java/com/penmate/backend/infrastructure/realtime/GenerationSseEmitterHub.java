package com.penmate.backend.infrastructure.realtime;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * 生成任务 SSE 连接中心。
 * <p>按任务维度维护 SSE emitter 集合，负责连接创建、事件发布与连接关闭回收。</p>
 */
@Component
@Slf4j
public class GenerationSseEmitterHub {

    private static final long SSE_TIMEOUT_MS = 10 * 60 * 1000L;
    private static final int MAX_BUFFERED_EVENTS_PER_TASK = 256;
    private static final long COMPLETED_STATE_TTL_MS = 2 * 60 * 1000L;

    private final ConcurrentHashMap<Long, TaskStreamState> statesByTask = new ConcurrentHashMap<>();

    private record StreamEvent(String eventName, Object data) {
    }

    private static final class TaskStreamState {
        private final Set<SseEmitter> emitters = ConcurrentHashMap.newKeySet();
        private final ConcurrentLinkedDeque<StreamEvent> bufferedEvents = new ConcurrentLinkedDeque<>();
        private volatile boolean completed;
        private volatile long completedAtEpochMs;

        synchronized void addEmitter(SseEmitter emitter) {
            emitters.add(emitter);
        }

        synchronized void removeEmitter(SseEmitter emitter) {
            emitters.remove(emitter);
        }

        synchronized int emitterCount() {
            return emitters.size();
        }

        synchronized boolean hasEmitter() {
            return !emitters.isEmpty();
        }

        synchronized void bufferEvent(StreamEvent event) {
            bufferedEvents.addLast(event);
            while (bufferedEvents.size() > MAX_BUFFERED_EVENTS_PER_TASK) {
                bufferedEvents.pollFirst();
            }
        }

        synchronized java.util.List<StreamEvent> snapshotBufferedEvents() {
            return new ArrayList<>(bufferedEvents);
        }

        synchronized void clearBufferedEvents() {
            bufferedEvents.clear();
        }

        synchronized java.util.List<SseEmitter> snapshotEmitters() {
            return new ArrayList<>(emitters);
        }

        synchronized void markCompleted() {
            completed = true;
            completedAtEpochMs = Instant.now().toEpochMilli();
        }

        synchronized boolean isCompleted() {
            return completed;
        }

        synchronized long completedAtEpochMs() {
            return completedAtEpochMs;
        }
    }

    /**
     * 创建并注册任务 SSE emitter。
     * <p>流程：创建 emitter -> 注册生命周期回调 -> 发送 connected 事件初始化连接。</p>
     */
    public SseEmitter create(Long taskId) {
        cleanupExpiredCompletedStates();
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        TaskStreamState state = statesByTask.computeIfAbsent(taskId, k -> new TaskStreamState());
        state.addEmitter(emitter);
        log.info("SSE emitter created: taskId={}, emitterCount={}", taskId, state.emitterCount());

        emitter.onCompletion(() -> remove(taskId, emitter));
        emitter.onTimeout(() -> remove(taskId, emitter));
        emitter.onError(ex -> remove(taskId, emitter));

        try {
            emitter.send(SseEmitter.event().name("connected").data(Map.of("taskId", taskId)));
            java.util.List<StreamEvent> bufferedEvents = state.snapshotBufferedEvents();
            log.info("SSE emitter connected: taskId={}, bufferedReplayCount={}, completed={}, emitterCount={}",
                    taskId, bufferedEvents.size(), state.isCompleted(), state.emitterCount());
            for (StreamEvent event : bufferedEvents) {
                emitter.send(SseEmitter.event().name(event.eventName()).data(event.data()));
            }
            if (state.isCompleted()) {
                emitter.complete();
            }
        } catch (IOException e) {
            log.warn("SSE emitter init failed: taskId={}, error={}", taskId, e.getMessage());
            remove(taskId, emitter);
        }
        return emitter;
    }

    /**
     * 向任务所有 SSE 连接发布事件。
     * <p>流程：查找任务 emitter 集合 -> 广播事件 -> 移除发送失败连接 -> 空集合回收。</p>
     */
    public void publish(Long taskId, String eventName, Object data) {
        cleanupExpiredCompletedStates();
        TaskStreamState state = statesByTask.computeIfAbsent(taskId, k -> new TaskStreamState());
        state.bufferEvent(new StreamEvent(eventName, data));

        java.util.List<SseEmitter> emitters = state.snapshotEmitters();
        if (emitters.isEmpty()) {
            log.info("SSE publish buffered: no active emitter yet. taskId={}, eventName={}", taskId, eventName);
            return;
        }
        log.debug("SSE publish: taskId={}, eventName={}, emitterCount={}", taskId, eventName, emitters.size());
        emitters.forEach(emitter -> {
            try {
                emitter.send(SseEmitter.event().name(eventName).data(data));
            } catch (IOException e) {
                log.warn("SSE publish failed: taskId={}, eventName={}, error={}", taskId, eventName, e.getMessage());
                remove(taskId, emitter);
            }
        });
    }

    /**
     * 主动完成任务 SSE 通道。
     * <p>流程：移除任务 emitter 集合并逐个调用 complete。</p>
     */
    public void complete(Long taskId) {
        TaskStreamState state = statesByTask.computeIfAbsent(taskId, k -> new TaskStreamState());
        state.markCompleted();
        java.util.List<SseEmitter> emitters = state.snapshotEmitters();
        if (emitters.isEmpty()) {
            log.info("SSE complete marked: taskId={}, no active emitter, waiting late subscriber replay", taskId);
            return;
        }
        log.info("SSE complete: taskId={}, closingEmitters={}", taskId, emitters.size());
        for (SseEmitter emitter : emitters) {
            emitter.complete();
        }
    }

    /**
     * 从任务 emitter 集合移除单个连接。
     * <p>用于 completion/timeout/error 回调后的资源回收。</p>
     */
    private void remove(Long taskId, SseEmitter emitter) {
        TaskStreamState state = statesByTask.get(taskId);
        if (state == null) {
            return;
        }
        state.removeEmitter(emitter);
        if (!state.hasEmitter() && !state.isCompleted()) {
            statesByTask.remove(taskId);
        }
    }

    private void cleanupExpiredCompletedStates() {
        long now = Instant.now().toEpochMilli();
        statesByTask.entrySet().removeIf(entry -> {
            TaskStreamState state = entry.getValue();
            if (!state.isCompleted()) {
                return false;
            }
            if (state.hasEmitter()) {
                return false;
            }
            long completedAt = state.completedAtEpochMs();
            return completedAt > 0 && now - completedAt > COMPLETED_STATE_TTL_MS;
        });
    }
}


package com.penmate.backend.application.agent.run;

import com.penmate.backend.application.agent.llm.AgentLlmStreamEvent;
import com.penmate.backend.application.agent.llm.AgentLlmTurnResponse;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;

@Service
public class AgentStreamingMessageService {

    private static final long FLUSH_INTERVAL_NANOS = 100_000_000L;
    private static final int MAX_PENDING_CHARS = 256;

    private final AgentRunEventPublisher events;
    private final AgentPartialMessageCheckpointStore checkpoints;

    public AgentStreamingMessageService(AgentRunEventPublisher events,
                                        AgentPartialMessageCheckpointStore checkpoints) {
        this.events = events;
        this.checkpoints = checkpoints;
    }

    public StreamSession open(Long runId, Long turnId, int llmTurnIndex, String existingText) {
        return new StreamSession(runId, turnId, llmTurnIndex, existingText);
    }

    public final class StreamSession {
        private final Long runId;
        private final Long turnId;
        private final int llmTurnIndex;
        private final String prefix;
        private final StringBuilder accumulated;
        private final StringBuilder pending = new StringBuilder();
        private final ProcessAccumulator commentary = new ProcessAccumulator(
                "model.commentary.snapshot", "model.commentary.completed");
        private final ProcessAccumulator reasoningSummary = new ProcessAccumulator(
                "model.reasoning_summary.snapshot", "model.reasoning_summary.completed");
        private long lastFlushNanos = 0L;

        private StreamSession(Long runId, Long turnId, int llmTurnIndex, String existingText) {
            this.runId = runId;
            this.turnId = turnId;
            this.llmTurnIndex = llmTurnIndex;
            this.prefix = existingText == null ? "" : existingText;
            this.accumulated = new StringBuilder(prefix);
        }

        public synchronized void accept(String text) {
            if (text == null || text.isEmpty()) return;
            accumulated.append(text);
            pending.append(text);
            long now = System.nanoTime();
            if (lastFlushNanos == 0L || now - lastFlushNanos >= FLUSH_INTERVAL_NANOS
                    || pending.length() >= MAX_PENDING_CHARS) {
                flush(now);
            }
        }

        public synchronized void acceptEvent(AgentLlmStreamEvent event) {
            if (event instanceof AgentLlmStreamEvent.OutputTextDelta delta) {
                accept(delta.text());
            } else if (event instanceof AgentLlmStreamEvent.CommentaryDelta delta) {
                commentary.accept(delta.text());
            } else if (event instanceof AgentLlmStreamEvent.ReasoningSummaryDelta delta) {
                reasoningSummary.accept(delta.text());
            }
        }

        public synchronized void complete(AgentLlmTurnResponse response) {
            AgentLlmTurnResponse resolved = response == null
                    ? new AgentLlmTurnResponse("stop", "", java.util.List.of(), "{}")
                    : response;
            complete(resolved.assistantText());
            commentary.complete(resolved.commentaryText());
            reasoningSummary.complete(resolved.reasoningSummary());
        }

        public synchronized void complete(String finalTurnText) {
            String resolvedTurnText = finalTurnText == null ? "" : finalTurnText;
            String expected = prefix + resolvedTurnText;
            if (accumulated.toString().equals(prefix) && !resolvedTurnText.isEmpty()) {
                accumulated.append(resolvedTurnText);
                pending.append(resolvedTurnText);
                flush(System.nanoTime());
                return;
            }
            if (!expected.contentEquals(accumulated)) {
                accumulated.setLength(0);
                accumulated.append(expected);
                pending.setLength(0);
                saveSnapshot();
                events.broadcastOnly(runId, "message.snapshot", Map.of(
                        "channel", "final",
                        "llmTurnIndex", llmTurnIndex,
                        "text", accumulated.toString(),
                        "offset", accumulated.length()
                ), -1L);
                return;
            }
            flush(System.nanoTime());
        }

        public synchronized void flushPending() {
            flush(System.nanoTime());
            commentary.flushSnapshot(true);
            reasoningSummary.flushSnapshot(true);
        }

        private void flush(long now) {
            if (pending.isEmpty()) return;
            int startOffset = accumulated.length() - pending.length();
            String chunk = pending.toString();
            pending.setLength(0);
            saveSnapshot();
            events.broadcastOnly(runId, "message.delta", Map.of(
                    "channel", "final",
                    "llmTurnIndex", llmTurnIndex,
                    "text", chunk,
                    "offset", startOffset,
                    "messageLength", accumulated.length()
            ), -1L);
            lastFlushNanos = now;
        }

        private void saveSnapshot() {
            checkpoints.save(new AgentPartialMessageCheckpointStore.Snapshot(
                    runId, turnId, accumulated.toString(), accumulated.length(), Instant.now()));
        }

        private final class ProcessAccumulator {
            private final String snapshotEvent;
            private final String completedEvent;
            private final StringBuilder accumulated = new StringBuilder();
            private int lastSnapshotLength;
            private long lastSnapshotNanos;

            private ProcessAccumulator(String snapshotEvent, String completedEvent) {
                this.snapshotEvent = snapshotEvent;
                this.completedEvent = completedEvent;
            }

            private void accept(String text) {
                if (text == null || text.isEmpty()) return;
                accumulated.append(text);
                flushSnapshot(false);
            }

            private void flushSnapshot(boolean force) {
                if (accumulated.length() == lastSnapshotLength) return;
                long now = System.nanoTime();
                if (!force && lastSnapshotNanos != 0L
                        && now - lastSnapshotNanos < FLUSH_INTERVAL_NANOS
                        && accumulated.length() - lastSnapshotLength < MAX_PENDING_CHARS) return;
                events.broadcastOnly(runId, snapshotEvent, Map.of(
                        "llmTurnIndex", llmTurnIndex,
                        "text", accumulated.toString(),
                        "offset", accumulated.length(),
                        "visibility", "public"
                ), -1L);
                lastSnapshotLength = accumulated.length();
                lastSnapshotNanos = now;
            }

            private void complete(String finalText) {
                String resolved = finalText == null ? "" : finalText;
                if (!resolved.isEmpty() && !resolved.equals(accumulated.toString())) {
                    accumulated.setLength(0);
                    accumulated.append(resolved);
                }
                if (accumulated.isEmpty()) return;
                flushSnapshot(true);
                events.publish(runId, completedEvent, Map.of(
                        "llmTurnIndex", llmTurnIndex,
                        "text", accumulated.toString(),
                        "visibility", "public"
                ));
            }
        }
    }
}

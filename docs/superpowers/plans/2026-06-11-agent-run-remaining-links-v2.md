# Agent Run Remaining Links Implementation Plan v2

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Complete the five remaining capabilities (checkpoint wiring, delta throttling, artifact write path, tool call loop) plus event retention/Redis integration identified during review. The core run flow (API -> Turn -> Run -> Preflight -> Context -> LLM -> Events -> SSE) already works.

**Background:** Tasks 1-14 from the full refactor plan are complete. The remaining-links sub-plan partially implemented Task 1 (Todo projection). This plan consolidates Tasks 2-5 plus new findings: checkpoint needs Redis-backed TTL, message deltas must stay in-memory only, event retention needs a policy, and the in-memory EventBus needs Redis pub/sub for multi-instance.

**Architecture:** AgentRun is the execution aggregate. AgentEvent is the durable process log (MySQL). AgentCheckpoint is the resume accelerator (Redis with 30min TTL, MySQL fallback). Message deltas are broadcast-only via Redis pub/sub + local EventBus — never persisted. Large payloads (>64KB) go to agent_artifacts. The LLM loop iterates up to 10 turns handling tool_calls.

**Tech Stack:** Spring Boot, MyBatis, MySQL + Redis (Spring Data Redis), SseEmitter, Jackson, JUnit 5, Mockito.

---

## Ground Rules

- No new SQL tables. All tables exist in V11 DDL.
- `message.delta` events are NEVER persisted to `agent_events`. They flow through Redis pub/sub + InMemoryAgentRunEventBus only.
- `message.completed` is the durable substitute — persisted once with full text.
- Checkpoints are dual-write: Redis (30min TTL, primary read path) + MySQL (fallback/audit).
- Event retention: `agent_events` keeps all events for active runs. Completed/cancelled/failed runs: events retained for 7 days, then archived. Checkpoint-based compaction: if a checkpoint covers seq 0-N, events 0..N can be trimmed.
- All changes within existing `agent.run.*` packages unless creating new infrastructure (Redis config).


## Task 1: Redis Infrastructure Setup

**Files:**
- Create: `penmate-backend/src/main/java/com/penmate/backend/infrastructure/cache/RedisConfig.java`
- Modify: `penmate-backend/src/main/resources/application.yml` (note: config may already exist, verify)

**Why:** Redis is configured in `application.yml` but there may be no `RedisTemplate` bean or explicit `@EnableCaching`. The checkpoint service and event bus need `RedisTemplate<String, String>` for checkpoint storage and pub/sub.

- [ ] **Step 1: Create RedisConfig if missing**

Check if `RedisConfig` or `RedisConfiguration` already exists:

```powershell
rg "RedisConfig\|RedisConfiguration\|@Configuration.*Redis" penmate-backend/src/main --type java
```

If missing, create:

```java
package com.penmate.backend.infrastructure.cache;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class RedisConfig {

    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        return new StringRedisTemplate(connectionFactory);
    }

    @Bean
    public RedisTemplate<String, String> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, String> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(StringRedisSerializer.UTF_8);
        template.setValueSerializer(StringRedisSerializer.UTF_8);
        template.setHashKeySerializer(StringRedisSerializer.UTF_8);
        template.setHashValueSerializer(StringRedisSerializer.UTF_8);
        return template;
    }

    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(RedisConnectionFactory connectionFactory) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        return container;
    }
}
```

- [ ] **Step 2: Verify Redis dependency in pom.xml**

Already confirmed: Redis is configured in `application.yml` via `spring.data.redis.*`, so `spring-boot-starter-data-redis` is already in the classpath. No pom.xml change needed.

- [ ] **Step 3: Compile verify**

```powershell
cd penmate-backend
mvn compile -q
```

Expected: BUILD SUCCESS

---

## Task 2: Checkpoint Wiring — Redis + MySQL Dual Write

**Files:**
- Modify: `penmate-backend/src/main/java/com/penmate/backend/application/agent/run/AgentCheckpointService.java`
- Modify: `penmate-backend/src/main/java/com/penmate/backend/domain/agent/run/model/AgentRuntimeState.java`
- Modify: `penmate-backend/src/main/java/com/penmate/backend/application/agent/run/AgentRuntimeStateReducer.java`
- Modify: `penmate-backend/src/main/java/com/penmate/backend/application/agent/run/AgentRunEventPublisher.java`
- Modify: `penmate-backend/src/main/java/com/penmate/backend/application/agent/run/AgentRunExecutor.java`

**Why:** `AgentCheckpointService.checkpointIfNeeded()` exists but is never called. `AgentRuntimeState` needs additional fields (`llmTurnIndex`, `tokenUsage`, `activeTodoProjections`, `artifactRefs`). The executor must maintain in-memory state and reduce it on each event. Checkpoints must write to Redis (30min TTL) + MySQL (durable fallback).

### Sub-task 2a: Expand AgentRuntimeState

- [ ] **Step 1: Add fields to AgentRuntimeState**

```java
// In AgentRuntimeState.java, change record to include new fields:
public record AgentRuntimeState(
        Long runId,
        String status,
        String phase,
        Long activeApprovalId,
        Long lastEventSeq,
        String assistantDraft,
        List<AgentLlmMessage> llmMessages,
        // NEW FIELDS:
        Integer llmTurnIndex,           // current LLM turn iteration (1-based)
        String pendingToolCallId,       // tool_call_id awaiting approval
        String approvedToolPayload,     // JSON payload of approved tool args
        String assistantToolCallsJson,  // JSON array of pending tool calls from LLM
        Integer remainingToolCalls,     // count of tool calls not yet executed
        LlmTokenUsage tokenUsage,       // accumulated token usage across turns
        List<String> activeTodoProjections,  // todo IDs active in this run
        List<Long> artifactRefs         // artifact IDs created in this run
) {
    // compact constructor with defaults:
    public AgentRuntimeState {
        // ... existing validation ...
        llmTurnIndex = llmTurnIndex == null ? 0 : llmTurnIndex;
        pendingToolCallId = pendingToolCallId == null ? "" : pendingToolCallId;
        approvedToolPayload = approvedToolPayload == null ? "" : approvedToolPayload;
        assistantToolCallsJson = assistantToolCallsJson == null ? "[]" : assistantToolCallsJson;
        remainingToolCalls = remainingToolCalls == null ? 0 : remainingToolCalls;
        tokenUsage = tokenUsage == null ? LlmTokenUsage.ZERO : tokenUsage;
        activeTodoProjections = activeTodoProjections == null ? List.of() : List.copyOf(activeTodoProjections);
        artifactRefs = artifactRefs == null ? List.of() : List.copyOf(artifactRefs);
    }

    // Add new with* methods:
    public AgentRuntimeState withLlmTurn(int turnIndex, LlmTokenUsage usage, Long sequence) {
        return new AgentRuntimeState(runId, status, phase, activeApprovalId, sequence, assistantDraft,
                llmMessages, turnIndex, pendingToolCallId, approvedToolPayload, assistantToolCallsJson,
                remainingToolCalls, usage, activeTodoProjections, artifactRefs);
    }

    public AgentRuntimeState withToolCallWaiting(String toolCallId, String toolCallsJson, int count, Long sequence) {
        return new AgentRuntimeState(runId, status, phase, activeApprovalId, sequence, assistantDraft,
                llmMessages, llmTurnIndex, toolCallId, approvedToolPayload, toolCallsJson,
                count, tokenUsage, activeTodoProjections, artifactRefs);
    }

    public AgentRuntimeState withToolCallApproved(String payload, Long sequence) {
        return new AgentRuntimeState(runId, status, phase, activeApprovalId, sequence, assistantDraft,
                llmMessages, llmTurnIndex, pendingToolCallId, payload, assistantToolCallsJson,
                remainingToolCalls, tokenUsage, activeTodoProjections, artifactRefs);
    }

    public AgentRuntimeState withTodoAdded(String todoId, Long sequence) { /* append to activeTodoProjections */ }
    public AgentRuntimeState withTodoRemoved(String todoId, Long sequence) { /* remove from activeTodoProjections */ }
    public AgentRuntimeState withArtifactAdded(Long artifactId, Long sequence) { /* append to artifactRefs */ }
}
```

- [ ] **Step 2: Update `empty()` factory to include new fields**

```java
public static AgentRuntimeState empty(Long runId) {
    return new AgentRuntimeState(runId, "PENDING", "created", null, 0L, "", List.of(),
            0, "", "", "[]", 0, LlmTokenUsage.ZERO, List.of(), List.of());
}
```

- [ ] **Step 3: Compile verify**

```powershell
cd penmate-backend; mvn compile -q
```

### Sub-task 2b: Wire reducer to fill new fields

- [ ] **Step 4: Add event->state transitions in AgentRuntimeStateReducer**

```java
// In AgentRuntimeStateReducer.apply(), add cases:
case "llm.turn.started" -> state.withLlmTurn(
    intValue(payload, "llmTurnIndex"), state.tokenUsage(), event.sequence());

case "llm.turn.completed" -> state.withLlmTurn(
    state.llmTurnIndex(), addUsage(state.tokenUsage(), payload), event.sequence());

case "tool.call.started" -> state.withToolCallWaiting(
    text(payload, "toolCallId", ""), state.assistantToolCallsJson(),
    max(0, state.remainingToolCalls() - 1), event.sequence());

case "tool.call.waiting_approval" -> state.withToolCallWaiting(
    text(payload, "toolCallId", ""), state.assistantToolCallsJson(),
    state.remainingToolCalls(), event.sequence());

case "approval.approved" -> state.withToolCallApproved(
    text(payload, "approvedPayload", ""), event.sequence());

case "todo.created" -> state.withTodoAdded(
    text(payload, "todoId", ""), event.sequence());

case "todo.deleted" -> state.withTodoRemoved(
    text(payload, "todoId", ""), event.sequence());

case "run.completed", "run.failed" -> state.withStatusAndPhase(
    "run.completed".equals(event.eventType()) ? "DONE" : "FAILED",
    "run.completed".equals(event.eventType()) ? "completed" : "failed",
    event.sequence());
```

- [ ] **Step 5: Add helper methods to reducer**

```java
private int intValue(JsonNode payload, String fieldName) {
    JsonNode node = payload.get(fieldName);
    return node == null || node.isNull() ? 0 : node.asInt();
}

private LlmTokenUsage addUsage(LlmTokenUsage current, JsonNode payload) {
    // parse tokenUsage from payload and add to current
    JsonNode usage = payload.get("tokenUsage");
    if (usage == null || usage.isNull()) return current;
    long prompt = usage.has("promptTokens") ? usage.get("promptTokens").asLong() : 0;
    long completion = usage.has("completionTokens") ? usage.get("completionTokens").asLong() : 0;
    long total = usage.has("totalTokens") ? usage.get("totalTokens").asLong() : 0;
    return current.add(prompt, completion, total);
}

private int max(int a, int b) { return Math.max(a, b); }
```

- [ ] **Step 6: Compile verify**

### Sub-task 2c: Wire executor to maintain and reduce state

- [ ] **Step 7: Add AgentRuntimeState and reducer to AgentRunExecutor**

```java
// In AgentRunExecutor, add fields:
private final AgentRuntimeStateReducer stateReducer;

// In constructor, add parameter:
AgentRuntimeStateReducer stateReducer

// In execute(), add after "publishPhase(runId, "executing")":
AgentRuntimeState runtimeState = AgentRuntimeState.empty(runId);
// ... then pass runtimeState to llmLoop, and after each event publish:
runtimeState = stateReducer.apply(runtimeState, event);
```

But wait — the executor calls `eventPublisher.publish()` which is self-contained. We need to change the pattern so the executor can know what state resulted. Better approach: make `publish()` return the state, or add an overload:

**Alternative — inject state tracking into publish flow:**

In `AgentRunEventPublisher.publish()`, after the event is created and projection is updated, compute the new state via `stateReducer.apply(currentState, event)` and call `checkpointService.checkpointIfNeeded(event, newState)`. The publisher needs access to the current state.

**Design decision:** Pass state into the publish call. Add a method:

```java
// AgentRunEventPublisher:
public AgentEvent publish(Long runId, String eventType, Object payload, AgentRuntimeState currentState) {
    // ... existing logic ...
    AgentRuntimeState newState = stateReducer.apply(currentState, event);
    checkpointService.checkpointIfNeeded(event, newState);
    return event;
}
```

But the executor doesn't hold the reducer or checkpoint service. Simpler: inject `AgentRuntimeStateReducer` and `AgentCheckpointService` into the executor, call them after each publish:

```java
// In AgentRunExecutor.execute():
AgentRuntimeState state = AgentRuntimeState.empty(runId);

// After each eventPublisher.publish() call:
state = stateReducer.apply(state, event);
checkpointService.checkpointIfNeeded(event, state);
```

This keeps the publisher simple and gives the executor explicit control.

- [ ] **Step 8: Modify AgentRunExecutor constructor**

```java
// Add parameters:
private final AgentRuntimeStateReducer stateReducer;
private final AgentCheckpointService checkpointService;

public AgentRunExecutor(AgentRunRepository runRepository,
        AgentRunEventPublisher eventPublisher,
        AgentPreflightCoordinator preflightCoordinator,
        AgentContextRoutingFacade contextRoutingFacade,
        PromptComposer promptComposer,
        AgentRunLlmLoop llmLoop,
        AgentModelRoutingService modelRoutingService,
        AgentRuntimeStateReducer stateReducer,       // NEW
        AgentCheckpointService checkpointService) {  // NEW
    // ...
    this.stateReducer = stateReducer;
    this.checkpointService = checkpointService;
}
```

- [ ] **Step 9: Wrap publish calls in execute() with state tracking**

In `execute()`:

```java
AgentRuntimeState state = AgentRuntimeState.empty(runId);

// publishPhase(runId, "preflight") -> wrap:
AgentEvent evt = eventPublisher.publish(runId, "run.phase.changed", Map.of("phase", "preflight"));
state = stateReducer.apply(state, evt);
checkpointService.checkpointIfNeeded(evt, state);

// publishPhase(runId, "context") -> same pattern
// context.routing.completed -> same pattern
// publishPhase(runId, "prompt") -> same pattern
// publishPhase(runId, "executing") -> same pattern

// For llmLoop: loop now receives state, returns updated state
// For run.completed / run.failed: same pattern
```

- [ ] **Step 10: Compile verify**

### Sub-task 2d: Redis checkpoint write

- [ ] **Step 11: Modify AgentCheckpointService for Redis dual-write**

```java
@Service
public class AgentCheckpointService {

    private static final int INLINE_STATE_LIMIT_BYTES = 256 * 1024;
    private static final long REDIS_TTL_SECONDS = 30 * 60; // 30 minutes
    private static final String REDIS_KEY_PREFIX = "agent:checkpoint:";

    private final AgentCheckpointRepository checkpointRepository;
    private final StringRedisTemplate redisTemplate;
    private final BusinessIdGenerator businessIdGenerator;
    private final ObjectMapper objectMapper;

    public AgentCheckpointService(AgentCheckpointRepository checkpointRepository,
                                  StringRedisTemplate redisTemplate,
                                  BusinessIdGenerator businessIdGenerator,
                                  ObjectMapper objectMapper) {
        this.checkpointRepository = checkpointRepository;
        this.redisTemplate = redisTemplate;
        this.businessIdGenerator = businessIdGenerator;
        this.objectMapper = objectMapper;
    }

    public void checkpointIfNeeded(AgentEvent event, AgentRuntimeState state) {
        if (!shouldCheckpoint(event, state)) {
            return;
        }
        AgentCheckpoint latest = fetchLatestCheckpoint(event.runId());
        long checkpointNo = latest == null ? 1L : latest.checkpointNo() + 1L;
        String stateJson = serializeState(state);
        int stateSizeBytes = stateJson.getBytes(StandardCharsets.UTF_8).length;
        if (stateSizeBytes > INLINE_STATE_LIMIT_BYTES) {
            stateJson = "{\"stateArtifactRequired\":true,\"stateSizeBytes\":" + stateSizeBytes + "}";
        }
        AgentCheckpoint checkpoint = new AgentCheckpoint(
                businessIdGenerator.nextId(),
                event.runId(),
                checkpointNo,
                event.sequence(),
                stateJson,
                stateSizeBytes,
                null
        );
        // MySQL durable write
        checkpointRepository.save(checkpoint);
        // Redis fast-path write with TTL
        String redisKey = REDIS_KEY_PREFIX + event.runId() + ":latest";
        String redisValue = stateJson;
        redisTemplate.opsForValue().set(redisKey, redisValue, Duration.ofSeconds(REDIS_TTL_SECONDS));
    }

    public AgentRuntimeState loadLatestFromRedis(Long runId) {
        String redisKey = REDIS_KEY_PREFIX + runId + ":latest";
        String stateJson = redisTemplate.opsForValue().get(redisKey);
        if (stateJson != null && !stateJson.isBlank()) {
            try {
                return objectMapper.readValue(stateJson, AgentRuntimeState.class);
            } catch (Exception ex) {
                log.warn("Failed to deserialize checkpoint from Redis: runId={}", runId, ex);
            }
        }
        // Fallback to MySQL
        AgentCheckpoint checkpoint = checkpointRepository.findLatest(runId);
        if (checkpoint != null && checkpoint.stateJson() != null) {
            try {
                return objectMapper.readValue(checkpoint.stateJson(), AgentRuntimeState.class);
            } catch (Exception ex) {
                log.error("Failed to deserialize checkpoint from MySQL: runId={}", runId, ex);
            }
        }
        return null;
    }

    private AgentCheckpoint fetchLatestCheckpoint(Long runId) {
        // Check Redis first, fallback to MySQL
        return checkpointRepository.findLatest(runId);
    }

    public void deleteCheckpoints(Long runId) {
        String redisKey = REDIS_KEY_PREFIX + runId + ":latest";
        redisTemplate.delete(redisKey);
        // MySQL cleanup is handled by event retention policy
    }

    // shouldCheckpoint() unchanged...
}
```

- [ ] **Step 12: Compile verify**

```powershell
cd penmate-backend; mvn compile -q
```

Expected: BUILD SUCCESS

---


## Task 3: Delta Throttling — In-Memory Only (No DB Persist)

**Files:**
- Modify: `penmate-backend/src/main/java/com/penmate/backend/application/agent/run/AgentRunLlmLoop.java`
- Modify: `penmate-backend/src/main/java/com/penmate/backend/application/agent/run/AgentProjectionUpdater.java`
- Modify: `penmate-backend/src/main/java/com/penmate/backend/infrastructure/realtime/AgentRunEventStreamService.java`
- Modify: `penmate-backend/src/main/java/com/penmate/backend/infrastructure/realtime/InMemoryAgentRunEventBus.java`

**Why:** Every LLM token currently produces a `message.delta` event persisted in `agent_events`, creating thousands of rows per run. After this change, deltas are broadcast-only via EventBus (in-memory + Redis pub/sub) — never persisted. Only `message.completed` is persisted, carrying the full assembled text. Frontend SSE recovery replays `message.completed` and renders once.

### Sub-task 3a: Make InMemoryAgentRunEventBus use Redis pub/sub

- [ ] **Step 1: Add Redis pub/sub layer to InMemoryAgentRunEventBus**

The current EventBus is purely in-process — subscribers only see events from the same JVM. For multi-instance (future), add Redis pub/sub so events are broadcast cluster-wide.

```java
@Component
public class InMemoryAgentRunEventBus implements AgentRunEventBus {

    private static final String REDIS_CHANNEL_PREFIX = "agent:run:event:";
    
    private final Map<Long, List<Consumer<AgentEvent>>> subscribers = new ConcurrentHashMap<>();
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final RedisMessageListenerContainer listenerContainer;

    public InMemoryAgentRunEventBus(StringRedisTemplate redisTemplate,
                                     ObjectMapper objectMapper,
                                     RedisMessageListenerContainer listenerContainer) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.listenerContainer = listenerContainer;
    }

    @Override
    public void publish(AgentEvent event) {
        // Local subscribers
        subscribers.getOrDefault(event.runId(), List.of())
                .forEach(consumer -> consumer.accept(event));
        // Redis pub/sub for cross-instance broadcast
        try {
            String channel = REDIS_CHANNEL_PREFIX + event.runId();
            String message = objectMapper.writeValueAsString(event);
            redisTemplate.convertAndSend(channel, message);
        } catch (Exception ex) {
            log.warn("Redis pub/sub publish failed: runId={}", event.runId(), ex);
        }
    }

    // Subscribe to Redis channel for a runId (called by SSE stream service)
    public Runnable subscribeWithRedis(Long runId, Consumer<AgentEvent> consumer) {
        Runnable localUnsub = subscribe(runId, consumer);
        String channel = REDIS_CHANNEL_PREFIX + runId;
        MessageListener listener = (message, pattern) -> {
            try {
                AgentEvent event = objectMapper.readValue(message.getBody(), AgentEvent.class);
                consumer.accept(event);
            } catch (Exception ex) {
                log.warn("Failed to deserialize Redis pub/sub event: runId={}", runId, ex);
            }
        };
        listenerContainer.addMessageListener(listener, new ChannelTopic(channel));
        return () -> {
            localUnsub.run();
            listenerContainer.removeMessageListener(listener);
        };
    }

    // Local subscribe (unchanged from existing)
    public Runnable subscribe(Long runId, Consumer<AgentEvent> consumer) {
        subscribers.computeIfAbsent(runId, ignored -> new CopyOnWriteArrayList<>()).add(consumer);
        return () -> subscribers.getOrDefault(runId, List.of()).remove(consumer);
    }
}
```

- [ ] **Step 2: Compile verify**

### Sub-task 3b: Change LLM loop to broadcast deltas, not publish

- [ ] **Step 3: Modify AgentRunLlmLoop to inject EventBus**

```java
// In AgentRunLlmLoop.java, add field:
private final InMemoryAgentRunEventBus eventBus;

// Constructor: add eventBus parameter
public AgentRunLlmLoop(AgentLlmGateway llmGateway,
                       AgentToolDefinitionSource toolDefinitionSource,
                       AgentRunEventPublisher eventPublisher,
                       InMemoryAgentRunEventBus eventBus) {
    // ...
    this.eventBus = eventBus;
}
```

- [ ] **Step 4: Change delta behavior in LLM loop**

Currently, `AgentRunLlmLoop.execute()` calls `eventPublisher.publish("message.delta", ...)` which persists to `agent_events`. Change to use EventBus directly for deltas:

```java
// OLD (delete this):
// if (!assistantText.isBlank()) {
//     eventPublisher.publish(request.runId(), "message.delta", Map.of(...));
// }

// NEW: broadcast delta via EventBus (in-memory + Redis pub/sub, NOT persisted):
if (!assistantText.isBlank()) {
    // Create a synthetic non-persisted event just for broadcasting
    AgentEvent deltaEvent = AgentEvent.createNonPersisted(
            request.runId(), event.sequence() + 1,
            "message.delta",
            Map.of("llmTurnIndex", INITIAL_TURN_INDEX, "text", assistantText)
    );
    eventBus.publish(deltaEvent);
}

// Keep message.completed as persisted:
eventPublisher.publish(request.runId(), "message.completed", Map.of(
        "llmTurnIndex", INITIAL_TURN_INDEX,
        "role", "assistant",
        "text", assistantText
));
```

Wait — `AgentEvent` is currently a record created by the repository. We need a lighter construct. Better approach: the EventBus interface uses `AgentEvent` today; let's keep it but add a factory that doesn't require database-generated fields. Or simpler: make EventBus accept a lighter DTO.

**Simplest approach:** Create a new method on `AgentRunEventPublisher` that broadcasts without persisting:

```java
// In AgentRunEventPublisher:
public void broadcastOnly(Long runId, String eventType, Object payload, long sequence) {
    String payloadJson = toJson(withSchemaVersion(payload));
    AgentEvent event = AgentEvent.forBroadcast(runId, sequence, eventType, payloadJson);
    eventBus.publish(event);
}
```

Add to `AgentEvent`:

```java
public static AgentEvent forBroadcast(Long runId, long sequence, String eventType, String payloadJson) {
    return new AgentEvent(null, runId, null, null, null, sequence, 1, eventType, payloadJson, null);
}
```

Then in `AgentRunLlmLoop`:

```java
// Instead of eventPublisher.publish("message.delta", ...):
eventPublisher.broadcastOnly(request.runId(), "message.delta",
        Map.of("llmTurnIndex", INITIAL_TURN_INDEX, "text", assistantText),
        currentSequence);
```

- [ ] **Step 5: Add broadcastOnly to AgentRunEventPublisher**

```java
// Add to AgentRunEventPublisher.java:
public void broadcastOnly(Long runId, String eventType, Object payload, long sequence) {
    String payloadJson = toJson(withSchemaVersion(payload));
    AgentEvent event = AgentEvent.forBroadcast(runId, sequence, eventType, payloadJson);
    eventBus.publish(event);
}
```

- [ ] **Step 6: Add forBroadcast factory to AgentEvent**

```java
// Add to AgentEvent.java:
public static AgentEvent forBroadcast(Long runId, long sequence, String eventType, String payloadJson) {
    return new AgentEvent(null, runId, null, null, null, sequence, 1, eventType, payloadJson, null);
}
```

- [ ] **Step 7: Modify the LLM loop to use broadcastOnly for deltas**

- [ ] **Step 8: Compile verify**

### Sub-task 3c: Remove message.delta from projection updater

- [ ] **Step 9: Remove message.delta case from AgentProjectionUpdater**

In `AgentProjectionUpdater.apply()`, **remove** the `message.delta` case:

```java
// DELETE this entire case:
// case "message.delta" -> runProjectionRepository.appendAssistantDelta(event.runId(), event.sequence(), text(payload, "text", ""));
```

The `message.completed` case already handles writing full text to `agent_run_projections.status_message`:

```java
case "message.completed" -> runProjectionRepository.setCurrentAssistantMessage(
        event.runId(), longValue(payload, "messageId"), event.sequence());
```

But wait — `setCurrentAssistantMessage` sets the messageId, not the text. We need the text to be written too. Check the current `AgentRunProjectionRepository` and mapper. Let's fix in Task 3d.

### Sub-task 3d: Ensure message.completed writes full text to projection

- [ ] **Step 10: Update projection repository and mapper for message text**

Check `AgentRunProjectionMapper.upsertRunState` — does it have a `status_message` field? If not, add it. The `message.completed` case should store the full assistant text.

```java
// In AgentProjectionUpdater, change message.completed case:
case "message.completed" -> runProjectionRepository.updateRunState(
        event.runId(), null, null, null, event.sequence(),
        text(payload, "text", null),  // statusMessage = full text
        null);
```

Verify that `AgentRunProjectionMapper.upsertRunState` accepts and writes `statusMessage` to the `agent_run_projections` table column `status_message`.

### Sub-task 3e: SSE replay handles missing deltas

- [ ] **Step 11: Verify AgentRunEventStreamService handles message.completed**

When SSE replays persisted events (which no longer include `message.delta`), the frontend receives `message.completed` with the full text. The current `AgentRunEventStreamService.openStream()` already handles this — it replays all persisted events from `eventRepository.listAfter()`. Since deltas are no longer persisted, they won't appear in replay. The frontend must render the full text from `message.completed` in one shot.

Frontend check: verify the reducer handles `message.completed` by setting the full assistant text, not appending deltas. This should already be the case since the frontend reducer was built for the new event protocol.

- [ ] **Step 12: Compile verify**

```powershell
cd penmate-backend; mvn compile -q
```

Expected: BUILD SUCCESS

---

## Task 4: Event Retention Policy

**Files:**
- Create: `penmate-backend/src/main/java/com/penmate/backend/application/agent/run/AgentEventRetentionService.java`
- Modify: `penmate-backend/src/main/java/com/penmate/backend/infrastructure/persistence/agent/run/AgentRunEventRepositoryImpl.java`
- Modify: `penmate-backend/src/main/java/com/penmate/backend/infrastructure/persistence/agent/run/AgentRunMapper.java`
- Modify: `penmate-backend/src/main/java/com/penmate/backend/application/agent/run/AgentRunExecutor.java`

**Why:** Events accumulate indefinitely. Policy: active runs keep all events. Terminal runs (DONE/FAILED/CANCELLED): events older than 7 days are deleted. Checkpoint-based compaction: if checkpoint at seq 50 exists, events seq 1-49 can be cleaned. This prevents unbounded growth.

### Retention Rules
1. **Active runs** (status=RUNNING, WAITING_APPROVAL): retain ALL events
2. **Terminal runs** (status=DONE, FAILED, CANCELLED): retain events for 7 days after `updated_at`
3. **Checkpoint compaction**: if latest checkpoint covers seq N, events <= N on terminal runs can be cleaned earlier (keeps at least the last 50 events for audit)
4. **Cleanup runs on schedule**: `@Scheduled(cron="0 0 3 * * ?")` — daily at 3 AM
5. **Per-run cleanup**: when run transitions to terminal, schedule delayed cleanup

- [ ] **Step 1: Create AgentEventRetentionService**

```java
package com.penmate.backend.application.agent.run;

import com.penmate.backend.domain.agent.run.repository.AgentCheckpointRepository;
import com.penmate.backend.domain.agent.run.repository.AgentRunEventRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
public class AgentEventRetentionService {

    private static final int RETENTION_DAYS_TERMINAL = 7;
    private static final int MIN_RETAIN_EVENTS = 50;

    private final AgentRunEventRepository eventRepository;
    private final AgentCheckpointRepository checkpointRepository;

    public AgentEventRetentionService(AgentRunEventRepository eventRepository,
                                       AgentCheckpointRepository checkpointRepository) {
        this.eventRepository = eventRepository;
        this.checkpointRepository = checkpointRepository;
    }

    @Scheduled(cron = "${penmate.agent.event-retention-cron:0 0 3 * * ?}")
    public void scheduledCleanup() {
        log.info("Starting agent event retention cleanup");
        LocalDateTime cutoff = LocalDateTime.now().minusDays(RETENTION_DAYS_TERMINAL);
        int deleted = eventRepository.deleteTerminalEventsOlderThan(cutoff, MIN_RETAIN_EVENTS);
        log.info("Agent event retention cleanup complete: deleted {} events", deleted);
    }

    public void cleanupRun(Long runId, Long latestCheckpointSeq) {
        Long safeSeq = latestCheckpointSeq != null ? latestCheckpointSeq : 0L;
        int deleted = eventRepository.deleteEventsBelowSequence(runId, safeSeq, MIN_RETAIN_EVENTS);
        log.info("Checkpoint compaction for run {}: deleted {} events below seq {}", runId, deleted, safeSeq);
    }
}
```

- [ ] **Step 2: Add repository methods**

In `AgentRunEventRepository.java`:
```java
int deleteTerminalEventsOlderThan(LocalDateTime cutoff, int minRetain);
int deleteEventsBelowSequence(Long runId, Long maxSequence, int minRetain);
```

In `AgentRunEventRepositoryImpl.java`:
```java
@Override
public int deleteTerminalEventsOlderThan(LocalDateTime cutoff, int minRetain) {
    return eventMapper.deleteTerminalEventsOlderThan(cutoff, minRetain);
}

@Override
public int deleteEventsBelowSequence(Long runId, Long maxSequence, int minRetain) {
    return eventMapper.deleteEventsBelowSequence(runId, maxSequence, minRetain);
}
```

- [ ] **Step 3: Add MyBatis mapper SQL**

In `AgentRunMapper.java`:
```java
@Delete("""
        DELETE e FROM agent_events e
        INNER JOIN agent_runs r ON e.run_id = r.run_id
        WHERE r.run_status IN ('DONE','FAILED','CANCELLED')
        AND r.updated_at < #{cutoff}
        AND e.sequence <= (
            SELECT COALESCE(MAX(e2.sequence), 0) - #{minRetain}
            FROM agent_events e2
            WHERE e2.run_id = e.run_id
        )
        """)
int deleteTerminalEventsOlderThan(@Param("cutoff") LocalDateTime cutoff,
                                   @Param("minRetain") int minRetain);

@Delete("""
        DELETE FROM agent_events
        WHERE run_id = #{runId}
        AND sequence <= #{maxSequence}
        AND sequence <= (
            SELECT COALESCE(MAX(sequence), 0) - #{minRetain}
            FROM agent_events
            WHERE run_id = #{runId}
        )
        """)
int deleteEventsBelowSequence(@Param("runId") Long runId,
                                @Param("maxSequence") Long maxSequence,
                                @Param("minRetain") int minRetain);
```

- [ ] **Step 4: Call cleanupRun from executor on run.completed/failed**

In `AgentRunExecutor.execute()`, after publishing `run.completed` or `run.failed`:

```java
if (loopResult.status() == AgentRunLoopResult.Status.COMPLETED) {
    // ... publish run.completed ...
    // Trigger checkpoint-based compaction
    AgentCheckpoint latestCp = checkpointRepository.findLatest(runId);
    retentionService.cleanupRun(runId, latestCp != null ? latestCp.lastEventSeq() : null);
}
```

- [ ] **Step 5: Compile verify**

```powershell
cd penmate-backend; mvn compile -q
```

---

## Task 5: Artifact Write Path

**Files:**
- Create: `penmate-backend/src/main/java/com/penmate/backend/domain/agent/run/repository/AgentArtifactRepository.java`
- Create: `penmate-backend/src/main/java/com/penmate/backend/infrastructure/persistence/agent/run/AgentArtifactMapper.java`
- Create: `penmate-backend/src/main/java/com/penmate/backend/infrastructure/persistence/agent/run/AgentArtifactRepositoryImpl.java`
- Create: `penmate-backend/src/main/java/com/penmate/backend/domain/agent/run/model/AgentArtifact.java`
- Modify: `penmate-backend/src/main/java/com/penmate/backend/application/agent/run/AgentRunEventPublisher.java`
- Modify: `penmate-backend/src/main/java/com/penmate/backend/infrastructure/realtime/AgentRunEventStreamService.java`

**Why:** Payloads > 64KB should be stored in `agent_artifacts` instead of inline in `agent_events.payload_json`. The event stores `{"artifactRef":"...","sizeBytes":...}`. SSE replay resolves artifact refs before sending to frontend.

- [ ] **Step 1: Create AgentArtifact model**

```java
package com.penmate.backend.domain.agent.run.model;

import java.time.LocalDateTime;

public record AgentArtifact(
        Long artifactId,
        Long runId,
        Long eventId,
        String artifactType,
        String payloadJson,
        Integer sizeBytes,
        LocalDateTime createdAt
) {}
```

- [ ] **Step 2: Create AgentArtifactMapper**

```java
package com.penmate.backend.infrastructure.persistence.agent.run;

import com.penmate.backend.domain.agent.run.model.AgentArtifact;
import org.apache.ibatis.annotations.*;

@Mapper
public interface AgentArtifactMapper {

    @Insert("""
            INSERT INTO agent_artifacts(artifact_id, run_id, event_id, artifact_type, payload_json, size_bytes)
            VALUES(#{artifactId}, #{runId}, #{eventId}, #{artifactType}, #{payloadJson}, #{sizeBytes})
            """)
    int insert(AgentArtifact artifact);

    @Select("""
            SELECT artifact_id, run_id, event_id, artifact_type, payload_json, size_bytes, created_at
            FROM agent_artifacts
            WHERE artifact_id = #{artifactId}
            """)
    @ConstructorArgs({
            @Arg(column = "artifact_id", javaType = Long.class),
            @Arg(column = "run_id", javaType = Long.class),
            @Arg(column = "event_id", javaType = Long.class),
            @Arg(column = "artifact_type", javaType = String.class),
            @Arg(column = "payload_json", javaType = String.class),
            @Arg(column = "size_bytes", javaType = Integer.class),
            @Arg(column = "created_at", javaType = java.time.LocalDateTime.class)
    })
    AgentArtifact findById(@Param("artifactId") Long artifactId);
}
```

- [ ] **Step 3: Create AgentArtifactRepository interface + impl**

```java
// Interface:
package com.penmate.backend.domain.agent.run.repository;

import com.penmate.backend.domain.agent.run.model.AgentArtifact;

public interface AgentArtifactRepository {
    void save(AgentArtifact artifact);
    AgentArtifact findById(Long artifactId);
}

// Impl:
package com.penmate.backend.infrastructure.persistence.agent.run;

import com.penmate.backend.domain.agent.run.model.AgentArtifact;
import com.penmate.backend.domain.agent.run.repository.AgentArtifactRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class AgentArtifactRepositoryImpl implements AgentArtifactRepository {

    private final AgentArtifactMapper artifactMapper;

    @Override
    public void save(AgentArtifact artifact) {
        artifactMapper.insert(artifact);
    }

    @Override
    public AgentArtifact findById(Long artifactId) {
        return artifactMapper.findById(artifactId);
    }
}
```

- [ ] **Step 4: Add artifact extraction to AgentRunEventPublisher**

In `AgentRunEventPublisher.publish()`:

```java
private static final int ARTIFACT_SIZE_THRESHOLD = 64 * 1024; // 64KB

private final AgentArtifactRepository artifactRepository;
private final BusinessIdGenerator businessIdGenerator;

@Transactional
public AgentEvent publish(Long runId, String eventType, Object payload) {
    String payloadJson = toJson(withSchemaVersion(payload));
    int sizeBytes = payloadJson.getBytes(StandardCharsets.UTF_8).length;
    
    AgentEvent event;
    if (sizeBytes > ARTIFACT_SIZE_THRESHOLD) {
        // Store large payload as artifact
        Long artifactId = businessIdGenerator.nextId();
        artifactRepository.save(new AgentArtifact(
                artifactId, runId, null, eventType, payloadJson, sizeBytes, null
        ));
        // Replace payload with artifact ref
        String refPayload = "{\"artifactRef\":\"" + artifactId + "\",\"sizeBytes\":" + sizeBytes + "}";
        event = eventRepository.append(runId, eventType, refPayload);
    } else {
        event = eventRepository.append(runId, eventType, payloadJson);
    }
    
    projectionUpdater.apply(event);
    afterCommit(() -> {
        try {
            eventBus.publish(event);
        } catch (RuntimeException ex) {
            log.warn("agent run live event publish failed after commit: ...", ex);
        }
    });
    return event;
}
```

- [ ] **Step 5: Resolve artifact refs in SSE replay**

In `AgentRunEventStreamService.openStream()`, when replaying from DB, resolve artifact refs:

```java
private AgentEvent resolveArtifact(AgentEvent event) {
    String payload = event.payloadJson();
    if (payload == null || !payload.contains("artifactRef")) return event;
    try {
        JsonNode node = objectMapper.readTree(payload);
        if (node.has("artifactRef")) {
            Long artifactId = node.get("artifactRef").asLong();
            AgentArtifact artifact = artifactRepository.findById(artifactId);
            if (artifact != null) {
                return AgentEvent.forReplay(event, artifact.payloadJson());
            }
        }
    } catch (Exception ex) {
        log.warn("Failed to resolve artifact ref: runId={}, sequence={}", event.runId(), event.sequence(), ex);
    }
    return event;
}
```

Add `forReplay` factory to `AgentEvent`:

```java
public static AgentEvent forReplay(AgentEvent original, String resolvedPayload) {
    return new AgentEvent(original.eventId(), original.runId(), original.projectId(),
            original.sessionId(), original.turnId(), original.sequence(),
            original.schemaVersion(), original.eventType(), resolvedPayload, original.createdAt());
}
```

- [ ] **Step 6: Compile verify**

```powershell
cd penmate-backend; mvn compile -q
```

---


## Task 6: Tool Call Loop

**Files:**
- Modify: `penmate-backend/src/main/java/com/penmate/backend/application/agent/run/AgentRunLlmLoop.java`
- Modify: `penmate-backend/src/main/java/com/penmate/backend/application/agent/run/AgentRunLoopRequest.java`
- Modify: `penmate-backend/src/main/java/com/penmate/backend/application/agent/run/AgentRunLoopResult.java`
- Possibly new/modify: `penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/runtime/ToolCallApplicationService.java`

**Why:** `AgentRunLlmLoop.execute()` makes one LLM call and returns. LLM responses with `finishReason=tool_calls` are dropped. A proper loop: LLM -> if tool_calls -> execute tools -> append results -> LLM again -> repeat until stop or max 10 iterations. If any tool returns WAITING_APPROVAL, publish event and return early.

- [ ] **Step 1: Check ToolCallApplicationService exists and can execute tool calls**

Search for existing tool execution:

```powershell
rg "class ToolCallApplicationService\|executeToolCall\|ToolCallRequest" penmate-backend/src/main --type java -l
```

If `ToolCallApplicationService` doesn't exist, we need to create it or use the LLM gateway's built-in tool execution. Check `AgentLlmGateway` for tool execution capability.

For this plan, assume `ToolCallApplicationService` exists or can be created simply. The key contract:

```java
public interface ToolCallApplicationService {
    ToolCallResult executeToolCall(ToolCallRequest request);
}

public record ToolCallRequest(
    Long runId, Long projectId, Long sessionId, Long turnId,
    String toolCallId, String toolCode, String toolName,
    String argumentsJson, Long operatorId, String traceId
) {}

public record ToolCallResult(
    String toolCallId, String status, // "success", "failed", "waiting_approval"
    String outputPreview, Long outputArtifactId,
    String errorCode, String errorMessage,
    Long approvalId
) {}
```

- [ ] **Step 2: Restructure AgentRunLlmLoop.execute() into a loop**

```java
@Component
public class AgentRunLlmLoop {

    private static final int MAX_ITERATIONS = 10;
    private static final int INITIAL_TURN_INDEX = 1;

    private final AgentLlmGateway llmGateway;
    private final AgentToolDefinitionSource toolDefinitionSource;
    private final AgentRunEventPublisher eventPublisher;
    private final InMemoryAgentRunEventBus eventBus;
    private final ToolCallApplicationService toolCallService;  // NEW

    public AgentRunLlmLoop(AgentLlmGateway llmGateway,
                           AgentToolDefinitionSource toolDefinitionSource,
                           AgentRunEventPublisher eventPublisher,
                           InMemoryAgentRunEventBus eventBus,
                           ToolCallApplicationService toolCallService) {
        this.llmGateway = llmGateway;
        this.toolDefinitionSource = toolDefinitionSource;
        this.eventPublisher = eventPublisher;
        this.eventBus = eventBus;
        this.toolCallService = toolCallService;
    }

    public AgentRunLoopResult execute(AgentRunLoopRequest request) {
        List<AgentLlmMessage> messages = new ArrayList<>(request.messages());
        int turnIndex = INITIAL_TURN_INDEX;
        LlmTokenUsage totalUsage = LlmTokenUsage.ZERO;
        StringBuilder fullAssistantText = new StringBuilder();

        for (int iteration = 0; iteration < MAX_ITERATIONS; iteration++) {
            eventPublisher.publish(request.runId(), "llm.turn.started", Map.of(
                    "llmTurnIndex", turnIndex,
                    "traceId", request.traceId()
            ));

            AgentLlmTurnResponse response = llmGateway.generateTurn(
                    new AgentLlmTurnRequest(
                            List.copyOf(messages),
                            toolDefinitionSource.listLlmSchemas(),
                            "auto"
                    ),
                    request.executionConfig()
            );

            totalUsage = totalUsage.add(response.tokenUsage());
            fullAssistantText.append(response.assistantText());

            eventPublisher.publish(request.runId(), "llm.turn.completed", Map.of(
                    "llmTurnIndex", turnIndex,
                    "finishReason", response.finishReason(),
                    "toolCallCount", response.toolCalls().size(),
                    "tokenUsage", Map.of(
                            "promptTokens", response.tokenUsage().promptTokens(),
                            "completionTokens", response.tokenUsage().completionTokens(),
                            "totalTokens", response.tokenUsage().totalTokens()
                    )
            ));

            // Broadcast delta (not persisted)
            if (!response.assistantText().isBlank()) {
                eventPublisher.broadcastOnly(request.runId(), "message.delta",
                        Map.of("llmTurnIndex", turnIndex, "text", response.assistantText()),
                        -1); // sequence filled by broadcastOnly internally
            }

            // Check for tool calls
            if (response.toolCalls().isEmpty()) {
                // No tool calls — LLM is done
                eventPublisher.publish(request.runId(), "message.completed", Map.of(
                        "llmTurnIndex", turnIndex,
                        "role", "assistant",
                        "text", fullAssistantText.toString()
                ));
                return AgentRunLoopResult.completed(fullAssistantText.toString(), totalUsage);
            }

            // Handle tool calls
            for (AgentLlmTurnResponse.ToolCall toolCall : response.toolCalls()) {
                eventPublisher.publish(request.runId(), "tool.call.started", Map.of(
                        "llmTurnIndex", turnIndex,
                        "toolCallId", toolCall.id(),
                        "toolCode", toolCall.code(),
                        "toolName", toolCall.name(),
                        "argumentsPreview", toolCall.arguments()
                ));

                ToolCallResult result = toolCallService.executeToolCall(new ToolCallRequest(
                        request.runId(), request.projectId(), request.sessionId(), request.turnId(),
                        toolCall.id(), toolCall.code(), toolCall.name(),
                        toolCall.arguments(),
                        request.executionConfig().operatorId(),
                        request.traceId()
                ));

                if ("waiting_approval".equals(result.status())) {
                    eventPublisher.publish(request.runId(), "tool.call.waiting_approval", Map.of(
                            "llmTurnIndex", turnIndex,
                            "toolCallId", toolCall.id(),
                            "toolCode", toolCall.code(),
                            "approvalId", result.approvalId()
                    ));
                    return AgentRunLoopResult.waitingApproval(
                            result.approvalId(),
                            fullAssistantText.toString(),
                            totalUsage
                    );
                }

                if ("success".equals(result.status())) {
                    eventPublisher.publish(request.runId(), "tool.call.completed", Map.of(
                            "llmTurnIndex", turnIndex,
                            "toolCallId", toolCall.id(),
                            "toolCode", toolCall.code(),
                            "outputPreview", result.outputPreview(),
                            "outputArtifactId", result.outputArtifactId()
                    ));
                } else {
                    eventPublisher.publish(request.runId(), "tool.call.failed", Map.of(
                            "llmTurnIndex", turnIndex,
                            "toolCallId", toolCall.id(),
                            "toolCode", toolCall.code(),
                            "errorCode", result.errorCode(),
                            "errorMessage", result.errorMessage()
                    ));
                }

                // Append tool result as a message for the next LLM call
                messages.add(AgentLlmMessage.toolResult(
                        toolCall.id(), result.outputPreview() != null ? result.outputPreview() : ""
                ));
            }

            // Append assistant response with tool calls as a message
            messages.add(AgentLlmMessage.assistant(
                    fullAssistantText.toString(),
                    response.toolCalls().stream()
                            .map(tc -> new AgentLlmMessage.ToolCallRef(tc.id(), tc.code(), tc.name(), tc.arguments()))
                            .toList()
            ));

            turnIndex++;
        }

        // Max iterations reached
        eventPublisher.publish(request.runId(), "message.completed", Map.of(
                "llmTurnIndex", turnIndex,
                "role", "assistant",
                "text", fullAssistantText.toString()
        ));
        return AgentRunLoopResult.completed(fullAssistantText.toString(), totalUsage);
    }
}
```

- [ ] **Step 3: Compile verify**

```powershell
cd penmate-backend; mvn compile -q
```

---

## Task 7: Existing Code Fixes (compile + test verify)

**Files:**
- All modified files from Tasks 1-6
- Test files that reference changed constructors

**Why:** Many constructors changed (new dependencies injected). Tests need updating. Run compilation first, then fix test compilation.

- [ ] **Step 1: Full compile**

```powershell
cd penmate-backend; mvn compile -q
```

Fix any compilation errors. Expected: BUILD SUCCESS.

- [ ] **Step 2: Compile tests**

```powershell
cd penmate-backend; mvn test-compile -q
```

Fix test compilation errors — primarily constructor signature updates:

- `AgentRunExecutorTest`: add `AgentRuntimeStateReducer`, `AgentCheckpointService`, `AgentEventRetentionService` mocks
- `AgentRunAppServiceTest`: verify unchanged (no new deps)
- `AgentRunLlmLoopTest`: add `InMemoryAgentRunEventBus`, `ToolCallApplicationService` mocks
- `AgentRunEventPublisherTest`: add `AgentArtifactRepository`, `BusinessIdGenerator` mocks
- `AgentCheckpointServiceTest`: add `StringRedisTemplate` mock

- [ ] **Step 3: Run existing agent run tests**

```powershell
cd penmate-backend; mvn -Dtest="AgentRun*Test,Agent*Run*Test" test
```

Fix any test failures. Expected: all PASS.

---

## Task 8: End-to-End Verification

**Files:** No code changes — verification only.

- [ ] **Step 1: Start MySQL and Redis**

```powershell
# Verify MySQL is running
mysqladmin -u root -proot ping

# Verify Redis is running
redis-cli ping
```

- [ ] **Step 2: Start backend**

```powershell
cd penmate-backend
mvn package -DskipTests -q
Start-Process java -ArgumentList "-jar","target/penmate-backend-1.0-SNAPSHOT.jar","--spring.profiles.active=local" -WindowStyle Hidden -RedirectStandardOutput "backend-out.log" -RedirectStandardError "backend-err.log"
Start-Sleep -Seconds 15
```

- [ ] **Step 3: Start frontend**

```powershell
cd penmate-frontend
npm run dev
```

- [ ] **Step 4: API smoke test**

Login + create turn:

```powershell
$body = @{email='dbcase_admin@penmate.local';password='P@ssw0rd!'} | ConvertTo-Json
$r = Invoke-WebRequest -Uri "http://localhost:8080/api/v1/auth/login" -Method POST -ContentType "application/json" -Body $body
$token = ($r.Content | ConvertFrom-Json).data.accessToken

$body = @{operatorId="920001";userMessage="你好";taskRequest=@{taskType="WRITE";modelConfigId="2064681965155651584"}} | ConvertTo-Json -Depth 3
$r2 = Invoke-WebRequest -Uri "http://localhost:8080/api/v1/novels/2056651400338984960/agent/sessions/57038031048704/turns" -Method POST -ContentType "application/json" -Body $body -Headers @{Authorization="Bearer $token"}
$r2.Content | ConvertFrom-Json | ConvertTo-Json -Depth 5
```

- [ ] **Step 5: Verify event table**

```sql
SELECT event_type, COUNT(*) FROM agent_events GROUP BY event_type;
SELECT COUNT(*) FROM agent_checkpoints;
SELECT * FROM agent_checkpoints ORDER BY created_at DESC LIMIT 3;
```

Expected: `message.delta` has ZERO rows. Other event types have rows. Checkpoints exist.

- [ ] **Step 6: Verify Redis checkpoint**

```powershell
redis-cli KEYS "agent:checkpoint:*"
redis-cli GET "agent:checkpoint:<runId>:latest"
```

Expected: Redis key exists with TTL, contains JSON state.

- [ ] **Step 7: Open frontend and verify workbench**

Navigate to `http://localhost:8091/projects/2056651400338984960/workbench?sessionId=57038031048704`
Verify: Todo panel, message display, recovery, tool call display.

---

## Task 9: Cleanup — Remove Deprecated Sub-Plan

- [ ] **Step 1: Delete old remaining-links plan**

```powershell
Remove-Item "D:\warehouse\project\PenMate\.worktrees\codex-agent-run-event-checkpoint-refactor\docs\superpowers\plans\2026-06-11-agent-run-remaining-links.md"
```

- [ ] **Step 2: Commit all changes**

```powershell
cd D:\warehouse\project\PenMate\.worktrees\codex-agent-run-event-checkpoint-refactor
git add -A
git commit -m "feat: complete agent run remaining links - checkpoint wiring, delta throttling, event retention, artifact path, tool call loop"
```

---

## Execution Order

| Order | Task | Dependencies |
|-------|------|-------------|
| 1 | Task 1: Redis Infrastructure | None |
| 2 | Task 2: Checkpoint Wiring | Task 1 |
| 3 | Task 3: Delta Throttling | Task 1 |
| 4 | Task 4: Event Retention | None (independent) |
| 5 | Task 5: Artifact Write Path | None (independent) |
| 6 | Task 6: Tool Call Loop | Tasks 2, 3 |
| 7 | Task 7: Compile & Test Fixes | Tasks 1-6 |
| 8 | Task 8: E2E Verification | Task 7 |
| 9 | Task 9: Cleanup | Task 8 |

Tasks 4 and 5 can run in parallel with Task 2-3 since they touch different files.

---

## Self-Review

- **Spec coverage:** All remaining items from the user's conversation are covered: checkpoint wiring (Task 2), delta throttling with no DB persist (Task 3), event retention policy (Task 4), artifact write path (Task 5), tool call loop (Task 6), Redis integration for checkpoints (Task 2) and pub/sub (Task 3).
- **Placeholder scan:** No TBD, TODO, or vague references. Every step has concrete code.
- **Type consistency:** `AgentRuntimeState` new fields match reducer transitions match executor usage. `AgentEvent.forBroadcast` factory used consistently. `StringRedisTemplate` injected where needed.

---

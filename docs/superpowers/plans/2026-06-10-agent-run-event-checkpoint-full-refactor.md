# Agent Run Event Checkpoint Full Refactor Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the current task-context/SSE generation pipeline with a clean Agent Run runtime backed by durable events, checkpoints, projections, and run-level streaming.

**Architecture:** `AgentRun` becomes the execution aggregate. `AgentEvent` is the durable process log, `AgentCheckpoint` is the resume accelerator, projections are query/read models, and large outputs move to artifacts. The old `AgentGenerationWorkflow`, task runtime snapshots, `generation.*` SSE protocol, and approval resume bridge are removed rather than preserved.

**Tech Stack:** Spring Boot, MyBatis, MySQL baseline DDL managed by Flyway, SseEmitter, Jackson, JUnit 5, Mockito, ArchUnit, Vue 3, TypeScript, Vitest.

---

## Ground Rules

- No compatibility layer for the old runtime protocol.
- Delete old orchestration and runtime snapshot code once replacement tasks pass.
- Keep reusable capabilities only when they are not tied to the old runtime:
  - Keep LLM gateway/provider abstractions.
  - Keep tool definitions and tool handlers.
  - Keep prompt/system/skill prompt loading.
  - Keep preflight, context routing, RAG, Story Bible, style binding, model routing, and approval repositories where they remain clean.
- Do not write run state into `agent_task_contexts`.
- Do not emit `generation.started`, `generation.status`, `generation.token`, `generation.tool_call`, `generation.waiting_approval`, `generation.done`, or `generation.failed`.
- New frontend code consumes `run.*`, `llm.*`, `tool.call.*`, `approval.*`, `todo.*`, `message.*`, `artifact.*`, and `checkpoint.*` events only.
- Runtime table changes must edit the original baseline `CREATE TABLE` SQL files directly. Do not add a destructive follow-up migration that drops old Agent task tables.
- Do not create `V14__agent_run_event_checkpoint_full_refactor.sql`. Remove old task runtime tables from their original DDL definitions instead.

---

## Authoritative Amendments From Review

These amendments supersede any older task text below that still mentions a destructive `V14` migration, `activeTask`, `taskId` as the runtime aggregate, or `generation.*` events.

### Schema Amendment

- Edit `penmate-backend/src/main/resources/db/migration/V11__init_agent_and_ops_domains.sql` directly.
- Remove `agent_tasks`, `agent_task_contexts`, and `agent_task_results` from the baseline DDL.
- Keep `agent_sessions`, `agent_turns`, `agent_messages`, `agent_session_style_bindings`, `ops_async_jobs`, and `ops_migrations`.
- Replace `agent_sessions.last_task_id` with `agent_sessions.last_run_id`.
- Replace `agent_turns.task_id` with `agent_turns.run_id`.
- Add `agent_runs`, `agent_run_inputs`, `agent_events`, `agent_checkpoints`, `agent_run_projections`, `agent_tool_call_projections`, `agent_todo_projections`, and `agent_artifacts` to the same baseline DDL file.
- Edit `penmate-backend/src/main/resources/db/migration/V12__init_pending_tool_invocations.sql` directly. Replace `agent_pending_approvals` with `agent_run_pending_approvals`.
- Edit `penmate-backend/src/main/resources/db/migration/V2__init_novel_and_approval_minimal.sql` directly. Replace task-only approval linkage with `run_id` linkage in `agent_approval_requests`.
- Edit `penmate-backend/src/main/resources/db/migration/V3__init_storage_and_rag_minimal.sql` directly. Replace `rag_retrieval_logs.task_id` and `idx_rag_retrieval_task_created` with `run_id` and a run-based index, unless the implementation proves the table is not runtime-scoped and records that exception in the result report.
- Edit `penmate-backend/src/main/resources/db/migration/V10__init_plugin_and_model_domains.sql` directly. Replace `plugin_call_logs.task_id` and `idx_plugin_call_task` with `run_id` and a run-based index, unless the implementation proves the table is not runtime-scoped and records that exception in the result report.
- Edit `penmate-backend/src/main/resources/db/migration/V13__init_agent_session_todos.sql` directly. Replace `agent_session_todos.task_id` with `source_run_id` and a run-based index; Todo domain persistence must not expose runtime `taskId`.
- Keep SQL comments syntactically valid. Do not copy mojibake comments with broken quotes from the old draft.

### Backend Coverage Amendment

- Rewrite `AgentSessionRepository` and `AgentSessionMapper`, not only `AgentRepository` and `AgentMapper`.
- Remove all production SQL references to `agent_tasks`, `agent_task_contexts`, `agent_task_results`, `agent_pending_approvals`, `last_task_id`, and old runtime `task_id` columns.
- Rewrite `ToolCallRequest` to carry `runId`, `sessionId`, `turnId`, `toolCallId`, and approval resume metadata. It must not require `taskId`.
- Rewrite every production tool handler and tool support class that reads `request.taskId()` or `AgentGenerationTask` before deleting `AgentTaskRuntimeUpdater` and `AgentGenerationTask`. This includes at minimum `TodoCrudToolHandler`, `DraftGenerationToolHandler`, `RagQueryToolHandler`, `QualityReviewToolHandler`, `ContextEnhancerToolHandler`, `DefaultStoryBibleUpdateApplicationService`, `PluginToolExecuteCommand`, and `PluginToolExecutor`.
- Rewrite Todo runtime linkage end-to-end: `TodoCrudToolDefinition`, `TodoCrudToolHandler`, `TodoCrudApplicationService`, `SessionTodo`, `SessionTodoMapper`, and `TodoController` must use `sourceRunId`/`runId` instead of runtime `taskId`.
- Rewrite RAG runtime linkage end-to-end: `HybridRagQuery`, `RagRetrievalService`, `RagApplicationService`, retrieval log model/mapper/repository, and RAG tool handlers must use `runId` instead of runtime `taskId`.
- Rewrite plugin runtime linkage end-to-end: `PluginToolExecuteCommand`, `PluginToolExecutor`, plugin call log model/mapper/repository, and plugin request JSON must use `runId` instead of runtime `taskId`.
- Rewrite `ApprovalRequest`, `CreateApprovalCommand`, and `ApprovalRequestMapper` so approval persistence can store and query `run_id`. Do not use `task_id` as the approval resume pointer.
- Replace or delete `PendingToolInvocationRepository`, `PendingToolInvocationMapper`, `PendingToolInvocationRepositoryImpl`, and `PendingToolInvocationSnapshot`. They currently point at `pending_tool_invocations`, which must not survive beside `agent_run_pending_approvals`.
- Delete or replace old generation API/usecase/status types: `AgentGenerationAppService`, `AgentTaskDto`, `CreateAgentGenerationDto`, `ApplyAgentGenerationDto`, `AgentTaskStatus`, `AgentTaskStateMachine`, `AgentTaskTransitionPolicy`, and `InvalidAgentTaskTransitionException`.
- Remove all production SQL and Java references to `pending_tool_invocations`; approval resume state is stored only in `agent_run_pending_approvals` and checkpoints.

### Frontend Coverage Amendment

- Rewrite `penmate-frontend/src/stores/workbenchSession.ts` from `activeTask` to `activeRun`.
- Rewrite `penmate-frontend/src/api/types.ts` to remove task-runtime DTOs and `generation.*` event-source types.
- Rewrite `useWorkbenchRuntimePresenter.ts` if present in the branch.
- Remove `openTurnStream`, `getTurnStreamUrl`, `activeTask`, `activeTaskRuntime`, `taskId`, `taskStatus`, `streamChannelKey`, and `generation.*` from production frontend code.
- Rewrite `penmate-frontend/src/test/workbenchRuntimeContract.fixture.ts` and Workbench-related tests so fixtures assert `activeRun`, `runId`, `latestSequence`, and `run.*` events. Do not leave old fixtures that keep the task runtime contract alive.
- `penmate-frontend/src/utils/request.spec.ts` may keep generic safe-integer examples containing `taskId` only if the test is explicitly not an agent runtime contract. All agent/workbench payload examples must use run-shaped fields.

### Review Hardening Amendment

The review found several places where a simple `generation.*` or `agent_task_contexts` scan is insufficient. The implementation must treat these as blockers before claiming the migration is complete:

- There must be one pending-approval persistence model. `agent_run_pending_approvals` replaces both `agent_pending_approvals` and code-level `pending_tool_invocations`.
- Runtime identity is `runId`. Tool calls, RAG retrieval logs, plugin call logs, approval requests, pending approvals, checkpoints, projections, SSE streams, Workbench store state, and recovery payloads must not require `taskId`.
- Old task status machines must not remain as hidden runtime dependencies. Run status and phase transitions belong to `AgentRunStatus`, `AgentRunPhase`, and the run projection updater.
- Old generation endpoints, DTOs, and services must be deleted or replaced, not left unused in production code.
- Event replay and resume must be idempotent: duplicate approval callbacks, duplicate `dispatchResume`, SSE reconnects after terminal events, and concurrent event appends must be covered by tests or explicit locking.
- Large `message.delta` persistence must be bounded. If chunk persistence creates excessive rows, persist only bounded deltas plus `message.completed`/artifact references and document the chosen policy.
- `run.started` has exactly one owner. Either `AgentTurnAppService` publishes it or `AgentRunExecutor` publishes it, but not both. This plan uses `AgentTurnAppService` as the owner; executor starts with `run.phase.changed`.
- `AgentRuntimeState` and checkpoints must contain enough state to resume without the old task snapshot tables: phase, last applied event sequence, LLM turn index, message window, pending approval id, pending tool call id, approved tool payload, assistant tool-calls JSON, remaining tool calls, token usage, active todo/tool projections, and artifact refs.
- `agent_events` is the only reliable delivery source. The in-memory `AgentRunEventBus` is a low-latency notification path only; SSE replay from durable events must recover from missed live broadcasts.
- Event publishing must use after-commit semantics. Append the event and apply projections in the transaction, then publish to the live bus only after commit. If live publish fails after commit, log/metric the failure and rely on reconnect/history replay; do not roll back the committed event.
- Projection handlers must be generally idempotent, not only terminal-state idempotent. `agent_run_projections.latest_sequence` is the apply watermark; if `event.sequence <= latest_sequence`, the projection update is a no-op.
- Tool and todo projections must use upsert semantics keyed by `(run_id, tool_call_id)` and `(run_id, todo_id)`. `message.delta` projection updates must deduplicate by sequence so replay does not duplicate assistant text.
- SSE events must use standard Server-Sent Events ids. Every emitted SSE must set `id = event.sequence`, `event = event.eventType`, and data = `AgentRunEventDto`; the stream endpoint must accept both `after` and `Last-Event-ID`.
- When both `after` and `Last-Event-ID` are provided, use the larger numeric sequence as the replay cursor. Invalid or missing values are treated as `0`.
- Durable and ephemeral event policy is explicit: status changes, approval events, tool terminal results, `message.completed`, `artifact.created`, and `checkpoint.created` are always durable. High-frequency `message.delta` may be bounded durable or live-only, but recovery and final result rendering must rely on `message.completed` and/or artifacts, not live-only deltas.
- External side-effect tools must be idempotent. Use `idempotencyKey = runId + ":" + llmTurnIndex + ":" + toolCallId`; before executing a side-effecting tool, check durable tool projection/events for an existing terminal result and reuse it instead of executing again.
- Run status and phase transitions must be centralized in the new run state machine. Terminal statuses are irreversible.
- Event payloads must be versioned with `schemaVersion` in the envelope or payload. Consumers must ignore unknown fields and handle older schema versions explicitly.
- Retention is not deletion by default. Durable events remain the source of truth; terminal run deltas/checkpoints may be compacted or archived only after result artifacts and recovery requirements are satisfied and the policy is documented.
- Multi-instance deployment must not rely on a process-local event bus for cross-instance delivery. The initial implementation may use an in-memory bus only for single-instance or sticky-session operation; multi-instance deployment requires Redis pub/sub, a message broker, DB polling, or another shared notification layer. Durable `agent_events` replay remains the correctness fallback.

### Verification Amendment

Before claiming the migration is complete, run:

```powershell
rg "V14__agent_run_event_checkpoint_full_refactor|CREATE TABLE IF NOT EXISTS agent_tasks|CREATE TABLE IF NOT EXISTS agent_task_contexts|CREATE TABLE IF NOT EXISTS agent_task_results|agent_pending_approvals|pending_tool_invocations|last_task_id|generation\\.|AgentGenerationWorkflow|AgentGenerationAppService|AgentToolLoopRunner|GenerationSseEmitterHub|GenerationStreamService|AgentSessionRecovery|ToolCallResumeService|PendingToolInvocation|AgentTaskStatus|AgentTaskStateMachine|AgentTaskTransitionPolicy|AgentTaskDto|CreateAgentGenerationDto|ApplyAgentGenerationDto|activeTask|activeTaskRuntime|openTurnStream|getTurnStreamUrl|createGeneration|openGenerationStream|publishGeneration" penmate-backend/src/main penmate-frontend/src penmate-backend/src/main/resources/db/migration
rg "\\btaskId\\b|task_id|getTaskId|setTaskId" penmate-backend/src/main penmate-frontend/src penmate-backend/src/main/resources/db/migration
```

Expected: no production-code or baseline-DDL matches.

---

## Current Code Findings

### Delete / Replace

- Delete: `penmate-backend/src/main/java/com/penmate/backend/application/agent/orchestration/AgentGenerationWorkflow.java`
- Delete: `penmate-backend/src/main/java/com/penmate/backend/application/agent/orchestration/AgentGenerationWorkflowDispatcher.java`
- Delete: `penmate-backend/src/main/java/com/penmate/backend/application/agent/orchestration/AgentToolLoopRunner.java`
- Delete: `penmate-backend/src/main/java/com/penmate/backend/application/agent/orchestration/AgentToolLoopIterationResult.java`
- Delete: `penmate-backend/src/main/java/com/penmate/backend/application/agent/orchestration/AgentTaskRuntimeUpdater.java`
- Delete: `penmate-backend/src/main/java/com/penmate/backend/application/agent/orchestration/AgentResultPublisher.java`
- Delete: `penmate-backend/src/main/java/com/penmate/backend/application/agent/runtime/RealtimeTaskRuntimeStatusPublisher.java`
- Delete: `penmate-backend/src/main/java/com/penmate/backend/application/agent/runtime/TaskRuntimeStatusPublisher.java`
- Delete: `penmate-backend/src/main/java/com/penmate/backend/application/agent/runtime/RuntimeStatusView.java`
- Delete: `penmate-backend/src/main/java/com/penmate/backend/application/agent/runtime/ToolCallStatusView.java`
- Delete: `penmate-backend/src/main/java/com/penmate/backend/application/agent/runtime/StoryBibleApprovalView.java`
- Delete: `penmate-backend/src/main/java/com/penmate/backend/infrastructure/realtime/GenerationSseEmitterHub.java`
- Delete: `penmate-backend/src/main/java/com/penmate/backend/infrastructure/realtime/GenerationStreamServiceImpl.java`
- Delete: `penmate-backend/src/main/java/com/penmate/backend/domain/shared/service/GenerationStreamService.java`
- Delete: `penmate-backend/src/main/java/com/penmate/backend/application/agent/query/AgentSessionRecoveryQueryService.java`
- Delete: `penmate-backend/src/main/java/com/penmate/backend/application/agent/usecase/AgentSessionRecoveryAppService.java`
- Delete: `penmate-backend/src/main/java/com/penmate/backend/application/agent/usecase/AgentSessionRecoveryResult.java`
- Delete: `penmate-backend/src/main/java/com/penmate/backend/domain/agent/model/AgentSessionRecoverySnapshot.java`
- Delete: `penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/runtime/ToolCallResumeService.java`
- Delete: `penmate-backend/src/main/java/com/penmate/backend/application/approval/coordination/AgentApprovalResumeCoordinator.java`
- Delete: `penmate-backend/src/main/java/com/penmate/backend/application/approval/ApprovalAgentResumeCoordinator.java`
- Replace: `penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/gateway/ToolCallApplicationService.java`
- Replace: generation-related methods in `penmate-backend/src/main/java/com/penmate/backend/domain/shared/service/RealtimeEventService.java`
- Replace: generation-related methods in `penmate-backend/src/main/java/com/penmate/backend/infrastructure/realtime/RealtimeEventServiceImpl.java`
- Rewrite: `penmate-backend/src/main/java/com/penmate/backend/application/agent/usecase/AgentTurnAppService.java`
- Rewrite: `penmate-backend/src/main/java/com/penmate/backend/interfaces/api/agent/AgentController.java`
- Rewrite: `penmate-frontend/src/api/modules/agent.api.ts`
- Rewrite: `penmate-frontend/src/composables/workbench/useWorkbenchTaskRuntime.ts`
- Rewrite: `penmate-frontend/src/composables/workbench/useWorkbenchChat.ts`
- Rewrite: `penmate-frontend/src/composables/workbench/useWorkbenchSessionRecovery.ts`
- Rewrite: `penmate-frontend/src/composables/workbench/useWorkbenchRuntimePresenter.ts` if present in the branch
- Rewrite: `penmate-frontend/src/stores/workbenchSession.ts`
- Rewrite: `penmate-frontend/src/api/types.ts`
- Replace task-bound runtime fields in `ToolCallRequest`, `TodoCrudToolHandler`, `AgentSessionRepository`, `AgentSessionMapper`, `ApprovalRequest`, `CreateApprovalCommand`, and `ApprovalRequestMapper`.

### Keep / Migrate

- Keep and inject into new runtime: `AgentLlmGateway`, `AgentLlmTurnRequest`, `AgentLlmTurnResponse`, `AgentLlmToolSchema`
- Keep and inject into new runtime: `AgentModelRoutingService`
- Keep and inject into new runtime: `DefaultAgentPreflightCoordinator`, `AgentPreflightDecision`, `TaskProfileMapper`
- Keep and inject into new runtime: `DefaultAgentContextRoutingFacade`, `ContextPackage`, Story Bible context providers, RAG services
- Keep and inject into new runtime: `PromptComposer`, `SkillPromptRegistry`, `ClasspathSkillPromptRegistry`, `SystemPromptProvider`
- Keep and inject into new runtime: `AgentPromptAssembler`, but move it out of `orchestration` or rename it to `AgentRunPromptMessageBuilder`
- Keep and inject into new runtime: `AgentToolDefinitionSource`, `InMemoryAgentToolDefinitionSource`, all `AgentToolDefinition` classes
- Keep and inject into new runtime: `ToolCallExecutionService`, all `AgentToolHandler` classes, but remove task-runtime dependencies from handlers before deleting old runtime classes. `TodoCrudToolHandler` currently imports `AgentTaskRuntimeUpdater` and uses `taskId`; it must be adapted explicitly.
- Keep and adapt: `DefaultApprovalPolicyEngine`, `ToolApprovalViewFactory`, `ApprovalApplicationService`, approval persistence
- Keep and adapt: `AgentTaskResultRecorder`, but rename to `AgentRunResultRecorder` and write run result/artifacts/messages, not task result snapshots
- Keep: `agent_sessions`, `agent_turns`, `agent_messages`, `agent_session_style_bindings`

---

## Capability Migration Matrix

| Existing capability | Current location | New owner |
|---|---|---|
| Create chat session | `AgentConversationAppService` | Keep |
| Create user turn | `AgentTurnAppService` | Rewrite to create `agent_turns` + `agent_runs` + `agent_run_inputs` |
| Async dispatch | `AgentGenerationWorkflowDispatcher` | Replace with `AgentRunDispatcher` |
| Main workflow | `AgentGenerationWorkflow` | Replace with `AgentRunExecutor` |
| Tool loop | `AgentToolLoopRunner` | Replace with `AgentRunLlmLoop` |
| LLM call | `AgentLlmGateway` | Keep |
| Tool schema exposure | `AgentToolDefinitionSource` | Keep |
| Tool handlers | `AgentToolHandler` implementations | Keep |
| Tool approval governance | `ToolCallApplicationService` | Replace with `AgentToolGovernanceService` |
| Tool direct execution | `ToolCallExecutionService` | Keep |
| Approval resume | `ToolCallResumeService` | Delete; `AgentRunExecutor.resume(runId)` resumes from checkpoint |
| Skill prompts | `PromptComposer` + `SkillPromptRegistry` | Keep |
| Context routing/RAG | `DefaultAgentContextRoutingFacade` | Keep |
| Runtime snapshot | `agent_task_contexts` fields | Replace with `agent_checkpoints` + `agent_run_projections` |
| Realtime stream | `GenerationSseEmitterHub` | Replace with `AgentRunEventStreamService` |
| Recovery | `AgentSessionRecoveryQueryService` | Replace with `AgentRunRecoveryQueryService` |
| Frontend stream reducer | `useWorkbenchTaskRuntime.ts` | Rewrite to `run` event reducer |

---

## Target Event Contract

All event payloads include:

```json
{
  "eventId": "string",
  "runId": "string",
  "projectId": "string",
  "sessionId": "string",
  "turnId": "string",
  "sequence": 1,
  "schemaVersion": 1,
  "type": "run.started",
  "payload": {},
  "createdAt": "2026-06-10T00:00:00Z"
}
```

Durable events:

```text
run.started
run.phase.changed
run.paused
run.resumed
run.completed
run.failed
context.routing.started
context.routing.completed
llm.turn.started
llm.turn.completed
tool.call.started
tool.call.completed
tool.call.failed
tool.call.waiting_approval
approval.requested
approval.approved
approval.rejected
approval.expired
todo.created
todo.updated
todo.completed
todo.failed
message.delta
message.completed
artifact.created
checkpoint.created
```

Durability policy:

- Always durable: `run.*`, `approval.*`, `tool.call.completed`, `tool.call.failed`, `tool.call.waiting_approval`, `message.completed`, `artifact.created`, and `checkpoint.created`.
- Bounded durable or live-only: `message.delta`. If deltas are live-only or compacted, recovery must still render from `message.completed` and/or `agent_artifacts`.
- Query/projection correctness must never depend on a live-only event.

Do not persist one event per token. Persist `message.delta` in chunks only when using bounded durable deltas, emitted every 100-250ms or after accumulated text reaches 80 characters, whichever happens first.

Run status values:

```text
PENDING
RUNNING
WAITING_APPROVAL
COMPLETED
FAILED
CANCELLED
```

Run phase values:

```text
created
preflight
context
prompt
executing
approval
result
terminal
```

Allowed status transitions:

```text
PENDING -> RUNNING, FAILED, CANCELLED
RUNNING -> WAITING_APPROVAL, COMPLETED, FAILED, CANCELLED
WAITING_APPROVAL -> RUNNING, FAILED, CANCELLED
COMPLETED -> terminal
FAILED -> terminal
CANCELLED -> terminal
```

Once a run reaches `COMPLETED`, `FAILED`, or `CANCELLED`, no event handler may move it back to a non-terminal status.

---

## File Structure To Create

### Backend Domain

- Create: `penmate-backend/src/main/java/com/penmate/backend/domain/agent/run/model/AgentRun.java`
- Create: `penmate-backend/src/main/java/com/penmate/backend/domain/agent/run/model/AgentRunStatus.java`
- Create: `penmate-backend/src/main/java/com/penmate/backend/domain/agent/run/model/AgentRunPhase.java`
- Create: `penmate-backend/src/main/java/com/penmate/backend/domain/agent/run/model/AgentRunInput.java`
- Create: `penmate-backend/src/main/java/com/penmate/backend/domain/agent/run/model/AgentEvent.java`
- Create: `penmate-backend/src/main/java/com/penmate/backend/domain/agent/run/model/AgentCheckpoint.java`
- Create: `penmate-backend/src/main/java/com/penmate/backend/domain/agent/run/model/AgentRuntimeState.java`
- Create: `penmate-backend/src/main/java/com/penmate/backend/domain/agent/run/model/AgentRunProjection.java`
- Create: `penmate-backend/src/main/java/com/penmate/backend/domain/agent/run/model/AgentToolCallProjection.java`
- Create: `penmate-backend/src/main/java/com/penmate/backend/domain/agent/run/model/AgentTodoProjection.java`
- Create: `penmate-backend/src/main/java/com/penmate/backend/domain/agent/run/model/AgentArtifact.java`
- Create: `penmate-backend/src/main/java/com/penmate/backend/domain/agent/run/model/AgentPendingApproval.java`
- Create: `penmate-backend/src/main/java/com/penmate/backend/domain/agent/run/repository/AgentRunRepository.java`
- Create: `penmate-backend/src/main/java/com/penmate/backend/domain/agent/run/repository/AgentRunEventRepository.java`
- Create: `penmate-backend/src/main/java/com/penmate/backend/domain/agent/run/repository/AgentCheckpointRepository.java`
- Create: `penmate-backend/src/main/java/com/penmate/backend/domain/agent/run/repository/AgentRunProjectionRepository.java`
- Create: `penmate-backend/src/main/java/com/penmate/backend/domain/agent/run/repository/AgentArtifactRepository.java`
- Create: `penmate-backend/src/main/java/com/penmate/backend/domain/agent/run/repository/AgentPendingApprovalRepository.java`

### Backend Application

- Create: `penmate-backend/src/main/java/com/penmate/backend/application/agent/run/AgentRunAppService.java`
- Create: `penmate-backend/src/main/java/com/penmate/backend/application/agent/run/AgentRunCommand.java`
- Create: `penmate-backend/src/main/java/com/penmate/backend/application/agent/run/AgentRunResult.java`
- Create: `penmate-backend/src/main/java/com/penmate/backend/application/agent/run/AgentRunDispatcher.java`
- Create: `penmate-backend/src/main/java/com/penmate/backend/application/agent/run/AgentRunExecutor.java`
- Create: `penmate-backend/src/main/java/com/penmate/backend/application/agent/run/AgentRunRecoveryQueryService.java`
- Create: `penmate-backend/src/main/java/com/penmate/backend/application/agent/run/AgentRunEventPublisher.java`
- Create: `penmate-backend/src/main/java/com/penmate/backend/application/agent/run/AgentProjectionUpdater.java`
- Create: `penmate-backend/src/main/java/com/penmate/backend/application/agent/run/AgentRuntimeStateReducer.java`
- Create: `penmate-backend/src/main/java/com/penmate/backend/application/agent/run/AgentCheckpointService.java`
- Create: `penmate-backend/src/main/java/com/penmate/backend/application/agent/run/AgentRunContextBuilder.java`
- Create: `penmate-backend/src/main/java/com/penmate/backend/application/agent/run/AgentRunPromptMessageBuilder.java`
- Create: `penmate-backend/src/main/java/com/penmate/backend/application/agent/run/AgentRunLlmLoop.java`
- Create: `penmate-backend/src/main/java/com/penmate/backend/application/agent/run/AgentToolGovernanceService.java`
- Create: `penmate-backend/src/main/java/com/penmate/backend/application/agent/run/AgentRunResultRecorder.java`
- Create: `penmate-backend/src/main/java/com/penmate/backend/application/agent/run/AgentRunStreamEventView.java`

### Backend Infrastructure

- Create: `penmate-backend/src/main/java/com/penmate/backend/infrastructure/persistence/agent/run/AgentRunMapper.java`
- Create: `penmate-backend/src/main/java/com/penmate/backend/infrastructure/persistence/agent/run/AgentRunRepositoryImpl.java`
- Create: `penmate-backend/src/main/java/com/penmate/backend/infrastructure/persistence/agent/run/AgentRunEventMapper.java`
- Create: `penmate-backend/src/main/java/com/penmate/backend/infrastructure/persistence/agent/run/AgentRunEventRepositoryImpl.java`
- Create: `penmate-backend/src/main/java/com/penmate/backend/infrastructure/persistence/agent/run/AgentCheckpointMapper.java`
- Create: `penmate-backend/src/main/java/com/penmate/backend/infrastructure/persistence/agent/run/AgentCheckpointRepositoryImpl.java`
- Create: `penmate-backend/src/main/java/com/penmate/backend/infrastructure/persistence/agent/run/AgentRunProjectionMapper.java`
- Create: `penmate-backend/src/main/java/com/penmate/backend/infrastructure/persistence/agent/run/AgentRunProjectionRepositoryImpl.java`
- Create: `penmate-backend/src/main/java/com/penmate/backend/infrastructure/persistence/agent/run/AgentArtifactMapper.java`
- Create: `penmate-backend/src/main/java/com/penmate/backend/infrastructure/persistence/agent/run/AgentArtifactRepositoryImpl.java`
- Create: `penmate-backend/src/main/java/com/penmate/backend/infrastructure/persistence/agent/run/AgentPendingApprovalMapper.java`
- Create: `penmate-backend/src/main/java/com/penmate/backend/infrastructure/persistence/agent/run/AgentPendingApprovalRepositoryImpl.java`
- Create: `penmate-backend/src/main/java/com/penmate/backend/infrastructure/realtime/AgentRunEventBus.java`
- Create: `penmate-backend/src/main/java/com/penmate/backend/infrastructure/realtime/AgentRunEventStreamService.java`

### Backend Interfaces

- Create: `penmate-backend/src/main/java/com/penmate/backend/interfaces/api/agent/dto/AgentRunDto.java`
- Create: `penmate-backend/src/main/java/com/penmate/backend/interfaces/api/agent/dto/AgentRunRecoveryDto.java`
- Create: `penmate-backend/src/main/java/com/penmate/backend/interfaces/api/agent/dto/AgentRunEventDto.java`
- Modify: `penmate-backend/src/main/java/com/penmate/backend/interfaces/api/agent/AgentController.java`

### Frontend

- Create: `penmate-frontend/src/composables/workbench/useAgentRunRuntime.ts`
- Create: `penmate-frontend/src/composables/workbench/useAgentRunReducer.ts`
- Modify: `penmate-frontend/src/api/modules/agent.api.ts`
- Modify: `penmate-frontend/src/api/types.ts`
- Modify: `penmate-frontend/src/composables/workbench/useWorkbenchChat.ts`
- Modify: `penmate-frontend/src/composables/workbench/useWorkbenchSessionRecovery.ts`
- Delete: `penmate-frontend/src/composables/workbench/useWorkbenchTaskRuntime.ts`

---

### Task 1: Baseline Schema Reset To Agent Run Runtime

**Files:**
- Modify: `penmate-backend/src/main/resources/db/migration/V2__init_novel_and_approval_minimal.sql`
- Modify: `penmate-backend/src/main/resources/db/migration/V3__init_storage_and_rag_minimal.sql`
- Modify: `penmate-backend/src/main/resources/db/migration/V10__init_plugin_and_model_domains.sql`
- Modify: `penmate-backend/src/main/resources/db/migration/V11__init_agent_and_ops_domains.sql`
- Modify: `penmate-backend/src/main/resources/db/migration/V12__init_pending_tool_invocations.sql`
- Modify: `penmate-backend/src/main/resources/db/migration/V13__init_agent_session_todos.sql`
- Test: `penmate-backend/src/test/resources/db/cases/seed_agent_run_runtime_base.sql`

- [ ] **Step 1: Rewrite baseline DDL in place**

Do not create `V14__agent_run_event_checkpoint_full_refactor.sql`. Edit existing baseline DDL files:

- `V11__init_agent_and_ops_domains.sql`: remove `agent_tasks`, `agent_task_contexts`, and `agent_task_results`; replace `last_task_id` with `last_run_id`; replace `agent_turns.task_id` with `agent_turns.run_id`; add the new run/event/checkpoint/projection/artifact tables.
- `V12__init_pending_tool_invocations.sql`: replace `agent_pending_approvals` with `agent_run_pending_approvals`.
- `V2__init_novel_and_approval_minimal.sql`: replace `agent_approval_requests.task_id` with `run_id`.
- `V3__init_storage_and_rag_minimal.sql`: replace `rag_retrieval_logs.task_id` with `run_id` and rename `idx_rag_retrieval_task_created`.
- `V10__init_plugin_and_model_domains.sql`: replace `plugin_call_logs.task_id` with `run_id` and rename `idx_plugin_call_task`.
- `V13__init_agent_session_todos.sql`: replace `agent_session_todos.task_id` with `source_run_id` and add `idx_agent_session_todos_source_run (source_run_id)`.

Use the table definitions below as the baseline shapes, but do not include the `DROP TABLE` statements in any migration file. If a definition below still contains mojibake comments, replace the comment with plain valid ASCII SQL comments before implementing.

```sql
CREATE TABLE IF NOT EXISTS agent_runs (
    id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
    run_id BIGINT UNSIGNED NOT NULL,
    project_id BIGINT UNSIGNED NOT NULL,
    session_id BIGINT UNSIGNED NOT NULL,
    turn_id BIGINT UNSIGNED NOT NULL,
    owner_user_id BIGINT UNSIGNED NOT NULL,
    run_status VARCHAR(32) NOT NULL,
    run_phase VARCHAR(64) NOT NULL,
    active_approval_id BIGINT UNSIGNED NULL,
    latest_event_seq BIGINT UNSIGNED NOT NULL DEFAULT 0,
    latest_checkpoint_id BIGINT UNSIGNED NULL,
    trace_id VARCHAR(64) NULL,
    started_at DATETIME(3) NULL,
    finished_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_agent_runs_run_id (run_id),
    UNIQUE KEY uk_agent_runs_turn_id (turn_id),
    KEY idx_agent_runs_session_updated (session_id, updated_at),
    KEY idx_agent_runs_project_status (project_id, run_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent Run 鎵ц涓昏〃';

CREATE TABLE IF NOT EXISTS agent_run_inputs (
    id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
    run_id BIGINT UNSIGNED NOT NULL,
    prompt_snapshot LONGTEXT NOT NULL,
    task_type VARCHAR(32) NOT NULL,
    chapter_id BIGINT UNSIGNED NULL,
    selected_text LONGTEXT NULL,
    style_snapshot_json LONGTEXT NULL,
    model_snapshot_json LONGTEXT NULL,
    plugin_bindings_json LONGTEXT NULL,
    input_hash VARCHAR(128) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_agent_run_inputs_run_id (run_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent Run 杈撳叆蹇収琛?;

CREATE TABLE IF NOT EXISTS agent_events (
    id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
    event_id BIGINT UNSIGNED NOT NULL,
    run_id BIGINT UNSIGNED NOT NULL,
    project_id BIGINT UNSIGNED NOT NULL,
    session_id BIGINT UNSIGNED NOT NULL,
    turn_id BIGINT UNSIGNED NOT NULL,
    sequence BIGINT UNSIGNED NOT NULL,
    schema_version INT UNSIGNED NOT NULL DEFAULT 1,
    event_type VARCHAR(96) NOT NULL,
    payload_json LONGTEXT NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_agent_events_event_id (event_id),
    UNIQUE KEY uk_agent_events_run_seq (run_id, sequence),
    KEY idx_agent_events_run_type (run_id, event_type),
    KEY idx_agent_events_session_created (session_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent Run 浜嬩欢娴佽〃';

CREATE TABLE IF NOT EXISTS agent_checkpoints (
    id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
    checkpoint_id BIGINT UNSIGNED NOT NULL,
    run_id BIGINT UNSIGNED NOT NULL,
    checkpoint_no BIGINT UNSIGNED NOT NULL,
    last_event_seq BIGINT UNSIGNED NOT NULL,
    state_json LONGTEXT NOT NULL,
    state_size_bytes INT UNSIGNED NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_agent_checkpoints_checkpoint_id (checkpoint_id),
    UNIQUE KEY uk_agent_checkpoints_run_no (run_id, checkpoint_no),
    KEY idx_agent_checkpoints_run_latest (run_id, checkpoint_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent Run 鍙仮澶?checkpoint 琛?;

CREATE TABLE IF NOT EXISTS agent_run_projections (
    id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
    run_id BIGINT UNSIGNED NOT NULL,
    project_id BIGINT UNSIGNED NOT NULL,
    session_id BIGINT UNSIGNED NOT NULL,
    turn_id BIGINT UNSIGNED NOT NULL,
    run_status VARCHAR(32) NOT NULL,
    run_phase VARCHAR(64) NOT NULL,
    status_message VARCHAR(500) NULL,
    active_approval_id BIGINT UNSIGNED NULL,
    latest_sequence BIGINT UNSIGNED NOT NULL DEFAULT 0,
    last_error_code VARCHAR(96) NULL,
    last_error_message VARCHAR(500) NULL,
    current_assistant_message_id BIGINT UNSIGNED NULL,
    result_artifact_id BIGINT UNSIGNED NULL,
    token_usage_json LONGTEXT NULL,
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_agent_run_projections_run_id (run_id),
    KEY idx_agent_run_projections_session (session_id, updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent Run 鏌ヨ鎶曞奖瑙嗗浘';

CREATE TABLE IF NOT EXISTS agent_tool_call_projections (
    id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
    run_id BIGINT UNSIGNED NOT NULL,
    tool_call_id VARCHAR(128) NOT NULL,
    tool_code VARCHAR(100) NOT NULL,
    tool_name VARCHAR(200) NULL,
    status VARCHAR(32) NOT NULL,
    iteration INT NULL,
    arguments_preview_json LONGTEXT NULL,
    output_preview LONGTEXT NULL,
    output_artifact_id BIGINT UNSIGNED NULL,
    approval_id BIGINT UNSIGNED NULL,
    error_code VARCHAR(96) NULL,
    error_message VARCHAR(500) NULL,
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_agent_tool_call_projection (run_id, tool_call_id),
    KEY idx_agent_tool_call_run_status (run_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent tool 璋冪敤鎶曞奖瑙嗗浘';

CREATE TABLE IF NOT EXISTS agent_todo_projections (
    id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
    run_id BIGINT UNSIGNED NOT NULL,
    todo_id VARCHAR(128) NOT NULL,
    title VARCHAR(500) NOT NULL,
    status VARCHAR(32) NOT NULL,
    sort_order INT NOT NULL,
    blocked_reason VARCHAR(500) NULL,
    error_summary VARCHAR(500) NULL,
    completed_summary VARCHAR(500) NULL,
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_agent_todo_projection (run_id, todo_id),
    KEY idx_agent_todo_run_status (run_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent Todo 鎶曞奖瑙嗗浘';

CREATE TABLE IF NOT EXISTS agent_artifacts (
    id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
    artifact_id BIGINT UNSIGNED NOT NULL,
    run_id BIGINT UNSIGNED NOT NULL,
    artifact_type VARCHAR(64) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    content_text LONGTEXT NULL,
    metadata_json LONGTEXT NULL,
    size_bytes INT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_agent_artifacts_artifact_id (artifact_id),
    KEY idx_agent_artifacts_run_type (run_id, artifact_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent artifact/blob 琛?;

CREATE TABLE IF NOT EXISTS agent_run_pending_approvals (
    id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
    pending_approval_id BIGINT UNSIGNED NOT NULL,
    approval_id BIGINT UNSIGNED NOT NULL,
    run_id BIGINT UNSIGNED NOT NULL,
    project_id BIGINT UNSIGNED NOT NULL,
    session_id BIGINT UNSIGNED NOT NULL,
    turn_id BIGINT UNSIGNED NOT NULL,
    tool_call_id VARCHAR(128) NOT NULL,
    tool_code VARCHAR(100) NOT NULL,
    tool_args_json LONGTEXT NULL,
    tool_context_json LONGTEXT NULL,
    resume_payload_json LONGTEXT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    pending_status VARCHAR(24) NOT NULL DEFAULT 'PENDING',
    operator_id BIGINT UNSIGNED NULL,
    trace_id VARCHAR(64) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_agent_run_pending_approvals_pending_id (pending_approval_id),
    UNIQUE KEY uk_agent_run_pending_approvals_approval_id (approval_id),
    UNIQUE KEY uk_agent_run_pending_approvals_idempotency (idempotency_key),
    KEY idx_agent_run_pending_approvals_run_status (run_id, pending_status),
    KEY idx_agent_run_pending_approvals_session_status (session_id, pending_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent Run 瀹℃壒鎸傝捣琛?;
```

- [ ] **Step 2: Add seed fixture for repository tests**

Create `seed_agent_run_runtime_base.sql`:

```sql
INSERT INTO agent_sessions(session_id, project_id, owner_user_id, title, session_status)
VALUES (90001, 101, 201, 'Run runtime session', 'ACTIVE');

INSERT INTO agent_turns(turn_id, session_id, turn_seq, user_message_id, run_id, turn_status)
VALUES (50001, 90001, 1, 60001, 70001, 'PENDING');

INSERT INTO agent_runs(run_id, project_id, session_id, turn_id, owner_user_id, run_status, run_phase, latest_event_seq)
VALUES (70001, 101, 90001, 50001, 201, 'PENDING', 'created', 0);

INSERT INTO agent_run_inputs(run_id, prompt_snapshot, task_type, chapter_id, selected_text, input_hash)
VALUES (70001, 'Write a suspense opening.', 'WRITE', 30001, 'selected text', 'hash-70001');
```

- [ ] **Step 3: Apply review corrections before leaving schema task**

The old draft SQL above is not authoritative where it conflicts with this step. Apply these corrections:

- Do not keep any `DROP TABLE` statement in a new migration.
- Do not keep `agent_pending_approvals`; use `agent_run_pending_approvals`.
- `agent_run_pending_approvals` must include `run_id`, `project_id`, `session_id`, `turn_id`, `tool_context_json`, `resume_payload_json`, `operator_id`, and `trace_id`.
- `agent_approval_requests` must use `run_id` and `idx_approval_run`; it must not keep `task_id` or `idx_approval_task`.
- `rag_retrieval_logs` and `plugin_call_logs` must use `run_id` for Agent runtime linkage. If either table is proven to be intentionally non-runtime-scoped, document the exception in `docs/analysis/2026-06-10-agent-run-full-refactor-result.md` and keep the final forbidden scan narrow enough not to hide real runtime task leakage.
- `agent_session_todos` must use `source_run_id`, not `task_id`. The Todo API may expose `sourceRunId` only; it must not accept or return runtime `taskId`.
- `seed_agent_run_runtime_base.sql` must insert `agent_turns.run_id`, not `agent_turns.task_id`.
- Seed prompt text must be valid quoted SQL, for example `'Write a suspense opening.'`, not mojibake.
- SQL comments in the copied table shapes must be replaced with valid ASCII comments before committing.

- [ ] **Step 4: Run baseline schema checks**

Run:

```powershell
cd penmate-backend
rg "V14__agent_run_event_checkpoint_full_refactor|DROP TABLE IF EXISTS agent_task|DROP TABLE IF EXISTS agent_pending_approvals|CREATE TABLE IF NOT EXISTS agent_tasks|CREATE TABLE IF NOT EXISTS agent_task_contexts|CREATE TABLE IF NOT EXISTS agent_task_results|CREATE TABLE IF NOT EXISTS agent_pending_approvals|pending_tool_invocations|last_task_id|idx_approval_task|idx_plugin_call_task|idx_rag_retrieval_task_created| task_id |\\btaskId\\b" src/main/resources/db/migration src/test/resources/db/cases/seed_agent_run_runtime_base.sql
mvn -Dtest=OpenApiArtifactContractTest test
```

Expected: `rg` finds no matches. Existing OpenAPI test still compiles. If Flyway migration validation runs in the chosen test profile, the edited baseline DDL must apply without duplicate table errors.

---

### Task 2: Domain Runtime Model

**Files:**
- Create: domain model/repository files listed in "Backend Domain"
- Test: `penmate-backend/src/test/java/com/penmate/backend/domain/agent/run/AgentRuntimeStateReducerContractTest.java`

- [ ] **Step 1: Write reducer contract test**

Create `AgentRuntimeStateReducerContractTest`:

```java
package com.penmate.backend.domain.agent.run;

import com.penmate.backend.application.agent.run.AgentRuntimeStateReducer;
import com.penmate.backend.domain.agent.run.model.AgentEvent;
import com.penmate.backend.domain.agent.run.model.AgentRuntimeState;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AgentRuntimeStateReducerContractTest {

    @Test
    void applies_run_tool_message_and_approval_events() {
        AgentRuntimeState state = AgentRuntimeState.empty(70001L);
        AgentRuntimeStateReducer reducer = new AgentRuntimeStateReducer();

        AgentRuntimeState reduced = reducer.applyAll(state, List.of(
                AgentEvent.replay(1L, 70001L, 1L, "run.started", "{\"phase\":\"created\"}"),
                AgentEvent.replay(2L, 70001L, 2L, "tool.call.started", "{\"toolCallId\":\"call-1\",\"toolCode\":\"draft_generation\"}"),
                AgentEvent.replay(3L, 70001L, 3L, "tool.call.waiting_approval", "{\"toolCallId\":\"call-1\",\"approvalId\":88001}"),
                AgentEvent.replay(4L, 70001L, 4L, "message.delta", "{\"text\":\"abc\"}")
        ));

        assertThat(reduced.runId()).isEqualTo(70001L);
        assertThat(reduced.phase()).isEqualTo("preflight");
        assertThat(reduced.activeApprovalId()).isEqualTo(88001L);
        assertThat(reduced.assistantDraft()).isEqualTo("abc");
        assertThat(reduced.lastEventSeq()).isEqualTo(4L);
    }
}
```

- [ ] **Step 2: Implement model records/classes**

Define model classes with explicit constructors and minimal behavior:

```java
public record AgentEvent(
        Long eventId,
        Long runId,
        Long projectId,
        Long sessionId,
        Long turnId,
        Long sequence,
        Integer schemaVersion,
        String eventType,
        String payloadJson,
        java.time.LocalDateTime createdAt
) {
    public static AgentEvent replay(Long eventId, Long runId, Long sequence, String eventType, String payloadJson) {
        return new AgentEvent(eventId, runId, null, null, null, sequence, 1, eventType, payloadJson, null);
    }
}
```

```java
public record AgentRuntimeState(
        Long runId,
        String status,
        String phase,
        Long activeApprovalId,
        Long lastEventSeq,
        String assistantDraft,
        java.util.List<com.penmate.backend.domain.agent.model.AgentLlmMessage> llmMessages
) {
    public static AgentRuntimeState empty(Long runId) {
        return new AgentRuntimeState(runId, "PENDING", "created", null, 0L, "", java.util.List.of());
    }
}
```

- [ ] **Step 3: Run domain test**

Run:

```powershell
cd penmate-backend
mvn -Dtest=AgentRuntimeStateReducerContractTest test
```

Expected before reducer implementation: compile or assertion failure. Expected after reducer implementation: PASS.

---

### Task 3: MyBatis Persistence For Runs, Events, Checkpoints, Projections, Artifacts

**Files:**
- Create infrastructure mapper/repository files listed in "Backend Infrastructure"
- Test: `penmate-backend/src/test/java/com/penmate/backend/infrastructure/persistence/agent/run/AgentRunEventRepositoryImplTest.java`
- Test: `penmate-backend/src/test/java/com/penmate/backend/infrastructure/persistence/agent/run/AgentCheckpointRepositoryImplTest.java`

- [ ] **Step 1: Write event repository test**

Create a test proving append assigns ordered sequence and updates `agent_runs.latest_event_seq`:

```java
@Test
void append_event_locks_run_and_increments_sequence() {
    AgentEvent first = repository.append(70001L, "run.started", "{\"phase\":\"created\"}");
    AgentEvent second = repository.append(70001L, "run.phase.changed", "{\"phase\":\"context\"}");

    assertThat(first.sequence()).isEqualTo(1L);
    assertThat(second.sequence()).isEqualTo(2L);
    assertThat(repository.listAfter(70001L, 0L)).extracting(AgentEvent::eventType)
            .containsExactly("run.started", "run.phase.changed");
}
```

Add a concurrent append test:

```java
@Test
void concurrent_appends_for_same_run_get_unique_ordered_sequences() throws Exception {
    ExecutorService executor = Executors.newFixedThreadPool(2);
    List<Future<AgentEvent>> futures = List.of(
            executor.submit(() -> repository.append(70001L, "message.delta", "{\"text\":\"a\"}")),
            executor.submit(() -> repository.append(70001L, "message.delta", "{\"text\":\"b\"}"))
    );

    List<Long> sequences = futures.stream()
            .map(future -> uncheckedGet(future).sequence())
            .sorted()
            .toList();

    assertThat(sequences).containsExactly(1L, 2L);
}
```

- [ ] **Step 2: Implement append with row lock**

`AgentRunEventMapper` must expose:

```java
@Select("""
        SELECT latest_event_seq
        FROM agent_runs
        WHERE run_id = #{runId}
        FOR UPDATE
        """)
Long lockLatestSequence(Long runId);

@Update("""
        UPDATE agent_runs
        SET latest_event_seq = #{sequence},
            updated_at = CURRENT_TIMESTAMP(3)
        WHERE run_id = #{runId}
        """)
int updateLatestSequence(Long runId, Long sequence);
```

Repository append must run inside a transaction:

```java
@Transactional
public AgentEvent append(Long runId, String eventType, String payloadJson) {
    Long latest = mapper.lockLatestSequence(runId);
    if (latest == null) {
        throw new IllegalArgumentException("run not found: " + runId);
    }
    long next = latest + 1L;
    AgentRun run = runMapper.findByRunId(runId);
    AgentEvent event = new AgentEvent(idGenerator.nextId(), runId, run.projectId(), run.sessionId(), run.turnId(), next, 1, eventType, payloadJson, null);
    mapper.insert(event);
    mapper.updateLatestSequence(runId, next);
    return event;
}
```

`agent_events` insert must store `schema_version = 1`. Keep `UNIQUE KEY uk_agent_events_run_seq (run_id, sequence)` as the final guard against duplicate ordering.

- [ ] **Step 3: Write checkpoint repository test**

Verify latest checkpoint lookup:

```java
@Test
void finds_latest_checkpoint_by_run() {
    repository.save(new AgentCheckpoint(80001L, 70001L, 1L, 5L, "{\"phase\":\"context\"}", 19, null));
    repository.save(new AgentCheckpoint(80002L, 70001L, 2L, 9L, "{\"phase\":\"tool_call\"}", 21, null));

    AgentCheckpoint latest = repository.findLatest(70001L);

    assertThat(latest.checkpointNo()).isEqualTo(2L);
    assertThat(latest.lastEventSeq()).isEqualTo(9L);
}
```

- [ ] **Step 4: Run persistence tests**

Run:

```powershell
cd penmate-backend
mvn -Dtest=AgentRunEventRepositoryImplTest,AgentCheckpointRepositoryImplTest test
```

Expected: PASS.

---

### Task 4: Event Publisher, Projection Updater, Checkpoint Service

**Files:**
- Create: `AgentRunEventPublisher.java`
- Create: `AgentProjectionUpdater.java`
- Create: `AgentRuntimeStateReducer.java`
- Create: `AgentCheckpointService.java`
- Test: `penmate-backend/src/test/java/com/penmate/backend/application/agent/run/AgentRunEventPublisherTest.java`
- Test: `penmate-backend/src/test/java/com/penmate/backend/application/agent/run/AgentCheckpointServiceTest.java`

- [ ] **Step 1: Write publisher test**

```java
@Test
void publish_appends_event_updates_projection_and_broadcasts_after_commit() {
    AgentEvent appended = new AgentEvent(1L, 70001L, 101L, 90001L, 50001L, 1L, 1, "run.started", "{\"phase\":\"created\"}", null);
    when(eventRepository.append(70001L, "run.started", "{\"phase\":\"created\"}")).thenReturn(appended);

    AgentEvent result = publisher.publish(70001L, "run.started", Map.of("phase", "created"));

    assertThat(result.sequence()).isEqualTo(1L);
    verify(projectionUpdater).apply(appended);
    verify(eventBus).publish(appended);
}
```

Also add an idempotent projection test:

```java
@Test
void projection_ignores_events_at_or_below_latest_applied_sequence() {
    when(runProjectionRepository.findLatestSequence(70001L)).thenReturn(5L);

    projectionUpdater.apply(event(70001L, 5L, "message.delta", Map.of("text", "abc")));

    verify(runProjectionRepository, never()).appendAssistantDelta(any(), any(), any());
}
```

- [ ] **Step 2: Implement publisher**

Rules:

- Serialize payload with Jackson.
- Append event first.
- Apply projection in the same transaction.
- Register live bus publish with transaction after-commit synchronization. Do not publish inside the transaction before commit.
- `agent_events` is the durable source of truth. If after-commit bus publish fails, log and emit a metric; do not roll back or delete the committed event.
- Event envelope or payload must include `schemaVersion`.

```java
@Transactional
public AgentEvent publish(Long runId, String eventType, Object payload) {
    String payloadJson = toJson(withSchemaVersion(payload == null ? Map.of() : payload));
    AgentEvent event = eventRepository.append(runId, eventType, payloadJson);
    projectionUpdater.apply(event);
    afterCommit(() -> {
        try {
            eventBus.publish(event);
        } catch (RuntimeException ex) {
            log.warn("agent run live event publish failed after commit: runId={}, sequence={}, eventType={}",
                    event.runId(), event.sequence(), event.eventType(), ex);
        }
    });
    return event;
}
```

If the deployment needs cross-instance delivery, replace or complement the in-memory bus with a shared mechanism such as Redis pub/sub, a broker, or DB polling. Do not treat a process-local bus as reliable multi-instance delivery.

- [ ] **Step 3: Implement projection updater**

Projection mapping:

- `run.started`: run status `RUNNING`, phase from payload.
- `run.phase.changed`: phase from payload.
- `tool.call.started`: upsert tool projection status `running`.
- `tool.call.completed`: upsert status `success`, output preview/artifact ref.
- `tool.call.failed`: upsert status `failed`, error fields.
- `tool.call.waiting_approval`: run status `WAITING_APPROVAL`, active approval id.
- `approval.approved`: run status `RUNNING`.
- `approval.rejected`: run status `FAILED`.
- `message.delta`: update current assistant draft in projection if needed.
- `message.completed`: set current assistant message id.
- `run.completed`: run status `DONE`.
- `run.failed`: run status `FAILED`, error fields.

`run.started` is published only by `AgentTurnAppService.createTurn()` after the run row and input snapshot are persisted. `AgentRunExecutor.execute()` must not publish `run.started`; its first event is `run.phase.changed` with phase `preflight`.

Projection idempotency rules:

- `agent_run_projections.latest_sequence` is the projection apply watermark.
- If `event.sequence <= latest_sequence`, return without mutating projection rows.
- Apply projection changes and advance `latest_sequence` in the same transaction.
- Terminal transitions are idempotent. The projection updater must ignore duplicate terminal events for an already terminal run and must not move a run from `COMPLETED`, `FAILED`, or `CANCELLED` back to `RUNNING`.
- `message.delta` updates must deduplicate by sequence. Replay must not append the same delta twice.
- Tool and todo projections must upsert by `(run_id, tool_call_id)` and `(run_id, todo_id)`.

- [ ] **Step 4: Implement checkpoint service**

Checkpoint policy:

```java
public boolean shouldCheckpoint(AgentEvent event, AgentRuntimeState state) {
    if (event.eventType().equals("run.started")) return true;
    if (event.eventType().equals("context.routing.completed")) return true;
    if (event.eventType().equals("tool.call.waiting_approval")) return true;
    if (event.eventType().equals("run.completed")) return true;
    if (event.eventType().equals("run.failed")) return true;
    return event.sequence() % 15L == 0L;
}
```

State larger than 256KB must be stored as an artifact and referenced from checkpoint JSON:

```json
{
  "stateArtifactId": "81001",
  "stateSizeBytes": 440000
}
```

- [ ] **Step 5: Run tests**

```powershell
cd penmate-backend
mvn -Dtest=AgentRunEventPublisherTest,AgentCheckpointServiceTest test
```

Expected: PASS.

---

### Task 5: Run Creation Replaces Task Creation

**Files:**
- Modify: `AgentTurnAppService.java`
- Create: `AgentRunAppService.java`
- Create: `AgentRunCommand.java`
- Create: `AgentRunResult.java`
- Modify: `AgentController.java`
- Test: `penmate-backend/src/test/java/com/penmate/backend/application/agent/run/AgentRunAppServiceTest.java`
- Test: `penmate-backend/src/test/java/com/penmate/backend/interfaces/api/agent/AgentControllerRunContractTest.java`

- [ ] **Step 1: Write application test**

```java
@Test
void create_turn_persists_message_turn_run_input_and_run_started_event() {
    AgentTurnResult result = service.createTurn(101L, 90001L, command(), "trace-1");

    assertThat(result.activeRun().runId()).isNotNull();
    verify(agentRunRepository).insert(any(AgentRun.class));
    verify(agentRunRepository).insertInput(any(AgentRunInput.class));
    verify(eventPublisher).publish(anyLong(), eq("run.started"), any());
    verify(runDispatcher).dispatchInitialRun(eq(result.activeRun().runId()), eq("trace-1"));
}
```

- [ ] **Step 2: Change turn result contract**

`AgentTurnResult` should return `activeRun`, not `activeTask`:

```java
public record AgentTurnResult(
        SessionView session,
        ActiveRunView activeRun,
        String taskType,
        String userMessage
) {
    public record ActiveRunView(Long turnId, Long runId, String runStatus, String runPhase, Long latestSequence) {}
}
```

`latestSequence` must reflect the sequence returned by publishing `run.started`. In the normal create-turn response it should be `1`, and frontend streaming should reconnect with `after=latestSequence` only when resuming an already-started run. A newly submitted user turn may stream from `after=0`.

- [ ] **Step 3: Rewrite turn creation**

`AgentTurnAppService.createTurn()` must:

1. Insert user message into `agent_messages`.
2. Insert `agent_turns` with `run_id = runId`.
3. Insert `agent_runs`.
4. Insert `agent_run_inputs`.
5. Update `agent_sessions.last_turn_id`.
6. Update `agent_sessions.last_run_id`.
7. Publish `run.started`.
8. Dispatch `AgentRunDispatcher.dispatchInitialRun(runId, traceId)`.

It must not call:

```java
agentSessionRepository.insertTaskContext(...)
agentSessionRepository.insertRuntimeTask(...)
agentSessionRepository.updateLastRunningTask(...)
agentSessionRepository.updateRuntimeTaskTurnLink(...)
```

- [ ] **Step 4: Rewrite controller endpoint**

`POST /api/v1/novels/{projectId}/agent/sessions/{sessionId}/turns` returns:

```json
{
  "session": { "sessionId": "90001", "status": "ACTIVE" },
  "activeRun": { "turnId": "50001", "runId": "70001", "runStatus": "running", "runPhase": "created", "latestSequence": "1" },
  "taskType": "WRITE",
  "userMessage": "..."
}
```

- [ ] **Step 5: Run focused tests**

```powershell
cd penmate-backend
mvn -Dtest=AgentRunAppServiceTest,AgentControllerRunContractTest test
```

Expected: PASS.

---

### Task 6: AgentRunExecutor Replaces AgentGenerationWorkflow

**Files:**
- Create: `AgentRunExecutor.java`
- Create: `AgentRunDispatcher.java`
- Create: `AgentRunLlmLoop.java`
- Create: `AgentRunContextBuilder.java`
- Create: `AgentRunPromptMessageBuilder.java`
- Modify: `AgentPromptAssembler.java` or move logic into `AgentRunPromptMessageBuilder.java`
- Test: `penmate-backend/src/test/java/com/penmate/backend/application/agent/run/AgentRunExecutorTest.java`
- Test: `penmate-backend/src/test/java/com/penmate/backend/application/agent/run/AgentRunLlmLoopTest.java`

- [ ] **Step 1: Write executor phase test**

```java
@Test
void executor_runs_preflight_context_prompt_llm_and_completion_events() {
    when(runRepository.findInput(70001L)).thenReturn(runInput());
    when(preflightCoordinator.coordinate(any())).thenReturn(preflightDecision());
    when(contextRoutingFacade.route(any())).thenReturn(contextRoutingResult());
    when(llmLoop.execute(any())).thenReturn(AgentRunLoopResult.completed("瀹屾垚鏂囨湰", tokenUsage()));

    executor.execute(70001L, "trace-1");

    verify(eventPublisher).publish(eq(70001L), eq("run.phase.changed"), argThat(payload -> payload.toString().contains("preflight")));
    verify(eventPublisher).publish(eq(70001L), eq("context.routing.completed"), any());
    verify(eventPublisher).publish(eq(70001L), eq("message.completed"), any());
    verify(eventPublisher).publish(eq(70001L), eq("run.completed"), any());
}
```

- [ ] **Step 2: Implement executor phases**

`AgentRunExecutor.execute(runId, traceId)`:

```text
load run + input
do not publish run.started
publish run.phase.changed(preflight)
preflightCoordinator.coordinate(...)
publish run.phase.changed(context)
contextRoutingFacade.route(...)
publish context.routing.completed
publish run.phase.changed(prompt)
promptComposer.compose(...)
publish run.phase.changed(executing)
llmLoop.execute(...)
if waiting approval: checkpoint and return
record final message/result artifact
publish message.completed
publish run.completed
checkpoint
```

- [ ] **Step 3: Implement resume path**

`AgentRunExecutor.resume(runId, traceId)`:

```text
load latest checkpoint
load events after checkpoint
rebuild AgentRuntimeState
CAS pending approval APPROVED -> RESUMING before executing the approved tool
if state has pending approval:
  execute approved tool without asking approval again
  continue remaining tool calls and LLM turns
else:
  continue from phase
mark pending approval RESUMED only after the resumed tool result has been persisted
```

Do not call `ToolCallResumeService`.

- [ ] **Step 4: Implement LLM loop**

`AgentRunLlmLoop` owns:

- max LLM turns = 4
- max tool calls per turn = 3
- LLM call events: `llm.turn.started`, `llm.turn.completed`
- tool call events: `tool.call.started`, `tool.call.completed`, `tool.call.failed`, `tool.call.waiting_approval`
- `message.delta` chunks from final assistant text

It receives initial messages from `AgentRunPromptMessageBuilder` and appends tool result messages to in-memory runtime state.

`message.delta` chunking is bounded: emit and persist chunks every 100-250ms or once 80 accumulated characters are available, but never persist one event per token. The final text must be stored in `message.completed` and/or an artifact according to Task 11.

- [ ] **Step 5: Run tests**

```powershell
cd penmate-backend
mvn -Dtest=AgentRunExecutorTest,AgentRunLlmLoopTest test
```

Expected: PASS.

---

### Task 7: Tool Governance Migration

**Files:**
- Create: `AgentToolGovernanceService.java`
- Modify: `ToolCallApplicationService.java` or delete after callers move
- Modify: `penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/runtime/ToolCallRequest.java`
- Modify: `penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/handler/TodoCrudToolHandler.java`
- Modify: `penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/handler/DraftGenerationToolHandler.java`
- Modify: `penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/handler/RagQueryToolHandler.java`
- Modify: `penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/handler/QualityReviewToolHandler.java`
- Modify: `penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/handler/ContextEnhancerToolHandler.java`
- Modify: `penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/DefaultStoryBibleUpdateApplicationService.java`
- Modify: `penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/plugin/PluginToolExecuteCommand.java`
- Modify: `penmate-backend/src/main/java/com/penmate/backend/application/agent/tool/plugin/PluginToolExecutor.java`
- Keep: `ToolCallExecutionService.java`
- Keep: `AgentToolDefinitionSource.java`
- Keep: `InMemoryAgentToolDefinitionSource.java`
- Keep: all `application/agent/tool/definition/*ToolDefinition.java`
- Keep: all `application/agent/tool/handler/*ToolHandler.java`
- Test: `penmate-backend/src/test/java/com/penmate/backend/application/agent/run/AgentToolGovernanceServiceTest.java`

- [ ] **Step 1: Write run-shaped tool request and handler tests**

Add tests proving the tool layer no longer depends on task runtime:

```java
@Test
void tool_request_uses_run_identity_without_task_id() {
    AgentRunToolCallRequest request = requestForRun(70001L, 90001L, 50001L, "todo_crud");

    assertThat(request.runId()).isEqualTo(70001L);
    assertThat(request.sessionId()).isEqualTo(90001L);
    assertThat(request.turnId()).isEqualTo(50001L);
}

@Test
void todo_crud_handler_serializes_output_without_agent_task_runtime_updater() {
    ToolCallResult result = handler.execute(requestForRun(70001L, 90001L, 50001L, "todo_crud"));

    assertThat(result.status()).isEqualTo("SUCCESS");
    assertThat(result.outputText()).contains("\"operation\"");
}
```

- [ ] **Step 2: Rewrite `ToolCallRequest` for Agent Run**

Replace task-bound fields with run-bound fields:

```java
public record ToolCallRequest(
        Long projectId,
        Long runId,
        Long sessionId,
        Long turnId,
        String toolCode,
        String toolArgsJson,
        Long operatorId,
        String traceId,
        String contextJson,
        String idempotencyKey,
        Integer llmTurnIndex,
        String toolCallId,
        String assistantToolCallsJson,
        String conversationMessagesJson,
        String resumeMode,
        String approvalSummaryJson
) {
}
```

No production code may call `request.taskId()`.

- [ ] **Step 3: Rewrite `TodoCrudToolHandler`**

Remove:

```java
import com.penmate.backend.application.agent.orchestration.AgentTaskRuntimeUpdater;
```

Replace every `AgentTaskRuntimeUpdater.toSnapshotJson(...)` call with a local Jackson serialization helper:

```java
private String toToolOutputJson(Object value) {
    try {
        return OBJECT_MAPPER.writeValueAsString(value);
    } catch (JsonProcessingException ex) {
        throw new IllegalStateException("failed to serialize tool output", ex);
    }
}
```

Use `request.runId()` as `sourceRunId` for Todo persistence. Do not pass `request.taskId()` into todo persistence and do not leave a Todo model `taskId` field in production code.

Rewrite the Todo domain files in the same task:

```text
TodoCrudToolDefinition: remove taskId from the tool input schema; add sourceRunId only if user-visible API needs it.
TodoCrudToolHandler: pass request.runId() to TodoCrudApplicationService.
TodoCrudApplicationService: accept sourceRunId, not taskId.
SessionTodo: rename taskId to sourceRunId.
SessionTodoMapper: read/write source_run_id, not task_id.
TodoController: query/body parameters use sourceRunId, not taskId.
V13__init_agent_session_todos.sql: source_run_id column only.
```

- [ ] **Step 4: Rewrite the rest of the tool layer**

Update every production tool class that currently logs, loads, stores, or forwards `taskId`:

```text
DraftGenerationToolHandler
RagQueryToolHandler
QualityReviewToolHandler
ContextEnhancerToolHandler
DefaultStoryBibleUpdateApplicationService
HybridRagQuery
RagRetrievalService
RagApplicationService
RAG retrieval log model/mapper/repository
PluginToolExecuteCommand
PluginToolExecutor
Plugin call log model/mapper/repository
```

Rules:

- Replace runtime `taskId` with `runId`.
- Do not call `agentRepository.findGenerationTask(...)`.
- Do not import `AgentGenerationTask`.
- Do not write `plugin_call_logs.task_id`; write `run_id`.
- Do not write `rag_retrieval_logs.task_id`; write `run_id`.
- Do not serialize plugin request JSON containing `"taskId"`; serialize `"runId"`.
- Do not expose Todo tool parameters or outputs containing runtime `taskId`; use `sourceRunId` for Todo origin only when needed.
- Tool output payloads may include a domain-specific `todoId`, `chapterId`, or `artifactId`, but not runtime `taskId`.

Add a focused test for at least one migrated non-Todo handler:

```java
@Test
void draft_generation_handler_uses_run_identity_without_loading_generation_task() {
    ToolCallResult result = handler.execute(requestForRun(70001L, 90001L, 50001L, "draft_generation"));

    assertThat(result.status()).isIn("SUCCESS", "FAILED");
    verify(agentRepository, never()).findGenerationTask(any(), any());
}
```

Checkpoint state JSON must include all fields needed to resume without `agent_task_contexts`:

```json
{
  "runId": "70001",
  "phase": "executing",
  "lastAppliedSequence": "41",
  "llmTurnIndex": 2,
  "messageWindow": [],
  "pendingApprovalId": "88001",
  "pendingToolCallId": "call-1",
  "approvedToolPayload": {},
  "assistantToolCallsJson": "[]",
  "remainingToolCalls": [],
  "tokenUsage": {},
  "activeToolCalls": [],
  "todos": [],
  "artifactRefs": []
}
```

The resume path must use `lastAppliedSequence` to replay only events after the checkpoint and must deduplicate any event already reflected in the checkpoint.

- [ ] **Step 5: Write governance tests**

```java
@Test
void returns_waiting_approval_without_mutating_old_task_tables() {
    when(policyEngine.evaluate(any(), any())).thenReturn(new ApprovalPolicyDecision(true, "STORY_BIBLE_UPDATE"));
    when(approvalApplicationService.create(any(), eq("trace-1"))).thenReturn(approvalRequest(88001L));

    AgentToolGovernanceDecision decision = service.beforeExecute(request());

    assertThat(decision.requiresApproval()).isTrue();
    assertThat(decision.approvalId()).isEqualTo(88001L);
    verify(pendingApprovalRepository).save(any());
    verifyNoInteractions(agentRepository);
}
```

- [ ] **Step 6: Implement governance service**

`AgentToolGovernanceService.beforeExecute()`:

1. Load descriptor from `AgentToolDefinitionSource`.
2. Evaluate `DefaultApprovalPolicyEngine`.
3. If no approval: return allowed.
4. If approval: create approval request, save `agent_run_pending_approvals`, return waiting approval.

It must not:

```java
agentRepository.updateGenerationTaskStatus(...)
agentRepository.updateGenerationTaskActiveApproval(...)
realtimeEventService.publishGenerationWaitingApproval(...)
```

- [ ] **Step 7: Add side-effect tool idempotency**

Before executing any tool that can mutate state or call an external system, derive:

```text
idempotencyKey = runId + ":" + llmTurnIndex + ":" + toolCallId
```

Execution rules:

- Look up `agent_tool_call_projections` or durable `tool.call.completed`/`tool.call.failed` events by `(run_id, tool_call_id)` before invoking the handler.
- If a terminal tool result already exists, reuse that result and do not execute the handler again.
- Persist the terminal tool event before advancing the LLM loop.
- For side-effecting tools such as draft generation writes, Story Bible updates, Todo mutations, plugin calls, and RAG retrieval logs, the handler or repository must store the idempotency key where practical.
- Resume after approval must execute only the approved pending tool call and must not re-run already completed earlier tool calls.

Add a test:

```java
@Test
void completed_tool_call_is_reused_on_resume_without_reexecuting_handler() {
    when(toolProjectionRepository.findByRunAndToolCall(70001L, "call-1"))
            .thenReturn(completedToolProjection("call-1", "{\"ok\":true}"));

    ToolCallResult result = toolCallExecutionService.execute(requestForRun(70001L, "call-1"));

    assertThat(result.outputText()).contains("\"ok\":true");
    verifyNoInteractions(draftGenerationToolHandler);
}
```

- [ ] **Step 8: Integrate with AgentRunLlmLoop**

For each tool call:

```text
publish tool.call.started
decision = governance.beforeExecute(...)
if approval required:
  publish approval.requested
  publish tool.call.waiting_approval
  checkpoint
  return waiting approval
else:
  result = toolCallExecutionService.execute(...)
  publish completed/failed
```

- [ ] **Step 9: Run tests and forbidden-symbol scan**

```powershell
cd penmate-backend
mvn -Dtest=AgentToolGovernanceServiceTest,AgentRunLlmLoopTest,TodoCrudToolHandlerTest test
rg "request\\.taskId\\(|\\btaskId\\b|task_id|AgentGenerationTask|findGenerationTask\\(|plugin_call_logs.*task_id|rag_retrieval_logs.*task_id" src/main/java/com/penmate/backend/application/agent/tool src/main/java/com/penmate/backend/application/rag src/main/java/com/penmate/backend/domain/todo src/main/java/com/penmate/backend/infrastructure/persistence/todo src/main/java/com/penmate/backend/interfaces/api/todo src/main/resources/db/migration/V3__init_storage_and_rag_minimal.sql src/main/resources/db/migration/V10__init_plugin_and_model_domains.sql src/main/resources/db/migration/V13__init_agent_session_todos.sql
```

Expected: tests PASS and `rg` finds no production runtime task-linkage matches.

---

### Task 8: Approval Flow Rewiring

**Files:**
- Modify: `ApprovalApplicationService.java`
- Modify: `ApprovedToolInvocationAsyncResumer.java`
- Modify: `PendingToolInvocationTimeoutGuard.java`
- Modify: `penmate-backend/src/main/java/com/penmate/backend/application/approval/command/CreateApprovalCommand.java`
- Modify: `penmate-backend/src/main/java/com/penmate/backend/domain/approval/model/ApprovalRequest.java`
- Modify: `penmate-backend/src/main/java/com/penmate/backend/infrastructure/persistence/approval/ApprovalRequestMapper.java`
- Delete or replace: `penmate-backend/src/main/java/com/penmate/backend/domain/agent/repository/PendingToolInvocationRepository.java`
- Delete or replace: `penmate-backend/src/main/java/com/penmate/backend/domain/agent/model/PendingToolInvocationSnapshot.java`
- Delete or replace: `penmate-backend/src/main/java/com/penmate/backend/infrastructure/persistence/agent/PendingToolInvocationMapper.java`
- Delete or replace: `penmate-backend/src/main/java/com/penmate/backend/infrastructure/persistence/agent/PendingToolInvocationRepositoryImpl.java`
- Delete: `ToolCallResumeService.java`
- Delete: `AgentApprovalResumeCoordinator.java`
- Delete: `ApprovalAgentResumeCoordinator.java`
- Test: `penmate-backend/src/test/java/com/penmate/backend/application/approval/ApprovalApplicationServiceRunFlowTest.java`
- Test: `penmate-backend/src/test/java/com/penmate/backend/application/approval/PendingToolInvocationTimeoutGuardRunFlowTest.java`

- [ ] **Step 1: Write approval approve test**

```java
@Test
void approval_approved_emits_event_and_dispatches_run_resume() {
    when(pendingApprovalRepository.findByApprovalId(88001L)).thenReturn(pendingApproval(70001L));

    service.approve(88001L, new ReviewApprovalCommand(201L, "ok"), "trace-1");

    verify(eventPublisher).publish(eq(70001L), eq("approval.approved"), any());
    verify(runDispatcher).dispatchResume(eq(70001L), eq("trace-1"));
}
```

- [ ] **Step 2: Rewrite approval persistence model**

`CreateApprovalCommand` and `ApprovalRequest` must use `runId`, not `taskId`:

```java
public record CreateApprovalCommand(
        Long projectId,
        Long runId,
        String approvalType,
        String payloadJson,
        String riskLevel,
        Long requestedBy
) {
}
```

`ApprovalRequestMapper` must insert/select `run_id`. It must not reference `task_id`.

- [ ] **Step 3: Replace the old pending invocation bridge**

There must be exactly one pending-approval persistence path after this task:

```text
agent_run_pending_approvals
```

Remove or replace the old code-level bridge:

```text
PendingToolInvocationRepository
PendingToolInvocationMapper
PendingToolInvocationRepositoryImpl
PendingToolInvocationSnapshot
pending_tool_invocations
```

If the implementation keeps a Java repository interface, rename it to `AgentPendingApprovalRepository` and point it only at `agent_run_pending_approvals`. It must expose run-shaped methods:

```java
AgentPendingApproval findByApprovalId(Long approvalId);
int markStatus(Long approvalId, String expectedStatus, String targetStatus);
int markStatusByRunAndToolCall(Long runId, String toolCallId, String expectedStatus, String targetStatus);
List<AgentPendingApproval> findStaleResumingOrApproved(int timeoutMinutes, int limit);
```

`markStatus(...)` methods must be single conditional SQL updates:

```sql
UPDATE agent_run_pending_approvals
SET pending_status = #{targetStatus},
    operator_id = #{operatorId},
    trace_id = #{traceId},
    updated_at = CURRENT_TIMESTAMP(3)
WHERE approval_id = #{approvalId}
  AND pending_status = #{expectedStatus}
```

Callers must treat `affected == 0` as an idempotent duplicate or stale callback, not as permission to dispatch another resume.

- [ ] **Step 4: Rewrite approve/reject**

Approve:

```text
approval_requests -> approved
agent_run_pending_approvals PENDING -> APPROVED
publish approval.approved
dispatch run resume
```

The approve flow must dispatch resume only when both state transitions are newly applied:

1. `agent_approval_requests` moves from pending to approved.
2. `agent_run_pending_approvals` moves from `PENDING` to `APPROVED`.

Duplicate approvals must return the current approved state without publishing a second `approval.approved` event or dispatching a second resume.

Reject:

```text
approval_requests -> rejected
agent_run_pending_approvals PENDING -> REJECTED
publish approval.rejected
publish run.failed
```

Duplicate rejects must not publish duplicate `approval.rejected` or `run.failed` events.

- [ ] **Step 5: Rewrite timeout guard**

Timeout guard now looks at `agent_run_pending_approvals` rows with `pending_status='RESUMING'` or stale `APPROVED`.

On timeout:

```text
mark pending failed
publish approval.expired
publish run.failed
```

- [ ] **Step 6: Delete old resume bridge**

Remove references to:

```text
ToolCallResumeService
AgentApprovalResumeCoordinator
ApprovalAgentResumeCoordinator
ApprovedToolInvocationAsyncResumer if it remains coupled to task mutation
PendingToolInvocationRepository
PendingToolInvocationMapper
PendingToolInvocationRepositoryImpl
PendingToolInvocationSnapshot
pending_tool_invocations
AgentGenerationTask task status mutation during approval resume
ApprovalRequest.getTaskId()
CreateApprovalCommand.taskId()
agent_approval_requests.task_id
```

- [ ] **Step 7: Run approval tests and forbidden-symbol scan**

```powershell
cd penmate-backend
mvn -Dtest=ApprovalApplicationServiceRunFlowTest,PendingToolInvocationTimeoutGuardRunFlowTest test
rg "getTaskId\\(|taskId\\(|agent_approval_requests.*task_id|idx_approval_task|PendingToolInvocation|pending_tool_invocations|AgentTaskStateMachine|AgentTaskStatus|updateGenerationTaskStatus|publishGenerationFailed" src/main/java/com/penmate/backend/application/approval src/main/java/com/penmate/backend/domain/approval src/main/java/com/penmate/backend/domain/agent src/main/java/com/penmate/backend/infrastructure/persistence/approval src/main/java/com/penmate/backend/infrastructure/persistence/agent src/main/resources/db/migration
```

Expected: tests PASS and `rg` finds no approval task-linkage matches.

---

### Task 9: Run-Level SSE Stream

**Files:**
- Create: `AgentRunEventBus.java`
- Create: `AgentRunEventStreamService.java`
- Modify: `AgentController.java`
- Delete: `GenerationSseEmitterHub.java`
- Delete: `GenerationStreamServiceImpl.java`
- Delete: `GenerationStreamService.java`
- Test: `penmate-backend/src/test/java/com/penmate/backend/infrastructure/realtime/AgentRunEventStreamServiceTest.java`
- Test: `penmate-backend/src/test/java/com/penmate/backend/interfaces/api/agent/AgentRunStreamControllerTest.java`

- [ ] **Step 1: Write stream replay test**

```java
@Test
void stream_replays_persisted_events_after_sequence_then_subscribes_live_events() {
    when(eventRepository.listAfter(70001L, 2L)).thenReturn(List.of(event(3L, "message.delta"), event(4L, "run.completed")));

    SseEmitter emitter = streamService.openStream(70001L, 2L);

    verify(eventRepository).listAfter(70001L, 2L);
    verify(eventBus).subscribe(eq(70001L), any());
    assertThat(emitter).isNotNull();
}
```

Add a standard SSE id test:

```java
@Test
void stream_uses_sequence_as_sse_id_and_accepts_last_event_id_cursor() {
    when(eventRepository.listAfter(70001L, 9L)).thenReturn(List.of(event(10L, "message.completed")));

    SseEmitter emitter = streamService.openStream(70001L, null, "9");

    verify(eventRepository).listAfter(70001L, 9L);
    assertThat(emitter).isNotNull();
}
```

- [ ] **Step 2: Implement stream service**

Endpoint behavior:

```text
GET /api/v1/novels/{projectId}/agent/runs/{runId}/events/stream?after=152
1. Validate run belongs to project.
2. Resolve replay cursor from `after` query and `Last-Event-ID` header. Use the larger valid numeric value; invalid/missing values count as `0`.
3. Query persisted events with sequence > cursor.
4. Send each event with `id = event.sequence`, `name = event.eventType`, and `data = AgentRunEventDto`.
4. Subscribe emitter to AgentRunEventBus for live events.
5. Complete emitter after terminal event: run.completed or run.failed.
```

Spring `SseEmitter` send shape:

```java
emitter.send(SseEmitter.event()
        .id(String.valueOf(event.sequence()))
        .name(event.eventType())
        .data(toDto(event)));
```

If replay sends a terminal event, complete the emitter without subscribing to the live bus. If a terminal event arrives live, send it and then complete.

The process-local `AgentRunEventBus` is valid only for single-instance or sticky-session deployment. For multi-instance deployment, replace it with Redis pub/sub, a broker, DB polling, or another shared notification channel; durable replay remains the correctness fallback.

- [ ] **Step 3: Add non-stream event history endpoint**

```text
GET /api/v1/novels/{projectId}/agent/runs/{runId}/events?after=152
```

Returns ordered `AgentRunEventDto[]`.

- [ ] **Step 4: Run stream tests**

```powershell
cd penmate-backend
mvn -Dtest=AgentRunEventStreamServiceTest,AgentRunStreamControllerTest test
```

Expected: PASS.

---

### Task 10: Recovery Query From Projections, Checkpoints, Events

**Files:**
- Create: `AgentRunRecoveryQueryService.java`
- Create: `AgentRunRecoveryDto.java`
- Modify: `AgentController.java`
- Delete: old recovery service/result/snapshot files listed above
- Test: `penmate-backend/src/test/java/com/penmate/backend/application/agent/run/AgentRunRecoveryQueryServiceTest.java`
- Test: `penmate-backend/src/test/java/com/penmate/backend/interfaces/api/agent/AgentRunRecoveryContractTest.java`

- [ ] **Step 1: Write recovery service test**

```java
@Test
void recovery_uses_run_projection_and_messages_without_task_context() {
    when(runProjectionRepository.findLatestBySession(101L, 90001L)).thenReturn(projectionRunning());
    when(agentRepository.listMessages(90001L)).thenReturn(messages());
    when(toolProjectionRepository.listByRun(70001L)).thenReturn(toolCalls());

    AgentRunRecoveryResult result = service.getRecovery(101L, 90001L, "trace-1");

    assertThat(result.activeRun().runId()).isEqualTo(70001L);
    assertThat(result.workbenchContext().activeToolCalls()).hasSize(1);
    verifyNoInteractions(agentSessionRepository);
}
```

- [ ] **Step 2: Implement recovery result**

Response shape:

```json
{
  "session": {
    "sessionId": "90001",
    "title": "鏂颁細璇?,
    "status": "ACTIVE",
    "boundStyle": { "styleId": "81", "name": null }
  },
  "activeRun": {
    "turnId": "50001",
    "runId": "70001",
    "runStatus": "running",
    "runPhase": "executing",
    "latestSequence": "152"
  },
  "pendingApproval": null,
  "messages": [],
  "workbenchContext": {
    "selectedText": "...",
    "modelConfigId": "1001",
    "ragRefs": [],
    "taskProfile": {},
    "promptPlan": {},
    "contextPackage": {},
    "activeToolCalls": [],
    "todos": [],
    "resultSummary": {}
  }
}
```

- [ ] **Step 3: Rewrite session resume**

`POST /sessions/{sessionId}/resume`:

1. Returns recovery projection.
2. If latest active run status is `RUNNING`, it does not start another run.
3. If latest active run status is `WAITING_APPROVAL`, it returns pending approval.
4. It does not open a stream or dispatch work by itself.

- [ ] **Step 4: Run recovery tests**

```powershell
cd penmate-backend
mvn -Dtest=AgentRunRecoveryQueryServiceTest,AgentRunRecoveryContractTest test
```

Expected: PASS.

---

### Task 11: Result, Artifact, Message Persistence

**Files:**
- Create/modify: `AgentRunResultRecorder.java`
- Create/modify: `AgentArtifactRepository.java`
- Test: `penmate-backend/src/test/java/com/penmate/backend/application/agent/run/AgentRunResultRecorderTest.java`

- [ ] **Step 1: Write result recorder test**

```java
@Test
void records_assistant_message_result_artifact_and_message_completed_event() {
    recorder.recordCompletedRun(70001L, "鏈€缁堟鏂?, toolTrace(), tokenUsage());

    verify(agentRepository).insertMessage(argThat(message -> "assistant".equals(message.getRole())));
    verify(artifactRepository).save(argThat(artifact -> "run_result".equals(artifact.artifactType())));
    verify(eventPublisher).publish(eq(70001L), eq("message.completed"), any());
}
```

- [ ] **Step 2: Implement artifact policy**

Store directly in event payload only if preview <= 8KB.

Store in `agent_artifacts` if:

- tool output > 16KB
- final assistant text > 16KB
- raw LLM response needs retention
- checkpoint state > 256KB

Retention and compaction policy:

- Do not delete durable `agent_events` during this refactor.
- `message.delta` rows may be compacted only after `message.completed` and the final artifact/message have been persisted.
- Keep the latest checkpoint plus at least the most recent successful approval checkpoint for terminal runs until a documented retention job replaces this policy.
- Artifacts referenced by `message.completed`, tool terminal events, checkpoints, or run projection must not be deleted while the run is recoverable.
- If retention exceptions are needed for storage cost, document them in `docs/analysis/2026-06-10-agent-run-full-refactor-result.md`.

- [ ] **Step 3: Preserve old summaries**

Move summary extraction from `AgentTaskResultRecorder` into `AgentRunResultRecorder`:

- draft summary
- quality report summary
- todo summary
- Story Bible proposal summary
- token usage

The summaries are stored in artifact metadata and run projection, not `agent_task_results`.

- [ ] **Step 4: Run result tests**

```powershell
cd penmate-backend
mvn -Dtest=AgentRunResultRecorderTest test
```

Expected: PASS.

---

### Task 12: Frontend Run API And Event Reducer

**Files:**
- Modify: `penmate-frontend/src/api/modules/agent.api.ts`
- Modify: `penmate-frontend/src/api/types.ts`
- Modify: `penmate-frontend/src/stores/workbenchSession.ts`
- Create: `penmate-frontend/src/composables/workbench/useAgentRunReducer.ts`
- Create: `penmate-frontend/src/composables/workbench/useAgentRunRuntime.ts`
- Delete: `penmate-frontend/src/composables/workbench/useWorkbenchTaskRuntime.ts`
- Modify: `penmate-frontend/src/test/workbenchRuntimeContract.fixture.ts`
- Test: `penmate-frontend/src/api/modules/agent.api.spec.ts`
- Test: `penmate-frontend/src/composables/workbench/useAgentRunReducer.spec.ts`
- Test: `penmate-frontend/src/composables/workbench/useAgentRunRuntime.spec.ts`

- [ ] **Step 1: Rewrite API**

`agent.api.ts` must expose:

```ts
openRunEventStream(projectId: string, runId: string, after = '0') {
  const url = `${apiBase}/v1/novels/${projectId}/agent/runs/${runId}/events/stream?after=${encodeURIComponent(after)}`
  return new EventSource(url)
}
```

Remove:

```ts
openTurnStream(projectId, sessionId, turnId)
getTurnStreamUrl(projectId, sessionId, turnId)
getTask(projectId, taskId)
```

Also remove task-shaped API/types from `api/types.ts`:

```text
WorkbenchRuntimeEventSource.taskId
WorkbenchActiveTaskRuntimeSnapshot
activeTask
activeTaskRuntime
taskStatus
streamChannelKey
```

- [ ] **Step 2: Rewrite workbench store to activeRun**

`workbenchSession.ts` must store run state, not task state:

```ts
activeRun: {
  runId: null as string | null,
  runStatus: '',
  runPhase: '',
  latestSequence: '0'
}
```

Remove the old store field:

```ts
activeTask: { taskId, taskStatus, streamChannelKey }
```

- [ ] **Step 3: Write reducer test**

```ts
it('reduces run tool approval and message events', () => {
  let state = createInitialAgentRunState()
  state = reduceAgentRunEvent(state, { type: 'run.started', sequence: '1', payload: { phase: 'preflight' } })
  state = reduceAgentRunEvent(state, { type: 'tool.call.started', sequence: '2', payload: { toolCallId: 'call-1', toolCode: 'draft_generation' } })
  state = reduceAgentRunEvent(state, { type: 'message.delta', sequence: '3', payload: { text: 'abc' } })

  expect(state.phase).toBe('preflight')
  expect(state.toolCalls[0].toolCode).toBe('draft_generation')
  expect(state.assistantText).toBe('abc')
  expect(state.lastSequence).toBe('3')
})
```

- [ ] **Step 4: Implement runtime stream consumer**

`useAgentRunRuntime.consumeRunStream(projectId, runId, after)`:

- closes previous stream
- opens run event stream
- listens to all new event names
- applies reducer
- resolves on `run.completed`
- rejects on `run.failed`
- stores `lastSequence` for reconnect

- [ ] **Step 5: Run frontend focused tests**

Before running tests, rewrite `penmate-frontend/src/test/workbenchRuntimeContract.fixture.ts` so agent runtime fixtures use `activeRun`, `runId`, `latestSequence`, and `run.*`/`message.*`/`tool.call.*` events. Generic safe-integer tests outside the agent runtime may keep `taskId` examples only when they are clearly testing the JSON transformer rather than Workbench behavior.

```powershell
cd penmate-frontend
npm test -- agent.api.spec.ts useAgentRunReducer.spec.ts useAgentRunRuntime.spec.ts
rg "activeTask|activeTaskRuntime|taskStatus|streamChannelKey|generation\\.|openTurnStream|getTurnStreamUrl|getTask\\(" src/api src/stores src/composables/workbench src/test/workbenchRuntimeContract.fixture.ts
```

Expected: tests PASS and `rg` finds no production frontend runtime matches.

---

### Task 13: Frontend Workbench Integration

**Files:**
- Modify: `useWorkbenchChat.ts`
- Modify: `useWorkbenchSessionRecovery.ts`
- Modify: `useWorkbenchRuntimePresenter.ts` if present
- Modify: `Workbench/index.vue`
- Modify: `penmate-frontend/src/stores/workbenchSession.ts`
- Modify Workbench component tests under `penmate-frontend/src/components/workbench` only if they consume runtime/task state
- Modify tests under `penmate-frontend/src/views/Workbench` and `penmate-frontend/src/composables/workbench/__tests__`

- [ ] **Step 1: Update send message flow**

After `createTurn`, read:

```ts
const runId = generation.activeRun?.runId
```

Then stream:

```ts
await runtime.consumeRunStream(projectId, String(runId), '0')
```

Do not require `turnId` for streaming.

- [ ] **Step 2: Update recovery flow**

Recovery reads:

```ts
snapshot.activeRun?.runId
snapshot.activeRun?.runStatus
snapshot.activeRun?.latestSequence
```

If `runStatus === 'running'`, reconnect:

```ts
await resumeRunningRun(projectId, runId, latestSequence)
```

- [ ] **Step 3: Remove old generation event assertions**

Update tests that assert listeners for:

```text
generation.started
generation.status
generation.token
generation.tool_call
generation.waiting_approval
generation.done
generation.failed
```

Replace with:

```text
run.started
run.phase.changed
message.delta
tool.call.started
tool.call.completed
tool.call.waiting_approval
approval.requested
run.completed
run.failed
```

- [ ] **Step 4: Remove activeTask/taskId UI state**

Replace every Workbench production reference:

```text
activeTask -> activeRun
taskId -> runId
taskStatus -> runStatus
activeTaskRuntime -> activeRunRuntime or runtimeState
streamChannelKey -> latestSequence
```

Do not keep compatibility branches that read both `activeTask` and `activeRun`.

- [ ] **Step 5: Run workbench tests**

```powershell
cd penmate-frontend
npm test -- useWorkbenchChat.spec.ts useWorkbenchSessionRecovery.spec.ts index.runtime-e2e.spec.ts index.chat-binding.spec.ts
rg "activeTask|activeTaskRuntime|taskStatus|streamChannelKey|generation\\.|openTurnStream|getTurnStreamUrl" src/views/Workbench src/composables/workbench src/stores src/components/workbench src/test/workbenchRuntimeContract.fixture.ts
```

Expected: tests PASS and `rg` finds no production frontend matches.

---

### Task 14: Delete Old Backend Runtime Code

**Files:**
- Delete all old files listed in "Delete / Replace"
- Delete: `penmate-backend/src/main/java/com/penmate/backend/application/agent/usecase/AgentGenerationAppService.java`
- Delete: `penmate-backend/src/main/java/com/penmate/backend/interfaces/api/agent/dto/AgentTaskDto.java`
- Delete: `penmate-backend/src/main/java/com/penmate/backend/interfaces/api/agent/dto/CreateAgentGenerationDto.java`
- Delete: `penmate-backend/src/main/java/com/penmate/backend/interfaces/api/agent/dto/ApplyAgentGenerationDto.java`
- Delete: `penmate-backend/src/main/java/com/penmate/backend/domain/agent/model/AgentTaskStatus.java`
- Delete: `penmate-backend/src/main/java/com/penmate/backend/application/agent/AgentTaskStateMachine.java`
- Delete: `penmate-backend/src/main/java/com/penmate/backend/domain/agent/service/AgentTaskTransitionPolicy.java`
- Delete: `penmate-backend/src/main/java/com/penmate/backend/domain/agent/service/InvalidAgentTaskTransitionException.java`
- Modify: `penmate-backend/src/main/java/com/penmate/backend/domain/agent/repository/AgentSessionRepository.java`
- Modify: `penmate-backend/src/main/java/com/penmate/backend/infrastructure/persistence/agent/AgentSessionMapper.java`
- Modify: `AgentArchitectureDependencyTest.java`
- Modify: `DependencyRulesTest.java`
- Test: `penmate-backend/src/test/java/com/penmate/backend/architecture/AgentArchitectureDependencyTest.java`

- [ ] **Step 1: Delete old classes**

Delete old generation workflow, old generation API/usecase/DTOs, old task status machines, old generation SSE, old recovery snapshot, old tool resume bridge, and old runtime status view classes.

- [ ] **Step 2: Remove old repository methods**

Remove from `AgentRepository`:

```java
int insertGenerationTask(AgentGenerationTask task);
AgentGenerationTask findGenerationTask(Long projectId, Long taskId);
AgentTaskContext findTaskContext(Long taskId);
int updateGenerationTaskStatus(...);
int updateGenerationTaskActiveApproval(...);
int updateGenerationTaskRuntime(...);
int updateGenerationTaskSnapshots(...);
int insertTaskResult(AgentTaskResult taskResult);
int updateGenerationTaskResultLink(...);
```

Keep message/session methods that still serve chat history.

Also remove from `AgentSessionRepository`:

```java
AgentSessionRecoverySnapshot findRecoverySnapshot(Long projectId, Long sessionId);
AgentTaskContext findTaskByTurnId(Long projectId, Long sessionId, Long turnId);
int insertRuntimeTask(...);
int updateRuntimeTaskTurnLink(...);
int insertTaskContext(AgentTaskContext taskContext);
int updateLastRunningTask(Long projectId, Long sessionId, Long taskId);
```

Add or keep run-shaped session methods only:

```java
int updateLastRun(Long projectId, Long sessionId, Long runId);
```

- [ ] **Step 3: Remove old mapper SQL**

Remove `agent_tasks`, `agent_task_contexts`, `agent_task_results`, and `generation.*` SQL from:

- `AgentMapper.java`
- `AgentSessionMapper.java`

Also remove `last_task_id`, `task_id AS taskId`, `findTaskRow`, `findTaskRowByTurnId`, `insertRuntimeTask`, `updateRuntimeTaskTurnLink`, `findTaskContextRow`, `insertTaskContext`, `findTaskResultRow`, and `updateLastRunningTask` SQL/methods.

- [ ] **Step 4: Search for forbidden symbols**

Run:

```powershell
rg "AgentGenerationWorkflow|AgentGenerationAppService|AgentToolLoopRunner|GenerationSseEmitterHub|GenerationStreamService|AgentSessionRecovery|generation\\.|agent_task_contexts|agent_task_results|agent_tasks|agent_pending_approvals|pending_tool_invocations|last_task_id|ToolCallResumeService|AgentTaskRuntimeUpdater|PendingToolInvocation|AgentTaskStatus|AgentTaskStateMachine|AgentTaskTransitionPolicy|AgentTaskDto|CreateAgentGenerationDto|ApplyAgentGenerationDto|request\\.taskId\\(|activeTask|openTurnStream|getTurnStreamUrl|createGeneration|openGenerationStream|publishGeneration" penmate-backend/src penmate-frontend/src penmate-backend/src/main/resources/db/migration
rg "\\btaskId\\b|task_id|getTaskId|setTaskId" penmate-backend/src/main penmate-frontend/src penmate-backend/src/main/resources/db/migration
```

Expected: no production-code or baseline-DDL matches. Test fixtures may remain only if they are being deleted in the same task. Generic non-agent JSON transformer tests may keep `taskId` only if they are outside `src/api`, `src/stores`, `src/composables/workbench`, `src/views/Workbench`, and backend main runtime code.

- [ ] **Step 5: Run architecture tests**

```powershell
cd penmate-backend
mvn -Dtest=AgentArchitectureDependencyTest,DependencyRulesTest test
```

Expected: PASS.

---

### Task 15: End-To-End Backend Verification

**Files:**
- Test: `penmate-backend/src/test/java/com/penmate/backend/interfaces/api/agent/AgentRunWorkflowEndToEndContractTest.java`

- [ ] **Step 1: Add E2E contract test**

Test cases:

1. `POST /sessions/{sessionId}/turns` returns `activeRun.runId`.
2. `GET /runs/{runId}/events?after=0` returns `run.started`.
3. LLM text-only response emits `message.completed` and `run.completed`.
4. LLM tool call emits `tool.call.started` and `tool.call.completed`.
5. Approval-required tool emits `approval.requested`, `tool.call.waiting_approval`, checkpoint, and run status `waiting_approval`.
6. Approval approve dispatches resume and emits `approval.approved`.
7. Duplicate approval approve does not dispatch a second resume or duplicate terminal events.
8. Two concurrent event appends for one run produce unique ordered sequences.
9. SSE reconnect after `run.completed` replays persisted events and completes without subscribing forever.
10. SSE responses include `id: <sequence>` and reconnect with `Last-Event-ID` replays from that cursor.
11. Projection replay of the same event does not duplicate assistant text or regress terminal run status.
12. Resume after approval does not re-execute a completed side-effecting tool call.
13. A committed event remains queryable through `/events` even if live bus publish fails.
14. Recovery returns `activeRun`, tool projection, todos, and latest sequence.

- [ ] **Step 2: Run backend test suite slices**

```powershell
cd penmate-backend
mvn -Dtest=AgentRun*Test,Approval*RunFlowTest,*Tool*Test,*Prompt*Test,*Context*Test test
```

Expected: PASS.

- [ ] **Step 3: Run full backend tests**

```powershell
cd penmate-backend
mvn test
```

Expected: PASS.

---

### Task 16: Full Frontend Verification

**Files:**
- Existing frontend tests under `penmate-frontend/src`

- [ ] **Step 1: Run focused frontend tests**

```powershell
cd penmate-frontend
npm test -- agent.api.spec.ts useAgentRunReducer.spec.ts useAgentRunRuntime.spec.ts useWorkbenchChat.spec.ts useWorkbenchSessionRecovery.spec.ts
```

Expected: PASS.

- [ ] **Step 2: Run workbench tests**

```powershell
cd penmate-frontend
npm test -- Workbench
```

Expected: PASS.

- [ ] **Step 3: Run full frontend test suite**

```powershell
cd penmate-frontend
npm test
rg "activeTask|activeTaskRuntime|taskStatus|streamChannelKey|generation\\.|openTurnStream|getTurnStreamUrl" src/api src/stores src/composables/workbench src/views/Workbench src/components/workbench src/test/workbenchRuntimeContract.fixture.ts
```

Expected: tests PASS and `rg` finds no frontend Workbench runtime matches.

---

### Task 17: Final Cleanup And Documentation

**Files:**
- Modify: `docs/plans/agent-event-stream-tech-design.md`
- Modify: `docs/plans/agent-checkpoint-tech-design.md`
- Create: `docs/analysis/2026-06-10-agent-run-full-refactor-result.md`

- [ ] **Step 1: Update design docs**

Update both existing plan docs to reflect final implementation names:

- `agent_runs`
- `agent_events`
- `agent_checkpoints`
- `agent_run_projections`
- run-level SSE endpoint
- no old `generation.*` events

- [ ] **Step 2: Write result report**

Create a report with:

```markdown
# Agent Run Full Refactor Result

## Deleted

## Created

## Migrated Capabilities

## Verification

## Remaining Risks

## Event Reliability

Document after-commit publishing, live bus failure behavior, whether an outbox/shared bus was implemented, and the single-instance versus multi-instance deployment assumption.

## Retention And Compaction

Document message delta retention, checkpoint retention, artifact retention, and any event compaction policy.

## Schema Versioning

Document the event `schemaVersion` used by this migration and any compatibility handling in reducers/projections.
```

- [ ] **Step 3: Final forbidden-symbol scan**

```powershell
rg "generation\\.|AgentGenerationWorkflow|AgentGenerationAppService|AgentToolLoopRunner|GenerationSseEmitterHub|GenerationStreamService|AgentSessionRecovery|agent_task_contexts|agent_task_results|agent_tasks|agent_pending_approvals|pending_tool_invocations|ToolCallResumeService|PendingToolInvocation|AgentTaskStatus|AgentTaskStateMachine|AgentTaskTransitionPolicy|AgentTaskDto|CreateAgentGenerationDto|ApplyAgentGenerationDto|activeTask|activeTaskRuntime|openTurnStream|getTurnStreamUrl|createGeneration|openGenerationStream|publishGeneration" penmate-backend/src/main penmate-frontend/src penmate-backend/src/main/resources/db/migration
rg "\\btaskId\\b|task_id|getTaskId|setTaskId" penmate-backend/src/main penmate-frontend/src penmate-backend/src/main/resources/db/migration
```

Expected: no matches.

- [ ] **Step 4: Commit final branch state**

```powershell
git status --short
git add docs penmate-backend penmate-frontend
git commit -m "refactor: replace agent generation runtime with run event checkpoints"
```

Expected: commit succeeds.

---

## Self-Review

- Spec coverage: The plan replaces old orchestration, old SSE, old recovery snapshots, old tool approval resume, old task runtime tables, old pending invocation bridge, old generation DTO/usecase/status machines, and old frontend `generation.*` reducer.
- Capability coverage: Tool calling, tool definitions, tool handlers, skill prompts, preflight, context routing, RAG, Story Bible, approval, Todo projections, assistant messages, artifacts, and token/result summaries are migrated.
- Placeholder scan: No implementation step depends on undefined behavior. Every task names concrete files, test targets, and expected commands.
- Type consistency: The new primary identifiers are `runId`, `sequence`, `eventType`, `activeRun`, and `latestSequence`. Old `taskId` is intentionally removed from runtime API.

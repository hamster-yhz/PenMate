# Agent Run Full Refactor Result

## Deleted

- Removed the old generation/task runtime from production code: task aggregate models, task status/state-machine classes, generation workflow/usecase/DTOs, old generation SSE hub/service, old session recovery snapshot service, and pending tool invocation bridge.
- Removed baseline DDL for `agent_tasks`, `agent_task_contexts`, `agent_task_results`, `agent_pending_approvals`, old `task_id` runtime columns, and `last_task_id`.
- Removed frontend task runtime stream code and old `generation.*`/turn stream client APIs.

## Created

- Added the Agent Run runtime around `agent_runs`, `agent_run_inputs`, `agent_events`, `agent_checkpoints`, `agent_run_projections`, `agent_tool_call_projections`, `agent_todo_projections`, `agent_artifacts`, and `agent_run_pending_approvals`.
- Added run-level application services, executor/LLM loop, event publisher, projection updater, checkpoint service, recovery service, persistence mappers/repositories, and run-level SSE stream service.
- Added frontend `useAgentRunRuntime` and run-shaped Workbench session recovery state.

## Migrated Capabilities

- Runtime identity is now `runId` across agent turns, tool calls, approvals, RAG retrieval logs, plugin call logs, todo linkage, projections, checkpoints, recovery payloads, and frontend stream state.
- Tool definitions, tool handlers, LLM gateway abstractions, preflight, context routing, RAG, Story Bible update support, Todo persistence, plugin execution, approval persistence, and Workbench chat streaming were migrated onto the run contract.
- Recovery now returns `activeRun` with `runId`, `runStatus`, `runPhase`, and `latestSequence`.

## Verification

- Frontend build: `npm run build` passed.
- Frontend tests: `npm test -- --run` passed with 62 files and 295 tests.
- Backend targeted regression slice for the final failures passed with 28 tests.
- Backend full suite: `mvn test` passed with 634 tests.
- Final forbidden-symbol scans over `penmate-backend/src/main`, `penmate-frontend/src`, and baseline migrations returned no matches for old generation/task runtime names or `taskId`/`task_id`.

## Remaining Risks

- The live event bus is process-local. Correctness relies on durable `agent_events` replay; multi-instance deployment still needs Redis pub/sub, a broker, DB polling, or another shared notification path.
- The implementation persists `message.delta` events through the common event publisher. Current LLM loop emits one assistant-text delta per LLM turn, so row growth is bounded by turn count rather than token count, but no retention job exists yet.
- The plan referenced `docs/plans/agent-event-stream-tech-design.md` and `docs/plans/agent-checkpoint-tech-design.md`; those files were not present in this worktree, so they were not updated.

## Event Reliability

- `AgentRunEventPublisher` appends to `agent_events`, applies projections, then publishes to the live bus in `afterCommit`.
- If live publish fails after commit, the failure is logged and the committed event remains recoverable through replay.
- SSE uses run-level endpoint `/runs/{runId}/stream`, accepts `after` and `Last-Event-ID`, and uses the larger numeric cursor.
- SSE event id is the durable event `sequence`; event name is the durable event type.

## Retention And Compaction

- Durable events remain the source of truth.
- Checkpoints are written periodically and on terminal run events. No checkpoint compaction job was added.
- Result rendering relies on persisted assistant messages and run projections; artifact tables are present for large outputs.
- No event compaction or archival policy was implemented in this branch.

## Schema Versioning

- Event payloads are wrapped with `schemaVersion: 1` by `AgentRunEventPublisher`.
- Backend DTOs expose `schemaVersion` to stream consumers.
- Frontend reducers read known fields and ignore unknown fields; there is no older-version compatibility shim because this migration intentionally removes the old runtime protocol.

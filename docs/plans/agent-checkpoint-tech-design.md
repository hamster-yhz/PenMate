# Agent Run Checkpoint and Recovery Design

## Status

Implemented by the Story Bible and Context Epoch refactor on 2026-07-16.

This document describes the current Run recovery contract. It supersedes the former restart-from-input behavior.

## Recovery Invariants

1. A Run binds one immutable Context Epoch.
2. Routing, Story Bible selection, effective-state resolution, and prompt composition execute only during initial Run execution.
3. The resolved context and composed prompt are durable artifacts. Mutable Story Bible state is never read to reconstruct an existing Run.
4. Approval resume continues from the persisted LLM messages and tool-call position. It does not call the initial execution path.
5. Redis is an optional read-through cache. Redis expiry or loss does not change Run identity or Context Epoch identity.
6. Durable events and the latest PostgreSQL checkpoint remain the recovery source of truth.

## Initial Execution

The initial path is:

```text
routing
-> epoch_binding
-> context selection and effective-state resolution
-> context artifact persistence
-> prompt composition and prompt artifact persistence
-> LLM/tool execution
-> completion or approval wait
```

The durable boundary events are:

```text
context.epoch.bound
turn.route.completed
context.resolved
prompt.composed
tool.call.waiting_approval
run.completed | run.failed
```

`context.resolved` and `prompt.composed` include artifact IDs, SHA-256 hashes, and byte sizes. The reducer adds those artifact IDs to `AgentRuntimeState.artifactRefs`.

## Context and Prompt Artifacts

`AgentRunContextArtifactService` stores immutable JSON objects under:

```text
agent-runs/{runId}/context-{artifactId}.json
agent-runs/{runId}/prompt-{artifactId}.json
```

The context artifact contains:

- schema version and Run ID
- bound Context Epoch ID
- focused Story Bible route decision
- fully rendered `ContextPackage`
- Working Set node IDs observed by the Run

The prompt artifact contains the `PromptPlan` and a manifest of epoch, prompt bundle, tool catalog, skill catalog, Story Bible core, stable-prefix, and dynamic-context hashes.

Artifact metadata is stored in `agent_artifacts`. Every load verifies object byte size and SHA-256 before deserialization.

## Checkpoint State

`AgentRuntimeState` records:

- Run status, phase, and last durable event sequence
- active approval and pending tool-call state
- assistant draft, LLM turn index, and token usage
- persisted LLM messages and tool-call continuation fields
- active Todo projection IDs
- durable artifact references

`AgentCheckpointService` writes a checkpoint for Run start, route completion, resolved context, composed prompt, approval wait, and terminal events. It also writes every fifteenth event as a bounded replay checkpoint.

The checkpoint is written to PostgreSQL first. Redis key `agent:checkpoint:{runId}:latest` caches the same serialized state for 30 minutes. A Redis miss falls back to the latest PostgreSQL checkpoint.

## Event Replay

`AgentRunRecoveryService` loads the latest checkpoint and then replays `agent_events` after `lastEventSeq` through `AgentRuntimeStateReducer`. Duplicate or older sequences are ignored.

The projection updater consumes the same durable event types. `run.started` defaults to phase `routing`; there is no preflight phase.

## Approval Resume

Tool approval persistence includes the saved conversation messages, assistant tool calls, tool-call ID, continuation metadata, and idempotency key.

`AgentRunExecutor.resume()`:

1. recovers the latest state;
2. loads the approved pending tool call;
3. resolves only the execution model configuration needed to continue the LLM loop;
4. calls `AgentRunLlmLoop.resumeApproved()`;
5. executes remaining sibling tool calls in order;
6. checkpoints another approval wait or the terminal event.

It does not bind an epoch, route Story Bible context, create a Working Set selection, or compose a prompt.

## Failure Semantics

- Missing or corrupt context/prompt artifacts fail closed.
- A Run already bound to another epoch returns a conflict.
- A missing Session fails epoch binding before object creation.
- Context Epoch snapshot hash or size mismatch returns a conflict.
- Tool idempotency keys prevent approved calls from being applied twice.

## Current Limits

- Live event notification is process-local; durable replay preserves correctness but multi-instance low-latency fan-out needs a shared broker or polling strategy.
- Checkpoint JSON above 256 KiB is represented by an artifact-required marker. A dedicated large-checkpoint artifact path is still required before states of that size can be resumed.
- Durable event and checkpoint compaction are not implemented.

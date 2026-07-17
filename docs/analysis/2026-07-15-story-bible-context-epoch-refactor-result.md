# Story Bible and Context Epoch Refactor Result

## Outcome

The development baseline now uses one current, project-scoped Story Bible and session-scoped Context Epochs. Legacy Cards, global Story Bible versions, and the full Agent preflight path have been removed from production sources.

## Story Bible

- Added one current Story Bible root with a monotonically increasing content revision.
- Added semantic families, custom node types, categories, tags, aliases, directed relations, chapter-anchored state progressions, view preferences, changesets, and field-level change items.
- Added immediate user CRUD with optimistic revision checks.
- Added one approval-gated Agent batch mutation tool that invokes the same application commands as direct CRUD.
- Added recent history and gzip JSONL cold archive support.
- Category and tag deletion records each affected node membership change in the same changeset and increments the Story Bible revision once.
- Removed Card domain/API/frontend paths and global Story Bible version paths.

## Manuscript and Progression

- Removed stored chapter numbers from the current baseline.
- Canonical manuscript order is volume `sort_order`, chapter `sort_order`, then chapter identity.
- APIs derive a full-book continuous display number.
- Progressions use stable chapter IDs and RFC 6902 patches.
- Effective-state resolution detects unresolved anchors and same-position/same-path conflicts.

## Context Epoch and Routing

- Added immutable epoch snapshots with fingerprinted Story Bible, manuscript, style, routing, prompt, skill, and tool revisions/hashes.
- Added Session row locking so concurrent identical bindings create one epoch and bind both Runs to it.
- Excluded Working Set state from the epoch fingerprint.
- Added a bounded 8-turn/30-automatic-node Working Set with pinned entries.
- Added `RETRIEVAL`, `LLM_SELECTOR`, and `RETRIEVAL_THEN_LLM` routing modes with Session override -> user default -> system default resolution.
- Added only the semantic-retriever port and a no-op implementation. Story Bible routing performs no embedding provider or vector-index work.
- Added deterministic alias matching from the bound epoch catalog and a durable retrieval trace with exact-alias, lexical, semantic, merged-candidate, selector, and Working Set counts.
- Run context artifact schema v3 stores selected-node decisions, applied progression IDs, rendered-content hashes, retrieval trace, Working Set snapshot, and dependency revisions.

## Prompt and Run Recovery

- Stable execution prefix order is execution system prompt, deterministic tool schemas, Skill Catalog, and Context Epoch core.
- Working Set and selected Story Bible content remain in dynamic context and do not alter the epoch fingerprint.
- Selector prompts are separate from execution prompts; the execution model never receives the Selector Catalog.
- Initial Runs publish `context.epoch.bound`, `turn.route.completed`, `context.resolved`, and `prompt.composed`.
- Context and prompt plans are immutable object-storage artifacts with size/hash verification.
- Approval resume restores saved LLM/tool state and does not rerun epoch binding, routing, selection, or prompt composition.
- Context Epoch, Run context, prompt, continuation, event archive, and checkpoint objects are read back and verified before their metadata is published.
- Recoverable Runs retain the latest two verified checkpoints; oversized checkpoint state is stored as an object artifact and recovery falls back through the previous checkpoint to durable event replay.
- Terminal events and checkpoints remain hot for 7 days, cold for 90 days, and are deleted only after verified archival and retention expiry.

## Frontend

- Added a top-level Writing/Story Bible mode.
- Added the Story Bible navigation, node browser/editor, relation editor, progression editor, history, type/category/tag management, and routing settings.
- Preserved the approved routing labels:
  - `规则匹配 + Embedding`
  - `直接使用 LLM`
  - `规则匹配 + Embedding，LLM 兜底`
- Added mobile navigation, node-list, and Agent-chat drawers so the editor is not compressed by the desktop right panel.
- Run completion reloads the Story Bible workspace so approved Agent changes become visible.
- Story Bible approval previews are durable across live streaming and recovery, and their existing action opens the affected node through `mode=story-bible&nodeId=...`.

## Verification

Verified on 2026-07-17:

- backend clean full suite: 709 tests passed with zero failures, errors, or skips;
- frontend full suite: 279 tests passed;
- frontend production build passed;
- concurrent identical Context Epoch integration test passed;
- DDD/ArchUnit dependency rules passed: domain does not depend on application, interfaces, or infrastructure, and application does not depend on persistence implementations or API DTOs;
- forbidden Card, Story Bible version, full-preflight, and `chapter_no` production/schema scans returned no matches;
- Story Bible semantic retrieval has only its port and no-op implementation;
- desktop 1440x900 and tablet 1024x768 screenshots showed no clipping or panel overlap;
- Chrome device emulation at 390x844 reported `innerWidth=390`, document/body `scrollWidth=390`, and all fixed controls inside the viewport;
- mobile navigation and node drawers opened at 310px with a backdrop, and the Agent chat drawer opened at 92vw without resizing the editor;
- `git diff --check` passed.

## Operational Notes

- MySQL remains the execution and event source of truth. The process-local bus is only a latency accelerator; SSE also polls durable events, so multi-instance correctness does not require Redis Streams.
- Recoverable Run checkpoints have no age-based deletion. Terminal checkpoint and event retention follows the accepted 7-day hot plus 90-day cold policy; user conversation history is stored separately and is unaffected.
- Events older than the cold-retention window are intentionally unavailable for event-level replay. Expired cursors receive `stream.reset`, and the client reloads durable messages and projections.
- Embedding execution remains intentionally deferred. Selecting a retrieval mode uses lexical/alias rules plus the semantic-retriever no-op until a provider is implemented behind the existing port.

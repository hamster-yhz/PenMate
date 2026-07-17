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
- Waiting, failed, completed, and superseded outcomes first perform a lease/execution-token-guarded transition and publish their durable events in the same transaction. A stale worker cannot publish a terminal event or assistant message.
- Added idempotent explicit Run cancellation. It atomically moves any recoverable Run to `CANCELLED`, clears its lease, invalidates open approvals, revokes the old execution token, and writes `run.cancelled`.

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
- Active recoverable Runs expose an icon stop action; cancellation does not render a false failure message.

## Verification

Verified on 2026-07-17:

- backend clean full suite: 720 tests passed with zero failures, errors, or skips;
- frontend full suite: 61 files and 281 tests passed;
- frontend production build passed;
- concurrent identical Context Epoch integration test passed;
- DDD/ArchUnit dependency rules passed: domain does not depend on application, interfaces, or infrastructure, and application does not depend on persistence implementations or API DTOs;
- the real Spring wiring test passed with the dispatcher, executor, lease-guarded transition service, and successor service and no dependency cycle;
- all 19 Flyway migrations through V20 applied to a fresh MySQL 8 schema; metadata queries found zero legacy Card/Story Bible version tables and zero Story Bible `version_no`/chapter `chapter_no` columns;
- real MySQL-backed APIs created the Story Bible root, 17 system types, categories, tags, aliases, nodes, relation, progression, volume, and chapter;
- a real cancellation API call moved a leased `RUNNING` Run to `CANCELLED`, cleared its lease, retained execution token 7 only as a fence value, and durably appended sequence 1 `run.cancelled`;
- forbidden Card, Story Bible version, full-preflight, and `chapter_no` production/schema/generated-OpenAPI scans returned no matches;
- Story Bible semantic retrieval has only its port and no-op implementation;
- generated OpenAPI includes the explicit cancellation endpoint and contains no legacy Card or stored chapter-number contract;
- desktop 1440x900 screenshots showed no clipping, failed requests, console errors, or panel overlap;
- Chrome device emulation at 390x844 reported `innerWidth=390`, document/body `scrollWidth=390`, and all fixed controls inside the viewport;
- mobile navigation and node drawers opened at 310px with a backdrop, and the 359px Agent chat drawer opened without resizing the 390px editor;
- progression anchors displayed the backend-derived full-book number (`第 1 章`) rather than a chapter business ID;
- `git diff --check` passed.

## Acceptance Matrix

1. Fresh schema legacy objects: proven by Flyway V20 MySQL metadata and the final-schema migration contract.
2. Legacy runtime paths: production and generated-contract scans are empty.
3. Story Bible CRUD: exercised through real MySQL-backed APIs and component/application tests.
4. One user changeset/revision increment: covered by aggregate application and changeset service tests.
5. Agent mutation approval: the single batch tool is approval-gated and covered by tool/approval tests.
6. Continuous chapter display numbers: derived from volume/chapter sort order and verified in API/UI tests.
7. Progression ordering: resolved through manuscript ordinals, including reorder and unresolved-anchor tests.
8. Epoch reuse: exact fingerprint reuse and concurrent identical binding tests pass.
9. Epoch invalidation: fingerprint inputs exclude Working Set state and include all accepted dependency revisions/hashes.
10. Resume: checkpoint/artifact/continuation tests prove no route, context, or prompt recomposition.
11. Routing modes: all three modes persist through user default/session override and freeze into the bound epoch/artifact.
12. Embedding facade: only the semantic port and no-op implementation exist for Story Bible retrieval.
13. Stable prompt prefix: execution prompt, sorted tools, Skill Catalog, and epoch core order is contract-tested; full skill text uses `skill_load`.
14. Desktop/mobile Workbench: measured screenshots and browser diagnostics pass without overlap or overflow.
15. Tests/build/scans: backend, frontend, OpenAPI, MySQL, DDD, and visual checks above all pass.

## Operational Notes

- MySQL remains the execution and event source of truth. The process-local bus is only a latency accelerator; SSE also polls durable events, so multi-instance correctness does not require Redis Streams.
- Recoverable Run checkpoints have no age-based deletion. Terminal checkpoint and event retention follows the accepted 7-day hot plus 90-day cold policy; user conversation history is stored separately and is unaffected.
- Events older than the cold-retention window are intentionally unavailable for event-level replay. Expired cursors receive `stream.reset`, and the client reloads durable messages and projections.
- Embedding execution remains intentionally deferred. Selecting a retrieval mode uses lexical/alias rules plus the semantic-retriever no-op until a provider is implemented behind the existing port.
- Explicit cancellation is idempotent. A repeated cancel returns the existing `CANCELLED` Run without creating another event; other terminal Runs reject cancellation.

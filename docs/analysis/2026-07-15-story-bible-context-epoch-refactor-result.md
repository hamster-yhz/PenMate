# Story Bible and Context Epoch Refactor Result

## Outcome

The development baseline now uses one current, project-scoped Story Bible and session-scoped Context Epochs. Legacy Cards, global Story Bible versions, and the full Agent preflight path have been removed from production sources.

## Story Bible

- Added one current Story Bible root with a monotonically increasing content revision.
- Added semantic families, custom node types, categories, tags, aliases, directed relations, chapter-anchored state progressions, view preferences, changesets, and field-level change items.
- Added immediate user CRUD with optimistic revision checks.
- Added one approval-gated Agent batch mutation tool that invokes the same application commands as direct CRUD.
- Added recent history and gzip JSONL cold archive support.
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

## Prompt and Run Recovery

- Stable execution prefix order is execution system prompt, deterministic tool schemas, Skill Catalog, and Context Epoch core.
- Working Set and selected Story Bible content remain in dynamic context and do not alter the epoch fingerprint.
- Selector prompts are separate from execution prompts; the execution model never receives the Selector Catalog.
- Initial Runs publish `context.epoch.bound`, `turn.route.completed`, `context.resolved`, and `prompt.composed`.
- Context and prompt plans are immutable object-storage artifacts with size/hash verification.
- Approval resume restores saved LLM/tool state and does not rerun epoch binding, routing, selection, or prompt composition.

## Frontend

- Added a top-level Writing/Story Bible mode.
- Added the Story Bible navigation, node browser/editor, relation editor, progression editor, history, type/category/tag management, and routing settings.
- Preserved the approved routing labels:
  - `规则匹配 + Embedding`
  - `直接使用 LLM`
  - `规则匹配 + Embedding，LLM 兜底`
- Added mobile navigation, node-list, and Agent-chat drawers so the editor is not compressed by the desktop right panel.
- Run completion reloads the Story Bible workspace so approved Agent changes become visible.

## Verification

Verified on 2026-07-16:

- backend full suite: 633 tests passed after the final legacy-route cleanup;
- frontend full suite: 274 tests passed;
- frontend production build passed;
- concurrent identical Context Epoch integration test passed;
- forbidden Card and Story Bible version scans returned no production matches;
- the remaining Card-name matches are negative frontend assertions;
- desktop 1440x900 and tablet 1024x768 screenshots showed no clipping or panel overlap;
- Chrome device emulation at 390x844 reported `innerWidth=390`, document/body `scrollWidth=390`, and all fixed controls inside the viewport;
- mobile navigation and node drawers opened at 310px with a backdrop, and the Agent chat drawer opened at 92vw without resizing the editor;
- `git diff --check` passed.

## Remaining Operational Risks

- Live Run event notification is process-local; durable event replay remains correct, but multi-instance fan-out needs shared infrastructure.
- Large checkpoint states above the inline limit need a dedicated object-storage checkpoint artifact path.
- Event/checkpoint compaction and archive retention are not implemented.

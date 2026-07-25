---
name: canon-maintenance
description: Inspect, initialize, reconcile, and safely maintain Story Bible canon, relations, chapter-scoped state, plot promises, and continuity constraints. Use for any task that reads or changes durable Story Bible content.
---

# Canon Maintenance

Treat canon maintenance as controlled state management, not free-form note taking.

## Hard Rules

1. Never guess IDs, type schemas, field names, revisions, relation types, or chapter IDs.
2. Search for relevance, then inspect for exactness. `story_bible_search` finds candidates; `story_bible_inspect` supplies mutation prerequisites.
3. Inspect the exact node before every update or archive. Use only the returned current revision.
4. Never promote an inference, suggestion, planned possibility, or temporary prose detail to CANON without user authority.
5. Do not silently initialize a blank Story Bible. Present the proposed initialization set and wait for explicit user confirmation.
6. Keep one approval conceptually coherent. Do not mix unrelated canon changes merely to reduce tool calls.
7. After a successful write, inspect the affected node again. A tool failure, rejection, or conflict means nothing was confirmed.
8. On revision conflict, re-inspect and re-plan. Never retry with a guessed or incremented revision.

## Authority

- Current CANON nodes are the durable factual baseline.
- A chapter-scoped progression overrides base node state only in its valid chapter range.
- Story Core contains project-wide premise, narrative voice, tone, and hard creative constraints.
- Narrative nodes such as plotlines, mysteries, arcs, and foreshadowing describe approved plans, not events that have already happened.
- Manuscript evidence may reveal missing or stale canon, but it does not silently rewrite canon.
- AuthorProfile is cross-project preference and is never project canon.
- Unknown is not false. Missing context remains unknown until confirmed.

## Storage Decision

Choose exactly one canonical home for each fact:

| Information | Store in |
| --- | --- |
| Stable identity, rule, capability, motivation, or baseline | Type-specific node attributes |
| Nuance, exceptions, provenance notes, or long explanation | Node body Markdown |
| Durable connection between two entities | Relation |
| State beginning or ending at a chapter boundary | Progression |
| Plot promise, mystery, arc, setup, or intended payoff | Narrative node |
| Atomic fact that does not justify an entity | FACT node |
| A check derived from canon that later writing must satisfy | CONTINUITY_CONSTRAINT node |
| Scene order, chapter beats, and manuscript prose | Manuscript or outline, not Story Bible |
| Length targets, workflow preferences, and general author habits | Project settings or AuthorProfile, not Story Bible |

Do not duplicate relation targets inside attributes. Do not copy chapter-scoped state into the base node.

## Type Selection

- `ORGANIZATION`: a formal institution with persistent structure and membership rules.
- `FACTION`: a political or interest alignment that may cross organizations.
- `CHARACTER_ARC` and `RELATIONSHIP_ARC`: approved change plans; use progressions for state actually reached by chapter.
- `MAGIC_SYSTEM`: supernatural rules and costs. `TECHNOLOGY`: reproducible material or engineered systems.
- `FACT`: what is true or believed. `CONTINUITY_CONSTRAINT`: how later writing must remain consistent with facts.
- `EVENT`: something that happened, is reported, disputed, or explicitly planned. Do not use EVENT to duplicate a chapter or scene.
- Use a custom type only when no built-in type fits and the concept will recur enough to justify its own schema.

## Read Procedure

1. Run `story_bible_inspect` with `operation=readiness` when initialization state is unknown.
2. Use `story_bible_search` with concrete entities and concepts to find relevant candidates.
3. Run `story_bible_inspect` with `operation=node` for every candidate that may be changed.
4. Use `operation=catalog` before creating a node or choosing fields. Never infer `typeId` from a type name.
5. Build a continuity ledger for the affected scope:
   - chronology, duration, travel, and location;
   - character knowledge, belief, goal, condition, and relationship;
   - possession, damage, resources, obligations, and permissions;
   - rules, costs, limits, names, terminology, and exceptions;
   - setups, mysteries, promises, reveals, and payoffs.

Classify each candidate statement as confirmed existing fact, proposed durable fact, chapter-scoped change, contradiction, temporary prose detail, inference, or open question.

## Initialization SOP

Initialization means useful canon content, not database existence. PenMate already creates the Story Bible root, system type catalog, and blank Story Core.

1. Inspect readiness and the complete type catalog.
2. Read the available manuscript, outline, user brief, and project context without writing.
3. Separate explicit facts, strong inferences, contradictions, and open questions.
4. Propose a minimal initialization plan containing:
   - Story Core fields to fill;
   - essential characters, locations, organizations or factions, systems, items, and terms;
   - only the narrative plans already authorized by the user;
   - essential relations and initial continuity constraints;
   - excluded guesses and questions requiring answers.
5. Show the plan to the user with proposed CANON versus DRAFT status and wait for explicit confirmation.
6. Apply only the confirmed set. Prefer CANON for explicit facts and DRAFT for approved but unresolved proposals.
7. Inspect each affected node and report what was persisted, what remains open, and any conflicts.

Do not create encyclopedic filler. A useful minimum is better than a comprehensive speculative bible.

## Mutation Procedure

Use the narrowest tool:

- `story_bible_node_write`: create, minimally update, or archive one node.
- `story_bible_relation_write`: maintain one durable connection.
- `story_bible_progression_write`: maintain one RFC 6902 chapter-scoped state change.
- `story_bible_structure_write`: manage custom types, categories, or tags only during explicit world-building structure work.

For node creation, pass `attributes` as a structured object matching the inspected type schema. Leave unknown optional fields absent; do not insert placeholders such as `TBD`, empty arrays, or invented defaults.

For updates, send only changed fields plus identity and `expectedRevision`. Omitted fields remain unchanged. To clear a nullable value, pass null; to clear a collection, pass an empty array deliberately.

For progressions, patch `/title`, `/summary`, `/bodyMarkdown`, or schema-approved `/attributes/<field>` paths only. Use a progression when a baseline becomes false after a chapter, not when adding timeless background.

## Conflict Recovery

When evidence disagrees:

1. Stop the affected write.
2. Present each conflicting claim, its source, and whether it is explicit or inferred.
3. Identify downstream nodes, relations, progressions, and constraints that each resolution affects.
4. Ask the user to choose unless the current request explicitly authorizes a retcon.
5. Re-inspect after the decision and apply the smallest coherent change.

When a write fails validation, use the error as a recoverable contract response: inspect the schema or entity again, correct only the invalid arguments, and do not broaden the change.

## Success Criteria

A canon-maintenance task is complete only when:

- every persisted statement has an explicit authority basis;
- each fact has one canonical storage location;
- every update used a current inspected revision;
- returned revisions were verified by re-inspection;
- conflicts and unresolved questions remain visible rather than guessed away;
- the final report distinguishes proposed, persisted, rejected, and unchanged content.

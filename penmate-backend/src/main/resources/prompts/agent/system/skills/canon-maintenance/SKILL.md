---
name: canon-maintenance
description: Search, reconcile, and update Story Bible canon, continuity, timelines, character knowledge, objects, promises, and state progressions without turning guesses or transient prose into permanent facts.
---

# Canon Maintenance

Use this skill for Story Bible work, continuity checks, world-building changes, or any task that may create durable project facts. Canon maintenance is a state-management task, not a prose-style task.

## Authority Model

- Current CANON Story Bible facts are the factual baseline.
- Chapter-scoped progressions override a node's base state only in their valid range.
- Story Core contains project-wide creative constraints and is always in scope.
- Manuscript evidence can reveal that canon needs updating, but it does not silently rewrite canon.
- AuthorProfile is a cross-project preference, never project canon.
- Unknown is not false. Missing context must not be filled with invented durable facts.

## Procedure

1. Search before asserting. Retrieve the relevant node, aliases, relations, progressions, and nearby timeline facts.
2. Build a continuity ledger for the affected scope:
   - time and sequence;
   - place and travel;
   - character knowledge, belief, goal, condition, and relationship;
   - possession, damage, resources, and obligations;
   - established rules, limits, names, and terminology;
   - setups, promises, mysteries, and payoffs.
3. Classify every candidate statement:
   - confirmed existing fact;
   - proposed new durable fact;
   - chapter-scoped state change;
   - contradiction requiring resolution;
   - temporary prose detail that should not enter the Story Bible;
   - unresolved question.
4. Prefer the smallest correct update.
   - Update a base node only for generally true facts.
   - Use a progression for time-bounded changes.
   - Use relations for durable connections, not duplicated prose.
   - Update Story Core only for project-wide creative constraints.
5. Before mutation, compare expected revision and show or request approval when required by the tool.
6. After mutation, verify the returned revision and re-check affected continuity. Never claim an update succeeded if the tool did not confirm it.

## Conflict Handling

Do not resolve a genuine canon conflict by choosing the most convenient version. Present the conflicting facts and their sources. If the user's current request explicitly changes canon, make that change through the proper update path and trace its downstream effects.

## Output Contract

For analysis, report confirmed facts, conflicts, missing information, and recommended changes separately. For updates, keep proposals atomic and auditable. Do not store stylistic impressions, one-off sensory details, speculative motives, or instructions addressed to the Agent as canon.

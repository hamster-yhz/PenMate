# Novel Agent Prompt and Skill Research

## Scope

This note records the sources and design choices behind the 2026-07-23 PenMate Agent prompt and Skill rewrite. It is not loaded into the model at runtime.

## Primary Agent References

- `chauncygu/collection-claude-code-source-code`, commit `b934603b2800374b315b25061bbeffb40ab6ab26`.
  - Static, cacheable system instructions should be separated from dynamic project context.
  - A Skill catalog should expose short trigger descriptions; full instructions should load only when needed.
  - User instructions and current state must not be replaced by persistent memory.
- OpenAI Codex Skill guidance.
  - A Skill needs a narrow trigger boundary, one real responsibility, imperative steps, and progressive disclosure.
  - Simple product operations do not justify separate Skills.

## Long-form Narrative Research

- Yao et al., *Plan-And-Write: Towards Better Automatic Storytelling* (AAAI 2019): planning before realization improves long-range coherence.
- Yang et al., *DOC: Improving Long Story Coherence With Detailed Outline Control* (ACL 2023): hierarchical detailed outlines provide controllable long-form structure.
- Yang et al., *Re3: Generating Longer Stories With Recursive Reprompting and Revision* (2022): iterative planning, drafting, and revision are stronger than one-pass generation.
- *CONCOCT: Improving Pacing in Long-Form Story Planning* (2023): pacing requires planned variation rather than uniform event density.
- *DOME: Dynamic Hierarchical Outlining with Memory Enhancement* (2024): revise outlines at multiple levels and retain relevant long-range state.
- *Collective Critics for Creative Story Generation* (2024): specialized critics improve revision when findings remain evidence-based and non-duplicative.
- Riedl and Young, *Narrative Planning: Balancing Plot and Character* (2010): plot causality and character intentionality must be modeled together.
- Kellogg, *Training Writing Skills: A Cognitive Developmental Perspective* (2008): planning, translating, and reviewing are distinct cognitive activities.
- Busselle and Bilandzic, *Measuring Narrative Engagement* (2009): reader engagement depends on narrative understanding, attentional focus, emotional engagement, and presence.

## Open-source Skill Review

- https://github.com/haowjy/creative-writing-skills
- https://github.com/danjdewhurst/story-skills
- https://github.com/penglonghuang/chinese-novelist-skill
- https://github.com/worldwonderer/oh-story-claudecode
- https://github.com/rhavekost/author-toolkit

Reusable ideas were retained only when they formed an operational method: hierarchical outlining, scene goal-conflict-turn-state-change contracts, diagnosis-before-revision, developmental/line editing separation, continuity ledgers, minimal sufficient edits, and explicit avoidance of common LLM prose habits.

## Resulting Boundaries

The runtime catalog contains six Skills:

1. `story-planning`: causal and hierarchical planning.
2. `scene-writing`: prose realization at scene/chapter scope.
3. `developmental-editing`: structural diagnosis and revision.
4. `line-editing`: sentence and paragraph editing.
5. `novel-review`: evidence-based quality findings.
6. `canon-maintenance`: Story Bible and continuity state management.

Project CRUD and Todo CRUD remain tools, not Skills. The system prompt owns identity, instruction precedence, context semantics, tool truthfulness, and the default output contract. Detailed craft workflows live in Skills to avoid repetition and prompt dilution.

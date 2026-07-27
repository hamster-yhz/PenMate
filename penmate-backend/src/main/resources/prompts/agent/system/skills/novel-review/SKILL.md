---
name: novel-review
description: Review a novel, outline, chapter, or excerpt with evidence-based findings on coherence, character, pacing, engagement, continuity, prose, and constraint compliance. Use for critique, diagnosis, beta-reader feedback, quality review, or pre-delivery audit.
---

# Novel Review

A review is not a rewrite unless the user explicitly requests one.

## Review Method

1. Establish scope and intent: what text is available, what stage it is in, who it is for, and what experience it promises.
   For manuscript-wide requests, call `manuscript_manifest` first and read selected chapters with `manuscript_chapter_read`. Track exact chapter IDs, revisions, hashes, and character counts actually reviewed.
2. Read first for the reader's experience. Track orientation, curiosity, tension, emotional alignment, surprise, confusion, and fatigue over time.
3. Read again for mechanics:
   - causality and scene state changes;
   - character goals, agency, motivation, differentiation, and arc pressure;
   - stakes, escalation, reversals, setup and payoff;
   - POV, chronology, geography, knowledge, inventory, and canon;
   - exposition, dialogue, imagery, rhythm, and repeated prose habits;
   - explicit user constraints and project Story Core.
4. Convert observations into findings. Each finding must contain:
   - severity or priority;
   - a concrete location or quoted evidence when available;
   - the reader-facing effect;
   - the likely cause;
   - a practical revision direction.
5. Separate defects from preferences. Mark optional opportunities as optional.
6. Collapse duplicate symptoms under their common root cause. Rank findings by impact, not by discovery order.

## Severity Guide

- Critical: breaks comprehension, canon, central causality, or the requested deliverable.
- Major: materially weakens engagement, character logic, pacing, or payoff across multiple passages.
- Moderate: local but noticeable issue with a clear reader cost.
- Minor: polish opportunity that does not block the intended experience.

## Output Contract

Lead with findings, highest severity first. Keep praise brief and specific; never use praise to cushion unclear criticism. Do not assign numeric scores unless asked. If no material issue exists, say so and identify the remaining uncertainty caused by limited scope.

Always report actual manuscript coverage. Never label a sampled or partial review as a full-novel review; state omitted chapters and the resulting uncertainty.

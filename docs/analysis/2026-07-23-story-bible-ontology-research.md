# Story Bible Ontology Research

## Scope

This note defines the smallest general-purpose Story Bible ontology for PenMate. It separates four concerns that are often mixed together:

1. stable entity profiles;
2. durable relationships between entities;
3. chapter-scoped state changes;
4. narrative plans, promises, and payoffs.

The goal is not to model every possible fictional concept. The built-in ontology should cover an ordinary novel, while genre-specific concepts remain custom node types.

## Research Base

### Papers

1. Fan, Lewis, and Dauphin. [Hierarchical Neural Story Generation](https://doi.org/10.18653/v1/P18-1082), ACL 2018. Separating planning from surface realization improves long-form generation.
2. Yao et al. [Plan-and-Write: Towards Better Automatic Storytelling](https://doi.org/10.1609/aaai.v33i01.33017378), AAAI 2019. Explicit dynamic planning improves story generation over direct generation.
3. Clark, Ji, and Smith. [Neural Text Generation in Stories Using Entity Representations as Context](https://doi.org/10.18653/v1/N18-1204), NAACL 2018. Persistent entity representations improve character and entity coherence.
4. Rashkin et al. [PlotMachines: Outline-Conditioned Generation with Dynamic Plot State Tracking](https://doi.org/10.18653/v1/2020.emnlp-main.349), EMNLP 2020. Long generation benefits from an outline plus a changing plot-state representation.
5. Goldfarb-Tarrant et al. [Content Planning for Neural Story Generation with Aristotelian Rescoring](https://doi.org/10.18653/v1/2020.emnlp-main.351), EMNLP 2020. Character goals and narrative structure are useful planning signals.
6. Riedl and Young. [Narrative Planning: Balancing Plot and Character](https://doi.org/10.1613/jair.2989), JAIR 2010. Plot causality must remain compatible with character intentions and goals.
7. Li et al. [Story Generation with Crowdsourced Plot Graphs](https://doi.org/10.1609/aaai.v27i1.8649), AAAI 2013. Plot graphs capture event alternatives and causal progression without replacing rich story content.
8. Yang et al. [Re3: Generating Longer Stories With Recursive Reprompting and Revision](https://doi.org/10.18653/v1/2022.emnlp-main.296), EMNLP 2022. Long stories need recursive planning, drafting, and revision rather than one-pass generation.
9. Yang et al. [DOC: Improving Long Story Coherence With Detailed Outline Control](https://doi.org/10.18653/v1/2023.acl-long.190), ACL 2023. Detailed outlines provide controllable long-range coherence.
10. Mostafazadeh et al. [GLUCOSE: GeneraLized and COntextualized Story Explanations](https://doi.org/10.18653/v1/2020.emnlp-main.370), EMNLP 2020. Story understanding needs explicit causes, motivations, preconditions, and effects.
11. Barzilay and Lapata. [Modeling Local Coherence: An Entity-Based Approach](https://doi.org/10.1162/coli.2008.34.1.1), Computational Linguistics 2008. Tracking entities across discourse is a strong coherence signal.
12. Rashkin et al. [Event2Mind: Commonsense Inference on Events, Intents, and Reactions](https://doi.org/10.18653/v1/P18-1043), ACL 2018. Events should be connected to participant intent and reaction, not stored as isolated summaries.
13. Rashkin et al. [Modeling Naive Psychology of Characters in Simple Commonsense Stories](https://doi.org/10.18653/v1/P18-1213), ACL 2018. Character motivation, emotion, and reaction are distinct dimensions of narrative state.
14. Cai et al. [Temporal Knowledge Graph Completion: A Survey](https://doi.org/10.24963/ijcai.2023/734), IJCAI 2023. Facts that change over time require explicit temporal scope.
15. Ji et al. [A Survey on Knowledge Graphs: Representation, Acquisition, and Applications](https://doi.org/10.1109/TNNLS.2021.3070843), IEEE TNNLS 2021. Entity identity, relation semantics, acquisition, and quality control are separate knowledge-graph concerns.
16. Baldassano, Hasson, and Norman. [Representation of Real-World Event Schemas during Narrative Perception](https://doi.org/10.1523/JNEUROSCI.0251-18.2018), Journal of Neuroscience 2018. Event schemas provide boundaries and expectations that support narrative understanding.

### Product and Craft References

1. [Novelcrafter Codex](https://www.novelcrafter.com/) presents a linked wiki of characters, places, lore, automatic tracking, progressions, and series sharing.
2. [Sudowrite Story Bible](https://sudowrite.com/) organizes the workflow from idea and outline through chapter beats and generation.
3. [Campfire Writing](https://www.campfirewriting.com/write) separates character sheets, relationship webs, timelines, maps, calendars, systems, and encyclopedia articles.
4. [Plottr](https://plottr.com/features/) combines character/place profiles, custom attributes, scene attributes, plotlines, timelines, and reusable templates.
5. [Scrivener](https://www.literatureandlatte.com/scrivener/overview) keeps manuscript structure, research, metadata, character/location templates, corkboards, and outlines close without forcing one schema.
6. [Dabble](https://www.dabblewriter.com/features) separates plot grids, plotlines, plot points, character profiles, and worldbuilding notes.
7. [Aeon Timeline](https://www.aeontimeline.com/) emphasizes events, people, places, custom attributes, dependencies, chronology, and cross-references.
8. [World Anvil](https://www.worldanvil.com/features) represents worldbuilding as linked articles, timelines, maps, family trees, and secrets.
9. [Save the Cat beat sheets](https://savethecat.com/beat-sheets) provide a reusable planning vocabulary, but beats remain planning aids rather than permanent canon entities.
10. [Helping Writers Become Authors story structure series](https://www.helpingwritersbecomeauthors.com/secrets-story-structure-complete-series/) distinguishes structural turning points from character and world reference material.

No claim is made about the private storage technology used by these products. The comparison concerns their public author-facing information models.

## Decisions

### Built-in node set

The existing 17 node types are retained for compatibility. Two missing general-purpose concepts are added:

- `RELATIONSHIP_ARC`: the evolving dramatic state of a relationship. A static edge is insufficient for trust, intimacy, rivalry, or dependence that changes across the book.
- `CULTURE`: shared values, customs, taboos, language, and social organization do not fit cleanly into a location, organization, or faction.

Scenes and chapters are not duplicated as Story Bible nodes. They remain manuscript structure. Species, religions, vehicles, planets, schools, legal systems, and genre-specific concepts should be custom types unless usage proves that they are universally required.

### Storage boundary

| Information | Storage |
| --- | --- |
| Stable identity, description, rules, capabilities | Type-specific node attributes |
| Rich exceptions and author notes | Node body Markdown |
| Durable connection between two entities | Relation |
| State that changes at a chapter boundary | Progression |
| Plot promise, dramatic question, planned payoff | Narrative node |
| Chapter prose and scene order | Manuscript, not Story Bible |

Examples:

- A character's occupation can be a profile field; a job change belongs in a progression.
- Ownership is a relation; a transfer of ownership is a progression or event until temporal relations are introduced.
- A mystery's hidden truth is a field; who currently knows it is chapter-scoped state.
- A location's parent region is a relation; repeating the parent ID in attributes would create two sources of truth.

### Field policy

- Every built-in type receives a small, ordered schema with sections, descriptions, controls, placeholders, and enum labels.
- Fields are optional by default. Authors can start with a title and summary and enrich the entry later.
- Stable field keys are English identifiers. Display labels are localized metadata and can change without destroying stored values.
- Built-in schemas accept legacy extra properties during the upgrade window so existing data is not rejected.
- The first-party editor supports text, multiline text, integer/number, boolean, enum, and string-list controls.
- Nested objects are not used in built-in schemas because they are harder for authors, agents, patches, and mobile UI to edit reliably.

### Agent maintenance policy

The agent should search and reconcile before proposing a change. It should produce the smallest mutation, cite manuscript evidence, and keep inferred facts distinct from explicit facts. Automatic extraction may create proposals but must not silently promote them to canon. This follows the planning, entity-state, commonsense, and temporal findings above.

## Minimum Acceptance Criteria

1. New Story Bibles receive every built-in schema and the two new types.
2. Existing Story Bibles can idempotently synchronize system definitions without losing nodes or custom fields.
3. Switching node type changes the visible form immediately.
4. Every built-in type exposes meaningful, type-specific fields.
5. Custom types can create and edit stable keys, labels, control types, sections, hints, and enum options without raw JSON editing.
6. Node CRUD round-trips structured values without coercing arrays, booleans, numbers, or empty values incorrectly.
7. Desktop and mobile layouts keep labels, help text, and controls readable without nested-card clutter.

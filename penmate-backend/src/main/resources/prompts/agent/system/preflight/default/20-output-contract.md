只输出结构化 Json 决策结果。
不要直接生成最终给用户的正文内容。

输出字段契约：
- `behaviorType`: `WRITE` / `REWRITE` / `WORLD_BUILD` / `QUESTION_ANSWER` / `STORY_BIBLE_QUERY_CANDIDATE`
- `executionPromptProfile`: 例如 `default` / `world-build`
- `includeStyleContext`: boolean
- `includeRagContext`: boolean
- `includeStoryBibleContext`: boolean
- `intentTags`: string array，可多标签，例如 `DRAFT_GENERATION`、`CONTINUITY_CHECK`、`STYLE_ALIGNMENT`、`STORY_BIBLE_QUERY`、`CLARIFICATION`
- `hardConstraints`: string array，列出不可违反的硬约束
- `enabledSkills`: string array，列出应启用的 skill
- `enabledTools`: string array，列出应启用的 tool
- `outputExpectation`: string 或 null，描述期望输出
- `needsApproval`: boolean
- `needsStoryBibleUpdate`: boolean
- `needsClarification`: boolean；仅在严重歧义、缺少关键约束、必须先追问时为 true
- `reasoningSummary`: string，简述判定依据

要求：
- 所有 boolean 必须输出 true/false，不得输出字符串。
- 数组字段必须输出数组；没有值时输出空数组。
- 轻微歧义可继续执行并在 `reasoningSummary` 说明，但 `needsClarification` 必须为 false。
- 严重歧义必须将 `needsClarification` 设为 true，并优先让主编排进入澄清路径。
- 当任务涉及设定核对、长期世界观一致性或 canon 查询时，应考虑 `includeStoryBibleContext=true`。
- 当本轮输出可能产生新的 canon / proposal 线索时，应考虑 `needsStoryBibleUpdate=true`。

UPDATE story_bible_node_types
SET field_schema_json = '{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "type": "object",
  "properties": {
    "premise": { "type": "string", "title": "核心前提" },
    "themes": { "type": "array", "title": "主题", "items": { "type": "string" } },
    "targetAudience": { "type": "string", "title": "目标读者" },
    "contentRating": { "type": "string", "title": "内容分级", "enum": ["GENERAL", "TEEN", "MATURE"] },
    "narrativePov": { "type": "string", "title": "叙事视角", "enum": ["FIRST_PERSON", "SECOND_PERSON", "THIRD_LIMITED", "THIRD_OMNISCIENT", "MULTIPLE"] },
    "tense": { "type": "string", "title": "叙事时态", "enum": ["PAST", "PRESENT", "MIXED"] },
    "chapterTargetWords": { "type": "integer", "title": "章节目标字数", "minimum": 100, "maximum": 50000 },
    "hardConstraints": { "type": "array", "title": "硬约束", "items": { "type": "string" } },
    "softPreferences": { "type": "array", "title": "软偏好", "items": { "type": "string" } },
    "forbiddenElements": { "type": "array", "title": "禁用元素", "items": { "type": "string" } }
  },
  "additionalProperties": false
}'::jsonb,
updated_at = CURRENT_TIMESTAMP(3)
WHERE type_code = 'STORY_CORE' AND archived_at IS NULL;

WITH ranked_core AS (
    SELECT n.id, row_number() OVER (
        PARTITION BY n.story_bible_id ORDER BY n.created_at, n.id
    ) AS position
    FROM story_bible_nodes n
    JOIN story_bible_node_types t ON t.type_id = n.type_id
    WHERE t.type_code = 'STORY_CORE' AND n.deleted_at IS NULL AND n.archived_at IS NULL
)
UPDATE story_bible_nodes n
SET archived_at = CURRENT_TIMESTAMP(3), updated_at = CURRENT_TIMESTAMP(3)
FROM ranked_core ranked
WHERE n.id = ranked.id AND ranked.position > 1;

UPDATE story_bible_nodes n
SET inclusion_policy = 'ALWAYS_INCLUDE', canon_status = 'CANON', updated_at = CURRENT_TIMESTAMP(3)
FROM story_bible_node_types t
WHERE n.type_id = t.type_id AND t.type_code = 'STORY_CORE'
  AND n.deleted_at IS NULL AND n.archived_at IS NULL;

INSERT INTO story_bible_nodes(
    node_id, story_bible_id, type_id, title, summary, body_markdown, attributes_json,
    inclusion_policy, canon_status, revision, created_by, updated_by
)
SELECT sb.story_bible_id, sb.story_bible_id, t.type_id, 'Story Core', '', '', '{}'::jsonb,
       'ALWAYS_INCLUDE', 'CANON', 1, p.owner_user_id, p.owner_user_id
FROM story_bibles sb
JOIN novel_projects p ON p.project_id = sb.project_id
JOIN story_bible_node_types t ON t.story_bible_id = sb.story_bible_id AND t.type_code = 'STORY_CORE'
WHERE sb.deleted_at IS NULL AND p.deleted_at IS NULL
  AND NOT EXISTS (
      SELECT 1 FROM story_bible_nodes n
      WHERE n.story_bible_id = sb.story_bible_id AND n.type_id = t.type_id
        AND n.deleted_at IS NULL AND n.archived_at IS NULL
  );

CREATE OR REPLACE FUNCTION enforce_story_core_invariants()
RETURNS trigger AS $$
DECLARE
    next_type_code VARCHAR(80);
    old_type_code VARCHAR(80);
BEGIN
    SELECT type_code INTO next_type_code FROM story_bible_node_types WHERE type_id = NEW.type_id;
    IF TG_OP = 'UPDATE' THEN
        SELECT type_code INTO old_type_code FROM story_bible_node_types WHERE type_id = OLD.type_id;
        IF old_type_code = 'STORY_CORE' AND OLD.deleted_at IS NULL AND OLD.archived_at IS NULL
           AND (next_type_code <> 'STORY_CORE' OR NEW.deleted_at IS NOT NULL OR NEW.archived_at IS NOT NULL) THEN
            RAISE EXCEPTION 'Story Core node cannot be removed or retyped';
        END IF;
    END IF;
    IF next_type_code = 'STORY_CORE' AND NEW.deleted_at IS NULL AND NEW.archived_at IS NULL THEN
        PERFORM pg_advisory_xact_lock(NEW.story_bible_id);
        IF EXISTS (
            SELECT 1 FROM story_bible_nodes n
            JOIN story_bible_node_types t ON t.type_id = n.type_id
            WHERE n.story_bible_id = NEW.story_bible_id AND t.type_code = 'STORY_CORE'
              AND n.node_id <> NEW.node_id AND n.deleted_at IS NULL AND n.archived_at IS NULL
        ) THEN
            RAISE EXCEPTION 'Story Core node already exists';
        END IF;
        NEW.inclusion_policy := 'ALWAYS_INCLUDE';
        NEW.canon_status := 'CANON';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_story_core_invariants ON story_bible_nodes;
CREATE TRIGGER trg_story_core_invariants
BEFORE INSERT OR UPDATE ON story_bible_nodes
FOR EACH ROW EXECUTE FUNCTION enforce_story_core_invariants();

-- Demo book and Story Bible. IDs in the 920000 range are reserved for explicit test data.
INSERT INTO novel_projects(project_id, owner_user_id, title, summary, status, structure_revision)
VALUES (920001, 920001, 'The Ashen City', 'A compact PostgreSQL demo project.', 1, 1)
ON CONFLICT (project_id) DO UPDATE SET
    title = EXCLUDED.title,
    summary = EXCLUDED.summary,
    updated_at = CURRENT_TIMESTAMP(3);

INSERT INTO novel_volumes(volume_id, project_id, title, sort_order, description)
VALUES (920101, 920001, 'Volume One', 10, 'The opening volume.')
ON CONFLICT (volume_id) DO UPDATE SET
    title = EXCLUDED.title,
    sort_order = EXCLUDED.sort_order,
    updated_at = CURRENT_TIMESTAMP(3);

INSERT INTO novel_outline_nodes(outline_node_id, project_id, parent_id, title, node_type, sort_order, content)
VALUES (920201, 920001, NULL, 'Arrival at the Gate', 'chapter', 10, 'The protagonist reaches the sealed city.')
ON CONFLICT (outline_node_id) DO UPDATE SET
    title = EXCLUDED.title,
    content = EXCLUDED.content,
    updated_at = CURRENT_TIMESTAMP(3);

INSERT INTO novel_chapters(
    chapter_id, project_id, volume_id, outline_node_id, title, sort_order, status,
    word_count, excerpt, content_object_key, content_size, content_revision, storage_provider)
VALUES (
    920301, 920001, 920101, 920201, 'The Sealed Gate', 10, 1,
    860, 'Ash drifted across the northern road.', 'demo/920001/chapters/920301.md', 4096, 1, 's3')
ON CONFLICT (chapter_id) DO UPDATE SET
    title = EXCLUDED.title,
    excerpt = EXCLUDED.excerpt,
    content_revision = EXCLUDED.content_revision,
    updated_at = CURRENT_TIMESTAMP(3);

INSERT INTO story_bibles(story_bible_id, project_id, title, description, content_revision)
VALUES (920401, 920001, 'Ashen City Story Bible', 'Canonical demo data.', 1)
ON CONFLICT (story_bible_id) DO UPDATE SET
    title = EXCLUDED.title,
    description = EXCLUDED.description,
    updated_at = CURRENT_TIMESTAMP(3);

INSERT INTO story_bible_node_types(
    type_id, story_bible_id, type_code, semantic_family, display_name,
    field_schema_json, is_system, sort_order)
VALUES (
    920501, 920401, 'CHARACTER', 'CHARACTER', 'Character',
    '{"type":"object","properties":{"role":{"type":"string"}}}'::jsonb, FALSE, 10)
ON CONFLICT (type_id) DO UPDATE SET
    display_name = EXCLUDED.display_name,
    field_schema_json = EXCLUDED.field_schema_json,
    updated_at = CURRENT_TIMESTAMP(3);

INSERT INTO story_bible_nodes(
    node_id, story_bible_id, type_id, title, summary, body_markdown, attributes_json,
    inclusion_policy, canon_status, revision, created_by, updated_by)
VALUES (
    920601, 920401, 920501, 'Lin Jin', 'A courier carrying a forbidden map.',
    'Lin Jin arrives at the northern gate before dawn.', '{"role":"protagonist"}'::jsonb,
    'AUTO', 'CANON', 1, 920001, 920001)
ON CONFLICT (node_id) DO UPDATE SET
    summary = EXCLUDED.summary,
    attributes_json = EXCLUDED.attributes_json,
    revision = EXCLUDED.revision,
    updated_at = CURRENT_TIMESTAMP(3);

INSERT INTO style_profiles(
    style_id, project_id, name, is_default, pace, tone, narrative_focus,
    prompt_template, sample_text)
VALUES (
    920701, 920001, 'Tense and restrained', TRUE, 'medium', 'dark', 'character',
    'Use restrained prose and concrete sensory detail.', 'Ash crossed the road like dry snow.')
ON CONFLICT (style_id) DO UPDATE SET
    name = EXCLUDED.name,
    is_default = EXCLUDED.is_default,
    updated_at = CURRENT_TIMESTAMP(3);

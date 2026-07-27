-- Stable Agent data for replay and repository tests.
INSERT INTO agent_sessions(
    session_id, project_id, owner_user_id, title, session_status, bound_style_id)
VALUES (920801, 920001, 920001, 'Ashen City writing session', 'ACTIVE', 920701)
ON CONFLICT (session_id) DO UPDATE SET
    title = EXCLUDED.title,
    bound_style_id = EXCLUDED.bound_style_id,
    updated_at = CURRENT_TIMESTAMP(3);

INSERT INTO agent_messages(
    message_id, session_id, role, message_kind, content_markdown, delivery_status, seq_no)
VALUES (920811, 920801, 'user', 'CHAT', 'Continue the gate scene.', 'FINAL', 1)
ON CONFLICT (message_id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;

INSERT INTO agent_turns(
    turn_id, session_id, turn_seq, user_message_id, run_id, turn_status)
VALUES (920821, 920801, 1, 920811, 920831, 'COMPLETED')
ON CONFLICT (turn_id) DO UPDATE SET
    run_id = EXCLUDED.run_id,
    turn_status = EXCLUDED.turn_status,
    updated_at = CURRENT_TIMESTAMP(3);

INSERT INTO agent_runs(
    run_id, project_id, session_id, turn_id, owner_user_id, run_status, run_phase,
    latest_event_seq, started_at, finished_at)
VALUES (
    920831, 920001, 920801, 920821, 920001, 'COMPLETED', 'finished',
    0, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3))
ON CONFLICT (run_id) DO UPDATE SET
    run_status = EXCLUDED.run_status,
    run_phase = EXCLUDED.run_phase,
    updated_at = CURRENT_TIMESTAMP(3);

INSERT INTO agent_run_inputs(
    run_id, prompt_snapshot, chapter_id, selected_text,
    style_snapshot_json, model_snapshot_json, plugin_bindings_json, input_hash)
VALUES (
    920831, 'Continue the gate scene.', 920301, NULL,
    '{"styleId":920701}'::jsonb, '{"model":"demo"}'::jsonb, '[]'::jsonb,
    'demo-input-920831')
ON CONFLICT (run_id) DO UPDATE SET prompt_snapshot = EXCLUDED.prompt_snapshot;

INSERT INTO agent_session_style_bindings(binding_id, session_id, style_id, source)
VALUES (920841, 920801, 920701, 'PROJECT_DEFAULT')
ON CONFLICT (binding_id) DO NOTHING;

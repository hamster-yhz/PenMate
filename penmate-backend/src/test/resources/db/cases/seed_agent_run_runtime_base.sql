INSERT INTO agent_sessions(session_id, project_id, owner_user_id, title, session_status)
VALUES (90001, 101, 201, 'Run runtime session', 'ACTIVE');

INSERT INTO agent_turns(turn_id, session_id, turn_seq, user_message_id, run_id, turn_status)
VALUES (50001, 90001, 1, 60001, 70001, 'PENDING');

INSERT INTO agent_runs(run_id, project_id, session_id, turn_id, owner_user_id, run_status, run_phase, latest_event_seq)
VALUES (70001, 101, 90001, 50001, 201, 'PENDING', 'created', 0);

INSERT INTO agent_run_inputs(run_id, prompt_snapshot, task_type, chapter_id, selected_text, input_hash)
VALUES (70001, 'Write a suspense opening.', 'WRITE', 30001, 'selected text', 'hash-70001');

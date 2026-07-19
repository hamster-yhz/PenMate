-- Independent rows used for manual lock, lease, and rollback experiments.
INSERT INTO agent_sessions(session_id, project_id, owner_user_id, title, session_status)
VALUES (922001, 920001, 920001, 'Concurrency session', 'ACTIVE')
ON CONFLICT (session_id) DO UPDATE SET updated_at = CURRENT_TIMESTAMP(3);

INSERT INTO agent_turns(turn_id, session_id, turn_seq, turn_status)
VALUES
    (922011, 922001, 1, 'PENDING'),
    (922012, 922001, 2, 'PENDING')
ON CONFLICT (turn_id) DO UPDATE SET updated_at = CURRENT_TIMESTAMP(3);

INSERT INTO agent_runs(
    run_id, project_id, session_id, turn_id, owner_user_id, run_status, run_phase,
    lease_owner, lease_until, execution_token)
VALUES
    (922021, 920001, 922001, 922011, 920001, 'PENDING', 'created', NULL, NULL, 0),
    (922022, 920001, 922001, 922012, 920001, 'PENDING', 'created', NULL, NULL, 0)
ON CONFLICT (run_id) DO UPDATE SET updated_at = CURRENT_TIMESTAMP(3);

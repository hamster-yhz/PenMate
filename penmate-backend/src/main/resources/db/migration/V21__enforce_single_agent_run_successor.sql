ALTER TABLE agent_runs
    ADD UNIQUE KEY uk_agent_runs_predecessor (predecessor_run_id);

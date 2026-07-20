-- Executable boundary rows. Deliberately invalid conflicts belong in integration tests.
INSERT INTO novel_projects(project_id, owner_user_id, title, summary, status, structure_revision)
VALUES (921001, 920001, repeat('B', 200), NULL, 0, 1)
ON CONFLICT (project_id) DO UPDATE SET updated_at = CURRENT_TIMESTAMP(3);

INSERT INTO ops_async_jobs(job_id, job_type, biz_key, project_id, status, last_error_message)
VALUES (921011, 'RAG_REBUILD_PROJECT', 'novel:921001', 921001, 'FAILED', repeat('x', 255))
ON CONFLICT (job_id) DO UPDATE SET
    status = EXCLUDED.status,
    last_error_message = EXCLUDED.last_error_message,
    updated_at = CURRENT_TIMESTAMP(3);

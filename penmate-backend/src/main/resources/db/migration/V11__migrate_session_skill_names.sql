INSERT INTO agent_session_skill_bindings(session_id, skill_name, activated_at)
SELECT session_id,
       CASE skill_name
           WHEN 'planner' THEN 'story-planning'
           WHEN 'writer' THEN 'scene-writing'
           WHEN 'editor' THEN 'line-editing'
           WHEN 'checker' THEN 'novel-review'
           WHEN 'story-bible' THEN 'canon-maintenance'
       END,
       activated_at
FROM agent_session_skill_bindings
WHERE skill_name IN ('planner', 'writer', 'editor', 'checker', 'story-bible')
ON CONFLICT (session_id, skill_name) DO NOTHING;

DELETE FROM agent_session_skill_bindings
WHERE skill_name IN ('planner', 'writer', 'editor', 'checker', 'story-bible');

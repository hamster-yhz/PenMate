-- PenMate 数据库造数：并发 / 回滚 / 幂等等场景
-- Case: DBCASE_ALL_003_CONCURRENCY_ROLLBACK
-- 说明：本文件包含“可执行样本”与“应失败模板(注释)”。

SET NAMES utf8mb4;

-- =========================================================
-- A. 可执行并发样本（状态对照）
-- =========================================================

-- A1. 并发更新样本（基于同一 project_id 记录）
INSERT INTO novel_projects (
  id, project_id, owner_user_id, title, summary, status, structure_revision, created_at, updated_at, deleted_at
) VALUES
  (922001, 922001, 920002, 'DBCASE_并发样本项目', '用于并发更新冲突验证', 1, 1, NOW(3), NOW(3), NULL)
ON DUPLICATE KEY UPDATE
  updated_at = VALUES(updated_at),
  summary = VALUES(summary);

-- A2. 幂等重放样本：审计模块已移除，保留业务实体侧场景。

-- A3. 审批重复审核防重样本：同 request 已有终态 + 再次动作日志
INSERT INTO agent_approval_requests (id, approval_request_id, project_id, task_id, approval_type, payload_json, risk_level, status, requested_by, reviewed_by, reviewed_at, review_comment, created_at, updated_at) VALUES
  (922001, 922001, 920001, 920001, 'publish', JSON_OBJECT('chapterId', 920001), 2, 'approved', 920002, 920001, NOW(3), '首次审批通过', NOW(3), NOW(3))
ON DUPLICATE KEY UPDATE
  status = VALUES(status),
  reviewed_by = VALUES(reviewed_by),
  reviewed_at = VALUES(reviewed_at),
  review_comment = VALUES(review_comment),
  updated_at = VALUES(updated_at);

INSERT INTO agent_approval_actions (id, approval_action_id, request_id, action, operator_id, comment, created_at) VALUES
  (922001, 922001, 922001, 'approve', 920001, '首次通过', NOW(3)),
  (922002, 922002, 922001, 'approve', 920001, '重复审批(应用应拒绝)', NOW(3))
ON DUPLICATE KEY UPDATE created_at = VALUES(created_at);

-- A4. 迁移任务并发样本：同 migration_type 一个 running + 一个 pending
INSERT INTO ops_migrations (id, migration_id, migration_type, status, progress_pct, summary_json, error_msg, started_at, finished_at, created_at, updated_at) VALUES
  (922001, 922001, 'content_to_object_storage', 'running', 30, JSON_OBJECT('migrated', 3, 'failed', 0), NULL, NOW(3), NULL, NOW(3), NOW(3)),
  (922002, 922002, 'content_to_object_storage', 'running', 35, JSON_OBJECT('migrated', 4, 'failed', 0), NULL, NOW(3), NULL, NOW(3), NOW(3))
ON DUPLICATE KEY UPDATE
  status = VALUES(status),
  progress_pct = VALUES(progress_pct),
  updated_at = VALUES(updated_at);

-- =========================================================
-- B. 回滚模板（默认注释，需在事务中手工启用）
-- =========================================================

-- CASE-ROLLBACK-001：跨表写失败回滚（novel_chapter_versions 一致性）
-- START TRANSACTION;
-- INSERT INTO novel_chapter_versions (
--   id, chapter_version_id, chapter_id, version_no, change_type, change_reason, snapshot_object_key, snapshot_etag, snapshot_size, snapshot_checksum, created_by, created_at
-- ) VALUES (
--   922101, 922101, 920001, 99, 'rewrite', 'rollback-test', 'dbcase/rollback/v99.json', 'etag-rb', 1024, 'sha-rb', 920002, NOW(3)
-- );
--
-- -- 人工制造失败：重复唯一键 chapter_id + version_no(2)
-- INSERT INTO novel_chapter_versions (
--   id, chapter_version_id, chapter_id, version_no, change_type, change_reason, snapshot_object_key, snapshot_etag, snapshot_size, snapshot_checksum, created_by, created_at
-- ) VALUES (
--   922102, 922102, 920001, 2, 'rewrite', 'duplicate-for-rollback', 'dbcase/rollback/v2.json', 'etag-rb2', 1024, 'sha-rb2', 920002, NOW(3)
-- );
--
-- 审计表已移除：该段不再适用。
-- COMMIT;
--
-- -- 预期：上面事务应失败回滚，922101/922102 与审计日志均不应落库

-- CASE-CONCURRENCY-001：并发写覆盖模板（应用层应使用版本号/条件保护）
-- UPDATE novel_projects
-- SET summary = 'writer-a', updated_at = NOW(3)
-- WHERE project_id = 922001;
--
-- UPDATE novel_projects
-- SET summary = 'writer-b', updated_at = NOW(3)
-- WHERE project_id = 922001;
-- -- 预期：若无版本控制，后写覆盖前写；应用层需通过条件更新/版本号规避


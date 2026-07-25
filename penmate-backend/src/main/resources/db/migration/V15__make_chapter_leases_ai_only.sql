UPDATE novel_chapters
SET lease_owner_type = NULL,
    lease_owner_id = NULL,
    lease_token = NULL,
    lease_expires_at = NULL
WHERE lease_owner_type IS DISTINCT FROM 'AI';

ALTER TABLE novel_chapters
    ADD CONSTRAINT chk_novel_chapters_ai_lease
    CHECK (
        (lease_owner_type IS NULL
            AND lease_owner_id IS NULL
            AND lease_token IS NULL
            AND lease_expires_at IS NULL)
        OR
        (lease_owner_type = 'AI'
            AND lease_owner_id IS NOT NULL
            AND lease_token IS NOT NULL
            AND lease_expires_at IS NOT NULL)
    );

-- ---------- NOTIFICATION: inbox index matching the keyset order (created_at DESC, id DESC) -----------
-- Replaces idx_notification_user_created; the new index covers the same (user_id, created_at DESC) prefix.
CREATE INDEX IF NOT EXISTS idx_notification_user_created_id
    ON notification (user_id, created_at DESC, id DESC);

DROP INDEX IF EXISTS idx_notification_user_created;

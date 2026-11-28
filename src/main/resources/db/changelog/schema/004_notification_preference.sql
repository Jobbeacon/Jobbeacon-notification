-- ---------- NOTIFICATION_PREFERENCE: the user's notification switches, owned by this service -----------
-- No row means the defaults (everything enabled). A row is created only when the user changes a switch.
CREATE TABLE IF NOT EXISTS notification_preference (
    user_id            UUID        NOT NULL,
    job_match_enabled  BOOLEAN     NOT NULL DEFAULT TRUE,
    updated_at         TIMESTAMPTZ NOT NULL,

    CONSTRAINT pk_notification_preference PRIMARY KEY (user_id)
);

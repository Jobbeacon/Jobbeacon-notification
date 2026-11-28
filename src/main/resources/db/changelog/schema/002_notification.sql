-- ---------- NOTIFICATION: one message to one user, shown in the in-app inbox -----------
-- No FK to user_contact: a notification may be created before the user's contact event arrives.
CREATE TABLE IF NOT EXISTS notification (
    id               UUID         NOT NULL,
    user_id          UUID         NOT NULL,
    type             VARCHAR(50)  NOT NULL,
    title            VARCHAR(255) NOT NULL,
    body             TEXT,
    payload          JSONB,
    dedupe_key       VARCHAR(200) NOT NULL,
    source_event_id  UUID,
    read_at          TIMESTAMPTZ,
    created_at       TIMESTAMPTZ  NOT NULL,

    CONSTRAINT pk_notification PRIMARY KEY (id),
    CONSTRAINT uq_notification_dedupe_key UNIQUE (dedupe_key)
);

CREATE INDEX IF NOT EXISTS idx_notification_user_created
    ON notification (user_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_notification_user_unread
    ON notification (user_id) WHERE read_at IS NULL;

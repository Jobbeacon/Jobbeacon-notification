-- ---------- NOTIFICATION_DELIVERY: one send of a notification through an external channel (EMAIL) -----------
-- In-app and SSE have no row here. The worker claims PENDING rows whose next_retry_at has passed.
CREATE TABLE IF NOT EXISTS notification_delivery (
    id               UUID        NOT NULL,
    notification_id  UUID        NOT NULL,
    channel          VARCHAR(20) NOT NULL,
    status           VARCHAR(20) NOT NULL,
    attempts         INT         NOT NULL DEFAULT 0,
    next_retry_at    TIMESTAMPTZ NOT NULL,
    last_error       TEXT,
    sent_at          TIMESTAMPTZ,
    created_at       TIMESTAMPTZ NOT NULL,
    updated_at       TIMESTAMPTZ NOT NULL,

    CONSTRAINT pk_notification_delivery PRIMARY KEY (id),
    CONSTRAINT uq_notification_delivery_channel UNIQUE (notification_id, channel),
    CONSTRAINT fk_notification_delivery_notification_id
        FOREIGN KEY (notification_id) REFERENCES notification(id) ON DELETE CASCADE,
    CONSTRAINT ck_notification_delivery_status
        CHECK (status IN ('PENDING', 'SENT', 'FAILED'))
);

CREATE INDEX IF NOT EXISTS idx_notification_delivery_pending
    ON notification_delivery (next_retry_at) WHERE status = 'PENDING';

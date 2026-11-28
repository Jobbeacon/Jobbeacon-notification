-- ---------- PROCESSED_EVENT: event ids already handled, so a redelivered event is skipped -----------
CREATE TABLE IF NOT EXISTS processed_event (
    event_id      UUID         NOT NULL,
    event_type    VARCHAR(100) NOT NULL,
    processed_at  TIMESTAMPTZ  NOT NULL,

    CONSTRAINT pk_processed_event PRIMARY KEY (event_id)
);

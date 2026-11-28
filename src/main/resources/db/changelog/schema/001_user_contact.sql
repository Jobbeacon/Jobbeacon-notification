-- ---------- USER_CONTACT: copy of the backend's user data needed to deliver notifications -----------
-- Written only by the user.upserted consumer (full snapshot, last write wins). The backend owns the data.
-- firebase_uid maps the gateway's X-User-Id header to the backend user id used in events.
CREATE TABLE IF NOT EXISTS user_contact (
    user_id       UUID         NOT NULL,
    firebase_uid  VARCHAR(128) NOT NULL,
    email         VARCHAR(255) NOT NULL,
    display_name  VARCHAR(150),
    status        VARCHAR(20)  NOT NULL,
    updated_at    TIMESTAMPTZ  NOT NULL,

    CONSTRAINT pk_user_contact PRIMARY KEY (user_id),
    CONSTRAINT uq_user_contact_firebase_uid UNIQUE (firebase_uid)
);
